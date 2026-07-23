package forestry.beegistics.gametest;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
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
import appeng.api.networking.crafting.ICraftingProvider;
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
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationManager;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.apiculture.InventoryBeeHousing;
import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.inventory.InventoryApiary;
import forestry.apiculture.items.ItemCreativeHiveFrame;
import forestry.beegistics.BeeCellTier;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.ControllerMode;
import forestry.core.utils.SpeciesUtil;

/**
 * The full-tree "player test": from a drive whose only bee stock is the wild <b>root</b> species (produced by no
 * mutation), the Apiary Controller autocrafts an optimised, min-maxed princess for a terminal <b>leaf</b> species. This
 * exercises the whole pipeline end to end: AE2 breeds the leaf's chassis and its trait donor <em>up from the wild
 * roots</em> via the offered species closure (deterministic under the controller's frame-forcing hack), then the
 * min-max target is fabricated - all without the player stocking anything but wild bees.
 *
 * <ul>
 *     <li>{@link #planFullTree} - a non-breeding diagnostic that enumerates the tree and prints the min-max plan.</li>
 *     <li>{@link #breedDeepestLeafFromRoots} - the explicit worst-case: the deepest breedable leaf, bred from roots.</li>
 *     <li>{@link BeeLeafFromRootsTests} - generates one <b>isolated</b> test per breedable leaf (its own grid, so leaves
 *     never contend), each calling {@link #runLeafFromRoots}. Isolation is what makes every leaf reachable reliably.</li>
 * </ul>
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeFullTreeMinMaxTest {
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);
	private static final BlockPos CONTROLLER = new BlockPos(1, 0, 0);
	private static final BlockPos DRIVE = new BlockPos(2, 0, 0);
	/** Low-priority stock cells for breeding byproducts (honey/combs) - bee cells reject non-bees and would jam the apiary. */
	private static final BlockPos STOCK_DRIVE = new BlockPos(3, 0, 0);
	private static final BlockPos CRAFTING = new BlockPos(4, 0, 0);
	/** The min-max pool: a plain creative frame (introgression preserves the chassis species). */
	private static final BlockPos APIARY_PLAIN = new BlockPos(1, 0, -1);
	/** The creation pool: a force-mutation creative frame (breeds the species mutations up from the roots). */
	private static final BlockPos APIARY_FORCE = new BlockPos(1, 1, 0);
	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	/** How many traits to min-max per leaf. Kept small so multi-trait fixation converges well inside the watchdog budget. */
	private static final int PIN_CAP = 1;

	/** A terminal leaf, the min-max target genome (species + the best donor's traits), and its pin count. */
	record MinMax(IBeeSpecies leaf, IGenome target, int pins) {
	}

	// --- diagnostic ----------------------------------------------------------------------------------------------

	@GameTest(template = "empty")
	public static void planFullTree(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		List<IBeeSpecies> roots = roots(beeType);
		List<IBeeSpecies> terminals = terminals(beeType);

		int pinned = 0;
		int maxPins = 0;
		for (IBeeSpecies leaf : terminals) {
			MinMax mm = bestMinMax(beeType, leaf, Integer.MAX_VALUE);
			if (mm.pins() > 0) {
				pinned++;
				maxPins = Math.max(maxPins, mm.pins());
			}
		}
		int breedable = breedableMinMaxes(beeType, PIN_CAP).size();
		System.out.println("[full-tree] species=" + beeType.getAllSpecies().size() + " roots=" + roots.size()
				+ " terminals=" + terminals.size() + " with-reachable-cross=" + pinned + " maxPins(uncapped)=" + maxPins
				+ " breedable-from-roots(dominant+unconditional, pinCap=" + PIN_CAP + ")=" + breedable);

		helper.assertTrue(!roots.isEmpty(), "the bee tree must have wild roots");
		helper.assertTrue(!terminals.isEmpty(), "the bee tree must have terminal leaves");
		// The per-leaf isolation tests are generated as a fixed number of indexed slots (the generator runs before the bee
		// registry loads, so it cannot count them); guard that the real leaf count still fits, or those past the cap go untested.
		helper.assertTrue(breedable <= BeeLeafFromRootsTests.MAX_LEAVES,
				"breedable leaves (" + breedable + ") exceed BeeLeafFromRootsTests.MAX_LEAVES (" + BeeLeafFromRootsTests.MAX_LEAVES + ") - raise it");
		helper.succeed();
	}

	// --- each leaf, min-maxed straight from the wild roots, in ISOLATION -----------------------------------------

	/**
	 * The explicit deepest-leaf proof (the {@code emerald}-style test): pick the breedable leaf with the largest ancestor
	 * closure and drive it from wild roots to a min-maxed princess. This stresses the from-roots species autocraft the
	 * hardest; {@link #everyLeafFromRoots} then proves every other reachable leaf the same way, each in its own isolation.
	 */
	@GameTest(template = "empty", timeoutTicks = 400_000)
	public static void breedDeepestLeafFromRoots(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		MinMax mm = breedableMinMaxes(beeType, PIN_CAP).stream()
				.max(Comparator.comparingInt((MinMax m) -> BeeGenomeMutationPattern.ancestorClosure(List.of(m.leaf(), resolvedDonor(m))).size()))
				.orElseThrow(() -> new IllegalStateException("no breedable min-max leaf found"));
		helper.assertTrue(mm.pins() > 0, "the chosen leaf should have at least one reachable min-max trait");
		runLeafFromRoots(helper, mm);
	}

	/** @return one {@link MinMax} per leaf reachable/min-maxable from wild roots; the generator emits one isolated test each. */
	static List<MinMax> breedableLeaves() {
		return breedableMinMaxes(SpeciesUtil.BEE_TYPE.get(), PIN_CAP);
	}

	/**
	 * Drives one leaf from a wild-roots-only drive to its min-maxed princess: AE2 autocrafts the leaf's chassis and trait
	 * donor up from the roots (deterministic under frame-forcing), then the controller fabricates the pinned target
	 * directly (the hack) - so the whole species tree is exercised without the stochastic hill-climb - and we assert the
	 * exact target genome is delivered. Runs against whatever isolated structure the caller's {@code helper} owns.
	 */
	static void runLeafFromRoots(GameTestHelper helper, MinMax mm) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "leaf_" + mm.leaf().id().getPath()));
		buildPlot(plot, mm);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		AEItemKey targetKey = BeeMutationPattern.canonicalKey(mm.leaf(), mm.target(), BeeLifeStage.PRINCESS);
		ApiaryControllerBlockEntity controllerBe = (ApiaryControllerBlockEntity) level.getBlockEntity(origin.offset(CONTROLLER));
		IActionSource src = IActionSource.ofMachine(controllerBe);
		Future<ICraftingPlan>[] planFuture = new Future[1];
		ICraftingPlan[] plan = new ICraftingPlan[1];
		boolean[] submitted = {false};
		long start = System.currentTimeMillis();
		IBeeSpecies donor = BeeGenomeMutationPattern.resolveDonor(mm.target()).orElseThrow();
		long[] lastLog = {0};
		System.out.println("[full-tree] " + mm.leaf().id().getPath() + " (pins=" + mm.pins() + ", donor=" + donor.id().getPath()
				+ ", closure=" + BeeGenomeMutationPattern.ancestorClosure(List.of(mm.leaf(), donor)).size() + ") from roots...");

		helper.startSequence()
			.thenIdle(80)
			.thenExecute(() -> seedRoots(level, origin, beeType, src))
			.thenExecute(() -> refreshProvider(level, origin))
			.thenWaitUntil(() -> beginAndSubmit(level, origin, targetKey, 1, src, planFuture, plan, submitted, mm))
			.thenWaitUntil(() -> {
				MEStorage storage = gridStorage(level, origin);
				if (storage != null && System.currentTimeMillis() - lastLog[0] > 8000) {
					lastLog[0] = System.currentTimeMillis();
					System.out.println("[full-tree]   " + mm.leaf().id().getPath() + " @ " + (System.currentTimeMillis() - start) + " ms " + diagnose(level, origin, mm, targetKey));
				}
				if (storage == null || !networkHasTarget(storage, mm.target())) {
					throw new GameTestAssertException("min-maxed " + mm.leaf().id().getPath() + " not delivered yet: " + diagnose(level, origin, mm, targetKey));
				}
			})
			.thenExecute(() -> System.out.println("[full-tree] delivered " + mm.leaf().id().getPath() + " in " + (System.currentTimeMillis() - start) + " ms"))
			.thenSucceed();
	}

	private static IBeeSpecies resolvedDonor(MinMax mm) {
		return BeeGenomeMutationPattern.resolveDonor(mm.target()).orElse(mm.leaf());
	}


	// --- job driving (mirrors the genome autocraft tests) --------------------------------------------------------

	private static void beginAndSubmit(ServerLevel level, BlockPos origin, AEItemKey targetKey, long amount, IActionSource src,
			Future<ICraftingPlan>[] planFuture, ICraftingPlan[] plan, boolean[] submitted, MinMax mm) {
		IGrid grid = grid(level, origin);
		if (grid == null) {
			throw new GameTestAssertException("grid not ready");
		}
		ICraftingService crafting = grid.getCraftingService();
		if (planFuture[0] == null) {
			ICraftingSimulationRequester simRequester = () -> src;
			planFuture[0] = crafting.beginCraftingCalculation(level, simRequester, targetKey, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
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
			planFuture[0] = null;
			plan[0] = null;
			throw new GameTestAssertException("crafting plan is a simulation (missing items): " + diagnose(level, origin, mm, targetKey));
		}
		if (!submitted[0]) {
			if (!crafting.submitJob(plan[0], null, null, true, src).successful()) {
				throw new GameTestAssertException("failed to submit crafting job");
			}
			submitted[0] = true;
		}
	}

	private static void refreshProvider(ServerLevel level, BlockPos origin) {
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
			ICraftingProvider.requestUpdate(controller.getMainNode());
		}
	}

	/** How many of each wild root to keep stocked - high, so several deep trees can be PLANNED concurrently (a running
	 *  job reserves a big chunk of roots, and a plan that can't reserve enough resolves to an unsatisfiable simulation). */
	private static final int ROOT_STOCK = 256;

	/** Tops every wild root back up to {@link #ROOT_STOCK} princesses and drones - the only bee stock the network gets. */
	private static void seedRoots(ServerLevel level, BlockPos origin, IBeeSpeciesType beeType, IActionSource src) {
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			return;
		}
		KeyCounter snap = new KeyCounter();
		storage.getAvailableStacks(snap);
		for (IBeeSpecies root : roots(beeType)) {
			for (BeeLifeStage stage : new BeeLifeStage[]{BeeLifeStage.PRINCESS, BeeLifeStage.DRONE}) {
				AEItemKey key = BeeMutationPattern.canonicalKey(root, stage);
				long deficit = ROOT_STOCK - snap.get(key);
				if (deficit > 0) {
					storage.insert(key, deficit, Actionable.MODULATE, src);
				}
			}
		}
	}

	// --- plot ----------------------------------------------------------------------------------------------------

	private static void buildPlot(PlotBuilder plot, MinMax mm) {
		plot.creativeEnergyCell(ENERGY);
		BeegisticsGridTests.forceInfiniteChannels(plot, ENERGY);
		for (int x = 0; x <= 4; x++) {
			plot.cable(new BlockPos(x, 0, 1));
		}

		plot.blockState(CONTROLLER, BeegisticsBlocks.APIARY_CONTROLLER.get().defaultBlockState());
		// Bee cells (no 63-type cap) hold the many distinct bred genomes; a high priority keeps bees here, not in the stock
		// drive. A separate low-priority stock drive catches honey/combs (bee cells reject them, which would jam the apiary).
		plot.blockEntity(DRIVE, AEBlocks.DRIVE, drive -> {
			drive.setPriority(10);
			for (int c = 0; c < 8; c++) {
				drive.getInternalInventory().addItems(new ItemStack(BeegisticsItems.beeCell(BeeCellTier.T32K)));
			}
		});
		plot.blockEntity(STOCK_DRIVE, AEBlocks.DRIVE, drive -> {
			drive.setPriority(0);
			for (int c = 0; c < 8; c++) {
				drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
			}
		});
		plot.blockState(CRAFTING, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
		plot.blockState(APIARY_PLAIN, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState());
		plot.blockState(APIARY_FORCE, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState());

		plot.addPostInitAction((level, player, origin) -> {
			if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
				controller.setMode(ControllerMode.AUTOCRAFT);
				controller.setForceMutationHack(true); // deep species trees on one controller need deterministic mutations
				controller.getInternalInventory().setItemDirect(0, genomeCard(mm));
			}
			// Min-max pool: a plain creative frame - trait introgression that keeps the chassis species.
			if (level.getBlockEntity(origin.offset(APIARY_PLAIN)) instanceof Container apiary) {
				apiary.setItem(InventoryApiary.SLOT_FRAMES_1, ApicultureItems.FRAME_CREATIVE.stack());
			}
			// Creation pool: a force-mutation creative frame - breeds each species mutation up from the wild roots.
			if (level.getBlockEntity(origin.offset(APIARY_FORCE)) instanceof Container apiary) {
				apiary.setItem(InventoryApiary.SLOT_FRAMES_1, forceMutationFrame());
			}
		});
	}

	/** A creative frame that forces a random one of the staged pair's mutations to 100% (the species-creation pool). */
	private static ItemStack forceMutationFrame() {
		ItemStack frame = ApicultureItems.FRAME_CREATIVE.stack();
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ItemCreativeHiveFrame.NBT_FORCE_MUTATIONS, true);
		frame.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return frame;
	}

	/** A Bee Pattern card carrying the min-max target (leaf species + pinned traits); the controller resolves the donor. */
	private static ItemStack genomeCard(MinMax mm) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(mm.leaf().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(mm.target()), Set.of(),
			BeeGenomeMutationPattern.pinnedChromosomes(mm.target()));
		ItemBeeFilterCard.setFilter(card, filter);
		return card;
	}

	// --- tree enumeration + min-max planning ---------------------------------------------------------------------

	/** @return the wild species: those produced by no mutation. */
	static List<IBeeSpecies> roots(IBeeSpeciesType beeType) {
		IMutationManager<IBeeSpecies> mgr = beeType.getMutations();
		List<IBeeSpecies> out = new ArrayList<>();
		for (IBeeSpecies s : beeType.getAllSpecies()) {
			if (mgr.getMutationsInto(s).isEmpty()) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparing(s -> s.id().toString()));
		return out;
	}

	/** @return the terminal leaves: bred species (not wild) that are themselves a parent of no mutation. */
	static List<IBeeSpecies> terminals(IBeeSpeciesType beeType) {
		IMutationManager<IBeeSpecies> mgr = beeType.getMutations();
		List<IBeeSpecies> out = new ArrayList<>();
		for (IBeeSpecies s : beeType.getAllSpecies()) {
			if (mgr.getMutationsFrom(s).isEmpty() && !mgr.getMutationsInto(s).isEmpty()) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparing(s -> s.id().toString()));
		return out;
	}

	/**
	 * @return one {@link MinMax} per terminal leaf the current engine can reliably breed <em>from the wild roots in a
	 * plain test apiary</em>: the leaf must be <b>dominant</b> (so introgression hybrids express the chassis and stay
	 * selectable), have a reachable capped min-max cross, and have its whole ancestor closure (chassis + donor) be
	 * <b>unconditional</b> (no climate/biome-gated mutation the default-biome apiary cannot satisfy).
	 */
	static List<MinMax> breedableMinMaxes(IBeeSpeciesType beeType, int pinCap) {
		List<MinMax> out = new ArrayList<>();
		for (IBeeSpecies leaf : terminals(beeType)) {
			if (!leaf.isDominant()) {
				continue;
			}
			MinMax mm = bestMinMax(beeType, leaf, pinCap);
			if (mm.pins() == 0) {
				continue;
			}
			IBeeSpecies donor = BeeGenomeMutationPattern.resolveDonor(mm.target()).orElse(null);
			if (donor == null || !closureUnconditional(beeType, List.of(leaf, donor))) {
				continue;
			}
			out.add(mm);
		}
		return out;
	}

	/** @return whether every mutation on any breeding path from the roots to {@code targets} is unconditional. */
	static boolean closureUnconditional(IBeeSpeciesType beeType, List<IBeeSpecies> targets) {
		for (IMutation<IBeeSpecies> m : BeeGenomeMutationPattern.ancestorClosure(targets)) {
			if (!m.getConditions().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return the richest reachable min-max target for {@code leaf}, capped at {@code pinCap} traits: pick the recessive,
	 * non-combining donor that differs from the leaf on the most trait chromosomes, then pin (up to {@code pinCap} of)
	 * those chromosomes homozygous to the donor's values. Falls back to the pure leaf (zero pins) when no donor exists.
	 */
	static MinMax bestMinMax(IBeeSpeciesType beeType, IBeeSpecies leaf, int pinCap) {
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		IGenome leafGenome = leaf.getDefaultGenome();
		IMutationManager<IBeeSpecies> mgr = beeType.getMutations();

		IBeeSpecies bestDonor = null;
		List<IChromosome<?>> bestDiff = List.of();
		List<IBeeSpecies> donors = new ArrayList<>(beeType.getAllSpecies());
		donors.sort(Comparator.comparing(s -> s.id().toString())); // deterministic tie-break so the plan is stable across runs
		for (IBeeSpecies donor : donors) {
			if (donor == leaf || donor.isDominant() || !mgr.getCombinations(leaf, donor).isEmpty()) {
				continue;
			}
			IGenome donorGenome = donor.getDefaultGenome();
			List<IChromosome<?>> diff = new ArrayList<>();
			for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
				if (chromosome == speciesChromosome) {
					continue;
				}
				if (!Objects.equals(leafGenome.getActiveValue(cast(chromosome)), donorGenome.getActiveValue(cast(chromosome)))) {
					diff.add(chromosome);
				}
			}
			if (diff.size() > bestDiff.size()) {
				bestDiff = diff;
				bestDonor = donor;
			}
		}
		if (bestDonor == null || bestDiff.isEmpty()) {
			return new MinMax(leaf, leafGenome, 0);
		}
		// Deterministic subset (sorted by chromosome id), capped: still covered by the same donor, so it stays reachable.
		bestDiff.sort(Comparator.comparing(c -> c.id().toString()));
		IGenome donorGenome = bestDonor.getDefaultGenome();
		Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
		for (IChromosome<?> chromosome : bestDiff) {
			if (overrides.size() >= pinCap) {
				break;
			}
			overrides.put(chromosome, Allele.of(donorGenome.getActiveValue(cast(chromosome)), true));
		}
		IGenome target = leafGenome.copyWith(overrides);
		if (BeeGenomeMutationPattern.resolveDonor(target).isEmpty()) {
			return new MinMax(leaf, leafGenome, 0);
		}
		return new MinMax(leaf, target, overrides.size());
	}

	@SuppressWarnings("unchecked")
	private static IChromosome<Object> cast(IChromosome<?> chromosome) {
		return (IChromosome<Object>) chromosome;
	}

	// --- grid helpers --------------------------------------------------------------------------------------------

	@Nullable
	private static IGrid grid(ServerLevel level, BlockPos origin) {
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
			return controller.getMainNode().getGrid();
		}
		return null;
	}

	@Nullable
	private static MEStorage gridStorage(ServerLevel level, BlockPos origin) {
		IGrid grid = grid(level, origin);
		return grid == null ? null : grid.getStorageService().getInventory();
	}

	private static boolean networkHasTarget(MEStorage storage, IGenome target) {
		KeyCounter all = new KeyCounter();
		storage.getAvailableStacks(all);
		for (var entry : all) {
			if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey key)) {
				continue;
			}
			var individual = IIndividualHandlerItem.getIndividual(key.getReadOnlyStack());
			if (individual != null && individual.getGenome().isSameAlleles(target)
					&& IIndividualHandlerItem.getLifeStage(key.getReadOnlyStack()) == BeeLifeStage.PRINCESS) {
				return true;
			}
		}
		return false;
	}

	private static String diagnose(ServerLevel level, BlockPos origin, MinMax mm, AEItemKey targetKey) {
		IBeeSpecies donor = BeeGenomeMutationPattern.resolveDonor(mm.target()).orElse(mm.leaf());
		StringBuilder sb = new StringBuilder("leaf=").append(mm.leaf().id().getPath()).append(" pins=").append(mm.pins());
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity c) {
			sb.append(" apiary=").append(c.isApiaryConnected()).append(" busy=").append(c.isBusy())
					.append(" job=").append(c.getCraftResult() == null ? "-" : c.getCraftResult().getPath())
					.append(" patterns=").append(c.getAvailablePatterns().size())
					.append(" score=").append(c.getCraftBestScore()).append('/').append(c.getCraftMaxScore())
					.append(" idle=").append(c.getCraftIdleCycles());
		}
		MEStorage storage = gridStorage(level, origin);
		if (storage != null) {
			KeyCounter all = new KeyCounter();
			storage.getAvailableStacks(all);
			BeeFilter score = BeeGenomeMutationPattern.scoreFilter(mm.target());
			int chassisP = 0, chassisD = 0, donors = 0, bestP = 0, bestD = 0;
			for (var entry : all) {
				if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey key)) {
					continue;
				}
				var ind = IIndividualHandlerItem.getIndividual(key.getReadOnlyStack());
				if (ind == null) {
					continue;
				}
				ResourceLocation sp = ind.getSpecies().id();
				if (sp.equals(mm.leaf().id())) {
					int s = ApiaryControllerBlockEntity.scoreTowardFilter(ind.getGenome(), score);
					if (IIndividualHandlerItem.getLifeStage(key.getReadOnlyStack()) == BeeLifeStage.PRINCESS) {
						chassisP += (int) entry.getLongValue();
						bestP = Math.max(bestP, s);
					} else {
						chassisD += (int) entry.getLongValue();
						bestD = Math.max(bestD, s);
					}
				} else if (sp.equals(donor.id())) {
					donors += (int) entry.getLongValue();
				}
			}
			sb.append(" netKeys=").append(all.size()).append(" targetInNet=").append(all.get(targetKey))
					.append(" chassisPrincess=").append(chassisP).append("(best").append(bestP).append(")")
					.append(" chassisDrone=").append(chassisD).append("(best").append(bestD).append(")")
					.append(" donor=").append(donors).append("/").append(2 * mm.pins());
		}
		if (level.getBlockEntity(origin.offset(APIARY_PLAIN)) instanceof net.minecraft.world.Container plain) {
			sb.append(" plain[q=").append(describe(plain.getItem(0))).append(" d=").append(describe(plain.getItem(1)));
			int prod = 0;
			for (int s = InventoryBeeHousing.SLOT_PRODUCT_1; s < InventoryBeeHousing.SLOT_PRODUCT_1 + InventoryBeeHousing.SLOT_PRODUCT_COUNT; s++) {
				if (!plain.getItem(s).isEmpty()) {
					prod++;
				}
			}
			sb.append(" prod=").append(prod).append("]");
		}
		return sb.toString();
	}

	private static String describe(ItemStack stack) {
		if (stack.isEmpty()) {
			return "-";
		}
		var ind = IIndividualHandlerItem.getIndividual(stack);
		return ind == null ? stack.getItem().toString() : ind.getSpecies().id().getPath() + "/" + IIndividualHandlerItem.getLifeStage(stack);
	}

	private BeeFullTreeMinMaxTest() {
	}
}
