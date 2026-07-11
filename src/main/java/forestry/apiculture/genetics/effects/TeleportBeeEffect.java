package forestry.apiculture.genetics.effects;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The {@code forestry:teleport} primitive: picks a random living entity in range and randomly teleports it to
 * an empty spot within the housing's territory. Covers the ExtraBees TELEPORT effect from JSON.
 */
public class TeleportBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<TeleportBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.INT.optionalFieldOf("throttle", 80).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.05f).forGetter(effect -> effect.chance)
	).apply(instance, TeleportBeeEffect::new));

	private final float chance;

	public TeleportBeeEffect(boolean dominant, int throttle, float chance) {
		super(dominant, throttle, true, false);
		this.chance = chance;
	}

	@Override
	public MapCodec<TeleportBeeEffect> codec() {
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

		List<LivingEntity> entities = ThrottledBeeEffect.getEntitiesInRange(genome, housing, LivingEntity.class);
		if (entities.isEmpty()) {
			return storedData;
		}

		LivingEntity entity = entities.get(rand.nextInt(entities.size()));
		BlockPos target = ThrottledBeeEffect.getRandomPositionInRange(genome, housing);
		if (level.isEmptyBlock(target) && level.isEmptyBlock(target.above())) {
			entity.randomTeleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, true);
		}
		return storedData;
	}
}
