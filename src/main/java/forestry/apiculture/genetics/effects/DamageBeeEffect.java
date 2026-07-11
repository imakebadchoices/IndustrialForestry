package forestry.apiculture.genetics.effects;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The {@code forestry:damage_entities} primitive: hurts living entities in range for a fixed amount, with
 * optional apiarist-armor scaling (each worn piece reduces the damage). Covers the ExtraBees RADIOACTIVE
 * effect's entity-harm half from JSON (the base {@code radioactive} effect hardcodes damage and also
 * destroys blocks, so this primitive gives datapacks a configurable, block-safe alternative).
 */
public class DamageBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<DamageBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.floatRange(0f, Float.MAX_VALUE).fieldOf("damage").forGetter(effect -> effect.damage),
		Codec.BOOL.optionalFieldOf("armor_scaling", true).forGetter(effect -> effect.armorScaling),
		Codec.INT.optionalFieldOf("throttle", 40).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1.0f).forGetter(effect -> effect.chance)
	).apply(instance, DamageBeeEffect::new));

	private final float damage;
	private final boolean armorScaling;
	private final float chance;

	public DamageBeeEffect(boolean dominant, float damage, boolean armorScaling, int throttle, float chance) {
		super(dominant, throttle, false, true);
		this.damage = damage;
		this.armorScaling = armorScaling;
		this.chance = chance;
	}

	@Override
	public MapCodec<DamageBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		RandomSource rand = level.random;
		List<LivingEntity> entities = ThrottledBeeEffect.getEntitiesInRange(genome, housing, LivingEntity.class);

		for (LivingEntity entity : entities) {
			if (rand.nextFloat() >= this.chance) {
				continue;
			}

			float damage = this.damage;
			if (this.armorScaling) {
				// Entities wearing apiarist's armor take reduced (or no) damage.
				int count = BeeManager.armorApiaristHelper.wearsItems(entity, this, true);
				damage -= count * (this.damage / 4f);
			}
			if (damage <= 0f) {
				continue;
			}

			entity.hurt(level.damageSources().generic(), damage);
		}

		return storedData;
	}
}
