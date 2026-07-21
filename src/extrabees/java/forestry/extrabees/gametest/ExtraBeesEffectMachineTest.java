package forestry.extrabees.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.extrabees.ExtraBees;
import forestry.extrabees.gametest.EffectTestSupport.Carrier;

/**
 * In-world tests for the Extra Bees effects that need a partner block or a nearby player, which is why they are split
 * out of {@link ExtraBeesEffectWorldTest}: {@code inject_energy} and {@code fill_fluid} push into a capability on an
 * adjacent block (a Forestry Analyzer exposes an energy handler, a Bottler an unfiltered fluid tank), and
 * {@code spawn_mob} only fires when a player is within range. Same rig as the other world tests (a controlled
 * {@link EffectTestSupport} housing; effects driven synchronously past their throttle).
 */
@GameTestHolder(ExtraBees.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ExtraBeesEffectMachineTest {
	private static final BlockPos HIVE = new BlockPos(1, 2, 1);
	private static final BlockPos MACHINE = new BlockPos(2, 2, 1); // an adjacent neighbour of the hive

	/** inject_energy (effect_power): pushes Forge Energy into an adjacent energy storage (a Forestry Analyzer). */
	@GameTest(template = "empty")
	public static void injectEnergyChargesAdjacentMachine(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "ecstatic"); // extrabees:effect_power
		helper.setBlock(MACHINE, block("forestry:analyzer"));
		BlockPos coords = helper.absolutePos(HIVE);
		BlockPos machinePos = helper.absolutePos(MACHINE);

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 5);

		IEnergyStorage storage = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, machinePos, null);
		if (storage == null) {
			helper.fail("analyzer exposed no energy storage capability");
			return;
		}
		if (storage.getEnergyStored() <= 0) {
			helper.fail("inject_energy (effect_power) did not charge the adjacent machine (stored=" + storage.getEnergyStored() + ")");
			return;
		}
		helper.succeed();
	}

	/** fill_fluid (effect_water): fills a fluid handler adjacent to the hive (a Forestry Bottler's tank). */
	@GameTest(template = "empty")
	public static void fillFluidFillsAdjacentTank(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "water"); // extrabees:effect_water
		helper.setBlock(MACHINE, block("forestry:bottler"));
		BlockPos coords = helper.absolutePos(HIVE);
		BlockPos machinePos = helper.absolutePos(MACHINE);

		EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(helper.getLevel(), coords, 3), 3);

		IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, machinePos, null);
		if (handler == null) {
			helper.fail("bottler exposed no fluid handler capability");
			return;
		}
		if (handler.getFluidInTank(0).isEmpty()) {
			helper.fail("fill_fluid (effect_water) put no fluid into the adjacent tank");
			return;
		}
		helper.succeed();
	}

	/** spawn_mob (effect_spawn_zombie): spawns the configured mob near the hive when a player is in range. */
	@GameTest(template = "empty")
	public static void spawnMobSpawnsMobNearPlayer(GameTestHelper helper) {
		Carrier carrier = EffectTestSupport.carrier(helper, "rotten"); // extrabees:effect_spawn_zombie
		ServerLevel level = helper.getLevel();
		BlockPos coords = helper.absolutePos(HIVE);

		// spawn_mob only fires with a player within range. A full mock-player JOIN fires login events that other mods
		// (e.g. KubeJS) try to network-sync to the fake connection and crash, so instead use a FakePlayer (its connection
		// swallows packets, no login events) and drop it straight into the live player list for the duration of the drive.
		FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000ee511"), "extrabees_effect_test"));
		player.moveTo(coords.getX() + 0.5, coords.getY(), coords.getZ() + 0.5, 0f, 0f);
		level.players().add(player);
		try {
			EffectTestSupport.drive(carrier.effect(), carrier.genome(), EffectTestSupport.housing(level, coords, 3), 60);
		} finally {
			level.players().remove(player);
		}

		int zombies = level.getEntitiesOfClass(Zombie.class, new AABB(coords).inflate(12.0)).size();
		if (zombies == 0) {
			helper.fail("spawn_mob (effect_spawn_zombie) spawned no zombie over 60 activations with a player in range");
			return;
		}
		helper.succeed();
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}
}
