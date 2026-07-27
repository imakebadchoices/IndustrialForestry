package forestry.extrabees.blocks;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import forestry.apiculture.blocks.BlockBeeHive;
import forestry.apiculture.tiles.TileHive;
import forestry.extrabees.features.ExtraBeesBlocks;

/**
 * A wild Extra Bees hive block. Everything that makes a hive a hive - the angry-bee attack, the drop table lookup
 * through {@code IHiveManager}, the swarm behaviour - is inherited from {@link BlockBeeHive}; all this subclass
 * changes is which {@link BlockEntityType} backs it.
 * <p>
 * That indirection is required, not cosmetic. Base's {@code ApicultureTiles.HIVE} is built with an immutable
 * {@code validBlocks} set containing only the base hive blocks, and {@code BlockEntity}'s constructor throws on a
 * state its type does not accept - so an add-on hive block cannot reuse it. {@link TileHive} takes its type as a
 * constructor argument precisely so add-ons can register their own, which is what {@link ExtraBeesBlocks#HIVE_TILE}
 * does. The ticker has to be re-pointed for the same reason: base's returns null for any type but its own.
 */
public class BlockExtraBeesHive extends BlockBeeHive {
	public BlockExtraBeesHive(ResourceLocation speciesId) {
		super(speciesId);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileHive(ExtraBeesBlocks.HIVE_TILE.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> actualType) {
		return actualType != ExtraBeesBlocks.HIVE_TILE.get() ? null : (level1, pos, state1, tile) -> ((TileHive) tile).tick(level1);
	}
}
