package forestry.core.genetics;

import net.minecraft.core.RegistryAccess;
import net.minecraft.util.Unit;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import forestry.api.ForestryConstants;
import forestry.apiimpl.plugin.PluginManager;

/**
 * Rebuilds species from the datapack registries whenever server resources (re)load — i.e. on server
 * start and every {@code /reload}. The event's {@link RegistryAccess} already has the datapack
 * registries loaded, so we read {@code forestry:bee_species} from it and re-run the species build.
 * <p>
 * Runs in the reload's apply phase so it is sequenced after the registries are available. This is the
 * server/common side; clients apply the same rebuild once the synced registry arrives.
 */
@EventBusSubscriber(modid = ForestryConstants.MOD_ID)
public class DatapackSpeciesReloadListener {
	@SubscribeEvent
	public static void onAddReloadListeners(AddReloadListenerEvent event) {
		RegistryAccess registryAccess = event.getRegistryAccess();
		event.addListener((barrier, manager, prepProfiler, applyProfiler, prepExecutor, applyExecutor) ->
			barrier.wait(Unit.INSTANCE).thenRunAsync(() -> PluginManager.reloadDatapackSpecies(registryAccess), applyExecutor));
	}
}
