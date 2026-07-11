package forestry.apiculture.genetics;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.IGenome;
import forestry.core.tiles.TileUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class RequiresResourceBeeJubilance implements IBeeJubilance {
	public static final MapCodec<RequiresResourceBeeJubilance> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		BlockState.CODEC.listOf().fieldOf("blocks").forGetter(jubilance -> List.copyOf(jubilance.acceptedBlockStates))
	).apply(instance, blocks -> new RequiresResourceBeeJubilance(blocks.toArray(new BlockState[0]))));

	private final HashSet<BlockState> acceptedBlockStates = new HashSet<>();

	public RequiresResourceBeeJubilance(BlockState... acceptedBlockStates) {
		Collections.addAll(this.acceptedBlockStates, acceptedBlockStates);
	}

	@Override
	public MapCodec<RequiresResourceBeeJubilance> codec() {
		return MAP_CODEC;
	}

	@Override
	public boolean isJubilant(IBeeSpecies species, IGenome genome, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		BlockPos pos = housing.getCoordinates();

		BlockEntity tile;
		do {
			pos = pos.below();
			tile = TileUtil.getTile(level, pos);
		} while (tile instanceof IBeeHousing && pos.getY() > 0);

		BlockState blockState = level.getBlockState(pos);
		return this.acceptedBlockStates.contains(blockState);
	}

}
