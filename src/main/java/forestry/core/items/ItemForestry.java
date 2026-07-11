package forestry.core.items;

import forestry.core.utils.ItemTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ItemForestry extends Item {
	public ItemForestry() {
		this(new Properties());
	}

	public ItemForestry(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag advanced) {
		ItemTooltipUtil.addInformation(stack, tooltip);
	}
}
