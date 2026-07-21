package forestry.beegistics.terminal;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.ItemTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import forestry.api.genetics.IIndividual;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.beegistics.Beegistics;
import forestry.core.fluids.ForestryFluids;

/**
 * The Apiarist's Terminal part. Behaves like AE2's ME Terminal - it reuses {@link ItemTerminalPart}'s grid monitoring,
 * view cells, and config - but opens {@link BeegisticsMenus#APIARIST_TERMINAL} instead of the stock terminal menu, so
 * the client can attach the genetics detail panel. It also carries a small two-slot analyze inventory (input + output):
 * dropping an unanalyzed bee into the input analyzes it in place - paid for with grid energy and honey drawn from the ME
 * fluid network - and moves it to the output, mirroring the Analyzer that goes into its recipe.
 */
public class ApiaristTerminalPart extends ItemTerminalPart implements IApiaristTerminalHost {
	@PartModels
	public static final ResourceLocation MODEL_OFF = ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "part/apiarist_terminal_off");
	@PartModels
	public static final ResourceLocation MODEL_ON = ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "part/apiarist_terminal_on");

	public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
	public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
	public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

	/** AE energy consumed per bee analyzed, drawn from the network. */
	private static final double ENERGY_PER_ANALYZE = 160.0;
	/** Honey consumed per bee analyzed, drawn from the ME fluid network - matches {@code TileAnalyzer} and the Bee Analyzer. */
	private static final int HONEY_PER_ANALYZE = 100;

	public static final int SLOT_INPUT = 0;
	public static final int SLOT_OUTPUT = 1;

	private final AppEngInternalInventory analyzer = new AppEngInternalInventory(this, 2, 64, new AnalyzerFilter());

	public ApiaristTerminalPart(IPartItem<?> partItem) {
		super(partItem);
	}

	@Override
	public MenuType<?> getMenuType(Player player) {
		return BeegisticsMenus.APIARIST_TERMINAL.get();
	}

	@Override
	public InternalInventory getAnalyzerInventory() {
		return this.analyzer;
	}

	@Override
	public void onChangeInventory(AppEngInternalInventory inv, int slot) {
		super.onChangeInventory(inv, slot);
		if (inv == this.analyzer && !isClientSide()) {
			tryAnalyze();
		}
	}

	/**
	 * Analyzes the bee in the input slot (if any) and moves it to the output slot. Safe to call from within an inventory
	 * change callback: {@link AppEngInternalInventory} suppresses the re-entrant change notifications our writes trigger.
	 */
	private void tryAnalyze() {
		ItemStack input = this.analyzer.getStackInSlot(SLOT_INPUT);
		if (input.isEmpty() || !this.analyzer.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
			return;
		}

		IIndividual individual = IIndividualHandlerItem.getIndividual(input);
		if (individual == null) {
			return;
		}

		if (!individual.isAnalyzed()) {
			IGrid grid = getMainNode().getGrid();
			if (grid == null) {
				return;
			}
			int count = input.getCount();
			double energyCost = ENERGY_PER_ANALYZE * count;
			long honeyCost = (long) HONEY_PER_ANALYZE * count;

			MEStorage storage = grid.getStorageService().getInventory();
			IActionSource source = IActionSource.ofMachine(this);
			AEFluidKey honey = AEFluidKey.of(ForestryFluids.HONEY.getFluid());

			// Only analyze if both the energy and the honey are available, so we never consume one without the other.
			if (grid.getEnergyService().extractAEPower(energyCost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < energyCost - 0.5) {
				return;
			}
			if (storage.extract(honey, honeyCost, Actionable.SIMULATE, source) < honeyCost) {
				return;
			}
			grid.getEnergyService().extractAEPower(energyCost, Actionable.MODULATE, PowerMultiplier.CONFIG);
			storage.extract(honey, honeyCost, Actionable.MODULATE, source);
			individual.analyze();
			individual.saveToStack(input);
		}

		this.analyzer.setItemDirect(SLOT_OUTPUT, input.copy());
		this.analyzer.setItemDirect(SLOT_INPUT, ItemStack.EMPTY);
	}

	@Override
	public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
		super.addAdditionalDrops(drops, wrenched);
		for (ItemStack stack : this.analyzer) {
			if (!stack.isEmpty()) {
				drops.add(stack);
			}
		}
	}

	@Override
	public void clearContent() {
		super.clearContent();
		this.analyzer.clear();
	}

	@Override
	public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
		super.readFromNBT(data, registries);
		this.analyzer.readFromNBT(data, "analyzer", registries);
	}

	@Override
	public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
		super.writeToNBT(data, registries);
		this.analyzer.writeToNBT(data, "analyzer", registries);
	}

	@Override
	public IPartModel getStaticModels() {
		return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
	}

	/** Only accepts genetic individuals into the input slot; the output slot never accepts inserts. */
	private static final class AnalyzerFilter implements IAEItemFilter {
		@Override
		public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
			return slot == SLOT_INPUT && IIndividualHandlerItem.isIndividual(stack);
		}

		@Override
		public boolean allowExtract(InternalInventory inv, int slot, int amount) {
			return true;
		}
	}
}
