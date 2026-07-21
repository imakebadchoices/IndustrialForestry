package forestry.beegistics.gametest;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.core.utils.SpeciesUtil;

/**
 * Pure-helper coverage for the "Bee Pattern" unification: the registry-driven bridges on
 * {@link BeeGenomeMutationPattern} that turn a player-authored {@link BeeFilter} (species + homozygous trait pins) into a
 * concrete genome target, auto-resolve its donor, and enumerate the species mutations needed to breed it. These need the
 * live species/mutation registries, so they run as GameTests, but they touch no ME grid or apiary.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeGenomeSpecTest {
	/** A dominant chassis, a recessive mutation-free donor, and a non-species chromosome where their defaults differ. */
	private record Intro(IBeeSpecies base, IBeeSpecies donor, IChromosome<?> chromosome, Object donorValue) {
	}

	/** Finds a live single-trait introgression the same way the stress test does, deterministically (sorted by id). */
	@Nullable
	private static Intro findIntro() {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));
		for (IBeeSpecies base : species) {
			if (!base.isDominant()) {
				continue;
			}
			IGenome baseGenome = base.getDefaultGenome();
			for (IBeeSpecies donor : species) {
				if (donor == base || donor.isDominant() || !beeType.getMutations().getCombinations(base, donor).isEmpty()) {
					continue;
				}
				IGenome donorGenome = donor.getDefaultGenome();
				for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
					if (chromosome == speciesChromosome) {
						continue;
					}
					Object baseValue = baseGenome.getActiveValue(chromosome);
					Object donorValue = donorGenome.getActiveValue(chromosome);
					if (!Objects.equals(baseValue, donorValue)) {
						return new Intro(base, donor, chromosome, donorValue);
					}
				}
			}
		}
		return null;
	}

	/** Builds a Bee Pattern filter for {@code intro} whose template deliberately sits on the karyotype's DEFAULT species,
	 *  not the chosen one - the exact shape {@code BeeFilterScreen} produces, which {@code targetFrom} must not be fooled by. */
	private static BeeFilter patternFilter(Intro intro) {
		IBeeSpecies defaultSpecies = SpeciesUtil.BEE_TYPE.get().getDefaultSpecies();
		Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
		overrides.put(intro.chromosome(), Allele.of(intro.donorValue(), true));
		IGenome template = defaultSpecies.getDefaultGenome().copyWith(overrides);
		return new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(intro.base().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(template), Set.of(), Set.of(intro.chromosome().id()));
	}

	/** The expected target: the chosen species' default genome, homozygous for the pinned donor value. */
	private static IGenome expectedTarget(Intro intro) {
		Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
		overrides.put(intro.chromosome(), Allele.of(intro.donorValue(), true));
		return intro.base().getDefaultGenome().copyWith(overrides);
	}

	/** {@code targetFrom} rebuilds the target on the CHOSEN species (not the template's default species) with the pin fixed. */
	@GameTest(template = "empty")
	public static void targetFromRebuildsOnChosenSpecies(GameTestHelper helper) {
		Intro intro = findIntro();
		if (intro == null) {
			helper.fail("no live introgression in the registry to test with");
			return;
		}
		Optional<IGenome> target = BeeGenomeMutationPattern.targetFrom(patternFilter(intro));
		if (target.isEmpty()) {
			helper.fail("targetFrom returned empty for a valid species+pin filter");
			return;
		}
		// The trap: template species was the default species, but the target must be the chosen chassis species.
		if (!intro.base().id().equals(target.get().getActiveSpecies().id())) {
			helper.fail("targetFrom used the template's default species instead of the chosen species " + intro.base().id());
			return;
		}
		if (!target.get().isSameAlleles(expectedTarget(intro))) {
			helper.fail("targetFrom genome != chosen species default with the pin fixed homozygous");
			return;
		}
		if (!BeeGenomeMutationPattern.pinnedChromosomes(target.get()).contains(intro.chromosome().id())) {
			helper.fail("the pinned chromosome did not register as a pin on the target");
			return;
		}
		helper.succeed();
	}

	/** {@code resolveDonor} returns a reachable, mutation-free donor distinct from the base. */
	@GameTest(template = "empty")
	public static void resolveDonorFindsReachable(GameTestHelper helper) {
		Intro intro = findIntro();
		if (intro == null) {
			helper.fail("no live introgression in the registry to test with");
			return;
		}
		IGenome target = expectedTarget(intro);
		Optional<IBeeSpecies> donor = BeeGenomeMutationPattern.resolveDonor(target);
		if (donor.isEmpty()) {
			helper.fail("resolveDonor found no donor for a reachable target");
			return;
		}
		if (donor.get() == intro.base()) {
			helper.fail("resolveDonor returned the base species");
			return;
		}
		if (!BeeGenomeMutationPattern.isReachable(target, donor.get())) {
			helper.fail("resolveDonor returned a donor that does not carry the pinned allele");
			return;
		}
		helper.succeed();
	}

	/** {@code ancestorClosure} of a mutation-born species contains the mutation that produces it. */
	@GameTest(template = "empty")
	public static void ancestorClosureContainsProducingMutation(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));
		for (IBeeSpecies target : species) {
			List<IMutation<IBeeSpecies>> into = beeType.getMutations().getMutationsInto(target);
			if (into.isEmpty()) {
				continue;
			}
			Set<IMutation<IBeeSpecies>> closure = BeeGenomeMutationPattern.ancestorClosure(List.of(target));
			if (!closure.contains(into.get(0))) {
				helper.fail("ancestorClosure(" + target.id() + ") missing its producing mutation");
				return;
			}
			helper.succeed();
			return;
		}
		helper.fail("no mutation-born species in the registry to test ancestorClosure with");
	}

	private BeeGenomeSpecTest() {
	}
}
