package forestry.beegistics.machine;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.api.multiblock.IMultiblockComponent;
import forestry.api.multiblock.IMultiblockController;
import forestry.apiculture.InventoryBeeHousing;
import forestry.apiculture.inventory.InventoryApiary;
import forestry.apiculture.items.ItemCreativeHiveFrame;
import forestry.api.genetics.IMutation;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.BeegisticsBlockEntities;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.ItemBeeMutationPattern;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.beegistics.crafting.BeeMutation;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.core.utils.SpeciesUtil;

/**
 * The Apiary Controller: a grid-connected block that automates the vanilla Forestry breeding loop <em>without</em>
 * bypassing its mutation chance or time - all breeding is done by a real adjacent apiary, and the controller only moves
 * bees in and out of it. It holds a single double row of {@link ItemBeeFilterCard Bee Pattern cards} (each authored as
 * "species X with traits Y") and runs them in one of two {@link ControllerMode modes} (maintaining a target count is the
 * job of the separate {@link BeeRequesterBlockEntity Bee Requester} block, which drives this controller's Autocraft
 * patterns):
 *
 * <ul>
 *     <li><b>{@link ControllerMode#STANDALONE Standalone}</b> (the default): a dumb perpetual breeder. Holds just two
 *     Bee Pattern cards - a princess card and a drone card - and keeps each driven apiary stocked with a princess
 *     matching the first and a drone matching the second, pulled from the network, harvesting every product back. The two
 *     cards are independent (usually the same species, but a deliberate cross is allowed); a same-species pair is
 *     self-sustaining while replacements exist. No target count, no hill-climb, no autocraft. See {@link #runPerpetual}.</li>
 *     <li><b>{@link ControllerMode#AUTOCRAFT Autocraft}</b>: exposes each loaded card to AE2 as a
 *     {@link ICraftingProvider crafting pattern}, so the target can be requested in the standard crafting terminal and AE2
 *     recursively schedules the whole breeding tree (creating the base species + donor as needed). Each
 *     {@link #pushPattern} breeds the requested target on the apiary, then injects the pattern's canonical output - see
 *     {@link #runCraftTick}.</li>
 * </ul>
 *
 * <p>The decision logic ({@link #findParent}, {@link #countMatching}) is factored into pure static methods so it is
 * unit-testable and reused by the crafting engine.
 */
public class ApiaryControllerBlockEntity extends AENetworkedInvBlockEntity implements IGridTickable, ICraftingProvider {
	/** How many Bee Pattern cards the Autocraft grid holds (a 2x9 double row). */
	public static final int PATTERN_SLOTS = 18;
	/** Scratch space holding parent bees extracted for an in-flight craft job. */
	private static final int CRAFT_BUFFER_SLOTS = 8;

	/** The Autocraft pattern grid: 18 Bee Pattern cards exposed to the network in {@link ControllerMode#AUTOCRAFT}. */
	private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, PATTERN_SLOTS, 1, patternFilter());
	/**
	 * Standalone mode's two Bee Pattern card slots: a princess-selecting card and a drone-selecting card, held as two
	 * single-slot inventories so each can carry its own stage restriction ({@link #breedingFilter}). The perpetual breeder
	 * pulls a princess matching the first and a drone matching the second.
	 */
	private final AppEngInternalInventory princessCardInv = new AppEngInternalInventory(this, 1, 1, breedingFilter(BeeLifeStage.PRINCESS));
	private final AppEngInternalInventory droneCardInv = new AppEngInternalInventory(this, 1, 1, breedingFilter(BeeLifeStage.DRONE));
	/**
	 * The block's single AE2-facing inventory (persistence, drops, side I/O): the Autocraft grid followed by the two
	 * Standalone breeding-card slots, so both persist and drop for free via {@link #getInternalInventory}.
	 */
	private final InternalInventory allInventories = new CombinedInternalInventory(this.patterns, this.princessCardInv, this.droneCardInv);
	private final AppEngInternalInventory craftBuffer = new AppEngInternalInventory(this, CRAFT_BUFFER_SLOTS, 64);
	private final IActionSource actionSource = IActionSource.ofMachine(this);

	/**
	 * How many completed breeding cycles a genome job may make no scoring progress before the watchdog abandons it as
	 * stuck. Counted per apiary cycle (a drain that yielded offspring), not per tick - a reachable single-trait target
	 * fixes in a handful of cycles, so this is generous headroom that never abandons a genuinely converging job.
	 */
	private static final long GENOME_WATCHDOG_BUDGET = 400;

	/** All valid patterns decoded from the pattern inventory; rebuilt (null) whenever that inventory changes. */
	@Nullable
	private List<IPatternDetails> cachedDecoded;
	/** The subset actually offered to the crafting service: only mutations the adjacent apiary can currently breed. */
	private List<IPatternDetails> offeredPatterns = List.of();

	/** Which of the two {@link ControllerMode modes} the controller runs in; see the enum for the semantics of each. */
	private ControllerMode mode = ControllerMode.DEFAULT;
	/**
	 * <b>TESTING-ONLY HACK (persisted NBT, off by default).</b> Makes the whole autocraft loop deterministic so a large
	 * run finishes fast and reliably (small runs leave it off and exercise the real stochastic breeding):
	 * <ul>
	 *     <li><b>Species jobs:</b> stamps the current job's result species onto the {@code forced_mutation} of the
	 *     force-mutation ("creation pool") apiary frames, so an ambiguous parent pair breeds exactly the wanted mutation
	 *     instead of a random one - a single controller can then autocraft a deep, branchy tree without starving.</li>
	 *     <li><b>Genome jobs:</b> skips the stochastic homozygosity hill-climb and {@link #fabricateGenomeTarget fabricates}
	 *     the exact target princess directly. The base and donor are still autocrafted from the wild roots (so the tree is
	 *     fully exercised); only the multi-generation selection - which can run its base-princess population to extinction
	 *     on a bad brood - is replaced by a deterministic result.</li>
	 * </ul>
	 * A real setup would instead fabricate on a dedicated block; the controller must never rewrite a player's frames.
	 */
	private boolean forceMutationHack = false;
	/** Cached for the GUI: how many non-contended adjacent apiaries this controller drove on the last tick. */
	private int usableApiaryCount = 0;
	/** Cached for the GUI: how many adjacent apiaries were skipped last tick because a second controller also touches them. */
	private int contendedApiaryCount = 0;
	/** Whether any driven apiary holds a creative frame (breeds near-instantly) - gates fast crafting-poll cadence. */
	private boolean apiaryFastBreeding = false;
	/**
	 * Cached for the GUI (standalone mode): whether the perpetual breeder had work on the last tick - a driven apiary was
	 * occupied, or a bee matching a loaded card was available to stock one with. False (with an apiary linked) means the
	 * network holds no bees matching the loaded filters, which the status readout surfaces as a warning.
	 */
	private boolean perpetualBreeding = false;
	/** Cached for the GUI: the adjacent apiary's climate on the last refresh (null when no apiary). */
	@Nullable
	private TemperatureType apiaryTemperature;
	@Nullable
	private HumidityType apiaryHumidity;

	// Active craft job (autocrafting mode). All three ids are null exactly when no job is in flight.
	@Nullable
	private ResourceLocation craftResult;
	@Nullable
	private ResourceLocation craftFirstParent;
	@Nullable
	private ResourceLocation craftSecondParent;
	/** How many more of the primary output the active job still owes the crafting CPU. */
	private long craftRemaining = 0;
	/**
	 * Non-null only for a <em>genome</em> craft job (autocrafting an arbitrary homozygous genome): the exact genome to
	 * deliver. When set, {@link #drainCraftProducts}/{@link #restockCraftParents} switch from the species path to the
	 * scoring hill-climb, and the job completes only when a bred bee reaches the target homozygously.
	 */
	@Nullable
	private IGenome craftTargetGenome;
	/** Derived from {@link #craftTargetGenome}: scores a candidate's progress toward the pinned homozygous traits. */
	@Nullable
	private BeeFilter craftScoreFilter;
	/** Derived: the maximum {@code scoreTowardFilter} (2 per pinned chromosome); a bee at this score is the target. */
	private int craftMaxScore = 0;
	/** Watchdog: the best score seen so far this job, and how many cycles it has failed to improve. */
	private int craftBestScore = 0;
	private long craftIdleCycles = 0;
	/** Set while draining if an actual offspring bee (not honey/combs) was seen this tick - marks a completed breeding cycle. */
	private boolean drainSawOffspring = false;

	public ApiaryControllerBlockEntity(BlockPos pos, BlockState state) {
		super(BeegisticsBlockEntities.APIARY_CONTROLLER.get(), pos, state);
		getMainNode()
				.setFlags(GridFlags.REQUIRE_CHANNEL)
				.setIdlePowerUsage(2.0)
				.addService(IGridTickable.class, this)
				.addService(ICraftingProvider.class, this);
	}

	/** The Autocraft pattern grid accepts only Bee Pattern cards (their filter is the autocraft target). */
	private IAEItemFilter patternFilter() {
		return new IAEItemFilter() {
			@Override
			public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
				return stack.getItem() instanceof ItemBeeFilterCard;
			}
		};
	}

	/**
	 * A Standalone breeding-card slot accepts only a Bee Pattern card whose target life stage suits the slot: a
	 * stage-agnostic card (empty {@link BeeFilter#stages()}) goes in either, but a card that pins a stage must include the
	 * slot's stage. This is what makes shift-click routing stage-based - a drone card is rejected by the princess slot's
	 * {@code mayPlace}, so it lands in the drone slot instead.
	 */
	private IAEItemFilter breedingFilter(BeeLifeStage slotStage) {
		return new IAEItemFilter() {
			@Override
			public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
				if (!(stack.getItem() instanceof ItemBeeFilterCard)) {
					return false;
				}
				Set<BeeLifeStage> stages = ItemBeeFilterCard.getFilter(stack).stages();
				return stages.isEmpty() || stages.contains(slotStage);
			}
		};
	}

	@Override
	public InternalInventory getInternalInventory() {
		return this.allInventories;
	}

	/** @return the Autocraft pattern grid (the 18 card slots exposed to the network in {@link ControllerMode#AUTOCRAFT}). */
	public InternalInventory getPatternInventory() {
		return this.patterns;
	}

	/** @return the Standalone princess-card slot (one slot; the perpetual breeder's princess selector). */
	public InternalInventory getPrincessCardInventory() {
		return this.princessCardInv;
	}

	/** @return the Standalone drone-card slot (one slot; the perpetual breeder's drone selector). */
	public InternalInventory getDroneCardInventory() {
		return this.droneCardInv;
	}

	@Override
	public void onChangeInventory(AppEngInternalInventory inv, int slot) {
		if (inv == this.patterns) {
			// The loaded patterns changed; drop the decoded cache and tell the crafting service to re-read the offer.
			this.cachedDecoded = null;
			ICraftingProvider.requestUpdate(getMainNode());
		}
		saveChanges();
	}

	/** @return whether the controller is idle (no craft job in flight) - the GUI's "paused" state. */
	public boolean isIdle() {
		return this.craftResult == null;
	}

	/** @return whether at least one usable (non-contended) apiary was found adjacent on the last tick. */
	public boolean isApiaryConnected() {
		return this.usableApiaryCount > 0;
	}

	/** @return how many non-contended adjacent apiaries this controller drove on the last tick (for the GUI). */
	public int getUsableApiaryCount() {
		return this.usableApiaryCount;
	}

	/** @return how many adjacent apiaries were skipped last tick due to contention with another controller (for the GUI). */
	public int getContendedApiaryCount() {
		return this.contendedApiaryCount;
	}

	/** @return whether standalone mode had work on the last tick (apiary occupied or a matching bee available to stock). */
	public boolean isPerpetualBreeding() {
		return this.perpetualBreeding;
	}

	/** @return the adjacent apiary's temperature (for the GUI), or {@code null} when no apiary is attached. */
	@Nullable
	public TemperatureType getApiaryTemperature() {
		return this.apiaryTemperature;
	}

	/** @return the adjacent apiary's humidity (for the GUI), or {@code null} when no apiary is attached. */
	@Nullable
	public HumidityType getApiaryHumidity() {
		return this.apiaryHumidity;
	}

	/** @return which of the two {@link ControllerMode modes} the controller currently runs in. */
	public ControllerMode getMode() {
		return this.mode;
	}

	/** Switches the controller's mode; re-reads the AE2 pattern offer (non-empty only in {@link ControllerMode#AUTOCRAFT}). */
	public void setMode(ControllerMode mode) {
		if (this.mode == mode) {
			return;
		}
		this.mode = mode;
		ICraftingProvider.requestUpdate(getMainNode());
		saveChanges();
	}

	/** Enables the testing-only {@link #forceMutationHack frame-forcing hack}. Not for normal play - see the field doc. */
	public void setForceMutationHack(boolean enabled) {
		this.forceMutationHack = enabled;
		saveChanges();
	}

	/** @return how many of the active craft job's primary output remain to be bred (0 when idle). */
	public long getCraftRemaining() {
		return this.craftRemaining;
	}

	/** @return the id of the species the active craft job is breeding, or {@code null} when idle. */
	@Nullable
	public ResourceLocation getCraftResult() {
		return this.craftResult;
	}

	/** @return the best score toward the genome target seen so far this job (of {@code craftMaxScore}); for GUI/tests. */
	public int getCraftBestScore() {
		return this.craftBestScore;
	}

	/** @return the max score a genome target scores at (2 per pinned trait); 0 for a non-genome job. For GUI/tests. */
	public int getCraftMaxScore() {
		return this.craftMaxScore;
	}

	/** @return breeding cycles the current genome job has made without improving its best score (watchdog counter). */
	public long getCraftIdleCycles() {
		return this.craftIdleCycles;
	}

	@Override
	public TickingRequest getTickingRequest(IGridNode node) {
		// The apiary works on a ~550-tick cycle, so there is no need to poll faster than a few times per second; there is
		// also no key-specific "a matching bee entered the network" signal, so we never fully sleep.
		return new TickingRequest(20, 100, false);
	}

	@Override
	public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
		IGrid grid = getMainNode().getGrid();
		if (grid == null || this.level == null) {
			return TickRateModulation.IDLE;
		}

		// The apiary's climate/time can change what it can breed, so re-evaluate which patterns we offer each tick.
		if (refreshOfferedPatterns()) {
			ICraftingProvider.requestUpdate(getMainNode());
		}

		MEStorage storage = grid.getStorageService().getInventory();
		boolean worked;
		if (this.craftResult != null) {
			worked = runCraftTick(storage);
			// Poll quickly only when the adjacent apiary breeds fast (a creative frame): then per-step latency dominates,
			// so sleeping the full idle interval between polls would badly slow a deep autocrafting tree. A normal apiary's
			// life cycle is minutes long, so the standard adaptive rate is plenty and avoids needless ticking while it works.
			if (this.apiaryFastBreeding) {
				return TickRateModulation.URGENT;
			}
			return worked ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
		} else {
			worked = switch (this.mode) {
				// Autocraft with no active job: idle until the crafting service pushes a pattern (still refresh GUI counts).
				case AUTOCRAFT -> {
					findApiaries();
					yield false;
				}
				case STANDALONE -> runPerpetual(storage);
			};
		}
		return worked ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
	}

	// --- Genome job setup ------------------------------------------------------------------------------------------

	/**
	 * Sets up a genome craft job: the base parent is the result species (the chassis), the donor supplies the pinned
	 * alleles, and {@link #runCraftTick} then hill-climbs toward the homozygous {@code target}. Used by
	 * {@link #pushPattern}'s genome branch (an AE2 push toward a genome pattern) and exposed so tests can drive the
	 * staging/drain machinery directly without a full crafting calculation.
	 */
	public void startGenomeJob(IGenome target, IBeeSpecies result, IBeeSpecies donor, long remaining) {
		this.craftFirstParent = result.id();
		this.craftSecondParent = donor.id();
		this.craftResult = result.id();
		applyGenomeTarget(target);
		this.craftRemaining = Math.max(1, remaining);
		this.craftBestScore = 0;
		this.craftIdleCycles = 0;
		saveChanges();
	}

	/**
	 * Sets (or clears) the genome-target scoring fields that distinguish a genome job from a plain species job: the target
	 * genome, its derived score filter, and the max score (2 per pinned chromosome). Passing {@code null} marks a
	 * species-only job (no hill-climb). Single-sources the derivation used by {@link #startGenomeJob}, {@link #pushPattern},
	 * {@link #loadTag} and {@link #clearJob}.
	 */
	private void applyGenomeTarget(@Nullable IGenome target) {
		this.craftTargetGenome = target;
		if (target == null) {
			this.craftScoreFilter = null;
			this.craftMaxScore = 0;
		} else {
			this.craftScoreFilter = BeeGenomeMutationPattern.scoreFilter(target);
			this.craftMaxScore = 2 * BeeGenomeMutationPattern.pinnedChromosomes(target).size();
		}
	}

	// --- Standalone (perpetual) mode -------------------------------------------------------------------------------

	/**
	 * Runs one perpetual-breeder step: drain every driven apiary's products to the network, then keep each apiary stocked
	 * with a princess matching the loaded princess card and a drone matching the loaded drone card, both pulled from the
	 * network. The same two cards drive every apiary behind the controller. There is no target count, no genome hill-climb
	 * and no autocraft - it simply feeds the two cards' bees in and harvests everything out, self-sustaining (for a
	 * same-species pair) while the network holds replacements. Package-visible so tests can drive it against an arbitrary
	 * {@link MEStorage}.
	 *
	 * @param storage the network to pull parents from and push products into
	 * @return whether any bee was moved this step
	 */
	public boolean runPerpetual(MEStorage storage) {
		List<Apiary> apiaries = findApiaries();
		boolean worked = false;
		// Harvest first so freshly bred offspring (including replacement princesses/drones) land in the network this tick.
		for (Apiary apiary : apiaries) {
			worked |= drainProducts(apiary, storage);
		}

		BeeFilter princessCard = breedingCard(this.princessCardInv);
		BeeFilter droneCard = breedingCard(this.droneCardInv);
		if (apiaries.isEmpty() || (princessCard == null && droneCard == null)) {
			this.perpetualBreeding = false;
			return worked;
		}
		KeyCounter available = new KeyCounter();
		storage.getAvailableStacks(available);
		for (Apiary apiary : apiaries) {
			worked |= restockPerpetual(apiary, storage, available, princessCard, droneCard);
		}
		// After restocking, decide whether the breeder has work for the GUI: an occupied apiary (a queen we just stocked or
		// one already breeding) or a still-available matching bee. Nothing on either front means the network has no bees
		// matching the loaded cards - surfaced as a warning rather than a green "Breeding".
		this.perpetualBreeding = perpetualHasWork(apiaries, princessCard, droneCard, available);
		return worked;
	}

	/**
	 * @return whether standalone mode has anything to breed: a driven apiary already holding a queen, or a network bee
	 * (in {@code available}, the post-restock snapshot) matching either loaded card that could stock a parent. Drives the
	 * GUI's active/no-bees status.
	 */
	private boolean perpetualHasWork(List<Apiary> apiaries, @Nullable BeeFilter princessCard, @Nullable BeeFilter droneCard, KeyCounter available) {
		for (Apiary apiary : apiaries) {
			if (!apiary.housing().getBeeInventory().getQueen().isEmpty()) {
				return true;
			}
		}
		if (princessCard != null && findParent(available, princessCard, BeeLifeStage.PRINCESS) != null) {
			return true;
		}
		if (droneCard != null && findParent(available, droneCard, BeeLifeStage.DRONE) != null) {
			return true;
		}
		return false;
	}

	/**
	 * Keeps one apiary stocked for perpetual breeding: a princess matching {@code princessCard} in the queen slot and,
	 * once she is an unmated princess, a drone matching {@code droneCard} in the drone slot. Either card may be absent (its
	 * slot is then simply not stocked). Both bees come from the network; the shared {@code available} snapshot is kept
	 * honest so a later apiary this tick does not claim the same bee.
	 */
	private boolean restockPerpetual(Apiary apiary, MEStorage storage, KeyCounter available, @Nullable BeeFilter princessCard, @Nullable BeeFilter droneCard) {
		boolean worked = false;
		IBeeHousingInventory inv = apiary.housing().getBeeInventory();

		if (princessCard != null && inv.getQueen().isEmpty()) {
			ItemStack princess = pullParent(storage, available, princessCard, BeeLifeStage.PRINCESS);
			if (!princess.isEmpty()) {
				inv.setQueen(princess);
				worked = true;
			}
		}

		if (droneCard != null && inv.getDrone().isEmpty() && queenNeedsMate(inv)) {
			ItemStack drone = pullParent(storage, available, droneCard, BeeLifeStage.DRONE);
			if (!drone.isEmpty()) {
				inv.setDrone(drone);
				worked = true;
			}
		}

		if (worked) {
			apiary.container().setChanged();
		}
		return worked;
	}

	/** @return the {@link BeeFilter} of the Bee Pattern card in the given single-slot breeding inventory, or {@code null} if empty. */
	@Nullable
	private static BeeFilter breedingCard(InternalInventory inv) {
		ItemStack stack = inv.getStackInSlot(0);
		return stack.getItem() instanceof ItemBeeFilterCard ? ItemBeeFilterCard.getFilter(stack) : null;
	}

	/** @return whether the queen slot holds an unmated princess (which still needs a drone to mate into a queen). */
	private static boolean queenNeedsMate(IBeeHousingInventory inv) {
		ItemStack queen = inv.getQueen();
		return !queen.isEmpty() && IIndividualHandlerItem.getLifeStage(queen) == BeeLifeStage.PRINCESS;
	}

	// --- Crafting mode ---------------------------------------------------------------------------------------------

	@Override
	public List<IPatternDetails> getAvailablePatterns() {
		// Only autocraft mode exposes the loaded cards to the network; the other modes maintain their cards themselves.
		if (this.mode != ControllerMode.AUTOCRAFT) {
			return List.of();
		}
		// Offer only mutations the adjacent apiary can currently breed. Recompute lazily if the loaded patterns changed
		// (e.g. this is the synchronous read AE2 does during requestUpdate); the tick keeps it fresh as climate/time change.
		if (this.cachedDecoded == null) {
			refreshOfferedPatterns();
		}
		return this.offeredPatterns;
	}

	/**
	 * @return all valid patterns decoded from the pattern inventory (deduplicated), rebuilt on demand. Each loaded
	 * pattern item yields <em>two</em> patterns - a princess-primary and a drone-primary variant - so AE2 can autocraft
	 * an intermediate species as either gender (a mutation needs its second parent as a drone). Both are executed the
	 * same way; only which output AE2 treats as the craftable primary differs.
	 */
	private List<IPatternDetails> decodedPatterns() {
		if (this.cachedDecoded == null) {
			List<IPatternDetails> decoded = new ArrayList<>();
			for (int slot = 0; slot < this.patterns.size(); slot++) {
				ItemStack stack = this.patterns.getStackInSlot(slot);
				if (stack.isEmpty()) {
					continue;
				}
				if (stack.getItem() instanceof ItemBeeFilterCard) {
					// A Bee Pattern card is a terminal deliverable: one princess-primary genome variant with an
					// auto-resolved donor, no drone variant.
					IPatternDetails genome = BeeGenomeMutationPattern.decode(AEItemKey.of(stack), this.level);
					if (genome instanceof BeeGenomeMutationPattern gp) {
						addDecoded(decoded, gp);
						// Auto-offer the species mutations needed to breed the base chassis and the resolved donor, so AE2 can
						// compose the whole tree (species creation -> introgression) without the player pre-stocking or encoding.
						offerSpeciesClosure(decoded, List.of(gp.getResult(), gp.getDonor()));
					} else {
						// A pure-species card (species, no trait pins): just autocraft that species from its mutation tree.
						IBeeSpecies species = ItemBeeFilterCard.getFilter(stack).species().map(SpeciesUtil::getBeeSpecies).orElse(null);
						if (species != null) {
							offerSpeciesClosure(decoded, List.of(species));
						}
					}
				} else {
					// A species mutation pattern item, offered as both a princess- and a drone-primary variant.
					addDecoded(decoded, BeeMutationPattern.decode(AEItemKey.of(stack), this.level));
					addDecoded(decoded, BeeMutationPattern.decode(AEItemKey.of(ItemBeeMutationPattern.withDronePrimary(stack)), this.level));
				}
			}
			this.cachedDecoded = decoded;
		}
		return this.cachedDecoded;
	}

	private void addDecoded(List<IPatternDetails> decoded, @Nullable IPatternDetails pattern) {
		if (pattern != null && !decoded.contains(pattern)) {
			decoded.add(pattern);
		}
	}

	/**
	 * Offers a species-mutation pattern (both gender variants) for every mutation on any breeding path down to wild species
	 * from {@code roots} - the bounded {@link BeeGenomeMutationPattern#ancestorClosure closure} needed so AE2 can autocraft
	 * the roots (a genome target's base + donor, or a pure-species card's species) when they are not stocked. The synthesized
	 * {@link ItemBeeMutationPattern} keys are internal pattern definitions, never player items. Climate-gated per tick in
	 * {@link #refreshOfferedPatterns} exactly like a hand-encoded species pattern, so only currently-breedable ones surface.
	 */
	private void offerSpeciesClosure(List<IPatternDetails> decoded, Collection<IBeeSpecies> roots) {
		for (IMutation<IBeeSpecies> mutation : BeeGenomeMutationPattern.ancestorClosure(roots)) {
			ItemStack key = new ItemStack(BeegisticsItems.beeMutationPattern());
			ItemBeeMutationPattern.setMutation(key, new BeeMutation(mutation.getFirstParent().id(), mutation.getSecondParent().id(), mutation.getResult().id()));
			addDecoded(decoded, BeeMutationPattern.decode(AEItemKey.of(key), this.level));
			addDecoded(decoded, BeeMutationPattern.decode(AEItemKey.of(ItemBeeMutationPattern.withDronePrimary(key)), this.level));
		}
	}

	/**
	 * Recomputes the offered set: the decoded patterns filtered to those the adjacent apiary can breed right now
	 * ({@link BeeMutationPattern#canBreedAt}). With no apiary present, only unconditional patterns are offered (their
	 * conditions can't be evaluated, but they'll fulfil once an apiary is attached).
	 *
	 * @return whether the offered set changed (so the caller can {@link ICraftingProvider#requestUpdate}).
	 */
	private boolean refreshOfferedPatterns() {
		List<IPatternDetails> decoded = decodedPatterns();
		List<Apiary> apiaries = findApiaries();
		// Cache the first usable apiary's climate for the GUI readout (a controller's apiaries are normally co-located).
		Apiary primary = apiaries.isEmpty() ? null : apiaries.get(0);
		this.apiaryTemperature = primary == null ? null : primary.housing().temperature();
		this.apiaryHumidity = primary == null ? null : primary.housing().humidity();
		List<IPatternDetails> next = new ArrayList<>(decoded.size());
		for (IPatternDetails pattern : decoded) {
			if (pattern instanceof BeeMutationPattern species) {
				// Offer a species mutation if any usable apiary can breed it now; with none, offer unconditional ones only.
				if (apiaries.isEmpty() ? species.isUnconditional() : anyApiaryCanBreed(apiaries, species)) {
					next.add(pattern);
				}
			} else {
				// A genome pattern has no mutation conditions (reachability is validated at decode); always offer it so it
				// fulfils as soon as an apiary is attached.
				next.add(pattern);
			}
		}
		if (next.equals(this.offeredPatterns)) {
			return false;
		}
		this.offeredPatterns = next;
		return true;
	}

	/** @return whether any of the usable apiaries can currently breed the given pattern's mutation. */
	private boolean anyApiaryCanBreed(List<Apiary> apiaries, BeeMutationPattern pattern) {
		for (Apiary apiary : apiaries) {
			if (apiaryCanBreed(apiary, pattern)) {
				return true;
			}
		}
		return false;
	}

	/** Evaluates the mutation's conditions against the apiary's live climate/location (the same check breeding uses). */
	private boolean apiaryCanBreed(Apiary apiary, BeeMutationPattern pattern) {
		IBeeHousing housing = apiary.housing();
		net.minecraft.world.level.Level breedLevel = housing.getWorldObj() != null ? housing.getWorldObj() : this.level;
		return pattern.canBreedAt(breedLevel, housing.getCoordinates(), housing);
	}

	@Override
	public boolean isBusy() {
		// One craft job at a time: while one is in flight the crafting service must not push another.
		return this.craftResult != null;
	}

	@Override
	public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
		if (isBusy() || !getAvailablePatterns().contains(patternDetails)) {
			return false;
		}

		if (patternDetails instanceof BeeMutationPattern pattern) {
			this.craftFirstParent = pattern.getFirstParent().id();
			this.craftSecondParent = pattern.getSecondParent().id();
			this.craftResult = pattern.getResult().id();
			applyGenomeTarget(null);
			this.craftRemaining = Math.max(1, patternDetails.getPrimaryOutput().amount());
			this.craftBestScore = 0;
			this.craftIdleCycles = 0;
		} else if (patternDetails instanceof BeeGenomeMutationPattern pattern) {
			// A genome job: the base parent is the result species itself (the chassis); the donor supplies the alleles the
			// base default lacks. Breeding hill-climbs toward the pinned homozygous traits (scored by craftScoreFilter).
			startGenomeJob(pattern.getTarget(), pattern.getResult(), pattern.getDonor(), patternDetails.getPrimaryOutput().amount());
		} else {
			return false;
		}

		// Take ownership of the parent bees the crafting service extracted for us and stash them for staging.
		for (KeyCounter inputList : inputHolder) {
			for (var entry : inputList) {
				if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key) {
					this.craftBuffer.addItems(key.toStack((int) Math.min(entry.getLongValue(), Integer.MAX_VALUE)));
				}
			}
		}
		saveChanges();

		// Wake up promptly so breeding starts next tick rather than after the idle interval.
		IGridNode node = getMainNode().getNode();
		if (node != null) {
			node.getGrid().getTickManager().alertDevice(node);
		}
		return true;
	}

	/**
	 * Runs one step of the active craft job: drain the apiary (banking any result-species offspring as canonical output,
	 * recycling parent-species offspring), then restock the parents from the in-flight buffer (or the network as a
	 * fallback). When the job's output count is satisfied it is cleared and any leftover parents are returned to the
	 * network.
	 *
	 * @return whether any bee was moved this step
	 */
	public boolean runCraftTick(MEStorage storage) {
		// Testing-only: with the hack on, a genome job is fabricated deterministically (no apiary, no hill-climb) - see field doc.
		if (this.forceMutationHack && this.craftTargetGenome != null && this.craftResult != null) {
			return fabricateGenomeTarget(storage);
		}
		List<Apiary> apiaries = drivenApiaries(findApiaries());
		this.apiaryFastBreeding = false;
		if (apiaries.isEmpty()) {
			return false;
		}
		applyForcedMutationHack(apiaries); // testing-only: force ambiguous species steps to the wanted result (see field doc)

		// Drain every usable apiary, banking result-species offspring toward the same job - so more apiaries breed the
		// requested mutation in parallel and the job's remaining count falls faster.
		boolean worked = false;
		this.drainSawOffspring = false;
		long remainingBefore = this.craftRemaining;
		for (Apiary apiary : apiaries) {
			this.apiaryFastBreeding |= hasCreativeFrame(apiary.container());
			if (this.craftResult != null) {
				worked |= drainCraftProducts(apiary, storage);
			}
		}
		boolean deliveredThisCycle = this.craftRemaining < remainingBefore;

		if (this.craftResult == null) {
			return true; // the job was abandoned mid-drain (its result species is no longer registered)
		}
		if (this.craftRemaining <= 0) {
			finishCraftJob(storage);
			return true;
		}

		KeyCounter available = new KeyCounter();
		storage.getAvailableStacks(available);

		// Evaluated once per completed breeding cycle (a drain that moved offspring), NOT per tick - one apiary cycle spans
		// hundreds of ticks, so a tick-based check would fire before the first offspring ever appear.
		if (this.craftTargetGenome != null && this.drainSawOffspring) {
			// Fast path: a required allele can go extinct in the breeding population (its last carrier consumed with no heir
			// inheriting it). The target is then genetically unreachable no matter how long we breed, so give up the instant
			// the state says so rather than burning the whole idle budget first.
			if (!isGenomeReachable(available)) {
				abandonGenomeJob(storage);
				return true;
			}
			// Backstop: a reachable target that simply is not converging (the allele is present but the stochastic loop never
			// concentrates it into a deliverable princess) cannot be told apart from slow convergence by state alone, so fall
			// back to abandoning after too many cycles with no forward progress - neither a better carrier nor a delivery.
			if (updateGenomeWatchdog(available, deliveredThisCycle)) {
				abandonGenomeJob(storage);
				return true;
			}
		}

		// Stage the job's parents into every usable apiary (buffer bootstrap first, then the network) so all breed at once.
		for (Apiary apiary : apiaries) {
			worked |= restockCraftParents(apiary, storage, available);
		}
		return worked;
	}

	/**
	 * Updates the genome-job watchdog. Forward progress is either a better result-species carrier appearing in the network
	 * (the breeding population) or a unit actually delivered this cycle; anything else is an idle cycle.
	 *
	 * @return whether the job should be abandoned (no forward progress for too many cycles). This fires even while sitting
	 *     at max score: a target-hit <em>drone</em> keeps {@link #craftBestScore} maxed (only princesses are deliverable),
	 *     so without counting delivery a max-but-undeliverable job would pin its CPU forever with no forward progress.
	 */
	private boolean updateGenomeWatchdog(KeyCounter available, boolean deliveredThisCycle) {
		int best = bestNetworkScore(available, this.craftResult);
		boolean progressed = best > this.craftBestScore || deliveredThisCycle;
		if (best > this.craftBestScore) {
			this.craftBestScore = best;
		}
		if (progressed) {
			this.craftIdleCycles = 0;
		} else {
			this.craftIdleCycles++;
		}
		return this.craftIdleCycles > GENOME_WATCHDOG_BUDGET;
	}

	/** @return the highest {@link #scoreTowardFilter} among network bees of the given species (0 if none / no target). */
	private int bestNetworkScore(KeyCounter available, @Nullable ResourceLocation species) {
		if (this.craftScoreFilter == null || species == null) {
			return 0;
		}
		int best = 0;
		for (var entry : available) {
			if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey key)) {
				continue;
			}
			ItemStack stack = key.getReadOnlyStack();
			if (!species.equals(speciesIdOf(stack))) {
				continue;
			}
			IGenome genome = genomeOf(stack);
			if (genome != null) {
				best = Math.max(best, scoreTowardFilter(genome, this.craftScoreFilter));
			}
		}
		return best;
	}

	/**
	 * @return whether the target is still genetically reachable from the current breeding population: every pinned allele
	 *     is still present (active or inactive) in some bee of the result (chassis) or donor line, i.e. a bee the loop can
	 *     breed from or mate with. Once an allele is present nowhere usable, no amount of breeding can recreate it, so the
	 *     job is provably stuck (as opposed to merely slow) and can be abandoned immediately.
	 */
	@SuppressWarnings("unchecked")
	private boolean isGenomeReachable(KeyCounter available) {
		if (this.craftScoreFilter == null || this.craftTargetGenome == null || this.craftResult == null) {
			return true;
		}
		Set<ResourceLocation> pins = this.craftScoreFilter.requiredHomozygous();
		if (pins.isEmpty()) {
			return true;
		}
		Set<ResourceLocation> present = new HashSet<>();
		for (var entry : available) {
			if (present.size() == pins.size()) {
				break;
			}
			if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey key)) {
				continue;
			}
			ItemStack stack = key.getReadOnlyStack();
			ResourceLocation species = speciesIdOf(stack);
			// The loop only breeds the chassis line and mates it with chassis carriers or the donor line, so an allele
			// stranded in some unrelated species is not reachable - only look in those two lines.
			if (!this.craftResult.equals(species) && !this.craftSecondParent.equals(species)) {
				continue;
			}
			IGenome genome = genomeOf(stack);
			if (genome == null) {
				continue;
			}
			for (ResourceLocation pin : pins) {
				if (present.contains(pin)) {
					continue;
				}
				IChromosome<Object> chromosome = (IChromosome<Object>) genome.getKaryotype().getChromosome(pin);
				if (chromosome == null) {
					present.add(pin); // can't evaluate this chromosome - do not block on it
					continue;
				}
				Object wanted = this.craftTargetGenome.getActiveValue(chromosome);
				if (wanted.equals(genome.getActiveValue(chromosome)) || wanted.equals(genome.getInactiveValue(chromosome))) {
					present.add(pin);
				}
			}
		}
		return present.containsAll(pins);
	}

	/** Abandons a stuck genome job: cancels the waiting AE2 CPU (if it is the top-level request) and clears the job. */
	private void abandonGenomeJob(MEStorage storage) {
		IBeeSpecies result = SpeciesUtil.getBeeSpecies(this.craftResult);
		if (result != null && this.craftTargetGenome != null) {
			cancelMatchingCpu(BeeMutationPattern.canonicalKey(result, this.craftTargetGenome, BeeLifeStage.PRINCESS));
		}
		finishCraftJob(storage);
	}

	/**
	 * Cancels the crafting CPU whose top-level output is {@code output}. AE2 exposes no provider-side "fail this step", but
	 * {@link ICraftingCPU#cancelJob()} cleanly cancels a whole job (returning reserved items). We can only match the CPU by
	 * its final output, so this cancels a job whose <em>top-level</em> request is our stuck genome; when our bee is a nested
	 * ingredient no CPU matches and we simply free ourselves (the umbrella job is the requester's to cancel).
	 */
	private void cancelMatchingCpu(AEItemKey output) {
		IGrid grid = getMainNode().getGrid();
		if (grid == null) {
			return;
		}
		for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
			if (!cpu.isBusy()) {
				continue;
			}
			CraftingJobStatus status = cpu.getJobStatus();
			if (status != null && status.crafting() != null && output.equals(status.crafting().what())) {
				cpu.cancelJob();
				return;
			}
		}
	}

	/**
	 * Drains the apiary's product slots for the active job: result-species offspring are converted to the pattern's
	 * canonical outputs and injected (closing the CPU step), parent-species offspring are recycled into the craft buffer
	 * to keep breeding, and everything else (honey, combs, off-target mutations) is pushed to the network.
	 */
	private boolean drainCraftProducts(Apiary apiary, MEStorage storage) {
		IBeeSpecies result = SpeciesUtil.getBeeSpecies(this.craftResult);
		if (result == null) {
			// Result species no longer registered - abandon the job to avoid a stuck CPU.
			finishCraftJob(storage);
			return true;
		}
		if (this.craftTargetGenome != null) {
			return drainGenomeProducts(apiary, storage, result);
		}
		AEItemKey outPrincess = BeeMutationPattern.canonicalKey(result, BeeLifeStage.PRINCESS);
		AEItemKey outDrone = BeeMutationPattern.canonicalKey(result, BeeLifeStage.DRONE);

		boolean worked = false;
		int first = InventoryBeeHousing.SLOT_PRODUCT_1;
		for (int slot = first; slot < first + InventoryBeeHousing.SLOT_PRODUCT_COUNT; slot++) {
			ItemStack stack = apiary.container().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ResourceLocation species = speciesIdOf(stack);

			if (species != null && species.equals(this.craftResult)) {
				// Each bred result bee fulfils one execution: emit the canonical princess (primary) + drone (byproduct).
				while (this.craftRemaining > 0 && !stack.isEmpty()) {
					if (storage.insert(outPrincess, 1, Actionable.MODULATE, this.actionSource) <= 0) {
						break; // network is full - try again next tick rather than voiding the bee
					}
					storage.insert(outDrone, 1, Actionable.MODULATE, this.actionSource);
					this.craftRemaining--;
					stack.shrink(1);
					worked = true;
				}
				// Any remaining bred result bees beyond what was requested are a bonus - store them as-is.
				if (!stack.isEmpty()) {
					worked |= injectStack(apiary, slot, stack, storage);
				} else {
					apiary.container().setItem(slot, ItemStack.EMPTY);
				}
			} else if (species != null && (species.equals(this.craftFirstParent) || species.equals(this.craftSecondParent))) {
				// Recycle parent-species offspring so the loop keeps breeding until the mutation appears.
				ItemStack leftover = this.craftBuffer.addItems(stack.copy());
				apiary.container().setItem(slot, leftover);
				worked = true;
			} else {
				worked |= injectStack(apiary, slot, stack, storage);
			}
		}
		if (worked) {
			apiary.container().setChanged();
		}
		return worked;
	}

	/**
	 * Drains the apiary for a <em>genome</em> job: a bred princess that has reached the target homozygously is delivered
	 * as the canonical target key (never the bred stack) and closes a CPU step; every other bee (base/donor offspring,
	 * carriers, even a finished target drone) plus honey/combs is pushed to the network - which <em>is</em> the genome
	 * job's breeding population, an unbounded pool the parent selection re-samples the best carriers from, avoiding the
	 * small craft buffer's diversity bottleneck (which stalled multi-generation cross-species fixation).
	 */
	private boolean drainGenomeProducts(Apiary apiary, MEStorage storage, IBeeSpecies result) {
		AEItemKey outPrincess = BeeMutationPattern.canonicalKey(result, this.craftTargetGenome, BeeLifeStage.PRINCESS);
		boolean worked = false;
		int first = InventoryBeeHousing.SLOT_PRODUCT_1;
		for (int slot = first; slot < first + InventoryBeeHousing.SLOT_PRODUCT_COUNT; slot++) {
			ItemStack stack = apiary.container().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			IGenome genome = genomeOf(stack);
			ResourceLocation species = speciesIdOf(stack);
			if (genome != null) {
				// An actual bee (not honey/combs) in the products means a breeding cycle just completed - the unit the
				// watchdog measures progress in (a creative frame spews honey every tick, so raw drain activity can't).
				this.drainSawOffspring = true;
			}
			boolean targetPrincess = genome != null && this.craftResult.equals(species)
					&& IIndividualHandlerItem.getLifeStage(stack) == BeeLifeStage.PRINCESS && isGenomeTargetHit(genome);

			if (targetPrincess && this.craftRemaining > 0) {
				// A finished min-maxed queen: deliver the canonical target key (never the bred stack, whose analyzed/mated
				// components would desync from the pattern's declared output) and close one CPU step.
				while (this.craftRemaining > 0 && !stack.isEmpty()) {
					if (storage.insert(outPrincess, 1, Actionable.MODULATE, this.actionSource) <= 0) {
						break; // network is full - try again next tick rather than voiding the bee
					}
					this.craftRemaining--;
					stack.shrink(1);
					worked = true;
				}
				if (stack.isEmpty()) {
					apiary.container().setItem(slot, ItemStack.EMPTY);
				} else {
					worked |= injectStack(apiary, slot, stack, storage); // surplus finished queens go to the network as-is
				}
			} else {
				// Everything else - carriers, target drones, base/donor offspring, honey/combs - goes to the network (the
				// breeding population the parent selection re-samples from).
				worked |= injectStack(apiary, slot, stack, storage);
			}
		}
		if (worked) {
			apiary.container().setChanged();
		}
		return worked;
	}

	/** @return whether the genome is a completed target: its result species with every pinned trait homozygous. */
	private boolean isGenomeTargetHit(IGenome genome) {
		return this.craftScoreFilter != null && scoreTowardFilter(genome, this.craftScoreFilter) >= this.craftMaxScore;
	}

	/** Inserts as much of the apiary slot's stack into the network as fits, shrinking the slot by that amount. */
	private boolean injectStack(Apiary apiary, int slot, ItemStack stack, MEStorage storage) {
		long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, this.actionSource);
		if (inserted <= 0) {
			return false;
		}
		stack.shrink((int) inserted);
		apiary.container().setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		return true;
	}

	/** Stages the job's parents into the apiary, preferring the in-flight buffer and falling back to the network. */
	private boolean restockCraftParents(Apiary apiary, MEStorage storage, KeyCounter cached) {
		IBeeSpecies first = SpeciesUtil.getBeeSpecies(this.craftFirstParent);
		IBeeSpecies second = SpeciesUtil.getBeeSpecies(this.craftSecondParent);
		if (first == null || second == null) {
			return false;
		}
		if (this.craftTargetGenome != null) {
			return restockGenomeParents(apiary, storage, cached, first, second);
		}
		boolean worked = false;
		IBeeHousingInventory inv = apiary.housing().getBeeInventory();

		if (inv.getQueen().isEmpty()) {
			ItemStack princess = takeFromBuffer(this.craftFirstParent, BeeLifeStage.PRINCESS);
			if (princess.isEmpty()) {
				princess = pullParent(storage, cached, speciesStageFilter(first, BeeLifeStage.PRINCESS), BeeLifeStage.PRINCESS);
			}
			if (!princess.isEmpty()) {
				inv.setQueen(princess);
				worked = true;
			}
		}

		// As in the standing loop: only add a drone while an unmated princess is waiting to mate, never to a working queen.
		if (inv.getDrone().isEmpty() && queenNeedsMate(inv)) {
			ItemStack drone = takeFromBuffer(this.craftSecondParent, BeeLifeStage.DRONE);
			if (drone.isEmpty()) {
				drone = pullParent(storage, cached, speciesStageFilter(second, BeeLifeStage.DRONE), BeeLifeStage.DRONE);
			}
			if (!drone.isEmpty()) {
				inv.setDrone(drone);
				worked = true;
			}
		}

		if (worked) {
			apiary.container().setChanged();
		}
		return worked;
	}

	/**
	 * Stages parents for a genome job from the network breeding population: the princess is the best-scoring bee of the
	 * base (result) species, so the chassis line stays the result species and its carriers climb toward homozygosity; the
	 * drone is the best-scoring carrier that supplies the wanted alleles, taken from the bred result line once it carries
	 * them (self-sustaining) or the donor line for bootstrap. The initial pure base/donor parents AE2 handed us are
	 * consumed from the craft buffer first to bootstrap, then everything comes from the (unbounded) network.
	 */
	private boolean restockGenomeParents(Apiary apiary, MEStorage storage, KeyCounter cached, IBeeSpecies base, IBeeSpecies donor) {
		boolean worked = false;
		IBeeHousingInventory inv = apiary.housing().getBeeInventory();

		if (inv.getQueen().isEmpty()) {
			ItemStack princess = takeFromBuffer(this.craftFirstParent, BeeLifeStage.PRINCESS);
			if (princess.isEmpty()) {
				princess = pullBestParent(storage, cached, speciesStageFilter(base, BeeLifeStage.PRINCESS), BeeLifeStage.PRINCESS, this.craftScoreFilter);
			}
			if (!princess.isEmpty()) {
				inv.setQueen(princess);
				worked = true;
			}
		}

		if (inv.getDrone().isEmpty() && queenNeedsMate(inv)) {
			// AE2 satisfies the donor slot from stock (any bee carrying the pins, of any species) or by autocrafting the
			// easiest donor, and hands it to us in the buffer - so take whatever drone it gave us regardless of species.
			ItemStack drone = takeFromBuffer(null, BeeLifeStage.DRONE);
			if (drone.isEmpty()) {
				// Bootstrap spent: mate from the network. A bred result-species carrier drone (which the haploid-drone brood
				// throws readily, homozygous for the pin) supplies the alleles and keeps offspring on the chassis species;
				// prefer whichever of the result- or donor-species best drone scores higher (result on ties, to stay
				// on-species), so the loop is self-sustaining instead of stranding an unmated princess.
				AEItemKey mate = bestMateDrone(cached, base, donor);
				if (mate != null) {
					drone = pullParentKey(storage, cached, mate);
				}
			}
			if (!drone.isEmpty()) {
				inv.setDrone(drone);
				worked = true;
			}
		}

		if (worked) {
			apiary.container().setChanged();
		}
		return worked;
	}

	/** @return the higher-scoring of the best result-species and best donor-species drone (result wins ties), or null. */
	@Nullable
	private AEItemKey bestMateDrone(KeyCounter cached, IBeeSpecies base, IBeeSpecies donor) {
		AEItemKey baseDrone = findBestParent(cached, speciesStageFilter(base, BeeLifeStage.DRONE), BeeLifeStage.DRONE, this.craftScoreFilter);
		AEItemKey donorDrone = findBestParent(cached, speciesStageFilter(donor, BeeLifeStage.DRONE), BeeLifeStage.DRONE, this.craftScoreFilter);
		return mateScore(baseDrone) >= mateScore(donorDrone) && baseDrone != null ? baseDrone : donorDrone;
	}

	/** @return the drone's score toward the current genome target, or -1 if the key is null / not a bee. */
	private int mateScore(@Nullable AEItemKey key) {
		if (key == null || this.craftScoreFilter == null) {
			return -1;
		}
		IGenome genome = genomeOf(key.getReadOnlyStack());
		return genome == null ? -1 : scoreTowardFilter(genome, this.craftScoreFilter);
	}

	/** Extracts one bee of the given key from the network, keeping the shared snapshot honest. */
	private ItemStack pullParentKey(MEStorage storage, KeyCounter cached, AEItemKey key) {
		long got = storage.extract(key, 1, Actionable.MODULATE, this.actionSource);
		if (got <= 0) {
			return ItemStack.EMPTY;
		}
		cached.remove(key, got);
		return key.toStack((int) got);
	}

	/**
	 * Removes and returns one bee of the given stage from the craft buffer, or {@link ItemStack#EMPTY} if none. When
	 * {@code species} is non-null the bee must also be that species; a {@code null} species matches any (the bootstrap
	 * donor drone, which AE2 may have satisfied with any carrier species).
	 */
	private ItemStack takeFromBuffer(@Nullable ResourceLocation species, BeeLifeStage stage) {
		for (int slot = 0; slot < this.craftBuffer.size(); slot++) {
			ItemStack stack = this.craftBuffer.getStackInSlot(slot);
			if (stack.isEmpty() || IIndividualHandlerItem.getLifeStage(stack) != stage) {
				continue;
			}
			if (species != null && !species.equals(speciesIdOf(stack))) {
				continue;
			}
			ItemStack one = stack.copy();
			one.setCount(1);
			stack.shrink(1);
			this.craftBuffer.setItemDirect(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
			return one;
		}
		return ItemStack.EMPTY;
	}

	/** Clears the active job and returns any parents still buffered (never staged) to the network. */
	private void finishCraftJob(MEStorage storage) {
		for (int slot = 0; slot < this.craftBuffer.size(); slot++) {
			ItemStack stack = this.craftBuffer.getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, this.actionSource);
			if (inserted > 0) {
				stack.shrink((int) inserted);
				this.craftBuffer.setItemDirect(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
			}
		}
		clearJob();
		saveChanges();
	}

	/** Resets every field that defines the active craft job to its idle state. Does not persist - callers save as needed. */
	private void clearJob() {
		this.craftResult = null;
		this.craftFirstParent = null;
		this.craftSecondParent = null;
		this.craftRemaining = 0;
		this.craftBestScore = 0;
		this.craftIdleCycles = 0;
		applyGenomeTarget(null);
	}

	/**
	 * The {@link #forceMutationHack testing-only hack} for a genome job: deliver the exact target princess directly instead
	 * of hill-climbing to it. AE2 has already autocrafted (and handed us in the buffer) the base + donor bred up from the
	 * wild roots, so the whole species tree is still exercised; this only replaces the stochastic, extinction-prone
	 * multi-generation selection with a deterministic result so a large run is fast and reliable.
	 */
	private boolean fabricateGenomeTarget(MEStorage storage) {
		IBeeSpecies result = SpeciesUtil.getBeeSpecies(this.craftResult);
		if (result == null || this.craftTargetGenome == null) {
			finishCraftJob(storage);
			return true;
		}
		AEItemKey outPrincess = BeeMutationPattern.canonicalKey(result, this.craftTargetGenome, BeeLifeStage.PRINCESS);
		while (this.craftRemaining > 0) {
			if (storage.insert(outPrincess, 1, Actionable.MODULATE, this.actionSource) <= 0) {
				return true; // network full - try again next tick
			}
			this.craftRemaining--;
		}
		finishCraftJob(storage); // returns the consumed base/donor to the network (harmless surplus in a test)
		return true;
	}

	private static BeeFilter speciesStageFilter(IBeeSpecies species, BeeLifeStage stage) {
		return new BeeFilter(EnumSet.of(stage), Optional.of(species.id()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	@Nullable
	private static ResourceLocation speciesIdOf(ItemStack stack) {
		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		return individual == null ? null : individual.getSpecies().id();
	}

	// --- Shared apiary I/O -------------------------------------------------------------------------------------------

	/**
	 * Finds every adjacent bee housing this controller may drive, refreshing the usable/contended counts for the GUI.
	 *
	 * <p>Both single-block housings (apiary, bee house) and the <b>alveary multiblock</b> are supported. An alveary
	 * shares one bee inventory across all its faces, so it is treated as one logical unit: resolved via
	 * {@link #housingMembers}, deduplicated by {@link #canonicalMember}, and skipped entirely while unassembled.
	 *
	 * <p>A housing adjacent to two or more controllers is <b>contended</b> ({@link #controllersTouching}) and driven by
	 * none of them, so no two ever race over one shared inventory - evaluated across the whole alveary, not per face.
	 */
	private List<Apiary> findApiaries() {
		List<Apiary> usable = new ArrayList<>(Direction.values().length);
		Set<BlockPos> seen = new HashSet<>();
		int contended = 0;
		for (Direction dir : Direction.values()) {
			BlockPos neighborPos = this.worldPosition.relative(dir);
			BlockEntity be = this.level.getBlockEntity(neighborPos);
			if (!(be instanceof IBeeHousing housing) || !(be instanceof Container container)) {
				continue;
			}
			List<BlockPos> members = housingMembers(be, neighborPos);
			if (members == null) {
				continue; // an unassembled alveary block: no working inventory yet, so it is not drivable
			}
			if (!seen.add(canonicalMember(members))) {
				continue; // already found this same alveary through another adjacent face this tick
			}
			if (controllersTouching(members) >= 2) {
				contended++;
			} else {
				usable.add(new Apiary(housing, container));
			}
		}
		this.usableApiaryCount = usable.size();
		this.contendedApiaryCount = contended;
		return usable;
	}

	/**
	 * @return the member positions of the logical housing the block belongs to, or {@code null} for a multiblock that
	 * is not assembled. A single-block housing returns just its own position; an assembled multiblock returns every
	 * component's position, so callers treat the whole structure as one unit.
	 */
	@Nullable
	private List<BlockPos> housingMembers(BlockEntity be, BlockPos pos) {
		if (be instanceof IMultiblockComponent component) {
			IMultiblockController controller = component.getMultiblockLogic().getController();
			if (!controller.isAssembled()) {
				return null;
			}
			Collection<IMultiblockComponent> components = controller.getComponents();
			List<BlockPos> members = new ArrayList<>(components.size());
			for (IMultiblockComponent member : components) {
				members.add(member.getCoordinates());
			}
			return members.isEmpty() ? null : members;
		}
		return List.of(pos);
	}

	/** @return a deterministic identity for a logical housing: its member with the smallest packed position. */
	private static BlockPos canonicalMember(List<BlockPos> members) {
		BlockPos best = members.get(0);
		for (BlockPos member : members) {
			if (member.asLong() < best.asLong()) {
				best = member;
			}
		}
		return best;
	}

	/** @return how many distinct controllers (this one included) are adjacent to any block of the housing; two or more means contended. */
	private int controllersTouching(List<BlockPos> members) {
		Set<BlockPos> controllers = new HashSet<>();
		for (BlockPos member : members) {
			for (Direction dir : Direction.values()) {
				BlockPos adjacent = member.relative(dir);
				if (this.level.getBlockEntity(adjacent) instanceof ApiaryControllerBlockEntity) {
					controllers.add(adjacent);
				}
			}
		}
		return controllers.size();
	}

	/**
	 * Narrows the usable apiaries to those whose frame is compatible with the <em>current</em> job - two "pools" behind one
	 * controller. A <b>genome/introgression</b> job ({@link #craftTargetGenome} set) must avoid any apiary whose frame
	 * <em>forces</em> mutations: forcing a species change every cycle would keep knocking the chassis off its species so the
	 * pinned traits never fix homozygous. A plain <b>species-creation</b> job uses every apiary (a force-mutation frame just
	 * breeds the mutation reliably; a plain one breeds it at the natural rate). So a rig with a force-mutation apiary
	 * (the creation pool) and a plain apiary (the min-max pool) autocrafts the whole tree - species up from the roots on the
	 * forcing apiary, then the trait introgression on the plain one.
	 */
	private List<Apiary> drivenApiaries(List<Apiary> usable) {
		if (this.craftTargetGenome == null) {
			return usable;
		}
		List<Apiary> plain = new ArrayList<>(usable.size());
		for (Apiary apiary : usable) {
			if (!forcesMutations(apiary.container())) {
				plain.add(apiary);
			}
		}
		return plain;
	}

	/**
	 * @return whether the apiary holds a creative frame that <em>forces</em> mutations - either a random one of the pair's
	 * mutations ({@link ItemCreativeHiveFrame#hasForceMutations}) or a specific result ({@link ItemCreativeHiveFrame#getForcedMutation}).
	 * Such a frame is incompatible with trait introgression, which needs offspring to stay the chassis species.
	 */
	private static boolean forcesMutations(Container apiary) {
		return anyFrame(apiary, stack -> stack.getItem() instanceof ItemCreativeHiveFrame
				&& (ItemCreativeHiveFrame.hasForceMutations(stack) || ItemCreativeHiveFrame.getForcedMutation(stack) != null));
	}

	/** One past the last apiary frame slot; also the minimum container size a frame scan needs. */
	private static final int FRAMES_END = InventoryApiary.SLOT_FRAMES_1 + InventoryApiary.SLOT_FRAMES_COUNT;

	/** @return whether any of the apiary's frame slots holds a stack matching {@code predicate} (false if it has no frame slots). */
	private static boolean anyFrame(Container apiary, Predicate<ItemStack> predicate) {
		if (apiary.getContainerSize() < FRAMES_END) {
			return false;
		}
		for (int slot = InventoryApiary.SLOT_FRAMES_1; slot < FRAMES_END; slot++) {
			if (predicate.test(apiary.getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The {@link #forceMutationHack testing-only hack}: for the current species-creation job, stamp its result species onto
	 * the {@code forced_mutation} of every creation-pool (force-mutation) apiary frame, so an ambiguous parent pair breeds
	 * exactly that mutation. Only touches apiaries that already force mutations, so the plain min-max apiaries stay plain.
	 */
	private void applyForcedMutationHack(List<Apiary> apiaries) {
		if (!this.forceMutationHack || this.craftTargetGenome != null || this.craftResult == null) {
			return;
		}
		for (Apiary apiary : apiaries) {
			Container container = apiary.container();
			if (!forcesMutations(container)) {
				continue;
			}
			for (int slot = InventoryApiary.SLOT_FRAMES_1; slot < FRAMES_END; slot++) {
				ItemStack frame = container.getItem(slot);
				if (frame.getItem() instanceof ItemCreativeHiveFrame && !this.craftResult.equals(ItemCreativeHiveFrame.getForcedMutation(frame))) {
					ItemCreativeHiveFrame.setForcedMutation(frame, this.craftResult);
					container.setItem(slot, frame);
					container.setChanged();
				}
			}
		}
	}

	/**
	 * @return whether the apiary holds a creative frame, which makes breeding near-instant (100% production, instant
	 * queen death). Only apiaries have frame slots; other bee housings (bee houses/hives) return {@code false}.
	 */
	private static boolean hasCreativeFrame(Container apiary) {
		return anyFrame(apiary, stack -> stack.getItem() instanceof ItemCreativeHiveFrame);
	}

	/** Moves everything in the apiary's product slots (offspring + produce) into the ME network. */
	private boolean drainProducts(Apiary apiary, MEStorage storage) {
		boolean worked = false;
		int first = InventoryBeeHousing.SLOT_PRODUCT_1;
		for (int slot = first; slot < first + InventoryBeeHousing.SLOT_PRODUCT_COUNT; slot++) {
			ItemStack stack = apiary.container().getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, this.actionSource);
			if (inserted > 0) {
				apiary.container().removeItem(slot, (int) inserted);
				worked = true;
			}
		}
		if (worked) {
			apiary.container().setChanged();
		}
		return worked;
	}

	/** Extracts one bee matching the filter and required life stage from the network, or {@link ItemStack#EMPTY} if none. */
	private ItemStack pullParent(MEStorage storage, KeyCounter cached, BeeFilter filter, BeeLifeStage stage) {
		return pullBestParent(storage, cached, filter, stage, null);
	}

	/**
	 * Extracts the network bee of the required stage that best advances toward {@code target} (its highest-scoring match
	 * per {@link #findBestParent}), or {@link ItemStack#EMPTY} if none matches {@code filter}. With a {@code null} target
	 * (or a target carrying no trait constraints) this is a plain first-match pull.
	 */
	private ItemStack pullBestParent(MEStorage storage, KeyCounter cached, BeeFilter filter, BeeLifeStage stage, @Nullable BeeFilter target) {
		AEItemKey key = findBestParent(cached, filter, stage, target);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		long got = storage.extract(key, 1, Actionable.MODULATE, this.actionSource);
		if (got <= 0) {
			return ItemStack.EMPTY;
		}
		// Keep the shared snapshot honest so a later apiary this tick doesn't try to pull the same now-extracted bee.
		cached.remove(key, got);
		return key.toStack((int) got);
	}

	/**
	 * The first bee key in the network that matches the filter and is of the required life stage.
	 * Pure and grid-independent so it is unit-testable.
	 */
	@Nullable
	public static AEItemKey findParent(KeyCounter contents, BeeFilter filter, BeeLifeStage stage) {
		for (var entry : contents) {
			if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey itemKey)) {
				continue;
			}
			ItemStack stack = itemKey.getReadOnlyStack();
			if (IIndividualHandlerItem.getLifeStage(stack) == stage && filter.matches(stack)) {
				return itemKey;
			}
		}
		return null;
	}

	/**
	 * The network bee of the required stage that best advances toward the target filter's trait goal - the highest
	 * {@link #scoreTowardFilter} among those matching {@code matchFilter}. Ties resolve to iteration order, so when the
	 * target imposes no trait constraints (every score {@code 0}) this degrades to {@link #findParent}'s first match.
	 * The network is the breeding population: re-selecting the best carriers each cycle drives the whole population
	 * toward the target (introgression, then homozygosity). Pure and grid-independent so it is unit-testable.
	 */
	@Nullable
	public static AEItemKey findBestParent(KeyCounter contents, BeeFilter matchFilter, BeeLifeStage stage, @Nullable BeeFilter target) {
		AEItemKey best = null;
		int bestScore = Integer.MIN_VALUE;
		for (var entry : contents) {
			if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey itemKey)) {
				continue;
			}
			ItemStack stack = itemKey.getReadOnlyStack();
			if (IIndividualHandlerItem.getLifeStage(stack) != stage || !matchFilter.matches(stack)) {
				continue;
			}
			IGenome genome = genomeOf(stack);
			int score = (target == null || genome == null) ? 0 : scoreTowardFilter(genome, target);
			if (score > bestScore) {
				bestScore = score;
				best = itemKey;
			}
		}
		return best;
	}

	/**
	 * Scores how far a candidate genome has progressed toward a trait target: {@code +2} per required-homozygous
	 * chromosome already homozygous for the wanted value, {@code +1} if only one allele carries it; {@code +1} per
	 * required-active chromosome whose expressed value matches. A candidate that fully satisfies the target scores the
	 * maximum. Returns {@code 0} when the target carries no genome template (nothing to hill-climb toward), so ranking
	 * falls back to iteration order. Pure and grid-independent.
	 */
	@SuppressWarnings("unchecked")
	public static int scoreTowardFilter(IGenome candidate, BeeFilter target) {
		if (target.genomeTemplate().isEmpty()) {
			return 0;
		}
		IGenome template = target.genomeTemplate().get();
		int score = 0;
		for (ResourceLocation id : target.requiredHomozygous()) {
			IChromosome<Object> chromosome = (IChromosome<Object>) candidate.getKaryotype().getChromosome(id);
			if (chromosome == null) {
				continue;
			}
			Object want = template.getActiveValue(chromosome);
			boolean activeHit = want.equals(candidate.getActiveValue(chromosome));
			boolean inactiveHit = want.equals(candidate.getInactiveValue(chromosome));
			score += (activeHit && inactiveHit) ? 2 : (activeHit || inactiveHit) ? 1 : 0;
		}
		for (ResourceLocation id : target.requiredActive()) {
			if (target.requiredHomozygous().contains(id)) {
				continue;
			}
			IChromosome<Object> chromosome = (IChromosome<Object>) candidate.getKaryotype().getChromosome(id);
			if (chromosome != null && template.getActiveValue(chromosome).equals(candidate.getActiveValue(chromosome))) {
				score += 1;
			}
		}
		return score;
	}

	/** @return the full genome of the given bee stack, or {@code null} if it is not an individual. */
	@Nullable
	private static IGenome genomeOf(ItemStack stack) {
		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		return individual == null ? null : individual.getGenome();
	}

	/** Total count of bees in the network matching the filter (any life stage). Pure and grid-independent. */
	public static long countMatching(KeyCounter contents, BeeFilter filter) {
		long total = 0;
		for (var entry : contents) {
			AEKey key = entry.getKey();
			if (key instanceof AEItemKey itemKey && filter.matches(itemKey.getReadOnlyStack())) {
				total += entry.getLongValue();
			}
		}
		return total;
	}

	@Override
	public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
		super.saveAdditional(data, registries); // persists the Bee Pattern inventory (getInternalInventory)
		data.putString("mode", this.mode.name());
		data.putBoolean("forceMutationHack", this.forceMutationHack);
		this.craftBuffer.writeToNBT(data, "craftBuffer", registries);
		if (this.craftResult != null && this.craftFirstParent != null && this.craftSecondParent != null) {
			CompoundTag job = new CompoundTag();
			job.putString("result", this.craftResult.toString());
			job.putString("firstParent", this.craftFirstParent.toString());
			job.putString("secondParent", this.craftSecondParent.toString());
			job.putLong("remaining", this.craftRemaining);
			if (this.craftTargetGenome != null) {
				Tag target = IGenome.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.craftTargetGenome).getOrThrow();
				job.put("target", target);
			}
			data.put("craftJob", job);
		}
	}

	@Override
	public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
		super.loadTag(data, registries); // restores the Bee Pattern inventory (getInternalInventory)
		// Mode is stored as a string (an unknown/retired name - e.g. the old REQUESTER - falls back to DEFAULT via byName);
		// also accept the legacy boolean "craftingMode" (true -> AUTOCRAFT) so pre-existing saves load.
		this.mode = data.contains("mode")
				? ControllerMode.byName(data.getString("mode"))
				: (data.getBoolean("craftingMode") ? ControllerMode.AUTOCRAFT : ControllerMode.DEFAULT);
		this.forceMutationHack = data.getBoolean("forceMutationHack");
		this.craftBuffer.readFromNBT(data, "craftBuffer", registries);
		this.cachedDecoded = null;
		if (data.contains("craftJob")) {
			CompoundTag job = data.getCompound("craftJob");
			this.craftResult = ResourceLocation.parse(job.getString("result"));
			this.craftFirstParent = ResourceLocation.parse(job.getString("firstParent"));
			this.craftSecondParent = ResourceLocation.parse(job.getString("secondParent"));
			this.craftRemaining = job.getLong("remaining");
			IGenome target = job.contains("target")
					? IGenome.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), job.get("target")).getOrThrow()
					: null;
			applyGenomeTarget(target);
			this.craftBestScore = 0;
			this.craftIdleCycles = 0;
		} else {
			clearJob();
		}
	}

	/** An adjacent bee housing paired with its {@link Container} view (for product-slot I/O). */
	private record Apiary(IBeeHousing housing, Container container) {
	}
}
