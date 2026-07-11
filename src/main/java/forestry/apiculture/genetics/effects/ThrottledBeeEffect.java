package forestry.apiculture.genetics.effects;

import forestry.api.IForestryApi;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.apiculture.genetics.Bee;
import forestry.core.genetics.EffectData;
import forestry.core.utils.VecUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public abstract class ThrottledBeeEffect extends DummyBeeEffect implements IBeeEffect {
	private final boolean isCombinable;
	private final int throttle;
	private final boolean requiresWorkingQueen;

	protected ThrottledBeeEffect(boolean dominant, int throttle, boolean requiresWorking, boolean isCombinable) {
		super(dominant);
		this.throttle = throttle;
		this.isCombinable = isCombinable;
		this.requiresWorkingQueen = requiresWorking;
	}

	public static AABB getBounding(IBeeHousing housing, IGenome genome) {
		IBeeModifier beeModifier = IForestryApi.INSTANCE.getHiveManager().createBeeHousingModifier(housing);
		Vec3i territory = Bee.getAdjustedTerritory(genome, beeModifier);

		BlockPos min = housing.getCoordinates().offset(VecUtil.center(territory));
		BlockPos max = min.offset(territory);

		return new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
	}

	public static <T extends Entity> List<T> getEntitiesInRange(IGenome genome, IBeeHousing housing, Class<T> entityClass) {
		AABB boundingBox = getBounding(housing, genome);
		return housing.getWorldObj().getEntitiesOfClass(entityClass, boundingBox);
	}

	/**
	 * A uniformly random block position in a box centered on the housing, spanning {@code ± (1 + territory/2)}
	 * on each axis. Shared by the block/world-targeting primitives (bonemeal, transform/place block, lightning,
	 * firework, projectile spawn).
	 * <p>
	 * This intentionally samples a <em>symmetric</em> box (matching Extra Bees) rather than reusing
	 * {@link #getBounding}, whose {@code VecUtil.center} box is skewed upward — that skew makes ground-targeting
	 * effects almost never hit the ground and makes sky checks at the housing block always fail.
	 */
	public static BlockPos getRandomPositionInRange(IGenome genome, IBeeHousing housing) {
		IBeeModifier beeModifier = IForestryApi.INSTANCE.getHiveManager().createBeeHousingModifier(housing);
		Vec3i territory = Bee.getAdjustedTerritory(genome, beeModifier);
		int offsetX = 1 + territory.getX() / 2;
		int offsetY = 1 + territory.getY() / 2;
		int offsetZ = 1 + territory.getZ() / 2;

		BlockPos center = housing.getCoordinates();
		RandomSource rand = housing.getWorldObj().random;
		int x = center.getX() - offsetX + rand.nextInt(2 * offsetX + 1);
		int y = center.getY() - offsetY + rand.nextInt(2 * offsetY + 1);
		int z = center.getZ() - offsetZ + rand.nextInt(2 * offsetZ + 1);
		return new BlockPos(x, y, z);
	}

	/**
	 * Tries up to {@code attempts} random territory positions and returns the first one matching {@code valid},
	 * or {@code null} if none matched. Block-targeting effects (bonemeal, transform/place block) use this so a
	 * single activation reliably finds a valid ground block rather than usually sampling empty air — the base
	 * {@link RadioactiveBeeEffect} uses the same retry pattern.
	 */
	@Nullable
	public static BlockPos findPositionInRange(IGenome genome, IBeeHousing housing, int attempts, Predicate<BlockPos> valid) {
		for (int i = 0; i < attempts; i++) {
			BlockPos pos = getRandomPositionInRange(genome, housing);
			if (valid.test(pos)) {
				return pos;
			}
		}
		return null;
	}

	public int getThrottle() {
		return this.throttle;
	}

	@Override
	public boolean isCombinable() {
		return this.isCombinable;
	}

	@Override
	public IEffectData validateStorage(IEffectData storedData) {
		if (storedData instanceof EffectData) {
			return storedData;
		}

		return new EffectData(1, 0);
	}

	@Override
	public final IEffectData doEffect(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		if (isThrottled(storedData, housing)) {
			return storedData;
		}
		return doEffectThrottled(genome, storedData, housing);
	}

	private boolean isThrottled(IEffectData storedData, IBeeHousing housing) {
		if (this.requiresWorkingQueen && housing.getErrorLogic().hasErrors()) {
			return true;
		}

		int time = storedData.getInteger(0);
		time++;
		storedData.setInteger(0, time);

		if (time < this.throttle) {
			return true;
		}

		// Reset since we are done throttling.
		storedData.setInteger(0, 0);
		return false;
	}

	abstract IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing);
}
