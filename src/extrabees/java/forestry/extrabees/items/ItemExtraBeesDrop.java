package forestry.extrabees.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import forestry.core.items.definitions.IColoredItem;

/**
 * A single-layer tinted Extra Bees flavor item (propolis / honey-drop), colored by its
 * {@link ExtraBeesDrop} type. Mirrors base Forestry's single-color propolis overlay.
 */
public class ItemExtraBeesDrop extends Item implements IColoredItem {
	private final ExtraBeesDrop type;

	public ItemExtraBeesDrop(ExtraBeesDrop type) {
		super(new Item.Properties());
		this.type = type;
	}

	public ExtraBeesDrop getType() {
		return this.type;
	}

	@Override
	public int getColorFromItemStack(ItemStack stack, int tintIndex) {
		return tintIndex == 1 ? this.type.secondaryColor : this.type.primaryColor;
	}
}
