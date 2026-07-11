package forestry.arboriculture.features;

import forestry.api.modules.ForestryModuleIds;
import forestry.arboriculture.blocks.BlockAsh;
import forestry.arboriculture.blocks.BlockCharcoal;
import forestry.arboriculture.blocks.DecorativeLogPileBlock;
import forestry.arboriculture.blocks.LogPileBlock;
import forestry.core.items.ItemBlockForestry;
import forestry.modules.features.FeatureBlock;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

@FeatureProvider
public class CharcoalBlocks {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.CHARCOAL);

	public static final FeatureBlock<BlockCharcoal, ItemBlockForestry<BlockCharcoal>> CHARCOAL = REGISTRY.block(BlockCharcoal::new, BlockBehaviour.Properties::of, ItemBlockForestry::new, Item.Properties::new, "charcoal");
	public static final FeatureBlock<LogPileBlock, BlockItem> LOG_PILE = REGISTRY.block(LogPileBlock::new, BlockBehaviour.Properties::of, ItemBlockForestry::new, Item.Properties::new, "log_pile");
	public static final FeatureBlock<DecorativeLogPileBlock, BlockItem> DECORATIVE_LOG_PILE = REGISTRY.block(DecorativeLogPileBlock::new, BlockBehaviour.Properties::of, ItemBlockForestry::new, Item.Properties::new, "decorative_log_pile");
	public static final FeatureBlock<BlockAsh, BlockItem> ASH = REGISTRY.block(BlockAsh::new, "ash_block");
}
