package forestry.extrabees.hives;

import java.util.ArrayList;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import forestry.api.apiculture.hives.IHivePlacement;
import forestry.core.utils.BlockUtil;

/**
 * Places a hive <em>into</em> a wall: it replaces a block of the anchor material that has at least one open cave
 * face horizontally adjacent, so the hive ends up flush in the rock with its front exposed to the cave.
 * <p>
 * This is the modern equivalent of Binnie's rock hive generator, which looked for stone with air on one horizontal
 * side at a random height between bedrock and the surface. Two things about that no longer work on 1.21: it sampled
 * {@code rand.nextInt(worldHeight)}, which cannot reach anything below y=0 and so misses the entire deepslate band,
 * and it tested a single random y per attempt, which was already sparse and is far sparser now that caves are much
 * larger. This walks the whole column from the surface down to the world floor instead and picks uniformly among
 * every valid seat it finds, so hive density no longer depends on where vanilla happens to put its caves.
 * <p>
 * Distinct from base's {@link forestry.apiculture.hives.CaveCeilingHivePlacement}, which hangs a hive below a
 * ceiling block (Lush, Nether). Here the anchor block <em>is</em> the block that gets replaced.
 */
public class CaveWallHivePlacement implements IHivePlacement {
	private final TagKey<Block> anchors;

	public CaveWallHivePlacement(TagKey<Block> anchors) {
		this.anchors = anchors;
	}

	@Override
	@Nullable
	public BlockPos getPosForHive(WorldGenLevel level, RandomSource random, int posX, int posZ) {
		int groundY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, posX, posZ);
		int minBuildHeight = level.getMinBuildHeight();
		if (groundY <= minBuildHeight) {
			return null;
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(posX, groundY, posZ);
		ArrayList<BlockPos> validPos = new ArrayList<>();

		// Walk the full column rather than sampling it, so deepslate depths get the same shot as surface caves.
		while (pos.getY() > minBuildHeight) {
			if (level.getBlockState(pos).is(this.anchors) && hasOpenCaveFace(level, pos)) {
				validPos.add(pos.immutable());
			}
			pos.move(Direction.DOWN);
		}

		return validPos.isEmpty() ? null : validPos.get(validPos.size() > 1 ? random.nextInt(validPos.size()) : 0);
	}

	/** True if any of the four horizontal neighbours is open space, i.e. this block faces onto a cave. */
	private static boolean hasOpenCaveFace(WorldGenLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			side.setWithOffset(pos, direction);
			if (level.getBlockState(side).isAir()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isValidLocation(WorldGenLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(this.anchors) && hasOpenCaveFace(level, pos);
	}

	@Override
	public boolean canReplace(BlockState state, WorldGenLevel level, BlockPos pos) {
		// The seat is solid rock, not the empty space base's placements replace, so accept the anchor material too.
		return state.is(this.anchors) || BlockUtil.canReplace(state, level, pos);
	}
}
