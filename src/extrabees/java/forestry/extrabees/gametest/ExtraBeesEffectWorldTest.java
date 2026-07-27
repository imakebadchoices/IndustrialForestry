package forestry.extrabees.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.apiculture.IBeeHousing;
import forestry.extrabees.ExtraBees;
import forestry.extrabees.gametest.EffectTestSupport.Carrier;

/**
 * In-world behavioral tests for the Extra Bees effects: places a controlled {@link IBeeHousing} in the gametest
 * world (see {@link EffectTestSupport}), drives a species' resolved effect through enough throttled activations, and
 * asserts the actual world change the effect is supposed to cause (a potion applied to an entity, a block transformed,
 * a firework launched, …). This complements {@link ExtraBeesEffectTest} (which proves the effects load, decode, and are
 * wired to species) by proving the primitives still <em>do the right thing</em> — the kind of regression an upstream
 * rebase can silently introduce without breaking any codec. Machine/player-gated effects (inject_energy, fill_fluid,
 * spawn_mob) live in {@link ExtraBeesEffectMachineTest}. Each test resolves its effect the way the apiary would
 * ({@code genome.resolveActive(EFFECT)} on the carrier species' default genome), exercising the full
 * species → allele → effect → world chain. Effects run synchronously (no world ticking needed); chance-gated ones are
 * driven for many activations so the observable outcome is a near-certainty.
 */
@GameTestHolder(ExtraBees.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ExtraBeesEffectWorldTest {
	private static final BlockPos HIVE = new BlockPos(3, 3, 3);

	// --- deterministic entity effects (one activation, chance 1.0 / no chance) ------------------------------------

	/**
	 * apply_potion (effect_wither): a living entity in range gets the configured mob effect.
	 * <p>
	 * Carried by {@code abyss}. The shadow line's old {@code effect_blindness} was handed back to base
	 * ({@code forestry:bee_effect_darkness}), so this drives one of the apply_potion effects Extra Bees still owns.
	 */
	@GameTest(template = "empty")
	public static void applyPotionAffectsNearbyEntity(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "abyss"); // extrabees:effect_wither
		BlockPos coords = helper.absolutePos(HIVE);
		Pig pig = spawnPig(helper, coords.getX() + 0.5, coords.getY(), coords.getZ() + 0.5);

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 5), 1);

		if (!pig.hasEffect(MobEffects.WITHER)) {
			helper.fail("apply_potion (effect_wither) did not apply Wither to the entity in range");
			return;
		}
		helper.succeed();
	}

	/** entity_force (effect_gravity, attract): an entity offset from the hive is pushed back toward it. */
	@GameTest(template = "empty")
	public static void entityForceAttractsNearbyEntity(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "spatial"); // extrabees:effect_gravity (attract)
		BlockPos coords = helper.absolutePos(HIVE);
		// Two blocks in +X of the hive centre, so the attraction pull is toward -X (distSq >= 2 so it is not skipped).
		Pig pig = spawnPig(helper, coords.getX() + 2.5, coords.getY() + 0.5, coords.getZ() + 0.5);
		pig.setDeltaMovement(Vec3.ZERO);

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 5), 1);

		if (pig.getDeltaMovement().x >= -0.05) {
			helper.fail("entity_force (effect_gravity) did not pull the entity toward the hive (dx=" + pig.getDeltaMovement().x + ")");
			return;
		}
		helper.succeed();
	}

	// --- effects that spawn an entity under open sky (driven for many activations) --------------------------------

	/** firework (effect_fireworks): launches a firework rocket entity from a sky-lit territory position. */
	@GameTest(template = "empty")
	public static void fireworkLaunchesRocket(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "celebratory"); // extrabees:effect_fireworks
		BlockPos coords = helper.absolutePos(HIVE);
		clearColumn(helper.getLevel(), coords, 2, 25); // guarantee open sky over the box

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 200);

		if (countEntities(helper.getLevel(), coords, FireworkRocketEntity.class) == 0) {
			helper.fail("firework (effect_fireworks) launched no firework rocket over 200 activations");
			return;
		}
		helper.succeed();
	}

	/** spawn_projectile (effect_meteor): spawns a falling small fireball above a sky-lit territory position. */
	@GameTest(template = "empty")
	public static void projectileSpawnsFallingEntity(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "volcanic"); // extrabees:effect_meteor (small_fireball)
		BlockPos coords = helper.absolutePos(HIVE);
		clearColumn(helper.getLevel(), coords, 2, 40); // fireball spawns 20 up, so clear well above

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 60);

		if (countEntities(helper.getLevel(), coords, SmallFireball.class) == 0) {
			helper.fail("spawn_projectile (effect_meteor) spawned no small fireball over 60 activations");
			return;
		}
		helper.succeed();
	}

	/** strike_lightning (effect_lightning): strikes a lightning bolt at a sky-lit territory position. */
	@GameTest(template = "empty")
	public static void lightningStrikesUnderSky(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "excited"); // extrabees:effect_lightning
		BlockPos coords = helper.absolutePos(HIVE);
		clearColumn(helper.getLevel(), coords, 2, 25); // guarantee open sky over the box

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 80);

		if (countEntities(helper.getLevel(), coords, LightningBolt.class) == 0) {
			helper.fail("strike_lightning (effect_lightning) struck no lightning over 80 activations");
			return;
		}
		helper.succeed();
	}

	// --- effects that change blocks in the territory (setup + drive + scan) ---------------------------------------

	/** transform_block (effect_acid): replaces a matching block in the territory (cobblestone -> gravel). */
	@GameTest(template = "empty")
	public static void transformBlockConvertsMatchingBlock(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "caustic"); // extrabees:effect_acid
		BlockPos coords = helper.absolutePos(HIVE);
		fill(helper.getLevel(), coords, 2, Blocks.COBBLESTONE.defaultBlockState());

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 80);

		if (!anyBlock(helper.getLevel(), coords, 2, Blocks.GRAVEL)) {
			helper.fail("transform_block (effect_acid) converted no cobblestone to gravel over 80 activations");
			return;
		}
		helper.succeed();
	}

	/** place_block (effect_ectoplasm): places its block (cobweb) in an empty space above a solid face. */
	@GameTest(template = "empty")
	public static void placeBlockFillsEmptySpace(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "glutinous"); // extrabees:effect_ectoplasm
		BlockPos coords = helper.absolutePos(HIVE);
		clearColumn(helper.getLevel(), coords, 2, 25);
		fillLayer(helper.getLevel(), coords, -2, 2, Blocks.STONE.defaultBlockState()); // floor -> the layer above it is placeable

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 120);

		if (!anyBlock(helper.getLevel(), coords, 2, Blocks.COBWEB)) {
			helper.fail("place_block (effect_ectoplasm) placed no cobweb over 120 activations");
			return;
		}
		helper.succeed();
	}

	/** bonemeal (effect_bonemeal_sapling): applies a bonemeal growth tick to a growable block. */
	@GameTest(template = "empty")
	public static void bonemealGrowsCrop(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "blooming"); // extrabees:effect_bonemeal_sapling
		BlockPos coords = helper.absolutePos(HIVE);
		// A layer of freshly-planted wheat (age 0); flag 2 avoids the neighbour update that would pop it off the (absent) farmland.
		fillLayer(helper.getLevel(), coords, 0, 2, Blocks.WHEAT.defaultBlockState());

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 400);

		if (!anyCropGrew(helper.getLevel(), coords, 2)) {
			helper.fail("bonemeal (effect_bonemeal_sapling) grew no crop over 400 activations");
			return;
		}
		helper.succeed();
	}

	// --- local world helpers --------------------------------------------------------------------------------------

	private static Pig spawnPig(GameTestHelper helper, double x, double y, double z) {
		ServerLevel level = helper.getLevel();
		Pig pig = EntityType.PIG.create(level);
		if (pig == null) {
			helper.fail("could not create test Pig");
			throw new AssertionError("unreachable");
		}
		pig.moveTo(x, y, z, 0f, 0f);
		level.addFreshEntity(pig);
		return pig;
	}

	private static <E extends Entity> int countEntities(ServerLevel level, BlockPos coords, Class<E> type) {
		// Tall box: spawn_projectile spawns its entity well above the hive (height 20), so a symmetric inflate would miss it.
		return level.getEntitiesOfClass(type, new AABB(coords).inflate(12.0, 48.0, 12.0)).size();
	}

	private static void fill(ServerLevel level, BlockPos coords, int radius, BlockState state) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					level.setBlock(coords.offset(dx, dy, dz), state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void fillLayer(ServerLevel level, BlockPos coords, int dy, int radius, BlockState state) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				level.setBlock(coords.offset(dx, dy, dz), state, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Clears the box (and a column above it) to air so territory positions can see the sky. */
	private static void clearColumn(ServerLevel level, BlockPos coords, int radius, int up) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -radius; dy <= up; dy++) {
					level.setBlock(coords.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static boolean anyBlock(ServerLevel level, BlockPos coords, int radius, Block block) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (level.getBlockState(coords.offset(dx, dy, dz)).is(block)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean anyCropGrew(ServerLevel level, BlockPos coords, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockState state = level.getBlockState(coords.offset(dx, dy, dz));
					if (state.getBlock() instanceof CropBlock && state.getValue(CropBlock.AGE) > 0) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
