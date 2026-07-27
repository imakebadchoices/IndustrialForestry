package forestry.extrabees.gametest;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.IProduct;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.core.utils.SpeciesUtil;
import forestry.extrabees.ExtraBees;
import forestry.extrabees.features.ExtraBeesItems;
import forestry.extrabees.genetics.TagFluidOutput;
import forestry.extrabees.genetics.TagProduct;
import forestry.extrabees.items.ExtraBeesComb;
import forestry.factory.recipes.SqueezerRecipe;
import forestry.extrabees.items.ExtraBeesDrop;
import forestry.extrabees.items.ItemExtraBeesComb;
import forestry.extrabees.items.ItemExtraBeesDrop;

/**
 * Asserts the Extra Bees content is registered and hangs together. Runs as part of the {@code extrabees}
 * add-on (holder namespace {@code extrabees}); for now it covers the code-defined comb items (the
 * {@code extrabees}-namespace port of Binnie's combs).
 */
@GameTestHolder(ExtraBees.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ExtraBeesContentTest {
	/** Every {@link ExtraBeesComb} must resolve to a registered {@code extrabees:<name>_comb} item. */
	@GameTest(template = "empty")
	public static void combsRegistered(GameTestHelper helper) {
		for (ExtraBeesComb comb : ExtraBeesComb.VALUES) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, comb.combName + "_comb");
			Item item = BuiltInRegistries.ITEM.get(id);
			if (item == null || !BuiltInRegistries.ITEM.getKey(item).equals(id)) {
				helper.fail("Missing Extra Bees comb item: " + id);
				return;
			}
			if (!(item instanceof ItemExtraBeesComb ebComb) || ebComb.getType() != comb) {
				helper.fail("Comb item " + id + " is not wired to its ExtraBeesComb type");
				return;
			}
			if (item != ExtraBeesItems.comb(comb)) {
				helper.fail("ExtraBeesItems.comb(" + comb + ") disagrees with the registry");
				return;
			}
		}
		helper.succeed();
	}

	/** Every comb must carry the base {@code forestry:combs} tag so it works in the apiarist chest/backpack, comb recipes and village trades. */
	@GameTest(template = "empty")
	public static void combsTaggedLikeBaseCombs(GameTestHelper helper) {
		for (ExtraBeesComb comb : ExtraBeesComb.VALUES) {
			ItemExtraBeesComb item = ExtraBeesItems.comb(comb);
			if (!item.builtInRegistryHolder().is(forestry.api.ForestryTags.Items.BEE_COMBS)) {
				helper.fail("Comb " + comb.combName + "_comb is not in the forestry:combs tag");
				return;
			}
		}
		helper.succeed();
	}

	/** Every {@link ExtraBeesDrop} flavor item must resolve, and carry the same tag as its base counterpart. */
	@GameTest(template = "empty")
	public static void dropsRegistered(GameTestHelper helper) {
		for (ExtraBeesDrop drop : ExtraBeesDrop.VALUES) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, drop.itemName());
			Item item = BuiltInRegistries.ITEM.get(id);
			if (!(item instanceof ItemExtraBeesDrop ebDrop) || ebDrop.getType() != drop || item != ExtraBeesItems.drop(drop)) {
				helper.fail("Missing or mismatched Extra Bees drop item: " + id);
				return;
			}
			var tag = drop.family == ExtraBeesDrop.Family.PROPOLIS
				? forestry.api.ForestryTags.Items.PROPOLIS
				: forestry.api.ForestryTags.Items.DROP_HONEY;
			if (!item.builtInRegistryHolder().is(tag)) {
				helper.fail("Drop " + drop.itemName() + " is not in " + tag.location());
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Every comb must have a loaded centrifuge recipe, and the tag-based products must decode through the
	 * {@code extrabees:tag} dispatch (exercising the generalized centrifuge codec end-to-end).
	 */
	@GameTest(template = "empty")
	public static void combCentrifugeRecipesLoaded(GameTestHelper helper) {
		RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
		for (ExtraBeesComb comb : ExtraBeesComb.VALUES) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, "centrifuge/" + comb.combName + "_comb");
			RecipeHolder<?> holder = manager.byKey(id).orElse(null);
			if (holder == null || !(holder.value() instanceof ICentrifugeRecipe recipe)) {
				helper.fail("Missing centrifuge recipe: " + id);
				return;
			}
			if (!recipe.getInput().test(ExtraBeesItems.comb(comb).getDefaultInstance())) {
				helper.fail("Centrifuge recipe " + id + " does not accept its own comb");
				return;
			}
		}

		// The iron comb's ore-dust product must have decoded as a TagProduct(c:dusts/iron).
		ResourceLocation ironId = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, "centrifuge/iron_comb");
		ICentrifugeRecipe iron = (ICentrifugeRecipe) manager.byKey(ironId).orElseThrow().value();
		boolean hasIronDustTag = iron.getAllProducts().stream()
			.anyMatch(p -> p instanceof TagProduct tp && tp.tag().location().getPath().equals("dusts/iron"));
		if (!hasIronDustTag) {
			helper.fail("iron_comb centrifuge recipe did not decode a TagProduct for c:dusts/iron");
			return;
		}
		helper.succeed();
	}

	/**
	 * The bundled Extra Bees species must all load and project a full default genome, and every one of their
	 * combs/flowers must resolve (no dangling references left by the datapack lift).
	 */
	@GameTest(template = "empty")
	public static void speciesLoadAndProject(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		int count = 0;
		for (IBeeSpecies species : beeType.getAllSpecies()) {
			if (!species.id().getNamespace().equals(ExtraBees.NAMESPACE)) {
				continue;
			}
			count++;
			IGenome genome = species.getDefaultGenome();
			if (genome == null) {
				helper.fail("Extra Bees species " + species.id() + " has no default genome");
				return;
			}
			// Products/specialties must reference real items (the comb-ref rewrite must be complete).
			for (IProduct product : species.getProducts()) {
				if (product.createStack().isEmpty() && !(product instanceof TagProduct)) {
					helper.fail("Species " + species.id() + " has an unresolved product item");
					return;
				}
			}
		}
		if (count != 113) {
			helper.fail("Expected 113 Extra Bees species, found " + count);
			return;
		}
		helper.succeed();
	}

	/**
	 * Every mutation must reference loaded parent/result species, and the bulk of the Extra Bees mutations must have
	 * survived the lift (each involves at least one extrabees species). Catches dangling species refs (e.g. the old
	 * {@code forestry:bee_*} ids that had to be de-prefixed).
	 */
	@GameTest(template = "empty")
	public static void mutationsResolve(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		int extraBeesMutations = 0;
		for (IMutation<IBeeSpecies> mutation : beeType.getMutations().getAllMutations()) {
			IBeeSpecies first = mutation.getFirstParent();
			IBeeSpecies second = mutation.getSecondParent();
			IBeeSpecies result = mutation.getResult();
			if (first == null || second == null || result == null) {
				helper.fail("Mutation into " + (result == null ? "?" : result.id()) + " has a null parent/result");
				return;
			}
			boolean involvesExtraBees = first.id().getNamespace().equals(ExtraBees.NAMESPACE)
				|| second.id().getNamespace().equals(ExtraBees.NAMESPACE)
				|| result.id().getNamespace().equals(ExtraBees.NAMESPACE);
			if (involvesExtraBees) {
				extraBeesMutations++;
			}
		}
		if (extraBeesMutations < 130) {
			helper.fail("Expected the bulk of the 147 Extra Bees mutations to load, found only " + extraBeesMutations);
			return;
		}
		helper.succeed();
	}

	/**
	 * Every flavor drop with a squeezer recipe must load - both the concrete-fluid ones and the tag-based industrial
	 * ones - and the industrial ones must decode through the {@code extrabees:tag} fluid-output dispatch.
	 */
	@GameTest(template = "empty")
	public static void squeezerRecipesLoaded(GameTestHelper helper) {
		RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
		List<ExtraBeesDrop> all = List.of(ExtraBeesDrop.WATER, ExtraBeesDrop.MILK, ExtraBeesDrop.APPLE,
			ExtraBeesDrop.ICE, ExtraBeesDrop.SEED, ExtraBeesDrop.ALCOHOL,
			ExtraBeesDrop.OIL, ExtraBeesDrop.FUEL, ExtraBeesDrop.CREOSOTE, ExtraBeesDrop.ACID);
		for (ExtraBeesDrop drop : all) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, "squeezer/" + drop.itemName());
			if (manager.byKey(id).isEmpty()) {
				helper.fail("Missing squeezer recipe: " + id);
				return;
			}
		}

		// The oil propolis recipe's output must have decoded as a TagFluidOutput(c:crude_oil).
		ResourceLocation oilId = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, "squeezer/oil_propolis");
		SqueezerRecipe oil = (SqueezerRecipe) manager.byKey(oilId).orElseThrow().value();
		if (!(oil.getFluidOutput() instanceof TagFluidOutput tfo) || !tfo.tag().location().getPath().equals("crude_oil")) {
			helper.fail("oil_propolis squeezer recipe did not decode a TagFluidOutput for c:crude_oil");
			return;
		}
		helper.succeed();
	}
}
