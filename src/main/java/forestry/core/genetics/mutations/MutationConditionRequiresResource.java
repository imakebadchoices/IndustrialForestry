package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.core.tiles.TileUtil;
import net.minecraft.client.GameNarrator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

public class MutationConditionRequiresResource implements IMutationCondition {
	public static final MapCodec<MutationConditionRequiresResource> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		BlockState.CODEC.listOf().fieldOf("blocks").forGetter(condition -> condition.acceptedBlockStates)
	).apply(instance, blocks -> new MutationConditionRequiresResource(blocks.toArray(new BlockState[0]))));

	private final List<BlockState> acceptedBlockStates;

	public MutationConditionRequiresResource(BlockState... acceptedBlockStates) {
		this.acceptedBlockStates = Arrays.asList(acceptedBlockStates);
	}

	@Override
	public MapCodec<MutationConditionRequiresResource> codec() {
		return MAP_CODEC;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		BlockEntity tile;
		do {
			pos = pos.below();
			tile = TileUtil.getTile(level, pos);
		} while (tile instanceof IBeeHousing);

		BlockState blockState = level.getBlockState(pos);
		return this.acceptedBlockStates.contains(blockState) ? currentChance : 0f;
	}

	@Override
	public Component getDescription() {
		if (this.acceptedBlockStates.isEmpty()) {
			return GameNarrator.NO_TITLE;
		} else {
			return Component.translatable("for.mutation.condition.resource", this.acceptedBlockStates.get(0).getBlock().getName());
		}
	}
}
