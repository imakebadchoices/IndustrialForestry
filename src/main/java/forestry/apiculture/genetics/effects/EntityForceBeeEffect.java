package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code forestry:entity_force} primitive: pulls entities toward the housing (attract) or pushes them away
 * (repel), scaled inversely by distance. Covers the ExtraBees GRAVITY (attract) and THIEF (repel) effects.
 */
public class EntityForceBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<EntityForceBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.BOOL.optionalFieldOf("attract", true).forGetter(effect -> effect.attract),
		Codec.DOUBLE.optionalFieldOf("strength", 0.5).forGetter(effect -> effect.strength),
		Codec.BOOL.optionalFieldOf("affect_players", true).forGetter(effect -> effect.affectPlayers),
		Codec.INT.optionalFieldOf("throttle", 20).forGetter(ThrottledBeeEffect::getThrottle)
	).apply(instance, EntityForceBeeEffect::new));

	private final boolean attract;
	private final double strength;
	private final boolean affectPlayers;

	public EntityForceBeeEffect(boolean dominant, boolean attract, double strength, boolean affectPlayers, int throttle) {
		super(dominant, throttle, true, false);
		this.attract = attract;
		this.strength = strength;
		this.affectPlayers = affectPlayers;
	}

	@Override
	public MapCodec<EntityForceBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		if (level.isClientSide) {
			return storedData;
		}

		Vec3 center = Vec3.atCenterOf(housing.getCoordinates());
		for (Entity entity : ThrottledBeeEffect.getEntitiesInRange(genome, housing, Entity.class)) {
			if (entity instanceof Player && !this.affectPlayers) {
				continue;
			}
			Vec3 toCenter = center.subtract(entity.position());
			double distSq = toCenter.lengthSqr();
			if (distSq < 2.0) {
				continue;
			}
			double scale = this.strength / distSq * (this.attract ? 1.0 : -1.0);
			Vec3 push = toCenter.scale(scale);
			entity.setDeltaMovement(entity.getDeltaMovement().add(push));
			// Players ignore server-set velocity unless flagged, so force a velocity packet.
			entity.hurtMarked = true;
		}
		return storedData;
	}
}
