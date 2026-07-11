package forestry.core.genetics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
// DataPackRegistryEvent.NewRegistry is a mod-bus event; @EventBusSubscriber auto-detects the bus.

import forestry.api.ForestryConstants;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.apiculture.FlowerTypeDefinition;
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
		// Effect alleles must exist on clients too (the effect chromosome is populated during the client-side
		// species rebuild), so this registry is synced like the others.
		event.dataPackRegistry(IBeeEffect.REGISTRY_KEY, IBeeEffect.CODEC, IBeeEffect.CODEC);
		// Flower type alleles are synced for the same reason as effects (flower_type chromosome is populated
		// during the client-side rebuild).
		event.dataPackRegistry(FlowerTypeDefinition.REGISTRY_KEY, FlowerTypeDefinition.CODEC, FlowerTypeDefinition.CODEC);
	}
}
