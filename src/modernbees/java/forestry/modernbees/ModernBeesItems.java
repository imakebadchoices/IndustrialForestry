package forestry.modernbees;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers Modern Bees items under the {@code modernbees} namespace, through a plain NeoForge {@link DeferredRegister}
 * rather than Forestry's {@code FeatureRegistry} (which is keyed to the {@code forestry} mod container) - mirroring the
 * Beegistics / Extra Bees add-ons.
 */
public class ModernBeesItems {
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModernBees.NAMESPACE);

	// The tin drill is a plain crafting ingredient - it has no behaviour of its own; the Modern Industrialization
	// quarry recipe consumes it. Its recipes are all mod_loaded-gated on modern_industrialization.
	public static final DeferredHolder<Item, Item> TIN_DRILL = ITEMS.register("tin_drill", () -> new Item(new Item.Properties()));

	public static void register(IEventBus modBus) {
		ITEMS.register(modBus);
	}
}
