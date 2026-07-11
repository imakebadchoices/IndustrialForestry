package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The {@code forestry:bonemeal} primitive: applies a bonemeal growth tick to a random growable block in the
 * housing's territory. Covers the ExtraBees BONEMEAL_SAPLING / BONEMEAL_FRUIT / BONEMEAL_MUSHROOM effects
 * (any {@link BonemealableBlock} qualifies, so a single primitive replaces all three).
 */
public class BonemealBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<BonemealBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.INT.optionalFieldOf("throttle", 20).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.1f).forGetter(effect -> effect.chance)
	).apply(instance, BonemealBeeEffect::new));

	private final float chance;

	public BonemealBeeEffect(boolean dominant, int throttle, float chance) {
		super(dominant, throttle, false, false);
		this.chance = chance;
	}

	@Override
	public MapCodec<BonemealBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		if (!(level instanceof ServerLevel serverLevel)) {
			return storedData;
		}
		RandomSource rand = level.random;
		if (rand.nextFloat() >= this.chance) {
			return storedData;
		}

		BlockPos pos = ThrottledBeeEffect.findPositionInRange(genome, housing, 16, p -> {
			BlockState candidate = level.getBlockState(p);
			return candidate.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, p, candidate);
		});
		if (pos != null) {
			BlockState state = level.getBlockState(pos);
			BonemealableBlock growable = (BonemealableBlock) state.getBlock();
			if (growable.isBonemealSuccess(level, rand, pos, state)) {
				growable.performBonemeal(serverLevel, rand, pos, state);
				level.levelEvent(1505, pos, 0); // bonemeal particles + sound
			}
		}
		return storedData;
	}
}
