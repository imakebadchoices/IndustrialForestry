package forestry.beegistics.crafting;

import javax.annotation.Nullable;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.beegistics.BeegisticsComponents;
import forestry.beegistics.ItemBeeMutationPattern;
import forestry.core.genetics.mutations.Mutation;
import forestry.core.utils.SpeciesUtil;

/**
 * An AE2 {@link IPatternDetails processing pattern} for a single Forestry bee mutation, decoded from an
 * {@link ItemBeeMutationPattern}. It presents the mutation to AE2's autocrafting system as a deterministic recipe:
 *
 * <pre>pure firstParent princess + pure secondParent drone &rarr; pure result princess (+ pure result drone byproduct)</pre>
 *
 * <p>The trick that makes stochastic, multi-generation bee breeding fit AE2's exact-key crafting model:
 * <ul>
 *     <li><b>Honest fixed output.</b> A mutation's result is (per {@link forestry.api.genetics.IMutation}) usually the
 *     <em>default genome</em> of the result species, so "pure species M" is a stable canonical {@link AEItemKey} - the
 *     one this pattern declares as output and that the controller injects on success.</li>
 *     <li><b>Fuzzy input.</b> Each {@link IInput#isValid} accepts <em>any</em> bee of the parent species and stage (via
 *     a {@link BeeFilter}), so hand-bred stock in the network is consumed too, while {@link IInput#getPossibleInputs}
 *     names the canonical pure parent as the thing AE2 will recursively autocraft when none is stored.</li>
 * </ul>
 *
 * <p>Because the inputs are themselves canonical pure keys, AE2's crafting CPU resolves the entire breeding tree for
 * free; the mutation RNG and time cost are not bypassed - they are paid inside the controller's pattern execution.
 */
public final class BeeMutationPattern implements IPatternDetails {
	private final AEItemKey definition;
	private final IMutation<IBeeSpecies> mutation;
	private final IBeeSpecies firstParent;
	private final IBeeSpecies secondParent;
	private final IBeeSpecies result;
	private final IInput[] inputs;
	private final List<GenericStack> outputs;

	private BeeMutationPattern(AEItemKey definition, IMutation<IBeeSpecies> mutation, BeeLifeStage primaryStage) {
		this.definition = definition;
		this.mutation = mutation;
		this.firstParent = mutation.getFirstParent();
		this.secondParent = mutation.getSecondParent();
		this.result = mutation.getResult();
		this.inputs = new IInput[]{
			new BeeInput(this.firstParent, BeeLifeStage.PRINCESS),
			new BeeInput(this.secondParent, BeeLifeStage.DRONE),
		};
		GenericStack princess = new GenericStack(canonicalKey(result, BeeLifeStage.PRINCESS), 1);
		GenericStack drone = new GenericStack(canonicalKey(result, BeeLifeStage.DRONE), 1);
		// A single breeding yields both genders, but AE2 only autocrafts a pattern for its PRIMARY output. The controller
		// offers a princess-primary and a drone-primary variant of every pattern so an intermediate can be crafted as
		// either gender; the primary here is listed first (both are still produced/injected on execution).
		this.outputs = primaryStage == BeeLifeStage.DRONE ? List.of(drone, princess) : List.of(princess, drone);
	}

	/**
	 * Decodes the pattern from its encoded item key, or {@code null} if the key is not an encoded bee mutation or the
	 * named mutation is no longer registered (species removed or the combination no longer produces that result).
	 * Resolving the concrete {@link IMutation} lets the pattern read the mutation's breeding conditions. Registered with
	 * AE2 via {@link BeeMutationPatternDecoder}.
	 */
	@Nullable
	public static BeeMutationPattern decode(AEItemKey what, @Nullable Level level) {
		if (what == null) {
			return null;
		}
		BeeMutation encoded = ItemBeeMutationPattern.getMutation(what.getReadOnlyStack());
		if (encoded == null) {
			return null;
		}
		IBeeSpecies first = SpeciesUtil.getBeeSpecies(encoded.firstParent());
		IBeeSpecies second = SpeciesUtil.getBeeSpecies(encoded.secondParent());
		if (first == null || second == null) {
			return null;
		}
		BeeLifeStage primaryStage = what.getReadOnlyStack().has(BeegisticsComponents.PATTERN_DRONE_PRIMARY.get())
				? BeeLifeStage.DRONE : BeeLifeStage.PRINCESS;
		for (IMutation<IBeeSpecies> mutation : SpeciesUtil.BEE_TYPE.get().getMutations().getCombinations(first, second)) {
			if (mutation.getResult().id().equals(encoded.result())) {
				return new BeeMutationPattern(what, mutation, primaryStage);
			}
		}
		return null;
	}

	/** @return the canonical (pure default-genome) {@link AEItemKey} for a species at a life stage. */
	public static AEItemKey canonicalKey(IBeeSpecies species, BeeLifeStage stage) {
		return AEItemKey.of(species.createStack(stage));
	}

	/**
	 * @return the canonical {@link AEItemKey} for a species carrying a specific genome at a life stage. Generalizes
	 * {@link #canonicalKey(IBeeSpecies, BeeLifeStage)} (which is this with the species' default genome): any
	 * <em>homozygous</em> genome is a stable, reproducible key just like a pure species, so it is the unit the
	 * trait-breeding driver targets and emits. The individual is built fresh (unmated, unanalyzed) so equal genomes
	 * always yield equal keys - never derive this from a bred bee, whose extra components would desync the key.
	 */
	public static AEItemKey canonicalKey(IBeeSpecies species, IGenome genome, BeeLifeStage stage) {
		return AEItemKey.of(species.createStack(species.createIndividual(genome), stage));
	}

	/** @return the parent species bred as a princess (the first input). */
	public IBeeSpecies getFirstParent() {
		return this.firstParent;
	}

	/** @return the parent species bred as a drone (the second input). */
	public IBeeSpecies getSecondParent() {
		return this.secondParent;
	}

	/** @return the species this pattern breeds (its primary output species). */
	public IBeeSpecies getResult() {
		return this.result;
	}

	/** @return {@code true} if this mutation has no breeding conditions (always breedable in any apiary). */
	public boolean isUnconditional() {
		return this.mutation.getConditions().isEmpty();
	}

	/** @return localized, human-readable descriptions of this mutation's breeding conditions (empty if none). */
	public List<Component> requirements() {
		return this.mutation.getSpecialConditions();
	}

	/**
	 * @return {@code true} if the mutation's conditions are currently satisfied at the given location and climate - i.e.
	 * the apiary there could actually breed it. Uses the exact aggregation breeding uses ({@link Mutation#getChance}),
	 * evaluated against the two parents' default genomes (the pure bees this pattern breeds).
	 */
	public boolean canBreedAt(Level level, BlockPos pos, IClimateProvider climate) {
		float chance = Mutation.getChance(this.mutation, level, pos, this.firstParent.getDefaultGenome(), this.secondParent.getDefaultGenome(), climate);
		return chance > 0f;
	}

	@Override
	public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
		PatternDetailsTooltip tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
		tooltip.addInputsAndOutputs(this);
		for (Component requirement : requirements()) {
			tooltip.addProperty(requirement);
		}
		return tooltip;
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
		return o instanceof BeeMutationPattern other && this.definition.equals(other.definition);
	}

	@Override
	public int hashCode() {
		return this.definition.hashCode();
	}
}
