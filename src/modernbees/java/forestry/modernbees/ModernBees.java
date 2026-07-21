package forestry.modernbees;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import forestry.core.tab.ForestryCreativeTabs;

/**
 * Mod entry point for the standalone Modern Bees add-on: Modern Industrialization integration for Forestry. All MI
 * interop is data-driven - the tin drill's crafting/machine recipes are {@code mod_loaded}-gated on
 * {@code modern_industrialization} and the "forestry fluids as MI fuel" mapping is an MI-namespaced datamap that only
 * resolves when MI is present - so MI is only an optional dependency. The flammable fuel-bucket furnace-fuels datamap
 * works in any furnace. Content is registered under the {@code modernbees} namespace.
 */
@Mod(ModernBees.NAMESPACE)
public final class ModernBees {
	public static final String NAMESPACE = "modernbees";

	public ModernBees(IEventBus modBus, ModContainer container) {
		ModernBeesItems.register(modBus);
		modBus.addListener(ModernBees::onBuildCreativeTab);
	}

	private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
		// The tin drill lived in Forestry's main tab (next to the wrench / pipette / soldering iron).
		if (event.getTabKey() == ForestryCreativeTabs.FORESTRY.getKey()) {
			event.accept(ModernBeesItems.TIN_DRILL.get());
		}
	}
}
