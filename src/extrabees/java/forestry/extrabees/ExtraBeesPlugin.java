package forestry.extrabees;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.plugin.IApicultureRegistration;
import forestry.api.plugin.IForestryPlugin;
import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.items.EnumHoneyComb;
import forestry.extrabees.features.ExtraBeesItems;
import forestry.extrabees.hives.ExtraBeesHiveDefinition;
import forestry.extrabees.items.ExtraBeesComb;

/**
 * Registers the Extra Bees wild hives. Species, mutations and effects are all datapack-driven, so this is the only
 * thing the add-on needs a Forestry plugin for - hive registration has no datapack form yet.
 * <p>
 * Discovered through {@code META-INF/services/forestry.api.plugin.IForestryPlugin}.
 */
public class ExtraBeesPlugin implements IForestryPlugin {
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, "extrabees");

	@Override
	public ResourceLocation id() {
		return ID;
	}

	@Override
	public void registerApiculture(IApicultureRegistration apiculture) {
		// Drop rates mirror both base's hives and Binnie's originals: the hive's own bee at 0.80 with a 0.7 chance
		// of ignoble stock, plus the same 0.03 Valiant princess every base hive offers.
		apiculture.registerHive(ExtraBeesHiveDefinition.ROCK.id(), ExtraBeesHiveDefinition.ROCK)
			.setGenerationChance(ExtraBeesHiveDefinition.ROCK.defaultGenChance())
			.addDrop(0.80, ExtraBeesHiveDefinition.ROCK.id(), extraBeesComb(ExtraBeesComb.STONE), 0.7f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, extraBeesComb(ExtraBeesComb.STONE));

		apiculture.registerHive(ExtraBeesHiveDefinition.MARBLE.id(), ExtraBeesHiveDefinition.MARBLE)
			.setGenerationChance(ExtraBeesHiveDefinition.MARBLE.defaultGenChance())
			.addDrop(0.80, ExtraBeesHiveDefinition.MARBLE.id(), baseComb(EnumHoneyComb.HONEY), 0.7f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, baseComb(EnumHoneyComb.HONEY));
	}

	private static Supplier<List<ItemStack>> extraBeesComb(ExtraBeesComb comb) {
		return () -> List.of(new ItemStack(ExtraBeesItems.comb(comb)));
	}

	private static Supplier<List<ItemStack>> baseComb(EnumHoneyComb comb) {
		return () -> List.of(ApicultureItems.BEE_COMBS.stack(comb));
	}
}
