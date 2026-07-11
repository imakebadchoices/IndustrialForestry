package forestry.core.items;

import forestry.core.utils.ItemTooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class ItemBlockForestry<B extends Block> extends BlockItem {
	public ItemBlockForestry(B block, Item.Properties builder) {
		super(block, builder);
	}

	public ItemBlockForestry(B block) {
		this(block, new Item.Properties());
	}

	@Override
	public B getBlock() {
		//noinspection unchecked
		return (B) super.getBlock();
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag advanced) {
		super.appendHoverText(stack, context, tooltip, advanced);
		ItemTooltipUtil.addInformation(stack, tooltip);
	}
}
