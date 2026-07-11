package forestry.apiculture.genetics.effects;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The {@code forestry:transform_block} primitive: replaces matching blocks in the housing's territory with a
 * target block state. Covers the ExtraBees ACID effect (cobble/stone → gravel, dirt/grass → sand) from JSON.
 */
public class TransformBlockBeeEffect extends ThrottledBeeEffect {
	/** A single from→to block replacement rule. */
	public record Transform(Block from, BlockState to) {
		public static final Codec<Transform> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.BLOCK.byNameCodec().fieldOf("from").forGetter(Transform::from),
			BlockState.CODEC.fieldOf("to").forGetter(Transform::to)
		).apply(instance, Transform::new));
	}

	public static final MapCodec<TransformBlockBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Transform.CODEC.listOf().fieldOf("transforms").forGetter(effect -> effect.transforms),
		Codec.INT.optionalFieldOf("throttle", 20).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.06f).forGetter(effect -> effect.chance)
	).apply(instance, TransformBlockBeeEffect::new));

	private final List<Transform> transforms;
	private final float chance;

	public TransformBlockBeeEffect(boolean dominant, List<Transform> transforms, int throttle, float chance) {
		super(dominant, throttle, false, false);
		this.transforms = transforms;
		this.chance = chance;
	}

	@Override
	public MapCodec<TransformBlockBeeEffect> codec() {
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

		BlockPos pos = ThrottledBeeEffect.findPositionInRange(genome, housing, 16, p -> matchingTransform(level.getBlockState(p)) != null);
		if (pos != null) {
			Transform transform = matchingTransform(level.getBlockState(pos));
			if (transform != null) {
				level.setBlockAndUpdate(pos, transform.to());
			}
		}
		return storedData;
	}

	@Nullable
	private Transform matchingTransform(BlockState state) {
		for (Transform transform : this.transforms) {
			if (state.is(transform.from())) {
				return transform;
			}
		}
		return null;
	}
}
