package forestry.beegistics;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.capability.IIndividualHandlerItem;

/**
 * Helpers for treating {@link AEKey}s as Forestry bees. Bees are stored as ordinary {@link AEItemKey}s carrying their
 * {@code GENOME} component - no custom {@link appeng.api.stacks.AEKeyType} is required.
 */
public final class BeeKeys {
	private BeeKeys() {
	}

	/**
	 * @return {@code true} if the key is an apiculture individual (any life stage).
	 */
	public static boolean isBee(AEKey key) {
		return key instanceof AEItemKey itemKey && isBeeStack(itemKey.getReadOnlyStack());
	}

	private static boolean isBeeStack(ItemStack stack) {
		ISpeciesType<?, ?> type = IIndividualHandlerItem.getSpeciesType(stack);
		return type != null && type.id().equals(ForestrySpeciesTypes.BEE);
	}
}
