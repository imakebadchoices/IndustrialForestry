package forestry.beegistics.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IMutation;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeMutationPattern;
import forestry.beegistics.crafting.BeeMutation;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.core.utils.SpeciesUtil;

/**
 * Unit-style coverage for the {@link BeeMutationPattern} primitive (Phase 2 autocrafting): encoding a mutation onto an
 * {@link ItemBeeMutationPattern}, decoding it back into a working AE2 {@link IPatternDetails}, and verifying its
 * canonical (pure default-genome) inputs/outputs plus the fuzzy input matcher. No grid is needed - the pattern is pure.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeMutationPatternTest {
	/** @return the first unconditional mutation with two distinct parents, for a deterministic test subject. */
	private static IMutation<IBeeSpecies> pickMutation() {
		for (IMutation<IBeeSpecies> m : SpeciesUtil.BEE_TYPE.get().getMutations().getAllMutations()) {
			if (m.getConditions().isEmpty() && !m.getFirstParent().equals(m.getSecondParent())) {
				return m;
			}
		}
		throw new IllegalStateException("No unconditional two-parent bee mutation available for the pattern test");
	}

	private static ItemStack encodedPattern(IMutation<IBeeSpecies> m) {
		ItemStack stack = new ItemStack(BeegisticsItems.beeMutationPattern());
		ItemBeeMutationPattern.setMutation(stack, new BeeMutation(m.getFirstParent().id(), m.getSecondParent().id(), m.getResult().id()));
		return stack;
	}

	private static ItemStack bee(IBeeSpecies species, BeeLifeStage stage, boolean analyzed) {
		IBee individual = species.createIndividual();
		if (analyzed) {
			individual.analyze();
		}
		return individual.createStack(stage);
	}

	/** A round-tripped pattern exposes the canonical pure parent inputs and result outputs of its mutation. */
	@GameTest(template = "empty")
	public static void decodeExposesCanonicalInputsAndOutputs(GameTestHelper helper) {
		IMutation<IBeeSpecies> m = pickMutation();
		AEItemKey definition = AEItemKey.of(encodedPattern(m));

		BeeMutationPattern pattern = BeeMutationPattern.decode(definition, helper.getLevel());
		if (pattern == null) {
			helper.fail("Pattern failed to decode");
			return;
		}
		if (!pattern.getDefinition().equals(definition)) {
			helper.fail("getDefinition did not round-trip the encoded item key");
			return;
		}
		if (!pattern.getResult().id().equals(m.getResult().id())) {
			helper.fail("Decoded result species mismatch");
			return;
		}

		IPatternDetails.IInput[] inputs = pattern.getInputs();
		if (inputs.length != 2) {
			helper.fail("Expected two inputs, got " + inputs.length);
			return;
		}
		AEItemKey expectedPrincess = BeeMutationPattern.canonicalKey(m.getFirstParent(), BeeLifeStage.PRINCESS);
		AEItemKey expectedDrone = BeeMutationPattern.canonicalKey(m.getSecondParent(), BeeLifeStage.DRONE);
		if (!inputs[0].getPossibleInputs()[0].what().equals(expectedPrincess)) {
			helper.fail("Input 0 is not the canonical first-parent princess");
			return;
		}
		if (!inputs[1].getPossibleInputs()[0].what().equals(expectedDrone)) {
			helper.fail("Input 1 is not the canonical second-parent drone");
			return;
		}

		List<GenericStack> outputs = pattern.getOutputs();
		AEItemKey outPrincess = BeeMutationPattern.canonicalKey(m.getResult(), BeeLifeStage.PRINCESS);
		AEItemKey outDrone = BeeMutationPattern.canonicalKey(m.getResult(), BeeLifeStage.DRONE);
		if (outputs.size() != 2 || !outputs.get(0).what().equals(outPrincess) || !outputs.get(1).what().equals(outDrone)) {
			helper.fail("Outputs are not the canonical result princess + drone");
			return;
		}
		if (!pattern.getPrimaryOutput().what().equals(outPrincess)) {
			helper.fail("Primary output is not the result princess");
			return;
		}
		helper.succeed();
	}

	/** The input matcher is fuzzy on species + stage: it accepts hand-bred (analyzed) parents but rejects wrong stage/species/non-bees. */
	@GameTest(template = "empty")
	public static void inputIsFuzzyOnSpeciesAndStage(GameTestHelper helper) {
		IMutation<IBeeSpecies> m = pickMutation();
		BeeMutationPattern pattern = BeeMutationPattern.decode(AEItemKey.of(encodedPattern(m)), helper.getLevel());
		if (pattern == null) {
			helper.fail("Pattern failed to decode");
			return;
		}
		IPatternDetails.IInput princessInput = pattern.getInputs()[0];
		IBeeSpecies first = m.getFirstParent();
		IBeeSpecies second = m.getSecondParent();

		// Pure and analyzed (hand-bred) first-parent princesses are both valid, even though their keys differ.
		if (!princessInput.isValid(AEItemKey.of(bee(first, BeeLifeStage.PRINCESS, false)), helper.getLevel())) {
			helper.fail("Rejected the pure first-parent princess");
			return;
		}
		if (!princessInput.isValid(AEItemKey.of(bee(first, BeeLifeStage.PRINCESS, true)), helper.getLevel())) {
			helper.fail("Rejected an analyzed first-parent princess (fuzzy match failed)");
			return;
		}
		// Wrong stage, wrong species, and non-bees are all rejected.
		if (princessInput.isValid(AEItemKey.of(bee(first, BeeLifeStage.DRONE, false)), helper.getLevel())) {
			helper.fail("Accepted a drone for the princess input");
			return;
		}
		if (princessInput.isValid(AEItemKey.of(bee(second, BeeLifeStage.PRINCESS, false)), helper.getLevel())) {
			helper.fail("Accepted the wrong-species princess");
			return;
		}
		if (princessInput.isValid(AEItemKey.of(new ItemStack(Items.STONE)), helper.getLevel())) {
			helper.fail("Accepted a non-bee item");
			return;
		}
		helper.succeed();
	}

	/** Two patterns decoded from equal item keys are equal and hash equally (required for crafting-job resume). */
	@GameTest(template = "empty")
	public static void equalsAndHashCodeStable(GameTestHelper helper) {
		IMutation<IBeeSpecies> m = pickMutation();
		AEItemKey definition = AEItemKey.of(encodedPattern(m));
		BeeMutationPattern a = BeeMutationPattern.decode(definition, helper.getLevel());
		BeeMutationPattern b = BeeMutationPattern.decode(definition, helper.getLevel());
		if (a == null || b == null || !a.equals(b) || a.hashCode() != b.hashCode()) {
			helper.fail("Re-decoded patterns are not equal/consistent");
			return;
		}
		helper.succeed();
	}

	/** A blank pattern (no component) decodes to null and is not treated as a pattern. */
	@GameTest(template = "empty")
	public static void blankPatternDecodesToNull(GameTestHelper helper) {
		AEItemKey blank = AEItemKey.of(new ItemStack(BeegisticsItems.beeMutationPattern()));
		if (BeeMutationPattern.decode(blank, helper.getLevel()) != null) {
			helper.fail("Blank pattern unexpectedly decoded");
			return;
		}
		helper.succeed();
	}

	/** An unconditional mutation's pattern reports no requirements and an empty tooltip-properties list. */
	@GameTest(template = "empty")
	public static void unconditionalHasNoRequirements(GameTestHelper helper) {
		BeeMutationPattern pattern = BeeMutationPattern.decode(AEItemKey.of(encodedPattern(pickMutation())), helper.getLevel());
		if (pattern == null) {
			helper.fail("Pattern failed to decode");
			return;
		}
		if (!pattern.isUnconditional() || !pattern.requirements().isEmpty()) {
			helper.fail("Unconditional mutation reported requirements");
			return;
		}
		if (!pattern.getTooltip(helper.getLevel(), TooltipFlag.Default.NORMAL).getProperties().isEmpty()) {
			helper.fail("Unconditional pattern added tooltip properties");
			return;
		}
		helper.succeed();
	}

	/** A conditional mutation's pattern surfaces its conditions as requirements() and tooltip properties. */
	@GameTest(template = "empty")
	public static void conditionalSurfacesRequirements(GameTestHelper helper) {
		IMutation<IBeeSpecies> conditional = null;
		for (IMutation<IBeeSpecies> m : SpeciesUtil.BEE_TYPE.get().getMutations().getAllMutations()) {
			if (!m.getConditions().isEmpty() && !m.getFirstParent().equals(m.getSecondParent())) {
				conditional = m;
				break;
			}
		}
		if (conditional == null) {
			// No conditional bee mutation in the registry - nothing to gate, trivially satisfied.
			helper.succeed();
			return;
		}

		BeeMutationPattern pattern = BeeMutationPattern.decode(AEItemKey.of(encodedPattern(conditional)), helper.getLevel());
		if (pattern == null) {
			helper.fail("Conditional mutation now decodes to a pattern (unconditional-only restriction lifted)");
			return;
		}
		if (pattern.isUnconditional() || pattern.requirements().isEmpty()) {
			helper.fail("Conditional mutation reported no requirements");
			return;
		}
		if (pattern.getTooltip(helper.getLevel(), TooltipFlag.Default.NORMAL).getProperties().size() != pattern.requirements().size()) {
			helper.fail("Tooltip properties did not match the mutation's requirements");
			return;
		}
		helper.succeed();
	}
}
