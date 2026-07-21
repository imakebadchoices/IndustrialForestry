package forestry.beegistics.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.beegistics.BeeCellInventory;
import forestry.beegistics.BeeCellTier;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeCell;
import forestry.core.utils.SpeciesUtil;

/**
 * Guards the bee cell's AE2 Cell Workbench partitioning. Unlike a stock cell (exact-key or fuzzy same-item), a bee cell
 * partitions by <em>species</em>: the example bees placed in the cell's config define which species it will accept, since
 * nearly every analyzed bee is a unique genome key and an exact-key whitelist would be useless. This is what lets the
 * smoke-test drive route only the mutation output into a high-priority partitioned cell.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeCellPartitionTest {
	private static IBeeSpecies species(ResourceLocation id) {
		return SpeciesUtil.BEE_TYPE.get().getSpecies(id);
	}

	private static AEItemKey beeKey(IBeeSpecies s, BeeLifeStage stage) {
		return AEItemKey.of(s.createIndividual(s.getDefaultGenome()).createStack(stage));
	}

	/** A cell partitioned to one species accepts that species (any stage/genome) and rejects others. */
	@GameTest(template = "empty")
	public static void partitionRestrictsToConfiguredSpecies(GameTestHelper helper) {
		IBeeSpecies forest = species(ForestryBeeSpecies.FOREST);
		IBeeSpecies meadows = species(ForestryBeeSpecies.MEADOWS);

		ItemStack cell = new ItemStack(BeegisticsItems.beeCell(BeeCellTier.T1K));
		ItemBeeCell cellItem = (ItemBeeCell) cell.getItem();
		// Partition to Forest by placing an example Forest drone in the config, exactly as the Cell Workbench would.
		cellItem.getConfigInventory(cell).addFilter(beeKey(forest, BeeLifeStage.DRONE));

		BeeCellInventory inv = BeeCellInventory.createInventory(cell, null);
		helper.assertTrue(inv != null, "cell inventory should be created");

		long forestIn = inv.insert(beeKey(forest, BeeLifeStage.PRINCESS), 1, Actionable.MODULATE, IActionSource.empty());
		long meadowsIn = inv.insert(beeKey(meadows, BeeLifeStage.DRONE), 1, Actionable.MODULATE, IActionSource.empty());

		helper.assertTrue(forestIn == 1, "a Forest bee of any stage should be accepted by a Forest-partitioned cell");
		helper.assertTrue(meadowsIn == 0, "a Meadows bee should be rejected by a Forest-partitioned cell");
		helper.succeed();
	}

	/** With no partition configured, the cell accepts any bee species. */
	@GameTest(template = "empty")
	public static void unpartitionedCellAcceptsAnyBee(GameTestHelper helper) {
		IBeeSpecies forest = species(ForestryBeeSpecies.FOREST);
		IBeeSpecies meadows = species(ForestryBeeSpecies.MEADOWS);

		ItemStack cell = new ItemStack(BeegisticsItems.beeCell(BeeCellTier.T1K));
		BeeCellInventory inv = BeeCellInventory.createInventory(cell, null);

		long forestIn = inv.insert(beeKey(forest, BeeLifeStage.DRONE), 1, Actionable.MODULATE, IActionSource.empty());
		long meadowsIn = inv.insert(beeKey(meadows, BeeLifeStage.DRONE), 1, Actionable.MODULATE, IActionSource.empty());

		helper.assertTrue(forestIn == 1 && meadowsIn == 1, "an unpartitioned cell should accept any species");
		helper.succeed();
	}
}
