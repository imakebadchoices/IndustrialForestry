package forestry.modernbees.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import forestry.modernbees.ModernBees;
import forestry.modernbees.ModernBeesItems;

/**
 * Loads-and-registers coverage for the Modern Bees add-on. Modern Industrialization is not on the gametest classpath,
 * so the MI-gated drill recipes and the MI fuel datamap can't be exercised here; this pins down the pieces that are
 * testable without MI: the {@code modernbees:tin_drill} item registration and the flammable fuel-bucket entries the
 * add-on contributes to {@code neoforge:furnace_fuels} (which work in any furnace).
 */
@GameTestHolder(ModernBees.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ModernBeesContentTest {
	@GameTest(template = "empty")
	public static void tinDrillRegistered(GameTestHelper helper) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ModernBees.NAMESPACE, "tin_drill");
		Item registered = BuiltInRegistries.ITEM.get(id);
		if (registered == Items.AIR || registered != ModernBeesItems.TIN_DRILL.get()) {
			helper.fail("expected " + id + " to be registered to the Modern Bees tin drill item");
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void fuelBucketsBurnInFurnace(GameTestHelper helper) {
		assertBurnTime(helper, "forestry:bucket_ethanol", 12800);
		assertBurnTime(helper, "forestry:bucket_biomass", 4000);
		assertBurnTime(helper, "forestry:bucket_seed_oil", 2400);
		helper.succeed();
	}

	private static void assertBurnTime(GameTestHelper helper, String itemId, int expected) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
		FurnaceFuel fuel = item.builtInRegistryHolder().getData(NeoForgeDataMaps.FURNACE_FUELS);
		if (fuel == null) {
			helper.fail("expected " + itemId + " to have a furnace_fuels datamap entry (Modern Bees add-on)");
		} else if (fuel.burnTime() != expected) {
			helper.fail("expected " + itemId + " burn time " + expected + ", got " + fuel.burnTime());
		}
	}
}
