package forestry.extrabees.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import forestry.extrabees.ExtraBees;

/**
 * Data generation for the Extra Bees add-on. Emits the centrifuge / squeezer recipe JSON for the comb
 * and flavor items ({@code data/extrabees/recipe/**}). The bee species / mutation / effect / taxon /
 * flower_type JSON and the {@code forestry:} comb-tag merge files are authored content shipped as static
 * resources, not regenerated here.
 */
@EventBusSubscriber(modid = ExtraBees.NAMESPACE)
public final class ExtraBeesData {
	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		DataGenerator generator = event.getGenerator();
		PackOutput output = generator.getPackOutput();
		CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

		generator.addProvider(event.includeServer(), new Recipes(output, lookup));
	}

	private static final class Recipes extends RecipeProvider {
		private Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, registries);
		}

		@Override
		protected void buildRecipes(RecipeOutput out) {
			ExtraBeesRecipeProvider.addRecipes(out);
		}
	}

	private ExtraBeesData() {
	}
}
