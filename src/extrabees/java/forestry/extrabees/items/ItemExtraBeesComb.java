package forestry.extrabees.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import forestry.core.items.definitions.IColoredItem;

/**
 * A single Extra Bees comb item. Mirrors {@link forestry.apiculture.items.ItemHoneyComb}: a
 * two-layer tinted item whose colors come from its {@link ExtraBeesComb} type.
 */
public class ItemExtraBeesComb extends Item implements IColoredItem {
	private final ExtraBeesComb type;

	public ItemExtraBeesComb(ExtraBeesComb type) {
		super(new Item.Properties());
		this.type = type;
	}

	public ExtraBeesComb getType() {
		return this.type;
	}

	@Override
	public int getColorFromItemStack(ItemStack stack, int tintIndex) {
		if (tintIndex == 1) {
			return this.type.primaryColor;
		} else {
			return this.type.secondaryColor;
		}
	}
}
