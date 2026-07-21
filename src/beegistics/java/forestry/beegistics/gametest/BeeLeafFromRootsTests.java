package forestry.beegistics.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

import forestry.beegistics.Beegistics;
import forestry.beegistics.gametest.BeeFullTreeMinMaxTest.MinMax;

/**
 * Emits one <b>isolated</b> GameTest per breedable leaf: each runs {@link BeeFullTreeMinMaxTest#runLeafFromRoots} in its
 * <em>own</em> structure - its own grid, roots, controller and apiaries - so leaves never contend over shared roots,
 * CPUs or storage (the contention that made a single shared 17-leaf network unworkable). The framework can still run
 * them concurrently as a batch; isolation, not serialization, is what removes the fight.
 *
 * <p>NeoForge's {@code @GameTestHolder} scan only registers {@code @GameTest} methods, not {@code @GameTestGenerator}, so
 * - exactly like AE2's plot adapter - the generator lives in its own class registered explicitly through
 * {@link RegisterGameTestsEvent} (here via a mod-bus {@link EventBusSubscriber}). This class is not a
 * {@code @GameTestHolder}, so its tests are registered once (through the generator), never double-counted. It ships only
 * with the gametest sources, which are excluded from the jar.
 */
@GameTestHolder(Beegistics.NAMESPACE) // so the generator's tests resolve to the (enabled) beegistics namespace, not filtered out
@EventBusSubscriber(modid = Beegistics.NAMESPACE, bus = EventBusSubscriber.Bus.MOD)
public final class BeeLeafFromRootsTests {
	/**
	 * An upper bound on the number of breedable leaves. The generator runs at mod-registration time - BEFORE the bee
	 * datapack registry is loaded - so it cannot enumerate the leaves; instead it emits this many INDEXED tests, and each
	 * resolves its own leaf from the (by-then-loaded) registry at run time. Indices past the actual breedable count are
	 * trivial no-ops, so this just needs to be comfortably above the real leaf count (~17 for base Forestry).
	 */
	static final int MAX_LEAVES = 48;

	@SubscribeEvent
	static void onRegisterGameTests(RegisterGameTestsEvent event) {
		event.register(BeeLeafFromRootsTests.class);
	}

	@GameTestGenerator
	public static List<TestFunction> everyLeafFromRoots() {
		List<TestFunction> tests = new ArrayList<>();
		for (int i = 0; i < MAX_LEAVES; i++) {
			int index = i;
			tests.add(new TestFunction(
					"beegistics",
					String.format("beegistics.leaf_%02d", i),
					"beegistics:empty",
					400_000,
					0L,
					true,
					helper -> {
						List<MinMax> leaves = BeeFullTreeMinMaxTest.breedableLeaves();
						if (index >= leaves.size()) {
							helper.succeed(); // padding index beyond the actual breedable count
						} else {
							BeeFullTreeMinMaxTest.runLeafFromRoots(helper, leaves.get(index));
						}
					}));
		}
		return tests;
	}

	private BeeLeafFromRootsTests() {
	}
}
