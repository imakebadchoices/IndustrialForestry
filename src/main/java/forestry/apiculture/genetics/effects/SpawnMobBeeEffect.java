package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.core.utils.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The {@code forestry:spawn_mob} primitive: periodically spawns a configured entity near the housing when a
 * player is close by, up to a soft cap on nearby entities of that type. Covers the ExtraBees
 * SPAWN_ZOMBIE / SPAWN_SKELETON / SPAWN_CREEPER effects from JSON (unlike the base {@code creeper} effect,
 * which detonates rather than spawns).
 */
public class SpawnMobBeeEffect extends ThrottledBeeEffect {
	public static final MapCodec<SpawnMobBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(effect -> effect.entityType),
		Codec.INT.optionalFieldOf("throttle", 100).forGetter(ThrottledBeeEffect::getThrottle),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.02f).forGetter(effect -> effect.chance),
		Codec.INT.optionalFieldOf("cap", 6).forGetter(effect -> effect.cap),
		Codec.INT.optionalFieldOf("player_range", 16).forGetter(effect -> effect.playerRange),
		Codec.BOOL.optionalFieldOf("check_spawn_rules", false).forGetter(effect -> effect.checkSpawnRules)
	).apply(instance, SpawnMobBeeEffect::new));

	private final EntityType<?> entityType;
	private final float chance;
	private final int cap;
	private final int playerRange;
	private final boolean checkSpawnRules;

	public SpawnMobBeeEffect(boolean dominant, EntityType<?> entityType, int throttle, float chance, int cap, int playerRange, boolean checkSpawnRules) {
		super(dominant, throttle, true, false);
		this.entityType = entityType;
		this.chance = chance;
		this.cap = cap;
		this.playerRange = playerRange;
		this.checkSpawnRules = checkSpawnRules;
	}

	@Override
	public MapCodec<SpawnMobBeeEffect> codec() {
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

		BlockPos pos = housing.getCoordinates();
		// Only spawn where a player can witness it (matches ExtraBees behavior). Use NO_SPECTATORS (not the
		// boolean overload, whose true value confusingly maps to NO_CREATIVE_OR_SPECTATOR) so creative-mode
		// players still count — otherwise spawns are suppressed while a creative player watches.
		if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.playerRange, EntitySelector.NO_SPECTATORS) == null) {
			return storedData;
		}

		// Respect a soft cap on nearby entities of this type so a hive doesn't flood the area.
		AABB capBox = new AABB(pos).inflate(8.0, 4.0, 8.0);
		if (level.getEntitiesOfClass(Entity.class, capBox, entity -> entity.getType() == this.entityType).size() >= this.cap) {
			return storedData;
		}

		if (this.entityType.create(level) instanceof Mob mob) {
			double x = pos.getX() + (rand.nextDouble() - rand.nextDouble()) * 4.0;
			double y = pos.getY() + rand.nextInt(3) - 1;
			double z = pos.getZ() + (rand.nextDouble() - rand.nextDouble()) * 4.0;
			mob.setPos(x, y, z);
			// By default force the spawn (a spawn-mob bee should work in daylight/lit areas); only honor
			// vanilla spawn rules (light/difficulty/placement) when the effect opts in.
			if (!this.checkSpawnRules || mob.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.SPAWNER)) {
				EntityUtil.spawnEntity(level, mob, x, y, z);
			} else {
				mob.discard();
			}
		}
		return storedData;
	}
}
