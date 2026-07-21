package forestry.beegistics.gametest;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.netty.buffer.Unpooled;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.ToleranceType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ILifeStage;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.genetics.alleles.IChromosome;
import forestry.beegistics.BeeCellInventory;
import forestry.beegistics.BeeCellTier;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsItems;
import forestry.core.features.CoreDataComponents;
import forestry.core.utils.SpeciesUtil;

/**
 * Pins down the round-trip fidelity of the compact {@link forestry.beegistics.BeeCellContents} encoding: a bee stored in
 * a cell, serialized (over the wire and to disk), and read back must reconstruct a byte-identical {@link AEItemKey}, so
 * ME-grid stacking and extraction are unaffected. This is the correctness half of the storage rework; {@code
 * BeeCellSizeTest} is the size half.
 *
 * <p>The bees are chosen to exercise every branch of the delta codec: a pure-species bee (empty genome delta), a bee
 * with several changed data chromosomes (float/int/bool/enum values), a hybrid whose active and inactive species differ
 * (a changed reference chromosome), a mated queen (a second full genome via {@code MATE_GENOME}), and per-stack side
 * components (analyzed, pristine, life stage as item identity, stacked amounts).
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeCellRoundTripTest {
	/** Storing then reading back through the network stream codec must return exactly the same keys and amounts. */
	@GameTest(template = "empty")
	public static void streamCodecRoundTripsExactly(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		Map<AEItemKey, Long> expected = buildVariedCell(helper);
		ItemStack cellStack = persistToCell(helper, expected);

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
		ItemStack.STREAM_CODEC.encode(buf, cellStack);
		ItemStack decoded = ItemStack.STREAM_CODEC.decode(buf);

		assertContents(helper, decoded, expected, "network stream codec");
		helper.succeed();
	}

	/** Storing then reading back through the persistent (disk) codec must return exactly the same keys and amounts. */
	@GameTest(template = "empty")
	public static void diskCodecRoundTripsExactly(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		Map<AEItemKey, Long> expected = buildVariedCell(helper);
		ItemStack cellStack = persistToCell(helper, expected);

		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		Tag tag = ItemStack.CODEC.encodeStart(ops, cellStack).result().orElseThrow(() -> new AssertionError("encode failed"));
		ItemStack decoded = ItemStack.CODEC.parse(ops, tag).result().orElseThrow(() -> new AssertionError("decode failed"));

		assertContents(helper, decoded, expected, "persistent disk codec");
		helper.succeed();
	}

	/** Inserts the given bees into a fresh 4k cell and persists them to its item. */
	private static ItemStack persistToCell(GameTestHelper helper, Map<AEItemKey, Long> bees) {
		ItemStack cellStack = new ItemStack(BeegisticsItems.beeCell(BeeCellTier.T4K));
		BeeCellInventory inv = BeeCellInventory.createInventory(cellStack, () -> {});
		if (inv == null) {
			helper.fail("Could not create a bee cell inventory");
			throw new AssertionError("unreachable");
		}
		IActionSource source = IActionSource.empty();
		for (Map.Entry<AEItemKey, Long> entry : bees.entrySet()) {
			long inserted = inv.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
			if (inserted != entry.getValue()) {
				helper.fail("Cell rejected a bee the test expected to fit (inserted " + inserted + " of " + entry.getValue() + ")");
			}
		}
		inv.persist();
		return cellStack;
	}

	/** Reads a persisted cell back and asserts its contents exactly match the expected key -> amount map. */
	private static void assertContents(GameTestHelper helper, ItemStack cellStack, Map<AEItemKey, Long> expected, String via) {
		BeeCellInventory inv = BeeCellInventory.createInventory(cellStack, () -> {});
		if (inv == null) {
			helper.fail("Round-tripped cell (" + via + ") was not recognized as a bee cell");
			throw new AssertionError("unreachable");
		}
		KeyCounter counter = new KeyCounter();
		inv.getAvailableStacks(counter);

		if (counter.size() != expected.size()) {
			helper.fail("After " + via + " round-trip, expected " + expected.size() + " distinct bees but found " + counter.size());
		}
		for (Map.Entry<AEItemKey, Long> entry : expected.entrySet()) {
			long actual = counter.get(entry.getKey());
			if (actual != entry.getValue()) {
				helper.fail("After " + via + " round-trip, bee " + entry.getKey().getReadOnlyStack().getHoverName().getString()
						+ " came back as " + actual + " (expected " + entry.getValue() + ") - the reconstructed key differs from the stored one");
			}
		}
	}

	/** Builds a genetically varied set of bees exercising every codec branch, mapped to the amount to store of each. */
	private static Map<AEItemKey, Long> buildVariedCell(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IBeeSpecies forest = beeType.getSpecies(ForestryBeeSpecies.FOREST);
		IBeeSpecies meadows = beeType.getSpecies(ForestryBeeSpecies.MEADOWS);
		IBeeSpecies common = beeType.getSpecies(ForestryBeeSpecies.COMMON);
		IBeeSpecies cultivated = beeType.getSpecies(ForestryBeeSpecies.CULTIVATED);

		Map<AEItemKey, Long> bees = new LinkedHashMap<>();

		// 1. Pure species, analyzed, mated queen (empty genome delta + a full mate genome).
		IBee pure = forest.createIndividual(forest.getDefaultGenome());
		pure.analyze();
		ItemStack pureStack = pure.createStack(BeeLifeStage.QUEEN);
		pureStack.set(CoreDataComponents.MATE_GENOME.get(), meadows.getDefaultGenome());
		bees.put(AEItemKey.of(pureStack), 1L);

		// 2. Several changed data chromosomes (float / int / bool / enum), unanalyzed drone, stacked.
		IGenome tweaked = meadows.getDefaultGenome().copyWith(Map.of(
				BeeChromosomes.SPEED, Allele.of(2.5f, true),
				BeeChromosomes.LIFESPAN, Allele.of(45, false),
				BeeChromosomes.FERTILITY, Allele.of(3, true),
				BeeChromosomes.CAVE_DWELLING, Allele.of(true, true),
				BeeChromosomes.TEMPERATURE_TOLERANCE, Allele.of(ToleranceType.BOTH_2, true)));
		bees.put(AEItemKey.of(meadows.createIndividual(tweaked).createStack(BeeLifeStage.DRONE)), 7L);

		// 3. Hybrid: active and inactive species differ (a changed reference chromosome), analyzed princess, pristine.
		IChromosome<net.minecraft.resources.ResourceLocation> speciesChromosome = forest.getKaryotype().getSpeciesChromosome();
		AllelePair<net.minecraft.resources.ResourceLocation> hybridPair = new AllelePair<>(
				new Allele<>(ForestryBeeSpecies.FOREST, true), new Allele<>(ForestryBeeSpecies.COMMON, false));
		IGenome hybrid = forest.getDefaultGenome().copyWithPairs(Map.of(speciesChromosome, hybridPair));
		IBee hybridBee = forest.createIndividual(hybrid);
		hybridBee.analyze();
		ItemStack hybridStack = hybridBee.createStack(BeeLifeStage.PRINCESS);
		hybridStack.set(CoreDataComponents.BEE_PRISTINE.get(), true);
		bees.put(AEItemKey.of(hybridStack), 1L);

		// 4. Plain unanalyzed larva of yet another species, single (a different life-stage item identity).
		bees.put(AEItemKey.of(common.createIndividual(common.getDefaultGenome()).createStack(BeeLifeStage.LARVAE)), 3L);

		// 5. Pure cultivated queen (another empty-delta case at a different species / stage), stacked large.
		bees.put(AEItemKey.of(cultivated.createIndividual(cultivated.getDefaultGenome()).createStack(BeeLifeStage.QUEEN)), 64L);

		if (bees.size() != 5) {
			helper.fail("Test setup produced " + bees.size() + " distinct bees, expected 5 (two collapsed into one key?)");
		}
		return bees;
	}
}
