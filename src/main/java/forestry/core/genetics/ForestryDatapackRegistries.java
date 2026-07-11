package forestry.core.genetics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
// DataPackRegistryEvent.NewRegistry is a mod-bus event; @EventBusSubscriber auto-detects the bus.

import forestry.api.ForestryConstants;
import forestry.apiculture.genetics.BeeMutationDefinition;
import forestry.apiculture.genetics.BeeSpeciesDefinition;

/**
 * Registers Forestry's datapack registries for genetics content. These let pack authors add and
 * override species (breeds) and mutations via JSON, and sync the definitions to clients so multiplayer
 * agrees on the available breeds.
 *
 * @see BeeSpeciesDefinition
 * @see BeeMutationDefinition
 */
@EventBusSubscriber(modid = ForestryConstants.MOD_ID)
public class ForestryDatapackRegistries {
	@SubscribeEvent
	public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
		// networkCodec == codec: definitions are synced to clients so breeds/mutations agree in multiplayer.
		event.dataPackRegistry(BeeSpeciesDefinition.REGISTRY_KEY, BeeSpeciesDefinition.CODEC, BeeSpeciesDefinition.CODEC);
		event.dataPackRegistry(BeeMutationDefinition.REGISTRY_KEY, BeeMutationDefinition.CODEC, BeeMutationDefinition.CODEC);
	}
}
