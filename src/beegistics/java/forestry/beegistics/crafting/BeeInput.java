package forestry.beegistics.crafting;

import javax.annotation.Nullable;

import java.util.EnumSet;
import java.util.Optional;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.beegistics.BeeFilter;

/**
 * One parent slot of a bee crafting pattern: fuzzy on species + life stage (so stored hand-bred parents are usable),
 * with the canonical pure parent as the sole "possible input" AE2 autocrafts when none is stored. Shared by
 * {@link BeeMutationPattern} (species mutations) and {@link BeeGenomeMutationPattern} (trait-genome breeding).
 */
final class BeeInput implements IPatternDetails.IInput {
	private final GenericStack[] possibleInputs;
	private final BeeFilter filter;

	BeeInput(IBeeSpecies species, BeeLifeStage stage) {
		this.possibleInputs = new GenericStack[]{new GenericStack(BeeMutationPattern.canonicalKey(species, stage), 1)};
		this.filter = new BeeFilter(EnumSet.of(stage), Optional.of(species.id()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	/**
	 * A parent slot whose stored-item validity is a full {@link BeeFilter} rather than a single species, with an explicit
	 * list of canonical stacks AE2 may autocraft (first = the one it prefers to craft when none is stored). Used for the
	 * genome pattern's donor slot: {@code possibleInputs} lists every candidate donor (easiest first) so AE2 crafts the
	 * cheapest, while {@code filter} accepts <em>any</em> drone that carries the wanted alleles, so a bee already in the
	 * network - of any species - satisfies the slot from stock instead of triggering a craft.
	 */
	BeeInput(GenericStack[] possibleInputs, BeeFilter filter) {
		this.possibleInputs = possibleInputs;
		this.filter = filter;
	}

	@Override
	public GenericStack[] getPossibleInputs() {
		return this.possibleInputs;
	}

	@Override
	public long getMultiplier() {
		return 1;
	}

	@Override
	public boolean isValid(AEKey input, Level level) {
		return this.filter.matches(input);
	}

	@Nullable
	@Override
	public AEKey getRemainingKey(AEKey template) {
		return null;
	}
}
