package forestry.beegistics.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.init.client.InitScreens;

import forestry.beegistics.machine.ApiaryControllerMenu;
import forestry.beegistics.terminal.ApiaristTerminalMenu;
import forestry.beegistics.terminal.BeegisticsMenus;

/**
 * Client-only wiring for the Beegistics add-on. Kept in a separate class referenced behind a {@code Dist.CLIENT} guard
 * so none of the client-only screen classes load on a dedicated server.
 */
public final class BeegisticsClientHandler {
	private BeegisticsClientHandler() {
	}

	public static void init(IEventBus modBus) {
		modBus.addListener(BeegisticsClientHandler::registerScreens);
	}

	private static void registerScreens(RegisterMenuScreensEvent event) {
		// Reuse AE2's stock terminal style so the screen looks and behaves like the ME Terminal; our subclass only adds
		// the genetics panel on top.
		InitScreens.<ApiaristTerminalMenu, ApiaristTerminalScreen>register(
				event,
				BeegisticsMenus.APIARIST_TERMINAL.get(),
				ApiaristTerminalScreen::new,
				"/screens/terminals/terminal.json");
		// Our style JSON lives under the ae2 namespace (StyleManager forces it); the background texture inside is
		// beegistics-namespaced. Slots are positioned by the style; status + threshold are drawn by the screen.
		InitScreens.<ApiaryControllerMenu, ApiaryControllerScreen>register(
				event,
				BeegisticsMenus.APIARY_CONTROLLER.get(),
				ApiaryControllerScreen::new,
				"/screens/beegistics_apiary_controller.json");
	}
}
