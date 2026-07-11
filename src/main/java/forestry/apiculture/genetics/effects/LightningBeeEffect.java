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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code forestry:strike_lightning} primitive: strikes lightning at a random position in the housing's
 * territory that can see the sky. Covers the ExtraBees LIGHTNING effect from JSON.
 */
public class LightningBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<LightningBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.INT.optionalFieldOf("throttle", 100).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.01f).forGetter(effect -> effect.chance)
	).apply(instance, LightningBeeEffect::new));

	private final float chance;

	public LightningBeeEffect(boolean dominant, int throttle, float chance) {
		super(dominant, throttle, false, false);
		this.chance = chance;
	}

	@Override
	public MapCodec<LightningBeeEffect> codec() {
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

		BlockPos pos = ThrottledBeeEffect.getRandomPositionInRange(genome, housing);
		if (!level.canSeeSky(pos)) {
			return storedData;
		}

		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
		if (bolt != null) {
			bolt.moveTo(Vec3.atBottomCenterOf(pos));
			serverLevel.addFreshEntity(bolt);
		}
		return storedData;
	}
}
