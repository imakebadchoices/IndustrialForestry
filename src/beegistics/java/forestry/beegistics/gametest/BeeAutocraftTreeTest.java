package forestry.beegistics.gametest;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.server.testworld.Plot;
import appeng.server.testworld.PlotBuilder;
import appeng.util.Platform;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.inventory.InventoryApiary;
import forestry.apiculture.items.ItemCreativeHiveFrame;
import forestry.beegistics.BeeKeys;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeMutationPattern;
import forestry.beegistics.crafting.BeeMutation;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.ControllerMode;
import forestry.core.utils.SpeciesUtil;

/**
 * The capstone autocrafting smoke test: a whole multi-level breeding tree resolved by AE2's crafting CPU. It dynamically
 * finds the largest all-unconditional vanilla (forestry) bee mutation tree (up to {@link #MAX_TREE_STEPS} mutations - in
 * practice the five-mutation imperial/industrious/avenging line), descending several levels to base species.
 *
 * <p>Each mutation gets its <b>own dedicated Apiary Controller + apiary</b> on one energized ME grid (whose crafting CPU
 * has co-processing accelerators). Controller <i>i</i> holds only mutation <i>i</i>'s pattern, and its apiary's frame is
 * a creative frame that <b>forces that specific mutation's result</b> ({@link ItemCreativeHiveFrame#setForcedMutation}) -
 * so every apiary breeds its own step deterministically even when the parent pair has several possible results. Only the
 * tree's <em>base</em> species are seeded; a single crafting request for the top species makes AE2 recursively schedule
 * the whole tree across the controllers. Success = the target is delivered to the network, which can only happen if every
 * intermediate was bred from the bases up. Both genders of every intermediate are craftable because the controller offers
 * a princess-primary and a drone-primary variant of each pattern (this would deadlock otherwise - a mutation needs its
 * second parent as a drone). The single-step {@link BeeAutocraftTest} plus this tree prove recursion to any depth.
 *
 * <p>Mutations must be <em>unconditional</em> (see {@link #unconditionalInto}): the forced frame forces the mutation
 * <em>chance</em> but not a mutation's <em>conditions</em> - {@code Bee.getChance} zeroes a condition-blocked mutation
 * before the frame applies.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeAutocraftTreeTest {
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);
	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	// One dedicated controller+apiary per mutation (laid out along +x, apiaries to the north at z=-1). Each apiary's frame
	// forces that step's specific result, so multi-result parent pairs breed deterministically. The drive + crafting CPU
	// sit past the last pair.
	private static BlockPos controllerPos(int i) {
		return new BlockPos(i + 1, 0, 0);
	}

	private static BlockPos apiaryPos(int i) {
		return new BlockPos(i + 1, 0, -1);
	}

	private static BlockPos drivePos(int steps) {
		return new BlockPos(steps + 1, 0, 0);
	}

	private static BlockPos craftingPos(int steps) {
		return new BlockPos(steps + 2, 0, 0);
	}

	// Generous cap: a gametest ends as soon as it succeeds, so a large timeout is free on a normal run and only saves the
	// occasional slow one from a false failure. The whole 5-step tree is dozens of breedings, so its tick count varies.
	/** Quantity requested by the stress variant: AE2 schedules the whole tree this many times over. */
	private static final int STRESS_QUANTITY = 16;

	@GameTest(template = "empty", timeoutTicks = 200000)
	public static void autocraftFullTree(GameTestHelper helper) {
		// The full 5-step avenging tree resolves in well under a second on the unthrottled GameTestServer (dozens of
		// deterministic forced-frame breedings). autocraftFullTreeStress below re-breeds the whole tree many times over.
		runTree(helper, 1);
	}

	@GameTest(template = "empty", timeoutTicks = 2_000_000)
	public static void autocraftFullTreeStress(GameTestHelper helper) {
		// Request the top species {@value #STRESS_QUANTITY} times, so AE2 re-breeds the whole multi-step tree that many times
		// over - sustained forced-mutation breeding + recursive crafting-CPU scheduling. Still finishes in a few seconds on
		// the unthrottled server, so it runs in the normal suite alongside the single-tree case.
		runTree(helper, STRESS_QUANTITY);
	}

	private static void runTree(GameTestHelper helper, int quantity) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		Tree tree = largestUnconditionalTree(beeType);
		IBeeSpecies target = beeType.getSpecies(tree.target());

		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "autocraft_tree"));
		buildPlot(plot, tree);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		AEItemKey targetKey = BeeMutationPattern.canonicalKey(target, BeeLifeStage.PRINCESS);
		IActionSource src = IActionSource.ofMachine((ApiaryControllerBlockEntity) level.getBlockEntity(origin.offset(controllerPos(0))));
		Future<ICraftingPlan>[] planFuture = new Future[1];
		ICraftingPlan[] plan = new ICraftingPlan[1];
		boolean[] submitted = {false};
		long startTime = System.currentTimeMillis();

		helper.startSequence()
			.thenIdle(80)
			.thenExecute(() -> seedBases(level, origin, tree, src, quantity))
			.thenExecute(() -> refreshProviders(level, origin))
			.thenWaitUntil(() -> beginAndSubmit(level, origin, targetKey, src, planFuture, plan, submitted, tree, quantity))
			.thenWaitUntil(() -> {
				MEStorage storage = gridStorage(level, origin);
				if (storage == null || countSpecies(storage, target) < quantity) {
					throw new GameTestAssertException("tree not complete (" + (storage == null ? "?" : countSpecies(storage, target))
							+ "/" + quantity + "): " + diagnose(level, origin, tree));
				}
			})
			.thenExecute(() -> {
				if (quantity > 1) {
					System.out.println("[tree-stress] delivered " + quantity + "x " + tree.target() + " (" + tree.steps().size()
							+ " steps) in " + (System.currentTimeMillis() - startTime) + " ms");
				}
			})
			.thenSucceed();
	}

	// --- tree resolution -----------------------------------------------------------------------------------------

	/** The chosen mutation tree: the ordered breeding steps, the base (leaf) species to seed, and the target species. */
	private record Tree(List<IMutation<IBeeSpecies>> steps, List<ResourceLocation> leaves, ResourceLocation target) {
	}

	/**
	 * Upper bound on tree depth - five is the deepest all-unconditional line in the vanilla registry (imperial /
	 * industrious / avenging). Each top bee is a dozen-plus recursive base breedings, but the forced frames make every
	 * step instant, so the whole tree still resolves in well under a second on the unthrottled GameTestServer.
	 */
	private static final int MAX_TREE_STEPS = 5;

	/** Picks the forestry species with the largest all-unconditional forestry mutation tree (up to {@link #MAX_TREE_STEPS}). */
	private static Tree largestUnconditionalTree(IBeeSpeciesType beeType) {
		Tree best = null;
		for (IBeeSpecies species : beeType.getAllSpecies()) {
			if (!isForestry(species.id())) {
				continue;
			}
			Resolved resolved = resolve(beeType, species, new ArrayList<>());
			if (resolved == null || resolved.steps.isEmpty() || resolved.steps.size() > MAX_TREE_STEPS) {
				continue;
			}
			// Pick the most-step tree; tie-break on species id so the choice is deterministic across runs.
			if (best == null || resolved.steps.size() > best.steps().size()
					|| (resolved.steps.size() == best.steps().size() && species.id().compareTo(best.target()) < 0)) {
				best = new Tree(new ArrayList<>(resolved.steps.values()), new ArrayList<>(resolved.leaves), species.id());
			}
		}
		if (best == null) {
			throw new IllegalStateException("No multi-step unconditional forestry bee tree available");
		}
		return best;
	}

	/** Result of resolving a species: the deduplicated mutation steps (keyed by identity) and the leaf species. */
	private record Resolved(Map<String, IMutation<IBeeSpecies>> steps, List<ResourceLocation> leaves) {
	}

	/** Recursively resolves a species down to base leaves using only unconditional forestry mutations (cycle-guarded). */
	@Nullable
	private static Resolved resolve(IBeeSpeciesType beeType, IBeeSpecies target, List<ResourceLocation> stack) {
		List<IMutation<IBeeSpecies>> into = unconditionalInto(beeType, target);
		if (into.isEmpty()) {
			return new Resolved(new LinkedHashMap<>(), new ArrayList<>(List.of(target.id())));
		}
		if (stack.contains(target.id())) {
			return null; // cycle
		}
		stack.add(target.id());
		try {
			Resolved best = null;
			for (IMutation<IBeeSpecies> m : into) {
				Resolved r1 = resolve(beeType, m.getFirstParent(), stack);
				Resolved r2 = resolve(beeType, m.getSecondParent(), stack);
				if (r1 == null || r2 == null) {
					continue;
				}
				Map<String, IMutation<IBeeSpecies>> steps = new LinkedHashMap<>(r1.steps);
				steps.putAll(r2.steps);
				steps.put(key(m), m);
				List<ResourceLocation> leaves = new ArrayList<>(r1.leaves);
				for (ResourceLocation l : r2.leaves) {
					if (!leaves.contains(l)) {
						leaves.add(l);
					}
				}
				if (best == null || steps.size() < best.steps.size()) {
					best = new Resolved(steps, leaves);
				}
			}
			return best;
		} finally {
			stack.remove(target.id());
		}
	}

	private static List<IMutation<IBeeSpecies>> unconditionalInto(IBeeSpeciesType beeType, IBeeSpecies target) {
		List<IMutation<IBeeSpecies>> out = new ArrayList<>();
		for (IMutation<IBeeSpecies> m : beeType.getMutations().getMutationsInto(target)) {
			// Unconditional only: the forced-mutation frame forces the mutation *chance*, but a mutation's *conditions*
			// (temperature/biome/…) are still enforced by Bee.getChance before the frame applies. Multi-result pairs are
			// fine now - each apiary's frame forces the specific result via ItemCreativeHiveFrame.setForcedMutation.
			if (m.getConditions().isEmpty() && isForestry(m.getFirstParent().id()) && isForestry(m.getSecondParent().id()) && isForestry(m.getResult().id())) {
				out.add(m);
			}
		}
		return out;
	}

	private static boolean isForestry(ResourceLocation id) {
		return id.getNamespace().equals("forestry");
	}

	private static String key(IMutation<IBeeSpecies> m) {
		return m.getFirstParent().id() + "|" + m.getSecondParent().id() + "|" + m.getResult().id();
	}

	// --- plot ----------------------------------------------------------------------------------------------------

	private static void buildPlot(PlotBuilder plot, Tree tree) {
		int steps = tree.steps().size();
		BlockPos drive = drivePos(steps);
		BlockPos crafting = craftingPos(steps);

		plot.creativeEnergyCell(ENERGY);
		for (int x = 0; x <= crafting.getX() + 1; x++) {
			plot.cable(new BlockPos(x, 0, 1));
		}
		// One controller+apiary per mutation.
		for (int i = 0; i < steps; i++) {
			plot.blockState(controllerPos(i), BeegisticsBlocks.APIARY_CONTROLLER.get().defaultBlockState());
			plot.blockState(apiaryPos(i), apiaryState());
		}
		plot.blockEntity(drive, AEBlocks.DRIVE, d -> d.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack()));
		// A 2x2 crafting CPU (one storage + three accelerators) so several breeding steps can run concurrently.
		plot.blockState(crafting, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
		for (BlockPos accel : new BlockPos[]{crafting.east(), crafting.above(), crafting.east().above()}) {
			plot.blockState(accel, AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
		}

		plot.addPostInitAction((level, player, origin) -> {
			// Each controller holds exactly one mutation's pattern, and its apiary's frame forces that mutation's result -
			// so every apiary breeds its own step deterministically, even for parent pairs with several possible results.
			List<IMutation<IBeeSpecies>> mutations = tree.steps();
			for (int i = 0; i < mutations.size(); i++) {
				IMutation<IBeeSpecies> m = mutations.get(i);
				if (level.getBlockEntity(origin.offset(controllerPos(i))) instanceof ApiaryControllerBlockEntity controller) {
					controller.setMode(ControllerMode.AUTOCRAFT); // expose the pattern to AE2's autocrafting
					controller.getInternalInventory().setItemDirect(0, patternStack(m));
				}
				installForcedFrame(level, origin.offset(apiaryPos(i)), m.getResult().id());
			}
		});
	}

	private static void seedBases(ServerLevel level, BlockPos origin, Tree tree, IActionSource src, int quantity) {
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			return;
		}
		// Seed both genders of each base species generously - the tree consumes many of them, and the stress variant
		// re-breeds the whole tree `quantity` times, so scale with it (unchanged 64 for the normal single-target run).
		long seed = Math.max(64, (long) quantity * 64);
		for (ResourceLocation leaf : tree.leaves()) {
			IBeeSpecies species = SpeciesUtil.getBeeSpecies(leaf);
			storage.insert(BeeMutationPattern.canonicalKey(species, BeeLifeStage.PRINCESS), seed, Actionable.MODULATE, src);
			storage.insert(BeeMutationPattern.canonicalKey(species, BeeLifeStage.DRONE), seed, Actionable.MODULATE, src);
		}
	}

	private static void refreshProviders(ServerLevel level, BlockPos origin) {
		int steps = treeStepCount(level, origin);
		for (int i = 0; i < steps; i++) {
			if (level.getBlockEntity(origin.offset(controllerPos(i))) instanceof ApiaryControllerBlockEntity controller) {
				appeng.api.networking.crafting.ICraftingProvider.requestUpdate(controller.getMainNode());
			}
		}
	}

	private static net.minecraft.world.level.block.state.BlockState apiaryState() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState();
	}

	/** Installs a creative frame that forces the given mutation result, so this apiary always breeds that species. */
	private static void installForcedFrame(ServerLevel level, BlockPos apiaryPos, ResourceLocation result) {
		if (level.getBlockEntity(apiaryPos) instanceof Container apiary) {
			ItemStack frame = ApicultureItems.FRAME_CREATIVE.stack();
			ItemCreativeHiveFrame.setForcedMutation(frame, result);
			apiary.setItem(InventoryApiary.SLOT_FRAMES_1, frame);
		}
	}

	/** @return how many controllers were placed (walks the +x line until there is no controller), i.e. the tree size. */
	private static int treeStepCount(ServerLevel level, BlockPos origin) {
		int steps = 0;
		while (level.getBlockEntity(origin.offset(controllerPos(steps))) instanceof ApiaryControllerBlockEntity) {
			steps++;
		}
		return steps;
	}

	private static ItemStack patternStack(IMutation<IBeeSpecies> m) {
		ItemStack stack = new ItemStack(BeegisticsItems.beeMutationPattern());
		ItemBeeMutationPattern.setMutation(stack, new BeeMutation(m.getFirstParent().id(), m.getSecondParent().id(), m.getResult().id()));
		return stack;
	}

	// --- job driving ---------------------------------------------------------------------------------------------

	private static void beginAndSubmit(ServerLevel level, BlockPos origin, AEItemKey targetKey, IActionSource src,
			Future<ICraftingPlan>[] planFuture, ICraftingPlan[] plan, boolean[] submitted, Tree tree, int quantity) {
		IGrid grid = grid(level, origin);
		if (grid == null) {
			throw new GameTestAssertException("grid not ready");
		}
		ICraftingService crafting = grid.getCraftingService();
		if (planFuture[0] == null) {
			ICraftingSimulationRequester simRequester = () -> src;
			planFuture[0] = crafting.beginCraftingCalculation(level, simRequester, targetKey, quantity, CalculationStrategy.REPORT_MISSING_ITEMS);
		}
		if (plan[0] == null) {
			try {
				plan[0] = planFuture[0].get(0, TimeUnit.MILLISECONDS);
			} catch (TimeoutException e) {
				throw new GameTestAssertException("crafting plan still calculating");
			} catch (InterruptedException | ExecutionException e) {
				throw new GameTestAssertException("crafting plan failed: " + e);
			}
		}
		if (plan[0].simulation()) {
			StringBuilder missing = new StringBuilder();
			for (var e : plan[0].missingItems()) {
				missing.append(e.getKey()).append("x").append(e.getLongValue()).append(" ");
			}
			StringBuilder leaves = new StringBuilder();
			MEStorage storage = gridStorage(level, origin);
			KeyCounter all = new KeyCounter();
			if (storage != null) {
				storage.getAvailableStacks(all);
			}
			for (ResourceLocation leaf : tree.leaves()) {
				leaves.append(leaf.getPath()).append("=").append(count(all, SpeciesUtil.getBeeSpecies(leaf))).append(" ");
			}
			planFuture[0] = null;
			plan[0] = null;
			throw new GameTestAssertException("plan is a simulation: missing=[" + missing + "] leaves=[" + leaves + "] " + diagnose(level, origin, tree));
		}
		if (!submitted[0]) {
			if (!crafting.submitJob(plan[0], null, null, true, src).successful()) {
				throw new GameTestAssertException("failed to submit tree crafting job");
			}
			submitted[0] = true;
		}
	}

	// --- assertions ----------------------------------------------------------------------------------------------

	@Nullable
	private static IGrid grid(ServerLevel level, BlockPos origin) {
		return level.getBlockEntity(origin.offset(controllerPos(0))) instanceof ApiaryControllerBlockEntity c ? c.getMainNode().getGrid() : null;
	}

	@Nullable
	private static MEStorage gridStorage(ServerLevel level, BlockPos origin) {
		IGrid grid = grid(level, origin);
		return grid == null ? null : grid.getStorageService().getInventory();
	}

	/** @return how many bees of the given species are in the network (any life stage). */
	private static long countSpecies(MEStorage storage, IBeeSpecies species) {
		KeyCounter all = new KeyCounter();
		storage.getAvailableStacks(all);
		return count(all, species);
	}

	private static String diagnose(ServerLevel level, BlockPos origin, Tree tree) {
		StringBuilder sb = new StringBuilder("target=").append(tree.target()).append(" steps=").append(tree.steps().size());
		MEStorage storage = gridStorage(level, origin);
		if (storage != null) {
			KeyCounter all = new KeyCounter();
			storage.getAvailableStacks(all);
			sb.append(" net[");
			for (IMutation<IBeeSpecies> m : tree.steps()) {
				sb.append(m.getResult().id().getPath()).append("=").append(count(all, m.getResult())).append(" ");
			}
			sb.append("]");
		}
		int steps = treeStepCount(level, origin);
		for (int i = 0; i < steps; i++) {
			if (level.getBlockEntity(origin.offset(controllerPos(i))) instanceof ApiaryControllerBlockEntity c) {
				sb.append(" ctrl").append(i).append("[busy=").append(c.isBusy())
						.append(" job=").append(c.getCraftResult() == null ? "-" : c.getCraftResult().getPath());
				if (level.getBlockEntity(origin.offset(apiaryPos(i))) instanceof Container apiary) {
					sb.append(" q=").append(slot(apiary.getItem(0))).append(" d=").append(slot(apiary.getItem(1)));
				}
				sb.append("]");
			}
		}
		return sb.toString();
	}

	private static String slot(ItemStack stack) {
		if (stack.isEmpty()) {
			return "empty";
		}
		IIndividual ind = IIndividualHandlerItem.getIndividual(stack);
		return ind == null ? "?" : ind.getSpecies().id().getPath() + "/" + IIndividualHandlerItem.getLifeStage(stack);
	}

	private static long count(KeyCounter contents, IBeeSpecies species) {
		long total = 0;
		for (var entry : contents) {
			if (entry.getKey() instanceof AEItemKey key && BeeKeys.isBee(key)) {
				IIndividual individual = IIndividualHandlerItem.getIndividual(key.getReadOnlyStack());
				if (individual != null && individual.getSpecies().id().equals(species.id())) {
					total += entry.getLongValue();
				}
			}
		}
		return total;
	}

	private BeeAutocraftTreeTest() {
	}
}
