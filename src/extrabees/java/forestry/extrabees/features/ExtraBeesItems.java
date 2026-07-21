package forestry.extrabees.features;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import forestry.extrabees.ExtraBees;
import forestry.extrabees.items.ExtraBeesComb;
import forestry.extrabees.items.ExtraBeesDrop;
import forestry.extrabees.items.ItemExtraBeesComb;
import forestry.extrabees.items.ItemExtraBeesDrop;

/**
 * Registers Extra Bees items under the {@code extrabees} namespace. These are code-defined items
 * (mirroring how base Forestry defines its combs), but registered through a plain NeoForge
 * {@link DeferredRegister} rather than Forestry's {@code FeatureRegistry}, because the latter is
 * keyed to the {@code forestry} mod container. Keeping everything under {@code extrabees} means a
 * future standalone add-on jar can lift this package with no world-breaking id renames.
 */
public class ExtraBeesItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExtraBees.NAMESPACE);

	public static final Map<ExtraBeesComb, DeferredHolder<Item, ItemExtraBeesComb>> COMBS = new EnumMap<>(ExtraBeesComb.class);
	public static final Map<ExtraBeesDrop, DeferredHolder<Item, ItemExtraBeesDrop>> DROPS = new EnumMap<>(ExtraBeesDrop.class);

	static {
		for (ExtraBeesComb comb : ExtraBeesComb.VALUES) {
			COMBS.put(comb, ITEMS.register(comb.combName + "_comb", () -> new ItemExtraBeesComb(comb)));
		}
		for (ExtraBeesDrop drop : ExtraBeesDrop.VALUES) {
			DROPS.put(drop, ITEMS.register(drop.itemName(), () -> new ItemExtraBeesDrop(drop)));
		}
	}

	public static ItemExtraBeesComb comb(ExtraBeesComb type) {
		return COMBS.get(type).get();
	}

	/** All registered comb items, for tagging them alongside the base Forestry combs. */
	public static Item[] combArray() {
		return COMBS.values().stream().map(DeferredHolder::get).toArray(Item[]::new);
	}

	/** The flavor items of one family, for tagging them alongside the base propolis / honey drops. */
	public static Item[] dropArray(ExtraBeesDrop.Family family) {
		return DROPS.entrySet().stream()
			.filter(e -> e.getKey().family == family)
			.map(e -> (Item) e.getValue().get())
			.toArray(Item[]::new);
	}

	public static ItemExtraBeesDrop drop(ExtraBeesDrop type) {
		return DROPS.get(type).get();
	}

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
	}

	private ExtraBeesItems() {
	}
}
