package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The {@code forestry:place_block} primitive: places a block into an empty space that sits on top of a solid
 * block, within the housing's territory. Covers the ExtraBees ECTOPLASM effect (place ectoplasm in air over
 * solid ground) from JSON.
 */
public class PlaceBlockBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<PlaceBlockBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		BlockState.CODEC.fieldOf("block").forGetter(effect -> effect.block),
		Codec.INT.optionalFieldOf("throttle", 25).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.04f).forGetter(effect -> effect.chance)
	).apply(instance, PlaceBlockBeeEffect::new));

	private final BlockState block;
	private final float chance;

	public PlaceBlockBeeEffect(boolean dominant, BlockState block, int throttle, float chance) {
		super(dominant, throttle, false, false);
		this.block = block;
		this.chance = chance;
	}

	@Override
	public MapCodec<PlaceBlockBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		if (level.isClientSide) {
			return storedData;
		}
		RandomSource rand = level.random;
		if (rand.nextFloat() >= this.chance) {
			return storedData;
		}

		BlockPos pos = ThrottledBeeEffect.findPositionInRange(genome, housing, 16, p ->
			level.isEmptyBlock(p) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP));
		if (pos != null) {
			level.setBlockAndUpdate(pos, this.block);
		}
		return storedData;
	}
}
