package forestry.core.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.items.ItemPollenCluster;
import forestry.arboriculture.features.ArboricultureBlocks;
import forestry.arboriculture.features.ArboricultureItems;
import forestry.arboriculture.features.CharcoalBlocks;
import forestry.core.features.CoreItems;
import forestry.core.fluids.ForestryFluids;
import forestry.core.items.ItemFruit;
import forestry.core.items.definitions.EnumCraftingMaterial;
import forestry.lepidopterology.features.LepidopterologyItems;

/**
 * Generates the NeoForge built-in data maps for Forestry items:
 * <ul>
 *     <li>{@code neoforge:compostables} - composter chances (replaces the old imperative
 *     {@code ComposterBlock.COMPOSTABLES} mutation)</li>
 *     <li>{@code neoforge:furnace_fuels} - burn times for peat, charcoal, log piles and the
 *     flammable fuel buckets, so they work in vanilla furnaces, Create blaze burners, etc.</li>
 * </ul>
 */
public class ForestryDataMapProvider extends DataMapProvider {
	public ForestryDataMapProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		super(packOutput, lookupProvider);
	}

	@Override
	protected void gather(HolderLookup.Provider provider) {
		gatherCompostables();
		gatherFurnaceFuels();
	}

	private void gatherCompostables() {
		Builder<Compostable, Item> composts = builder(NeoForgeDataMaps.COMPOSTABLES);

		for (ItemFruit fruit : CoreItems.FRUITS.getItems()) {
			compostable(composts, fruit, 0.65f);
		}
		compostable(composts, CoreItems.MOULDY_WHEAT.item(), 0.65f);
		compostable(composts, CoreItems.DECAYING_WHEAT.item(), 0.65f);
		compostable(composts, CoreItems.MULCH.item(), 0.65f);
		compostable(composts, CoreItems.ASH.item(), 0.65f);
		compostable(composts, CoreItems.CRAFTING_MATERIALS.item(EnumCraftingMaterial.WOOD_PULP), 0.65f);
		compostable(composts, CoreItems.PEAT.item(), 0.75f);
		compostable(composts, CoreItems.COMPOST.item(), 1f);
		for (ItemPollenCluster pollen : ApicultureItems.POLLEN_CLUSTER.getItems()) {
			compostable(composts, pollen, 0.3f);
		}
		compostable(composts, ArboricultureItems.SAPLING.item(), 0.3f);
		compostable(composts, ArboricultureItems.POLLEN_FERTILE.item(), 0.3f);
		for (BlockItem leaves : ArboricultureBlocks.LEAVES_DECORATIVE.getItems()) {
			compostable(composts, leaves, 0.3f);
		}
		compostable(composts, LepidopterologyItems.COCOON_GE.item(), 0.3f);
	}

	private void gatherFurnaceFuels() {
		Builder<FurnaceFuel, Item> fuels = builder(NeoForgeDataMaps.FURNACE_FUELS);

		// Solid fuels
		furnaceFuel(fuels, CharcoalBlocks.CHARCOAL.item(), 16000);
		furnaceFuel(fuels, CoreItems.BITUMINOUS_PEAT.item(), 4200);
		furnaceFuel(fuels, CoreItems.PEAT.item(), 2000);
		furnaceFuel(fuels, CharcoalBlocks.LOG_PILE.item(), 1200);
		furnaceFuel(fuels, CharcoalBlocks.DECORATIVE_LOG_PILE.item(), 1200);

		// Flammable fuel buckets (bucket is returned via craftRemainder)
		furnaceFuel(fuels, ForestryFluids.BIO_ETHANOL.getBucket(), 12800);
		furnaceFuel(fuels, ForestryFluids.BIOMASS.getBucket(), 4000);
		furnaceFuel(fuels, ForestryFluids.SEED_OIL.getBucket(), 2400);
	}

	private static void compostable(Builder<Compostable, Item> builder, ItemLike item, float chance) {
		builder.add(BuiltInRegistries.ITEM.getKey(item.asItem()), new Compostable(chance), false);
	}

	private static void furnaceFuel(Builder<FurnaceFuel, Item> builder, ItemLike item, int burnTime) {
		builder.add(BuiltInRegistries.ITEM.getKey(item.asItem()), new FurnaceFuel(burnTime), false);
	}

	@Override
	public String getName() {
		return "Forestry Data Maps";
	}
}
