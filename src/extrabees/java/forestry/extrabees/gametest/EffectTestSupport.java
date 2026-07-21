package forestry.extrabees.gametest;

import java.util.List;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import forestry.api.IForestryApi;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.IBeeListener;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.core.HumidityType;
import forestry.api.core.IErrorLogic;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.core.genetics.EffectData;
import forestry.core.utils.SpeciesUtil;
import forestry.extrabees.ExtraBees;

/**
 * Shared rig for the Extra Bees effect GameTests ({@link ExtraBeesEffectWorldTest},
 * {@link ExtraBeesEffectMachineTest}): resolve a carrier species' effect the way an apiary does, drive it past its
 * throttle a controlled number of times, and place it in a controlled {@link IBeeHousing} test double whose only
 * unusual trait is a {@link IBeeModifier} pinning the effect's working territory to a small deterministic box.
 */
final class EffectTestSupport {
	private EffectTestSupport() {}

	record Carrier(IBeeSpecies species, IGenome genome, IBeeEffect effect) {}

	/** Resolves an Extra Bees species' default-genome effect the way an apiary would, failing loudly if it is missing. */
	static Carrier carrier(GameTestHelper helper, String speciesPath) {
		IBeeSpecies species = SpeciesUtil.BEE_TYPE.get().getSpecies(ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, speciesPath));
		if (species == null) {
			helper.fail("Extra Bees carrier species not loaded: " + speciesPath);
		}
		IGenome genome = species.getDefaultGenome();
		return new Carrier(species, genome, genome.resolveActive(BeeChromosomes.EFFECT));
	}

	/** Runs {@code effect.doEffect} enough times to fire it {@code activations} times past its throttle. */
	static void drive(IBeeEffect effect, IGenome genome, IBeeHousing housing, int activations) {
		IEffectData data = new EffectData(1, 0);
		int throttle = Math.max(1, throttleOf(effect));
		for (int i = 0; i < activations * throttle; i++) {
			data = effect.doEffect(genome, data, housing);
		}
	}

	/**
	 * The activation period of a throttled effect. Extra Bees carriers use both the add-on's own throttled primitives
	 * (bonemeal, spawn_mob, ...) and core-owned ones (apply_potion, damage_entities, transform_block), which extend two
	 * unrelated {@code ThrottledBeeEffect} bases, so match either. Anything else is treated as firing every tick.
	 */
	private static int throttleOf(IBeeEffect effect) {
		if (effect instanceof forestry.extrabees.genetics.effects.ThrottledBeeEffect addon) {
			return addon.getThrottle();
		}
		if (effect instanceof forestry.apiculture.genetics.effects.ThrottledBeeEffect core) {
			return core.getThrottle();
		}
		return 1;
	}

	static IBeeHousing housing(ServerLevel level, BlockPos coords, int territorySize) {
		return new EffectHousing(level, coords, territorySize);
	}

	/**
	 * A controlled {@link IBeeHousing}: a real (empty) error logic so working-queen-gated effects are not throttled, a
	 * single {@link IBeeModifier} pinning the effect's territory to a small cube, and world/coordinate access. Members
	 * effects never touch on the server ({@code getBeeInventory}/{@code getBeekeepingLogic}) throw.
	 */
	private static final class EffectHousing implements IBeeHousing {
		private final ServerLevel level;
		private final BlockPos coords;
		private final Vec3i territory;
		private final IErrorLogic errorLogic = IForestryApi.INSTANCE.getErrorManager().createErrorLogic();

		EffectHousing(ServerLevel level, BlockPos coords, int territorySize) {
			this.level = level;
			this.coords = coords;
			this.territory = new Vec3i(territorySize, territorySize, territorySize);
		}

		@Override
		public Iterable<IBeeModifier> getBeeModifiers() {
			return List.of(new IBeeModifier() {
				@Override
				public Vec3i modifyTerritory(IGenome genome, Vec3i currentModifier) {
					return EffectHousing.this.territory;
				}
			});
		}

		@Override
		public ServerLevel getWorldObj() {
			return this.level;
		}

		@Override
		public BlockPos getCoordinates() {
			return this.coords;
		}

		@Override
		public IErrorLogic getErrorLogic() {
			return this.errorLogic;
		}

		@Override
		public TemperatureType temperature() {
			return TemperatureType.NORMAL;
		}

		@Override
		public HumidityType humidity() {
			return HumidityType.NORMAL;
		}

		@Override
		public Holder<Biome> getBiome() {
			return this.level.getBiome(this.coords);
		}

		@Override
		public int getBlockLightValue() {
			return 15;
		}

		@Override
		public boolean canBlockSeeTheSky() {
			return true;
		}

		@Override
		public boolean isRaining() {
			return false;
		}

		@Override
		public GameProfile getOwner() {
			return null;
		}

		@Override
		public Vec3 getBeeFXCoordinates() {
			return Vec3.atCenterOf(this.coords);
		}

		@Override
		public Iterable<IBeeListener> getBeeListeners() {
			return List.of();
		}

		@Override
		public IBeeHousingInventory getBeeInventory() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IBeekeepingLogic getBeekeepingLogic() {
			throw new UnsupportedOperationException();
		}
	}
}
