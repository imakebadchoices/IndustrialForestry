package forestry.extrabees.data;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;

import forestry.apiculture.features.ApicultureItems;
import forestry.core.data.builder.CentrifugeRecipeBuilder;
import forestry.core.data.builder.SqueezerRecipeBuilder;
import forestry.core.features.CoreItems;
import forestry.core.fluids.ForestryFluids;
import forestry.extrabees.features.ExtraBeesItems;
import forestry.extrabees.genetics.TagFluidOutput;
import forestry.extrabees.genetics.TagProduct;
import forestry.extrabees.items.ExtraBeesComb;
import forestry.extrabees.items.ExtraBeesDrop;

import static forestry.extrabees.items.ExtraBeesComb.*;

/**
 * Datagen for the Extra Bees comb products, ported from Binnie's {@code EnumHoneyComb.addSubtypes}.
 * Centrifuge products come from Binnie verbatim (chances + items); Binnie's IC2 / ore-dictionary
 * metal dusts become mod-agnostic {@code c:} tags via {@link TagProduct} (an absent tag simply drops
 * nothing, matching Binnie's {@code tryAddProduct} graceful skip). The 16 dye combs yield vanilla dye
 * directly instead of Binnie's intermediate dye honey-drops. The squeezer turns the fluid-bearing
 * drops into their modern fluid where one exists.
 */
public final class ExtraBeesRecipeProvider {
	public static void addRecipes(RecipeOutput out) {
		registerCentrifuge(out);
		registerSqueezer(out);
	}

	private static void registerCentrifuge(RecipeOutput out) {
		// --- Binnie combs (colors + products authoritative from binnie EnumHoneyComb) ---
		centrifuge(out, BARREN, b -> b.product(1.00f, beeswax()).product(0.50f, honeyDrop()));
		centrifuge(out, ROTTEN, b -> b.product(0.20f, beeswax()).product(0.20f, honeyDrop()).product(0.80f, item(Items.ROTTEN_FLESH)));
		centrifuge(out, BONE, b -> b.product(0.20f, beeswax()).product(0.20f, honeyDrop()).product(0.80f, item(Items.BONE_MEAL)));
		centrifuge(out, OIL, b -> b.product(0.60f, drop(ExtraBeesDrop.OIL)).product(0.75f, honeyDrop()));
		centrifuge(out, COAL, b -> b.product(0.80f, beeswax()).product(0.75f, honeyDrop()).product(tag("dusts/coal", 1.00f)));
		centrifuge(out, FUEL, b -> b.product(0.60f, drop(ExtraBeesDrop.FUEL)).product(0.50f, honeyDrop()));
		centrifuge(out, WATER, b -> b.product(1.00f, drop(ExtraBeesDrop.WATER)).product(0.90f, honeyDrop()));
		centrifuge(out, MILK, b -> b.product(1.00f, drop(ExtraBeesDrop.MILK)).product(0.90f, honeyDrop()));
		centrifuge(out, FRUIT, b -> b.product(1.00f, drop(ExtraBeesDrop.APPLE)).product(0.90f, honeyDrop()));
		centrifuge(out, SEED, b -> b.product(1.00f, drop(ExtraBeesDrop.SEED)).product(0.90f, honeyDrop()));
		centrifuge(out, ALCOHOL, b -> b.product(1.00f, drop(ExtraBeesDrop.ALCOHOL)).product(0.90f, honeyDrop()));
		centrifuge(out, STONE, ExtraBeesRecipeProvider::stone);
		centrifuge(out, REDSTONE, b -> b.product(0.80f, beeswax()).product(1.00f, item(Items.REDSTONE)).product(0.50f, honeyDrop()));
		centrifuge(out, RESIN, b -> b.product(1.00f, beeswax()).product(tag("sticky_resin", 1.00f)));
		centrifuge(out, IC2ENERGY, b -> b.product(0.80f, beeswax()).product(0.75f, item(Items.REDSTONE)).product(1.00f, drop(ExtraBeesDrop.ENERGY)));
		centrifuge(out, IRON, b -> stoneAnd(b, tag("dusts/iron", 1.00f)));
		centrifuge(out, GOLD, b -> stoneAnd(b, tag("dusts/gold", 1.00f)));
		centrifuge(out, COPPER, b -> stoneAnd(b, tag("dusts/copper", 1.00f)));
		centrifuge(out, TIN, b -> stoneAnd(b, tag("dusts/tin", 1.00f)));
		centrifuge(out, SILVER, b -> stoneAnd(b, tag("dusts/silver", 1.00f)));
		centrifuge(out, URANIUM, b -> stoneAnd(b, tag("dusts/uranium", 0.50f)));
		centrifuge(out, CLAY, b -> b.product(0.25f, beeswax()).product(0.80f, honeyDrop()).product(0.80f, item(Items.CLAY_BALL)));
		centrifuge(out, OLD, b -> b.product(1.00f, beeswax()).product(0.90f, honeyDrop()));
		centrifuge(out, FUNGAL, b -> b.product(0.90f, beeswax()).product(1.00f, item(Items.BROWN_MUSHROOM_BLOCK)).product(0.75f, item(Items.RED_MUSHROOM_BLOCK)));
		centrifuge(out, CREOSOTE, b -> b.product(0.70f, drop(ExtraBeesDrop.CREOSOTE)).product(0.50f, honeyDrop()));
		centrifuge(out, LATEX, b -> b.product(0.50f, honeyDrop()).product(0.85f, beeswax()).product(tag("ingots/rubber", 1.00f)));
		centrifuge(out, ACIDIC, b -> b.product(0.80f, beeswax()).product(0.50f, drop(ExtraBeesDrop.ACID)).product(tag("dusts/sulfur", 0.75f)));
		centrifuge(out, VENOMOUS, b -> b.product(0.80f, beeswax()).product(0.80f, drop(ExtraBeesDrop.POISON)));
		centrifuge(out, SLIME, b -> b.product(1.00f, beeswax()).product(0.75f, honeyDrop()).product(0.75f, item(Items.SLIME_BALL)));
		centrifuge(out, BLAZE, b -> b.product(0.75f, beeswax()).product(1.00f, item(Items.BLAZE_POWDER)));
		centrifuge(out, COFFEE, b -> b.product(0.90f, beeswax()).product(0.75f, honeyDrop()).product(tag("crops/coffee_beans", 0.75f)));
		centrifuge(out, GLACIAL, b -> b.product(0.80f, drop(ExtraBeesDrop.ICE)).product(0.75f, honeyDrop()));
		centrifuge(out, SHADOW, b -> b.product(0.50f, honeyDrop()).product(tag("dusts/obsidian", 0.75f)));
		centrifuge(out, LEAD, b -> stoneAnd(b, tag("dusts/lead", 1.00f)));
		centrifuge(out, ZINC, b -> stoneAnd(b, tag("ingots/zinc", 1.00f)));
		centrifuge(out, TITANIUM, b -> stoneAnd(b, tag("dusts/titanium", 1.00f)));
		centrifuge(out, TUNGSTEN, b -> stoneAnd(b, tag("dusts/tungsten", 1.00f)));
		centrifuge(out, PLATINUM, b -> stoneAnd(b, tag("dusts/platinum", 1.00f)));
		centrifuge(out, NICKEL, b -> stoneAnd(b, tag("dusts/nickel", 1.00f)));
		centrifuge(out, LAPIS, b -> {
			stone(b);
			b.product(1.00f, new ItemStack(Items.LAPIS_LAZULI, 6));
		});
		centrifuge(out, SODALITE, b -> {
			b.product(tag("tiny_dusts/beryllium", 1.00f)).product(tag("tiny_dusts/aluminum", 1.00f));
			stone(b);
		});
		centrifuge(out, PYRITE, b -> {
			b.product(tag("tiny_dusts/manganese", 1.00f)).product(tag("tiny_dusts/iron", 1.00f));
			stone(b);
		});
		centrifuge(out, BAUXITE, b -> {
			b.product(tag("tiny_dusts/bauxite", 1.00f)).product(tag("tiny_dusts/aluminum", 1.00f));
			stone(b);
		});
		centrifuge(out, CINNABAR, b -> {
			b.product(tag("tiny_dusts/chromium", 1.00f)).product(0.05f, item(Items.REDSTONE));
			stone(b);
		});
		centrifuge(out, SPHALERITE, b -> {
			b.product(tag("tiny_dusts/cadmium", 1.00f)).product(tag("nuggets/zinc", 1.00f));
			stone(b);
		});
		centrifuge(out, EMERALD, b -> stoneAnd(b, tag("tiny_dusts/emerald", 1.00f)));
		centrifuge(out, RUBY, b -> stoneAnd(b, tag("tiny_dusts/ruby", 1.00f)));
		centrifuge(out, SAPPHIRE, b -> stoneAnd(b, tag("gems/sapphire", 1.00f)));
		centrifuge(out, DIAMOND, b -> stoneAnd(b, tag("tiny_dusts/diamond", 1.00f)));
		centrifuge(out, GLOWSTONE, b -> b.product(0.25f, honeyDrop()).product(1.00f, item(Items.GLOWSTONE_DUST)));
		centrifuge(out, SALTPETER, b -> b.product(0.25f, honeyDrop()).product(tag("dusts/saltpeter", 1.00f)));
		centrifuge(out, COMPOST, b -> b.product(0.25f, honeyDrop()).product(1.00f, CoreItems.COMPOST.stack()));
		centrifuge(out, SAWDUST, b -> b.product(0.25f, honeyDrop()).product(tag("sawdust", 1.00f)));
		centrifuge(out, CERTUS, b -> b.product(0.25f, honeyDrop()).product(0.25f, item(Items.QUARTZ)).product(tag("dusts/certus_quartz", 0.20f)));
		centrifuge(out, ENDERPEARL, b -> b.product(0.25f, honeyDrop()).product(tag("dusts/ender_pearl", 0.25f)));
		centrifuge(out, YELLORIUM, b -> stoneAnd(b, tag("ingots/yellorium", 0.25f)));
		centrifuge(out, CYANITE, b -> stoneAnd(b, tag("ingots/cyanite", 0.25f)));
		centrifuge(out, BLUTONIUM, b -> stoneAnd(b, tag("ingots/blutonium", 0.25f)));

		// Dye combs: Binnie's addDyeSubtypes, simplified to yield the vanilla dye directly.
		dyeComb(out, RED, Items.RED_DYE);
		dyeComb(out, YELLOW, Items.YELLOW_DYE);
		dyeComb(out, BLUE, Items.BLUE_DYE);
		dyeComb(out, GREEN, Items.GREEN_DYE);
		dyeComb(out, BLACK, Items.BLACK_DYE);
		dyeComb(out, WHITE, Items.WHITE_DYE);
		dyeComb(out, BROWN, Items.BROWN_DYE);
		dyeComb(out, ORANGE, Items.ORANGE_DYE);
		dyeComb(out, CYAN, Items.CYAN_DYE);
		dyeComb(out, PURPLE, Items.PURPLE_DYE);
		dyeComb(out, GRAY, Items.GRAY_DYE);
		dyeComb(out, LIGHTBLUE, Items.LIGHT_BLUE_DYE);
		dyeComb(out, PINK, Items.PINK_DYE);
		dyeComb(out, LIMEGREEN, Items.LIME_DYE);
		dyeComb(out, MAGENTA, Items.MAGENTA_DYE);
		dyeComb(out, LIGHTGRAY, Items.LIGHT_GRAY_DYE);

		// --- Our additions beyond Binnie: 6 alloy combs + quartz (products from our datapack, tag-ified) ---
		centrifuge(out, BRONZE, b -> stoneAnd(b, tag("ingots/bronze", 1.00f)));
		centrifuge(out, BRASS, b -> stoneAnd(b, tag("ingots/brass", 1.00f)));
		centrifuge(out, ELECTRUM, b -> stoneAnd(b, tag("dusts/electrum", 1.00f)));
		centrifuge(out, INVAR, b -> stoneAnd(b, tag("dusts/invar", 1.00f)));
		centrifuge(out, STEEL, b -> stoneAnd(b, tag("dusts/steel", 1.00f)));
		centrifuge(out, IRIDIUM, b -> stoneAnd(b, tag("dusts/iridium", 1.00f)));
		centrifuge(out, QUARTZ, b -> stoneAnd(b, new ItemStack(Items.QUARTZ), 1.00f));
	}

	private static void registerSqueezer(RecipeOutput out) {
		// Flavor drops whose fluid ships in base (or vanilla/NeoForge): concrete outputs.
		squeezer(out, ExtraBeesDrop.WATER, new FluidStack(Fluids.WATER, 500), 20);
		squeezer(out, ExtraBeesDrop.MILK, new FluidStack(NeoForgeMod.MILK.value(), 200), 10);
		squeezer(out, ExtraBeesDrop.APPLE, ForestryFluids.JUICE.getFluid(200), 10);
		squeezer(out, ExtraBeesDrop.ICE, ForestryFluids.ICE.getFluid(200), 10);
		squeezer(out, ExtraBeesDrop.SEED, ForestryFluids.SEED_OIL.getFluid(200), 10);
		squeezer(out, ExtraBeesDrop.ALCOHOL, ForestryFluids.SHORT_MEAD.getFluid(200), 10);

		// Industrial flavors: mod-agnostic fluid tags, resolved at runtime. No mod filling the tag = no recipe
		// (the squeezer refuses an empty output), so these gracefully no-op in a base install. Materials follow the
		// old MI mapping (crude_oil/diesel/creosote/sulfuric_acid), tag-ified.
		squeezerTag(out, ExtraBeesDrop.OIL, "crude_oil", 500, 20);
		squeezerTag(out, ExtraBeesDrop.FUEL, "diesel", 500, 20);
		squeezerTag(out, ExtraBeesDrop.CREOSOTE, "creosote", 500, 20);
		squeezerTag(out, ExtraBeesDrop.ACID, "sulfuric_acid", 200, 10);
	}

	// --- helpers ---

	private static void centrifuge(RecipeOutput out, ExtraBeesComb comb, Consumer<CentrifugeRecipeBuilder> products) {
		CentrifugeRecipeBuilder builder = new CentrifugeRecipeBuilder()
			.setProcessingTime(20)
			.setInput(Ingredient.of(ExtraBeesItems.comb(comb)));
		products.accept(builder);
		builder.build(out, id("centrifuge/" + comb.combName + "_comb"));
	}

	private static void dyeComb(RecipeOutput out, ExtraBeesComb comb, Item dye) {
		centrifuge(out, comb, b -> b.product(0.80f, beeswax()).product(0.80f, honeyDrop()).product(1.00f, new ItemStack(dye)));
	}

	private static void squeezer(RecipeOutput out, ExtraBeesDrop drop, FluidStack fluid, int time) {
		new SqueezerRecipeBuilder()
			.setProcessingTime(time)
			.setResources(List.of(Ingredient.of(ExtraBeesItems.drop(drop))))
			.setFluidOutput(fluid)
			.build(out, id("squeezer/" + drop.itemName()));
	}

	private static void squeezerTag(RecipeOutput out, ExtraBeesDrop drop, String fluidTagPath, int amount, int time) {
		TagKey<Fluid> tag = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", fluidTagPath));
		new SqueezerRecipeBuilder()
			.setProcessingTime(time)
			.setResources(List.of(Ingredient.of(ExtraBeesItems.drop(drop))))
			.setFluidOutput(new TagFluidOutput(tag, amount))
			.build(out, id("squeezer/" + drop.itemName()));
	}

	/** Binnie's STONE base table: beeswax@0.5, honey drop@0.25. */
	private static void stone(CentrifugeRecipeBuilder b) {
		b.product(0.50f, beeswax()).product(0.25f, honeyDrop());
	}

	private static void stoneAnd(CentrifugeRecipeBuilder b, TagProduct extra) {
		stone(b);
		b.product(extra);
	}

	private static void stoneAnd(CentrifugeRecipeBuilder b, ItemStack extra, float chance) {
		stone(b);
		b.product(chance, extra);
	}

	private static TagProduct tag(String path, float chance) {
		return new TagProduct(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)), chance);
	}

	private static ItemStack beeswax() {
		return CoreItems.BEESWAX.stack();
	}

	private static ItemStack honeyDrop() {
		return ApicultureItems.HONEY_DROP.stack();
	}

	private static ItemStack drop(ExtraBeesDrop drop) {
		return new ItemStack(ExtraBeesItems.drop(drop));
	}

	private static ItemStack item(Item item) {
		return new ItemStack(item);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("extrabees", path);
	}

	private ExtraBeesRecipeProvider() {
	}
}
