package forestry.beegistics;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import forestry.beegistics.machine.ApiaryControllerBlock;
import forestry.beegistics.machine.BeeAnalyzerBlock;

/**
 * Registers Beegistics blocks under the {@code beegistics} namespace through a plain NeoForge {@link DeferredRegister},
 * mirroring {@link BeegisticsItems}. Block items are registered alongside so they land in the same items registry.
 */
public class BeegisticsBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Beegistics.NAMESPACE);

	public static final DeferredBlock<BeeAnalyzerBlock> BEE_ANALYZER = BLOCKS.register("bee_analyzer", BeeAnalyzerBlock::new);

	public static final DeferredHolder<Item, BlockItem> BEE_ANALYZER_ITEM = BeegisticsItems.ITEMS.register(
			"bee_analyzer",
			() -> new BlockItem(BEE_ANALYZER.get(), new Item.Properties()));

	public static final DeferredBlock<ApiaryControllerBlock> APIARY_CONTROLLER = BLOCKS.register("apiary_controller", ApiaryControllerBlock::new);

	public static final DeferredHolder<Item, BlockItem> APIARY_CONTROLLER_ITEM = BeegisticsItems.ITEMS.register(
			"apiary_controller",
			() -> new BlockItem(APIARY_CONTROLLER.get(), new Item.Properties()));

	public static BlockItem beeAnalyzerItem() {
		return BEE_ANALYZER_ITEM.get();
	}

	public static BlockItem apiaryControllerItem() {
		return APIARY_CONTROLLER_ITEM.get();
	}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
	}
}
