package forestry.beegistics.crafting;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;

import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.ItemBeeMutationPattern;

/**
 * Teaches AE2 how to recover our custom patterns from their encoded key so a crafting CPU can persist and resume jobs
 * across a world/chunk reload (registered via {@link PatternDetailsHelper#registerDecoder}). Two encodings decode: a
 * <b>Bee Pattern card</b> ({@link ItemBeeFilterCard} whose filter is a genome target) into a
 * {@link BeeGenomeMutationPattern}, and a (synthesized, internal) species {@link ItemBeeMutationPattern} into a
 * {@link BeeMutationPattern}.
 */
public enum BeeMutationPatternDecoder implements IPatternDetailsDecoder {
	INSTANCE;

	/** Registers this decoder with AE2. Call once during common setup. */
	public static void register() {
		PatternDetailsHelper.registerDecoder(INSTANCE);
	}

	@Override
	public boolean isEncodedPattern(ItemStack stack) {
		if (stack.getItem() instanceof ItemBeeFilterCard) {
			return BeeGenomeMutationPattern.targetFrom(ItemBeeFilterCard.getFilter(stack)).isPresent();
		}
		if (stack.getItem() instanceof ItemBeeMutationPattern) {
			return ItemBeeMutationPattern.getMutation(stack) != null;
		}
		return false;
	}

	@Nullable
	@Override
	public IPatternDetails decodePattern(AEItemKey what, Level level) {
		if (what.getItem() instanceof ItemBeeFilterCard) {
			return BeeGenomeMutationPattern.decode(what, level);
		}
		if (what.getItem() instanceof ItemBeeMutationPattern) {
			return BeeMutationPattern.decode(what, level);
		}
		return null;
	}
}
