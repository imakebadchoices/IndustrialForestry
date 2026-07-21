package forestry.beegistics;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.items.parts.PartItem;

import forestry.beegistics.terminal.ApiaristTerminalPart;

/**
 * Registers Beegistics items under the {@code beegistics} namespace, through a plain NeoForge {@link DeferredRegister}
 * rather than Forestry's {@code FeatureRegistry} (which is keyed to the {@code forestry} mod container) - mirroring the
 * Extra Bees add-on.
 */
public class BeegisticsItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Beegistics.NAMESPACE);

	public static final Map<BeeCellTier, DeferredHolder<Item, ItemBeeCell>> BEE_CELLS = new EnumMap<>(BeeCellTier.class);

	public static final DeferredHolder<Item, ItemBeeFilterCard> BEE_FILTER_CARD = ITEMS.register("bee_filter_card", ItemBeeFilterCard::new);

	/** Internal-only pattern carrier: never a player item, but the AE2 pattern-definition key for synthesized species mutations. */
	public static final DeferredHolder<Item, ItemBeeMutationPattern> BEE_MUTATION_PATTERN = ITEMS.register("bee_mutation_pattern", ItemBeeMutationPattern::new);

	public static final DeferredHolder<Item, PartItem<ApiaristTerminalPart>> APIARIST_TERMINAL = ITEMS.register(
			"apiarist_terminal",
			() -> new PartItem<>(new Item.Properties(), ApiaristTerminalPart.class, ApiaristTerminalPart::new));

	static {
		for (BeeCellTier tier : BeeCellTier.values()) {
			BEE_CELLS.put(tier, ITEMS.register("bee_cell_" + tier.getSerializedName(), () -> new ItemBeeCell(tier)));
		}
	}

	public static ItemBeeCell beeCell(BeeCellTier tier) {
		return BEE_CELLS.get(tier).get();
	}

	public static ItemBeeFilterCard beeFilterCard() {
		return BEE_FILTER_CARD.get();
	}

	public static ItemBeeMutationPattern beeMutationPattern() {
		return BEE_MUTATION_PATTERN.get();
	}

	@SuppressWarnings("unchecked")
	public static PartItem<ApiaristTerminalPart> apiaristTerminal() {
		return (PartItem<ApiaristTerminalPart>) APIARIST_TERMINAL.get();
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
	}
}
