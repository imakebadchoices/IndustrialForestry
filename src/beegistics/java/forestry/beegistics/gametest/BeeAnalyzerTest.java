package forestry.beegistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.AECapabilities;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.stacks.AEItemKey;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.machine.BeeAnalyzerBlockEntity;
import forestry.core.utils.SpeciesUtil;

/**
 * Guards the Bee Analyzer (2d). Two concerns are separable and tested independently: the grid-independent genetic
 * transformation ({@link BeeAnalyzerBlockEntity#analyzed}) that turns an unanalyzed bee key into its analyzed
 * counterpart, and the AE2 wiring - that the placed block exposes its grid-node capability and forms a node - which
 * would silently break if the block-entity type, {@code setBlockEntity} hookup, or capability registration regressed.
 * The full honey-fed extract/analyze/insert loop over a live network is exercised in-game rather than here.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeAnalyzerTest {
	/** Analyzing an unanalyzed bee key yields a distinct, analyzed key with the same active species. */
	@GameTest(template = "empty")
	public static void analyzeTransformRevealsGenome(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IBeeSpecies forest = beeType.getSpecies(ForestryBeeSpecies.FOREST);

		ItemStack unanalyzedStack = forest.createIndividual(forest.getDefaultGenome()).createStack(BeeLifeStage.DRONE);
		AEItemKey unanalyzed = AEItemKey.of(unanalyzedStack);

		IIndividual before = IIndividualHandlerItem.getIndividual(unanalyzed.getReadOnlyStack());
		if (before == null || before.isAnalyzed()) {
			helper.fail("Test setup: the bee should start unanalyzed");
		}

		AEItemKey analyzed = BeeAnalyzerBlockEntity.analyzed(unanalyzed);
		if (analyzed == null) {
			helper.fail("analyzed() returned null for a valid bee key");
			throw new AssertionError("unreachable");
		}
		if (analyzed.equals(unanalyzed)) {
			helper.fail("analyzed() should produce a different key than the unanalyzed bee");
		}

		IIndividual after = IIndividualHandlerItem.getIndividual(analyzed.getReadOnlyStack());
		helper.assertTrue(after != null && after.isAnalyzed(), "analyzed key should carry an analyzed bee");
		helper.assertTrue(after.getSpecies() == forest, "analyzing must not change the active species");

		// Already-analyzed keys are a no-op (returned as-is), so the block never busy-loops on them.
		helper.assertTrue(BeeAnalyzerBlockEntity.analyzed(analyzed) == analyzed, "analyzing an analyzed bee should be a no-op");

		helper.succeed();
	}

	/** The placed block exposes the AE2 grid-node capability and forms a node once it readies. */
	@GameTest(template = "empty")
	public static void analyzerFormsGridNode(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 1);
		helper.setBlock(pos, BeegisticsBlocks.BEE_ANALYZER.get());

		IInWorldGridNodeHost host = helper.getLevel().getCapability(
				AECapabilities.IN_WORLD_GRID_NODE_HOST, helper.absolutePos(pos), null);
		helper.assertTrue(host != null, "Bee Analyzer must expose IN_WORLD_GRID_NODE_HOST so cables can find its node");

		// The grid node is created on the block entity's first tick (via AE2's TickHandler), so poll until it forms.
		helper.succeedWhen(() -> {
			IGridNode node = host.getGridNode(Direction.UP);
			helper.assertTrue(node != null, "Bee Analyzer should form a grid node after readying");
		});
	}
}
