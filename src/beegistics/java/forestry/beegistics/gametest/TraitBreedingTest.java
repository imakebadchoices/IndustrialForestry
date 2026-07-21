package forestry.beegistics.gametest;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.genetics.alleles.IChromosome;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.Beegistics;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.core.utils.SpeciesUtil;

/**
 * Guards the trait-based selective-breeding driver: the pure scoring/selection logic
 * ({@link ApiaryControllerBlockEntity#scoreTowardFilter} / {@link ApiaryControllerBlockEntity#findBestParent}) and the
 * trait-aware {@link BeeFilter} matching that expresses both true-breeding (homozygous) and phenotype (active) targets.
 * These are deterministic and grid-independent, so they are the reliable proof of the driver (the whole-loop convergence
 * is inherently stochastic). Uses the boolean {@code cave_dwelling} chromosome as the distinctive trait: its two states
 * are trivial to construct and assert, so a homozygous / heterozygous / absent bee scores 2 / 1 / 0.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class TraitBreedingTest {
	private static final IChromosome<Boolean> TRAIT = BeeChromosomes.CAVE_DWELLING;

	private static IBeeSpecies forest() {
		return SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FOREST);
	}

	/** A Forest genome homozygous (both alleles) for the given cave-dwelling value, the rest defaulted. */
	private static IGenome homozygous(boolean value) {
		Map<IChromosome<?>, Allele<?>> alleles = Map.of(TRAIT, Allele.dominant(value));
		return forest().getDefaultGenome().copyWith(alleles);
	}

	/** A Forest genome that expresses (active) cave-dwelling true but hides (inactive) false - a heterozygous carrier. */
	private static IGenome carrier() {
		Map<IChromosome<?>, AllelePair<?>> pairs = Map.of(TRAIT, new AllelePair<>(Allele.dominant(true), Allele.recessive(false)));
		return forest().getDefaultGenome().copyWithPairs(pairs);
	}

	private static ItemStack drone(IGenome genome) {
		return forest().createIndividual(genome).createStack(BeeLifeStage.DRONE);
	}

	private static BeeFilter forestMatch() {
		return new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(forest().id()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	/** A target requiring cave-dwelling homozygous-true (true-breeding). */
	private static BeeFilter homozygousTarget() {
		return new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(forest().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(homozygous(true)), Set.of(), Set.of(TRAIT.id()));
	}

	/** A target requiring only that cave-dwelling is expressed (active), ignoring the hidden allele (phenotype). */
	private static BeeFilter activeTarget() {
		return new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(forest().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(homozygous(true)), Set.of(TRAIT.id()), Set.of());
	}

	/** Scoring strictly increases with breeding progress toward a homozygous target: absent 0, carrier 1, pure 2. */
	@GameTest(template = "empty")
	public static void scoreRewardsProgressTowardHomozygous(GameTestHelper helper) {
		BeeFilter target = homozygousTarget();
		int none = ApiaryControllerBlockEntity.scoreTowardFilter(homozygous(false), target);
		int carrier = ApiaryControllerBlockEntity.scoreTowardFilter(carrier(), target);
		int pure = ApiaryControllerBlockEntity.scoreTowardFilter(homozygous(true), target);
		helper.assertTrue(none == 0, "a bee lacking the trait should score 0, got " + none);
		helper.assertTrue(carrier == 1, "a heterozygous carrier should score 1, got " + carrier);
		helper.assertTrue(pure == 2, "a homozygous bee should score 2, got " + pure);
		helper.assertTrue(pure > carrier && carrier > none, "score must strictly increase with progress");
		helper.succeed();
	}

	/** The driver selects the highest-scoring parent (introgression toward the target), and stays first-match with no target. */
	@GameTest(template = "empty")
	public static void findBestParentPicksHighestScorer(GameTestHelper helper) {
		AEItemKey noTrait = AEItemKey.of(drone(homozygous(false)));
		AEItemKey carrier = AEItemKey.of(drone(carrier()));
		AEItemKey pure = AEItemKey.of(drone(homozygous(true)));

		KeyCounter contents = new KeyCounter();
		contents.add(noTrait, 5);
		contents.add(carrier, 5);
		contents.add(pure, 1);

		BeeFilter match = forestMatch();
		AEItemKey best = ApiaryControllerBlockEntity.findBestParent(contents, match, BeeLifeStage.DRONE, homozygousTarget());
		helper.assertTrue(pure.equals(best), "should select the homozygous drone as the best parent toward the target");

		// Backward compatibility: with no trait target, selection degrades to findParent's first-match behavior.
		AEItemKey first = ApiaryControllerBlockEntity.findBestParent(contents, match, BeeLifeStage.DRONE, null);
		AEItemKey firstMatch = ApiaryControllerBlockEntity.findParent(contents, match, BeeLifeStage.DRONE);
		helper.assertTrue(java.util.Objects.equals(first, firstMatch), "with no target, findBestParent must match findParent's first-match behavior");
		helper.succeed();
	}

	/** A homozygous target accepts only true-breeding bees; a phenotype target accepts any bee expressing the trait. */
	@GameTest(template = "empty")
	public static void filterMatchesHomozygousAndPhenotype(GameTestHelper helper) {
		BeeFilter homo = homozygousTarget();
		helper.assertTrue(homo.matches(drone(homozygous(true))), "homozygous target should accept a homozygous bee");
		helper.assertTrue(!homo.matches(drone(carrier())), "homozygous target should reject a heterozygous carrier");
		helper.assertTrue(!homo.matches(drone(homozygous(false))), "homozygous target should reject a bee lacking the trait");

		BeeFilter active = activeTarget();
		helper.assertTrue(active.matches(drone(carrier())), "phenotype target should accept a bee expressing the trait");
		helper.assertTrue(!active.matches(drone(homozygous(false))), "phenotype target should reject a bee not expressing the trait");
		helper.succeed();
	}

	/** A trait filter (genome template + required chromosomes) survives a codec round-trip unchanged. */
	@GameTest(template = "empty")
	public static void traitFilterCodecRoundTrip(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		BeeFilter original = homozygousTarget();
		Tag encoded = BeeFilter.CODEC.encodeStart(ops, original).getOrThrow(msg -> new AssertionError("encode failed: " + msg));
		BeeFilter decoded = BeeFilter.CODEC.parse(ops, encoded).getOrThrow(msg -> new AssertionError("decode failed: " + msg));
		helper.assertTrue(decoded.equals(original), "trait filter did not survive codec round-trip: " + decoded + " != " + original);
		helper.succeed();
	}
}
