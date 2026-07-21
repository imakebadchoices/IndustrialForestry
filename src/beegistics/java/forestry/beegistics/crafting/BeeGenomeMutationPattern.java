package forestry.beegistics.crafting;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationManager;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.core.utils.SpeciesUtil;

/**
 * An AE2 {@link IPatternDetails processing pattern} that autocrafts an <em>arbitrary homozygous genome</em> - a
 * min-maxed queen - rather than a pure default-genome species (which is {@link BeeMutationPattern}'s job). Decoded from
 * a {@link ItemBeeFilterCard Bee Pattern card} (species + homozygous trait pins). It presents breeding to AE2 as:
 *
 * <pre>pure base princess + pure donor drone &rarr; the exact homozygous target princess</pre>
 *
 * <p>The base parent is the target genome's own species (the chassis); the donor supplies whatever pinned alleles the
 * base species' default genome lacks. As with {@link BeeMutationPattern}, the inputs are fuzzy (any bee of the parent
 * species and stage) but advertise the canonical pure parent as the recursively-autocraftable input, so AE2 resolves
 * the parent tree for free. Unlike a single species mutation, reaching the exact homozygous target is a multi-generation
 * hill-climb: the controller pays that cost inside its pattern execution (selecting the best carriers each cycle) and
 * injects the canonical target key only once a bred bee {@link IGenome#isSameAlleles is genetically identical} to it.
 *
 * <p>Only a princess is offered as the (single) primary output - a min-maxed queen is a terminal deliverable, not an
 * intermediate that must also be craftable as a drone, so the drone-primary variant {@link BeeMutationPattern} needs is
 * unnecessary here.
 */
public final class BeeGenomeMutationPattern implements IPatternDetails {
	private final AEItemKey definition;
	private final IGenome target;
	/** The result species; also the base parent (the chassis line the target genome belongs to). */
	private final IBeeSpecies result;
	private final IBeeSpecies donor;
	private final IInput[] inputs;
	private final List<GenericStack> outputs;

	private BeeGenomeMutationPattern(AEItemKey definition, IGenome target, IBeeSpecies result, List<IBeeSpecies> donors) {
		this.definition = definition;
		this.target = target;
		this.result = result;
		this.donor = donors.get(0); // the easiest donor: the closure root AE2 autocrafts when none is stocked
		this.inputs = new IInput[]{
			new BeeInput(result, BeeLifeStage.PRINCESS),
			donorInput(donors, target),
		};
		this.outputs = List.of(new GenericStack(BeeMutationPattern.canonicalKey(result, target, BeeLifeStage.PRINCESS), 1));
	}

	/**
	 * The donor parent slot: AE2 may autocraft any of {@code donors} (easiest first, so the cheapest is crafted when none is
	 * stocked), but validity accepts <em>any</em> drone that expresses the target's pinned alleles - so any suitable bee
	 * already in the network, of any species, is used from stock rather than triggering a craft.
	 */
	private static BeeInput donorInput(List<IBeeSpecies> donors, IGenome target) {
		GenericStack[] possible = new GenericStack[donors.size()];
		for (int i = 0; i < donors.size(); i++) {
			possible[i] = new GenericStack(BeeMutationPattern.canonicalKey(donors.get(i), BeeLifeStage.DRONE), 1);
		}
		BeeFilter carriesPins = new BeeFilter(EnumSet.of(BeeLifeStage.DRONE), Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(target), pinnedChromosomes(target), Set.of());
		return new BeeInput(possible, carriesPins);
	}

	/**
	 * Decodes the pattern from its encoded key, or {@code null} if the key does not describe a reachable genome target. The
	 * key is a <b>Bee Pattern card</b> ({@link ItemBeeFilterCard}), keyed off the item stack (the AE2 pattern-definition
	 * key): the target is {@link #targetFrom derived} from the card's filter (species + homozygous pins) and the donor is
	 * {@link #resolveDonor auto-resolved}. Because the card key is the definition, decoding must be deterministic -
	 * {@code resolveDonor} is registry-order, not network-aware. An unreachable target (no donor carries the pins) yields
	 * {@code null} so the CPU is never asked to breed a stall.
	 */
	@Nullable
	public static BeeGenomeMutationPattern decode(AEItemKey what, @Nullable Level level) {
		if (what == null || !(what.getItem() instanceof ItemBeeFilterCard)) {
			return null;
		}
		Optional<IGenome> target = targetFrom(ItemBeeFilterCard.getFilter(what.getReadOnlyStack()));
		if (target.isEmpty()) {
			return null;
		}
		List<IBeeSpecies> donors = candidateDonors(target.get());
		IBeeSpecies result = target.get().getActiveSpecies();
		if (donors.isEmpty() || result == null) {
			return null;
		}
		return new BeeGenomeMutationPattern(what, target.get(), result, donors);
	}

	/** @return the exact genome this pattern breeds toward. */
	public IGenome getTarget() {
		return this.target;
	}

	/** @return the result species (also the base/princess parent). */
	public IBeeSpecies getResult() {
		return this.result;
	}

	/** @return the donor species (bred as a drone) supplying alleles the base species lacks. */
	public IBeeSpecies getDonor() {
		return this.donor;
	}

	/**
	 * @return the non-species chromosomes whose active value in {@code target} differs from the base species' default
	 * genome - the traits the breeding driver must introgress and fix homozygous. A chromosome already at the base
	 * default needs no work and is omitted, so an all-default target yields an empty set (nothing to hill-climb).
	 */
	public static Set<ResourceLocation> pinnedChromosomes(IGenome target) {
		IBeeSpecies base = target.getActiveSpecies();
		IGenome baseDefault = base.getDefaultGenome();
		IChromosome<?> speciesChromosome = target.getKaryotype().getSpeciesChromosome();
		Set<ResourceLocation> pinned = new LinkedHashSet<>();
		for (IChromosome<?> chromosome : target.getKaryotype().getChromosomes()) {
			if (chromosome == speciesChromosome) {
				continue;
			}
			if (!Objects.equals(target.getActiveValue(chromosome), baseDefault.getActiveValue(chromosome))) {
				pinned.add(chromosome.id());
			}
		}
		return pinned;
	}

	/**
	 * @return whether every pinned allele is present in the {@code donor}'s default genome (base-default pins are already
	 * excluded by {@link #pinnedChromosomes}). If a pinned value is on neither line, no amount of breeding pure base
	 * &times; pure donor can introduce it, so the target is unreachable and the pattern must not be offered.
	 */
	public static boolean isReachable(IGenome target, IBeeSpecies donor) {
		IGenome donorDefault = donor.getDefaultGenome();
		for (ResourceLocation id : pinnedChromosomes(target)) {
			IChromosome<?> chromosome = target.getKaryotype().getChromosome(id);
			if (chromosome == null) {
				continue;
			}
			if (!Objects.equals(target.getActiveValue(chromosome), donorDefault.getActiveValue(chromosome))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return a {@link BeeFilter} that both scores a candidate's progress toward the target (via
	 * {@code scoreTowardFilter}) and matches an exact homozygous hit: {@code genomeTemplate = target} with every
	 * {@link #pinnedChromosomes pinned} chromosome required homozygous.
	 */
	public static BeeFilter scoreFilter(IGenome target) {
		return new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.empty(), Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.of(target), Set.of(), pinnedChromosomes(target));
	}

	/**
	 * Bridges a player-authored {@link BeeFilter} (species + homozygous trait pins) into the exact homozygous genome to
	 * breed: the chosen species' default genome with each {@code requiredHomozygous} chromosome overridden to the filter
	 * template's value. This is the inverse of {@link #scoreFilter}, and lets one Bee Pattern card be a genome target.
	 *
	 * @return the target genome, or empty when the filter is not a concrete genome target (no registered species, or no
	 *     homozygous trait pins). The genome is rebuilt on the <em>chosen</em> species' default genome, NOT reused from the
	 *     filter's template - the template's species chromosome is the karyotype's default species, not the wanted one.
	 */
	public static Optional<IGenome> targetFrom(BeeFilter filter) {
		if (filter.species().isEmpty() || filter.genomeTemplate().isEmpty() || filter.requiredHomozygous().isEmpty()) {
			return Optional.empty();
		}
		IBeeSpecies base = SpeciesUtil.getBeeSpecies(filter.species().get());
		if (base == null) {
			return Optional.empty();
		}
		IGenome template = filter.genomeTemplate().get();
		IGenome baseGenome = base.getDefaultGenome();
		IChromosome<?> speciesChromosome = baseGenome.getKaryotype().getSpeciesChromosome();
		Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
		for (ResourceLocation id : filter.requiredHomozygous()) {
			IChromosome<?> chromosome = baseGenome.getKaryotype().getChromosome(id);
			if (chromosome == null || chromosome == speciesChromosome) {
				continue;
			}
			overrides.put(chromosome, Allele.of(template.getActiveValue(chromosome), true));
		}
		return overrides.isEmpty() ? Optional.empty() : Optional.of(baseGenome.copyWith(overrides));
	}

	/**
	 * Picks the donor species that supplies the target's pinned alleles, so the player never names one. Deterministic (first
	 * by species id) because a Bee Pattern card is its own AE2 pattern-definition key and must decode identically on reload
	 * from the key alone - no live-network preference here.
	 *
	 * @return every viable donor species, <b>easiest to obtain first</b> (fewest mutations in its ancestor closure, then by
	 *     id). A viable donor is not the base, is <em>recessive</em> (so base &times; donor hybrids express the chassis and
	 *     stay selectable - the convergence invariant), {@link #isReachable carries every pinned allele}, and does not itself
	 *     mutate with the base (so the cross recombines rather than breeding a third species). Empty means unreachable. The
	 *     controller offers <em>all</em> of these as the donor input's possibilities, so AE2 prefers whichever the player
	 *     already has in stock and only autocrafts the easiest when none exist.
	 */
	public static List<IBeeSpecies> candidateDonors(IGenome target) {
		IBeeSpecies base = target.getActiveSpecies();
		IMutationManager<IBeeSpecies> mutations = SpeciesUtil.BEE_TYPE.get().getMutations();
		List<IBeeSpecies> candidates = new ArrayList<>();
		for (IBeeSpecies donor : SpeciesUtil.getAllBeeSpecies()) {
			if (donor != base && !donor.isDominant() && isReachable(target, donor)
					&& mutations.getCombinations(base, donor).isEmpty()) {
				candidates.add(donor);
			}
		}
		candidates.sort(Comparator.<IBeeSpecies>comparingInt(d -> ancestorClosure(List.of(d)).size()).thenComparing(d -> d.id().toString()));
		return candidates;
	}

	/** @return the easiest viable donor (the first {@link #candidateDonors candidate}), or empty when unreachable. */
	public static Optional<IBeeSpecies> resolveDonor(IGenome target) {
		List<IBeeSpecies> candidates = candidateDonors(target);
		return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
	}

	/**
	 * @return every species mutation on any breeding path down to wild (mutation-free) species from the given roots - the
	 *     bounded set of species-mutation patterns the controller must offer so AE2 can autocraft the roots (base/donor)
	 *     when they are not stocked. BFS over {@link IMutationManager#getMutationsInto} from each root.
	 */
	public static Set<IMutation<IBeeSpecies>> ancestorClosure(Collection<IBeeSpecies> roots) {
		IMutationManager<IBeeSpecies> mutations = SpeciesUtil.BEE_TYPE.get().getMutations();
		Set<IMutation<IBeeSpecies>> closure = new LinkedHashSet<>();
		Set<IBeeSpecies> visited = new HashSet<>();
		Deque<IBeeSpecies> queue = new ArrayDeque<>(roots);
		while (!queue.isEmpty()) {
			IBeeSpecies species = queue.poll();
			if (species == null || !visited.add(species)) {
				continue;
			}
			for (IMutation<IBeeSpecies> mutation : mutations.getMutationsInto(species)) {
				if (closure.add(mutation)) {
					queue.add(mutation.getFirstParent());
					queue.add(mutation.getSecondParent());
				}
			}
		}
		return closure;
	}

	@Override
	public AEItemKey getDefinition() {
		return this.definition;
	}

	@Override
	public IInput[] getInputs() {
		return this.inputs;
	}

	@Override
	public List<GenericStack> getOutputs() {
		return this.outputs;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BeeGenomeMutationPattern other && this.definition.equals(other.definition);
	}

	@Override
	public int hashCode() {
		return this.definition.hashCode();
	}
}
