package forestry.apiculture;

import java.util.List;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import forestry.api.apiculture.IFlowerType;

/**
 * The single generic {@link IFlowerType} implementation driven entirely by datapack data. A queen accepts a
 * block as a flower when it is in the {@code accepted} {@link HolderSet} (an inline block list and/or a block
 * tag), and plants a random state from {@code plantable} at an empty position near the hive (an empty
 * {@code plantable} list makes it non-planting). This covers every Extra Bees flower type as pure JSON via
 * {@link FlowerTypeDefinition}; only genuinely code-shaped predicates (e.g. Forestry fruit-bearing block
 * entities) would still need a bespoke type.
 */
public class DataFlowerType implements IFlowerType {
	private final HolderSet<Block> accepted;
	private final List<BlockState> plantable;
	private final boolean dominant;

	public DataFlowerType(HolderSet<Block> accepted, List<BlockState> plantable, boolean dominant) {
		this.accepted = accepted;
		this.plantable = plantable;
		this.dominant = dominant;
	}

	@Override
	public boolean isAcceptableFlower(Level level, BlockPos pos) {
		return this.accepted.contains(level.getBlockState(pos).getBlock().builtInRegistryHolder());
	}

	@Override
	public boolean plantRandomFlower(Level level, BlockPos pos, List<BlockState> nearbyFlowers) {
		if (this.plantable.isEmpty() || !level.hasChunkAt(pos) || !level.isEmptyBlock(pos)) {
			return false;
		}
		ObjectArrayList<BlockState> candidates = new ObjectArrayList<>(this.plantable);
		Util.shuffle(candidates, level.random);

		for (BlockState state : candidates) {
			if (state.canSurvive(level, pos)) {
				if (state.hasProperty(DoublePlantBlock.HALF)) {
					BlockPos topPos = pos.above();

					if (level.isEmptyBlock(topPos)) {
						return level.setBlockAndUpdate(pos, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
							&& level.setBlockAndUpdate(topPos, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
					}
				} else {
					return level.setBlockAndUpdate(pos, state);
				}
			}
		}
		return false;
	}

	@Override
	public boolean isDominant() {
		return this.dominant;
	}
}
