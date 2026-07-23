package forestry.beegistics.machine;

import javax.annotation.Nullable;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import com.google.common.collect.ImmutableSet;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.BeegisticsBlockEntities;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.core.utils.SpeciesUtil;

/**
 * The Bee Requester: a grid-connected block that keeps a single target bee stocked in the ME network by <em>requesting
 * real AE2 crafting jobs</em> - never by breeding anything itself. It is a pure {@link ICraftingRequester} (unlike the
 * {@link ApiaryControllerBlockEntity Apiary Controller}, which provides patterns and drives an apiary), so it relies on
 * an <b>Autocraft</b> Apiary Controller elsewhere on the network to actually breed the tree. This mirrors AE2's stock
 * requester and the {@code merequester} block it is adapted from: a network citizen that generates demand, nothing more.
 *
 * <p>It holds one {@link ItemBeeFilterCard Bee Pattern card} - authored "species X with traits Y" - and a maintain
 * {@link #threshold}. Each grid tick it runs a small state machine ({@link RequesterStatus}):
 * <ul>
 *     <li><b>{@link RequesterStatus#IDLE IDLE}</b>: when the network count of the card ({@link
 *     ApiaryControllerBlockEntity#countMatching}) is below the threshold, begin a crafting calculation for the card's
 *     {@link #resolveTarget canonical output key} and move to SCHEDULED.</li>
 *     <li><b>{@link RequesterStatus#SCHEDULED SCHEDULED}</b>: await the plan; on a clean plan submit the job (tracking
 *     the {@link ICraftingLink}) and move to CRAFTING, else surface {@link RequesterStatus#MISSING}/{@link
 *     RequesterStatus#NO_CPU} and retry.</li>
 *     <li><b>{@link RequesterStatus#CRAFTING CRAFTING}</b>: hold the link until the job completes or cancels; the bred
 *     bee is delivered to us through {@link #insertCraftedItems}, which injects it into network storage.</li>
 * </ul>
 */
public class BeeRequesterBlockEntity extends AENetworkedInvBlockEntity implements IGridTickable, ICraftingRequester {
	private final AppEngInternalInventory target = new AppEngInternalInventory(this, 1, 1, cardFilter());
	private final IActionSource actionSource = IActionSource.ofMachine(this);

	/** How many of the target card's bee to maintain in the network. */
	private int threshold = 1;
	/** The maintain loop's current state; drives the tick logic and the GUI readout. */
	private RequesterStatus status = RequesterStatus.IDLE;
	/** Cached for the GUI: how many target-matching bees were in the network on the last tick. */
	private long knownCount = 0;

	/** A crafting calculation in flight (SCHEDULED); null otherwise. */
	@Nullable
	private Future<ICraftingPlan> pendingPlan;
	/** The active crafting job's link (CRAFTING); null otherwise. Persisted so it re-attaches after a chunk reload. */
	@Nullable
	private ICraftingLink activeLink;

	public BeeRequesterBlockEntity(BlockPos pos, BlockState state) {
		super(BeegisticsBlockEntities.BEE_REQUESTER.get(), pos, state);
		getMainNode()
				.setFlags(GridFlags.REQUIRE_CHANNEL)
				.setIdlePowerUsage(2.0)
				.addService(IGridTickable.class, this)
				.addService(ICraftingRequester.class, this);
	}

	/** The single target inventory accepts only Bee Pattern cards (the card whose bee the requester keeps stocked). */
	private IAEItemFilter cardFilter() {
		return new IAEItemFilter() {
			@Override
			public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
				return stack.getItem() instanceof ItemBeeFilterCard;
			}
		};
	}

	@Override
	public InternalInventory getInternalInventory() {
		return this.target;
	}

	@Override
	public void onChangeInventory(AppEngInternalInventory inv, int slot) {
		saveChanges();
	}

	public int getThreshold() {
		return this.threshold;
	}

	public void setThreshold(int value) {
		this.threshold = Math.max(1, value);
		saveChanges();
	}

	/** @return how many target-matching bees were in the network on the last tick (for the GUI readout). */
	public long getKnownCount() {
		return this.knownCount;
	}

	/** @return the maintain loop's current {@link RequesterStatus} (for the GUI readout). */
	public RequesterStatus getStatus() {
		return this.status;
	}

	@Override
	public TickingRequest getTickingRequest(IGridNode node) {
		// There is no key-specific "a matching bee left the network" signal, so poll: a few times a second while working,
		// slower while idle. A crafting job is long-lived, so we never need to poll fast.
		return new TickingRequest(10, 40, false);
	}

	@Override
	public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
		IGrid grid = getMainNode().getGrid();
		if (grid == null || this.level == null || this.level.isClientSide || !getMainNode().isActive()) {
			return TickRateModulation.IDLE;
		}
		if (advance(grid)) {
			saveChanges();
		}
		// Poll fast while a plan is computing (latency-sensitive), slow otherwise; a running job needs no fast polling.
		return this.status == RequesterStatus.SCHEDULED ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
	}

	/**
	 * Runs one step of the maintain state machine against the given grid.
	 *
	 * @return whether the persisted state (status/link) changed this step.
	 */
	private boolean advance(IGrid grid) {
		RequesterStatus previous = this.status;

		// CRAFTING: hold the link until the job finishes or is cancelled.
		if (this.activeLink != null) {
			if (this.activeLink.isCanceled() || this.activeLink.isDone()) {
				this.activeLink = null;
				this.status = RequesterStatus.IDLE;
				// fall through to (possibly) start a fresh request the same tick
			} else {
				this.status = RequesterStatus.CRAFTING;
				return false; // nothing persisted changed
			}
		}

		// SCHEDULED: await and then submit the crafting plan.
		if (this.pendingPlan != null) {
			if (!this.pendingPlan.isDone()) {
				this.status = RequesterStatus.SCHEDULED;
				return this.status != previous;
			}
			ICraftingPlan plan = resolvePlan(this.pendingPlan);
			this.pendingPlan = null;
			if (plan == null) {
				this.status = RequesterStatus.IDLE;
				return this.status != previous;
			}
			if (!plan.missingItems().isEmpty()) {
				this.status = RequesterStatus.MISSING;
				return this.status != previous;
			}
			ICraftingSubmitResult result = grid.getCraftingService().submitJob(plan, this, null, false, this.actionSource);
			if (result.successful() && result.link() != null) {
				this.activeLink = result.link();
				this.status = RequesterStatus.CRAFTING;
			} else {
				this.status = isCpuError(result.errorCode()) ? RequesterStatus.NO_CPU : RequesterStatus.IDLE;
			}
			return true;
		}

		// IDLE: request a top-up when the network count is below the threshold.
		Target request = resolveTarget();
		MEStorage storage = grid.getStorageService().getInventory();
		KeyCounter available = new KeyCounter();
		storage.getAvailableStacks(available);
		long count = request == null ? 0 : ApiaryControllerBlockEntity.countMatching(available, request.filter());
		this.knownCount = count;
		if (request == null || count >= this.threshold) {
			this.status = RequesterStatus.IDLE;
			return this.status != previous;
		}
		long amount = this.threshold - count;
		this.pendingPlan = grid.getCraftingService().beginCraftingCalculation(
				this.level, () -> this.actionSource, request.key(), amount, CalculationStrategy.REPORT_MISSING_ITEMS);
		this.status = RequesterStatus.SCHEDULED;
		return this.status != previous;
	}

	/** @return the completed plan, or {@code null} if the calculation was cancelled or failed. */
	@Nullable
	private ICraftingPlan resolvePlan(Future<ICraftingPlan> future) {
		if (future.isCancelled()) {
			return null;
		}
		try {
			return future.get();
		} catch (InterruptedException | ExecutionException e) {
			return null;
		}
	}

	/** @return whether the submit failure was a (transient) CPU-availability problem rather than an outright rejection. */
	private static boolean isCpuError(@Nullable CraftingSubmitErrorCode code) {
		return code == CraftingSubmitErrorCode.NO_CPU_FOUND
				|| code == CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND
				|| code == CraftingSubmitErrorCode.CPU_BUSY
				|| code == CraftingSubmitErrorCode.CPU_OFFLINE
				|| code == CraftingSubmitErrorCode.CPU_TOO_SMALL;
	}

	/**
	 * Resolves the loaded card into the exact bee to request: the canonical princess key AE2 should craft, plus the
	 * filter used to count how many already exist in the network. A genome card (species + homozygous trait pins)
	 * requests the pinned genome; a pure-species card requests the species' default genome.
	 *
	 * @return the request, or {@code null} when no card is loaded or its species is not registered.
	 */
	@Nullable
	private Target resolveTarget() {
		ItemStack card = this.target.getStackInSlot(0);
		if (!(card.getItem() instanceof ItemBeeFilterCard)) {
			return null;
		}
		BeeFilter filter = ItemBeeFilterCard.getFilter(card);
		Optional<IGenome> genome = BeeGenomeMutationPattern.targetFrom(filter);
		if (genome.isPresent()) {
			IBeeSpecies result = genome.get().getActiveSpecies();
			if (result == null) {
				return null;
			}
			return new Target(BeeMutationPattern.canonicalKey(result, genome.get(), BeeLifeStage.PRINCESS), filter);
		}
		IBeeSpecies species = filter.species().map(SpeciesUtil::getBeeSpecies).orElse(null);
		if (species == null) {
			return null;
		}
		return new Target(BeeMutationPattern.canonicalKey(species, BeeLifeStage.PRINCESS), filter);
	}

	// --- ICraftingRequester ----------------------------------------------------------------------------------------

	@Override
	public ImmutableSet<ICraftingLink> getRequestedJobs() {
		return this.activeLink == null ? ImmutableSet.of() : ImmutableSet.of(this.activeLink);
	}

	@Override
	public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
		// The job delivers the bred bee to us; put it straight into network storage (the point of the whole loop).
		IGrid grid = getMainNode().getGrid();
		if (grid == null) {
			return 0;
		}
		return grid.getStorageService().getInventory().insert(what, amount, mode, this.actionSource);
	}

	@Override
	public void jobStateChange(ICraftingLink link) {
		// The active link is re-examined each tick in advance(); nothing to do here.
	}

	// --- persistence -----------------------------------------------------------------------------------------------

	@Override
	public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
		super.saveAdditional(data, registries); // persists the target card (getInternalInventory)
		data.putInt("threshold", this.threshold);
		if (this.activeLink != null) {
			CompoundTag link = new CompoundTag();
			this.activeLink.writeToNBT(link);
			data.put("craftLink", link);
		}
	}

	@Override
	public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
		super.loadTag(data, registries); // restores the target card (getInternalInventory)
		this.threshold = Math.max(1, data.getInt("threshold"));
		if (data.contains("craftLink")) {
			this.activeLink = StorageHelper.loadCraftingLink(data.getCompound("craftLink"), this);
			this.status = RequesterStatus.CRAFTING;
		} else {
			this.activeLink = null;
			this.status = RequesterStatus.IDLE;
		}
	}

	/** The bee to request: its canonical princess key (what AE2 crafts) paired with the filter that counts existing stock. */
	private record Target(AEItemKey key, BeeFilter filter) {
	}
}
