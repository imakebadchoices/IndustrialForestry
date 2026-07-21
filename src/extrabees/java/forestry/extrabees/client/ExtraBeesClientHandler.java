package forestry.extrabees.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import forestry.core.models.ClientManager;
import forestry.extrabees.features.ExtraBeesItems;
import forestry.extrabees.items.ExtraBeesComb;
import forestry.extrabees.items.ExtraBeesDrop;

/**
 * Client-only wiring for Extra Bees items. Registers the two-layer tint handler for the combs, the
 * same way {@link forestry.core.client.CoreClientHandler} does for base Forestry combs.
 */
public final class ExtraBeesClientHandler {
	public static void init(IEventBus modBus) {
		modBus.addListener(ExtraBeesClientHandler::registerItemColors);
	}

	private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
		for (ExtraBeesComb comb : ExtraBeesComb.VALUES) {
			event.register(ClientManager.FORESTRY_ITEM_COLOR, ExtraBeesItems.comb(comb));
		}
		for (ExtraBeesDrop drop : ExtraBeesDrop.VALUES) {
			event.register(ClientManager.FORESTRY_ITEM_COLOR, ExtraBeesItems.drop(drop));
		}
	}

	private ExtraBeesClientHandler() {
	}
}
