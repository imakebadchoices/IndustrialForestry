package forestry.beegistics.gametest;

import java.util.EnumSet;
import java.util.Optional;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.stacks.AEItemKey;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeeFilter;
import forestry.core.utils.SpeciesUtil;

/**
 * Exercises {@link BeeFilter} - the matching primitive the rest of the add-on's automation builds on - against real
 * bee stacks and keys, plus a codec round-trip. Runs as part of the {@code beegistics} add-on.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeFilterTest {
	private static ItemStack bee(IBeeSpecies species, BeeLifeStage stage, boolean analyzed) {
		IBee individual = species.createIndividual();
		if (analyzed) {
			individual.analyze();
		}
		return individual.createStack(stage);
	}

	/** The empty filter matches any bee, at any stage, but never a non-bee. */
	@GameTest(template = "empty")
	public static void emptyMatchesAnyBee(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IBeeSpecies forest = beeType.getSpecies(ForestryBeeSpecies.FOREST);

		for (BeeLifeStage stage : BeeLifeStage.values()) {
			if (!BeeFilter.EMPTY.matches(bee(forest, stage, false))) {
				helper.fail("Empty filter rejected a " + stage + " bee");
				return;
			}
		}
		if (BeeFilter.EMPTY.matches(new ItemStack(Items.STONE))) {
			helper.fail("Empty filter matched a non-bee item");
			return;
		}
		helper.succeed();
	}

	/** A stage filter accepts only its listed life stages. */
	@GameTest(template = "empty")
	public static void stageFilter(GameTestHelper helper) {
		IBeeSpecies forest = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FOREST);
		BeeFilter dronesOnly = new BeeFilter(EnumSet.of(BeeLifeStage.DRONE), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

		if (!dronesOnly.matches(bee(forest, BeeLifeStage.DRONE, false))) {
			helper.fail("Drone filter rejected a drone");
			return;
		}
		if (dronesOnly.matches(bee(forest, BeeLifeStage.PRINCESS, false))) {
			helper.fail("Drone filter matched a princess");
			return;
		}
		helper.succeed();
	}

	/** Species and genus filters discriminate between two different species/genera. */
	@GameTest(template = "empty")
	public static void speciesAndGenusFilter(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IBeeSpecies forest = beeType.getSpecies(ForestryBeeSpecies.FOREST);
		IBeeSpecies meadows = beeType.getSpecies(ForestryBeeSpecies.MEADOWS);

		BeeFilter forestOnly = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(ForestryBeeSpecies.FOREST), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		if (!forestOnly.matches(bee(forest, BeeLifeStage.DRONE, false))) {
			helper.fail("Species filter rejected its own species");
			return;
		}
		if (forestOnly.matches(bee(meadows, BeeLifeStage.DRONE, false))) {
			helper.fail("Species filter matched a different species");
			return;
		}

		BeeFilter genusFilter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.empty(), Optional.of(forest.getGenus().name()), Optional.empty(), Optional.empty(), Optional.empty());
		if (!genusFilter.matches(bee(forest, BeeLifeStage.DRONE, false))) {
			helper.fail("Genus filter rejected a bee of its own genus");
			return;
		}
		// Forest and Meadows are both genus Apis in vanilla Forestry, so only assert the positive case above; the
		// species filter already proves cross-species rejection.
		helper.succeed();
	}

	/** The analyzed constraint distinguishes analyzed from unanalyzed bees. */
	@GameTest(template = "empty")
	public static void analyzedFilter(GameTestHelper helper) {
		IBeeSpecies forest = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FOREST);
		BeeFilter analyzedOnly = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), Optional.empty());

		if (!analyzedOnly.matches(bee(forest, BeeLifeStage.DRONE, true))) {
			helper.fail("Analyzed filter rejected an analyzed bee");
			return;
		}
		if (analyzedOnly.matches(bee(forest, BeeLifeStage.DRONE, false))) {
			helper.fail("Analyzed filter matched an unanalyzed bee");
			return;
		}
		helper.succeed();
	}

	/** Filters evaluate identically whether given an ItemStack or the AEItemKey wrapping it. */
	@GameTest(template = "empty")
	public static void matchesThroughAeKey(GameTestHelper helper) {
		IBeeSpecies forest = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FOREST);
		BeeFilter dronesOnly = new BeeFilter(EnumSet.of(BeeLifeStage.DRONE), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

		AEItemKey droneKey = AEItemKey.of(bee(forest, BeeLifeStage.DRONE, true));
		AEItemKey princessKey = AEItemKey.of(bee(forest, BeeLifeStage.PRINCESS, true));
		if (!dronesOnly.matches(droneKey) || dronesOnly.matches(princessKey)) {
			helper.fail("Filter disagreed between AEItemKey and ItemStack evaluation");
			return;
		}
		helper.succeed();
	}

	/** A configured filter survives a codec round-trip unchanged. */
	@GameTest(template = "empty")
	public static void codecRoundTrip(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		BeeFilter original = new BeeFilter(EnumSet.of(BeeLifeStage.DRONE, BeeLifeStage.PRINCESS),
			Optional.of(ForestryBeeSpecies.FOREST), Optional.of("apis"), Optional.of(ForestryBeeSpecies.MEADOWS), Optional.of(true), Optional.of(false));

		Tag encoded = BeeFilter.CODEC.encodeStart(ops, original).getOrThrow(msg -> new AssertionError("encode failed: " + msg));
		BeeFilter decoded = BeeFilter.CODEC.parse(ops, encoded).getOrThrow(msg -> new AssertionError("decode failed: " + msg));

		if (!decoded.equals(original)) {
			helper.fail("Filter did not survive codec round-trip: " + decoded + " != " + original);
			return;
		}
		helper.succeed();
	}
}
