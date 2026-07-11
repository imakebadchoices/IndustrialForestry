package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/**
 * The {@code forestry:spawn_projectile} primitive: spawns a configured entity high above the housing and sends
 * it hurtling downward. Covers the ExtraBees METEOR effect (a small fireball dropped from above) from JSON, but
 * works for any projectile/entity.
 */
public class ProjectileBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<ProjectileBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(effect -> effect.entityType),
		Codec.INT.optionalFieldOf("height", 32).forGetter(effect -> effect.height),
		Codec.FLOAT.optionalFieldOf("speed", 1.0f).forGetter(effect -> effect.speed),
		Codec.BOOL.optionalFieldOf("require_sky", true).forGetter(effect -> effect.requireSky),
		Codec.INT.optionalFieldOf("throttle", 60).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.02f).forGetter(effect -> effect.chance)
	).apply(instance, ProjectileBeeEffect::new));

	private final EntityType<?> entityType;
	private final int height;
	private final float speed;
	private final boolean requireSky;
	private final float chance;

	public ProjectileBeeEffect(boolean dominant, EntityType<?> entityType, int height, float speed, boolean requireSky, int throttle, float chance) {
		super(dominant, throttle, false, false);
		this.entityType = entityType;
		this.height = height;
		this.speed = speed;
		this.requireSky = requireSky;
		this.chance = chance;
	}

	@Override
	public MapCodec<ProjectileBeeEffect> codec() {
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

		// Use a random territory position (like Extra Bees) — the housing's own block never sees the sky.
		BlockPos base = ThrottledBeeEffect.getRandomPositionInRange(genome, housing);
		if (this.requireSky && !level.canSeeSky(base)) {
			return storedData;
		}

		Entity entity = this.entityType.create(level);
		if (entity == null) {
			return storedData;
		}
		double x = base.getX() + 0.5;
		double y = base.getY() + this.height;
		double z = base.getZ() + 0.5;
		entity.setPos(x, y, z);
		if (entity instanceof Projectile projectile) {
			projectile.shoot(0.0, -1.0, 0.0, this.speed, 1.0f);
		} else {
			entity.setDeltaMovement(0.0, -this.speed, 0.0);
		}
		level.addFreshEntity(entity);
		return storedData;
	}
}
