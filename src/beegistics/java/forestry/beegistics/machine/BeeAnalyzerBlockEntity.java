package forestry.beegistics.machine;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import forestry.api.genetics.IIndividual;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.beegistics.BeeKeys;
import forestry.beegistics.BeegisticsBlockEntities;
import forestry.core.fluids.FilteredTank;
import forestry.core.fluids.FluidTagFilter;
import forestry.core.fluids.ForestryFluids;

/**
 * A grid-connected block that analyzes bees anywhere in the ME network, so terminals and automation see full genetics
 * without manual analyzer trips. Each grid tick it tops up an internal honey buffer from the network's fluid storage,
 * then extracts unanalyzed bee keys, {@linkplain IIndividual#analyze() analyzes} them, and inserts the analyzed keys
 * back - charging {@link #HONEY_PER_BEE} mB of honey (mirroring {@code TileAnalyzer}) plus {@link #ENERGY_PER_BEE} AE
 * (mirroring the Apiarist's Terminal's inline analyzer box) per bee.
 */
public class BeeAnalyzerBlockEntity extends AENetworkedBlockEntity implements IGridTickable {
	/** Honey drained per bee analyzed, matching {@code TileAnalyzer.HONEY_REQUIRED}. */
	private static final int HONEY_PER_BEE = 100;
	/** AE energy drawn per bee analyzed, matching the terminal's inline analyzer box. */
	private static final double ENERGY_PER_BEE = 160.0;
	/** Internal honey buffer, auto-refilled from the ME fluid network. */
	private static final int TANK_CAPACITY = 16_000;
	/** Upper bound on bees processed in a single grid tick, to keep bursts bounded. */
	private static final int MAX_BEES_PER_TICK = 8;
	/** Only pull honey from the network once the buffer has at least this much free space, to avoid tiny churn. */
	private static final int REFILL_THRESHOLD = 1_000;

	private final FilteredTank honeyTank = (FilteredTank) new FilteredTank(TANK_CAPACITY).setFilter(FluidTagFilter.HONEY);
	private final IActionSource actionSource = IActionSource.ofMachine(this);

	public BeeAnalyzerBlockEntity(BlockPos pos, BlockState state) {
		super(BeegisticsBlockEntities.BEE_ANALYZER.get(), pos, state);
		getMainNode()
				.setIdlePowerUsage(0)
				.addService(IGridTickable.class, this);
	}

	/** Exposed as the block's fluid-handler capability so honey can also be piped in manually. */
	public IFluidHandler getHoneyTank() {
		return this.honeyTank;
	}

	@Override
	public TickingRequest getTickingRequest(IGridNode node) {
		// Never sleeps: there is no key-specific signal for "an unanalyzed bee entered the network", so we poll the
		// (cached) network contents, quickly while there is work and slowly while idle.
		return new TickingRequest(5, 40, false);
	}

	@Override
	public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
		IGrid grid = getMainNode().getGrid();
		if (grid == null) {
			return TickRateModulation.IDLE;
		}

		refillHoneyFromNetwork(grid);
		int analyzed = analyzeBees(grid);

		return analyzed > 0 ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
	}

	/** Draws honey out of the ME fluid network into the internal buffer when it has room. */
	private void refillHoneyFromNetwork(IGrid grid) {
		int space = this.honeyTank.getCapacity() - this.honeyTank.getFluidAmount();
		if (space < REFILL_THRESHOLD) {
			return;
		}

		MEStorage storage = grid.getStorageService().getInventory();
		AEFluidKey honey = AEFluidKey.of(ForestryFluids.HONEY.getFluid());
		long pulled = storage.extract(honey, space, Actionable.MODULATE, this.actionSource);
		if (pulled <= 0) {
			return;
		}

		int filled = this.honeyTank.fill(honey.toStack((int) pulled), IFluidHandler.FluidAction.EXECUTE);
		if (filled < pulled) {
			// The buffer accepted less than we pulled (shouldn't happen, we sized against free space) - return the rest.
			storage.insert(honey, pulled - filled, Actionable.MODULATE, this.actionSource);
		}
		if (filled > 0) {
			saveChanges();
		}
	}

	/**
	 * Analyzes up to {@link #MAX_BEES_PER_TICK} bees of the first unanalyzed species found in the network.
	 *
	 * @return the number of bees actually analyzed this tick.
	 */
	private int analyzeBees(IGrid grid) {
		int byHoney = this.honeyTank.getFluidAmount() / HONEY_PER_BEE;
		int budget = Math.min(MAX_BEES_PER_TICK, byHoney);
		if (budget <= 0) {
			return 0;
		}

		IStorageService storageService = grid.getStorageService();
		MEStorage storage = storageService.getInventory();
		IEnergyService energy = grid.getEnergyService();

		// Find the first unanalyzed bee key in the (cheap) cached network inventory.
		AEItemKey target = null;
		long targetAmount = 0;
		for (var entry : storageService.getCachedInventory()) {
			AEKey key = entry.getKey();
			if (!(key instanceof AEItemKey itemKey) || !BeeKeys.isBee(key)) {
				continue;
			}
			IIndividual individual = IIndividualHandlerItem.getIndividual(itemKey.getReadOnlyStack());
			if (individual == null || individual.isAnalyzed()) {
				continue;
			}
			target = itemKey;
			targetAmount = entry.getLongValue();
			break;
		}
		if (target == null) {
			return 0;
		}

		int toProcess = (int) Math.min(budget, targetAmount);

		// Cap by available grid energy.
		double simPower = energy.extractAEPower(toProcess * ENERGY_PER_BEE, Actionable.SIMULATE, PowerMultiplier.CONFIG);
		toProcess = Math.min(toProcess, (int) (simPower / ENERGY_PER_BEE + 1.0e-4));
		if (toProcess <= 0) {
			return 0;
		}

		// Build the analyzed key for this species.
		AEItemKey analyzedKey = analyzed(target);
		if (analyzedKey == null || analyzedKey.equals(target)) {
			return 0;
		}

		// Only take what we can put back analyzed, so bees can never be voided if the network is full.
		long insertable = storage.insert(analyzedKey, toProcess, Actionable.SIMULATE, this.actionSource);
		if (insertable <= 0) {
			return 0;
		}

		long extracted = storage.extract(target, insertable, Actionable.MODULATE, this.actionSource);
		if (extracted <= 0) {
			return 0;
		}
		long inserted = storage.insert(analyzedKey, extracted, Actionable.MODULATE, this.actionSource);
		long leftover = extracted - inserted;
		if (leftover > 0) {
			// Best-effort return of any bees we couldn't re-insert (racing insert should be near-impossible here).
			storage.insert(target, leftover, Actionable.MODULATE, this.actionSource);
		}

		int processed = (int) inserted;
		if (processed <= 0) {
			return 0;
		}

		this.honeyTank.drain(processed * HONEY_PER_BEE, IFluidHandler.FluidAction.EXECUTE);
		energy.extractAEPower(processed * ENERGY_PER_BEE, Actionable.MODULATE, PowerMultiplier.CONFIG);
		saveChanges();
		return processed;
	}

	/**
	 * The analyzed counterpart of a bee key: a copy of the bee with its genome revealed. Returns {@code null} if the key
	 * is not a genetic individual, and the key itself if it is already analyzed. Grid-independent, so it is unit-testable.
	 */
	@Nullable
	public static AEItemKey analyzed(AEItemKey key) {
		ItemStack stack = key.toStack(1);
		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		if (individual == null) {
			return null;
		}
		if (individual.isAnalyzed()) {
			return key;
		}
		individual.analyze();
		individual.saveToStack(stack);
		return AEItemKey.of(stack);
	}

	@Override
	public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
		super.saveAdditional(data, registries);
		CompoundTag tankTag = new CompoundTag();
		this.honeyTank.writeToNBT(registries, tankTag);
		data.put("honeyTank", tankTag);
	}

	@Override
	public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
		super.loadTag(data, registries);
		this.honeyTank.readFromNBT(registries, data.getCompound("honeyTank"));
	}
}
