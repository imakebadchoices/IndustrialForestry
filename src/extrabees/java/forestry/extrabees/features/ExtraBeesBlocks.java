package forestry.extrabees.features;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import forestry.apiculture.tiles.TileHive;
import forestry.extrabees.ExtraBees;
import forestry.extrabees.blocks.BlockExtraBeesHive;

/**
 * Registers the Extra Bees wild hive blocks under the {@code extrabees} namespace, alongside the shared
 * {@link BlockEntityType} that backs them.
 * <p>
 * Base's hive block entity type is built with an immutable {@code validBlocks} set listing only base's own hive
 * blocks, so an add-on hive block cannot reuse it - {@code BlockEntity}'s constructor throws on a state its type
 * does not accept. Registering our own type here, over the same {@link TileHive} class base uses, keeps the hives
 * behaving identically without needing a change on the Forestry side.
 */
public class ExtraBeesBlocks {
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExtraBees.NAMESPACE);
	public static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(ExtraBees.NAMESPACE);
	public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExtraBees.NAMESPACE);

	private static final List<DeferredHolder<Block, BlockExtraBeesHive>> HIVE_BLOCKS = new ArrayList<>();
	private static final List<DeferredHolder<Item, BlockItem>> HIVE_ITEMS = new ArrayList<>();

	/** The Rock hive: the wild source of {@code extrabees:rock}, set into cave walls in ordinary stone. */
	public static final DeferredHolder<Block, BlockExtraBeesHive> ROCK_HIVE = hive("rock");
	/** The Marble hive: the wild source of {@code extrabees:marble}, found on calcite and diorite. */
	public static final DeferredHolder<Block, BlockExtraBeesHive> MARBLE_HIVE = hive("marble");

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileHive>> HIVE_TILE = TILES.register("hive",
		() -> BlockEntityType.Builder.of(
			(pos, state) -> new TileHive(ExtraBeesBlocks.HIVE_TILE.get(), pos, state),
			HIVE_BLOCKS.stream().map(DeferredHolder::get).toArray(Block[]::new)
		).build(null));

	private static DeferredHolder<Block, BlockExtraBeesHive> hive(String speciesPath) {
		String name = speciesPath + "_hive";
		ResourceLocation speciesId = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, speciesPath);
		DeferredHolder<Block, BlockExtraBeesHive> block = BLOCKS.register(name, () -> new BlockExtraBeesHive(speciesId));
		HIVE_BLOCKS.add(block);
		HIVE_ITEMS.add(BLOCK_ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
		return block;
	}

	/** The hive block items, for the creative tab. */
	public static List<Item> hiveItems() {
		return HIVE_ITEMS.stream().map(holder -> (Item) holder.get()).toList();
	}

	public static void register(IEventBus modBus) {
		BLOCKS.register(modBus);
		BLOCK_ITEMS.register(modBus);
		TILES.register(modBus);
	}

	private ExtraBeesBlocks() {
	}
}
