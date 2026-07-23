package forestry.beegistics.gametest;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
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
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.server.testworld.Plot;
import appeng.server.testworld.PlotBuilder;
import appeng.util.Platform;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.inventory.InventoryApiary;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeeCellTier;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.ControllerMode;
import forestry.core.utils.SpeciesUtil;

/**
 * The genome-autocrafting <em>stress</em> test: many distinct arbitrary-homozygous genomes bred at once - one dedicated
 * Apiary Controller + apiary per genome, all on one ME grid - exercising the scoring hill-climb, the per-breeding-tick
 * full-network snapshot ({@code getAvailableStacks}) and the AE2 crafting scheduler concurrently. Two entrypoints share one
 * rig, both quick enough for the normal {@code runGameTestServer} suite:
 *
 * <ul>
 *   <li>{@link #autocraftManyGenomes} (the {@link #DISTINCT_DONORS} config): 12 genomes with distinct chassis and distinct
 *       donors across 6 CPUs, so each genome's breeding population is fully isolated - the clean happy path.</li>
 *   <li>{@link #autocraftManyGenomesSharedDonors} (the {@link #SHARED_DONORS} config): more genomes, and by dropping the
 *       distinct-donor rule several of them <em>share a donor line</em>, so their breeding populations contend for drones on
 *       the one shared ME network - a busier, more player-like setup that catches integration bugs the isolated rig would
 *       not (the contention that commit a5fa6856c once de-artifacted, now handled).</li>
 * </ul>
 *
 * <p>Each breeds every genome to its target once and stops. Each genome is a distinct <em>dominant chassis</em> line made homozygous for one trait its <em>recessive</em>,
 * mutation-free donor carries (a live introgression, exactly like the single-genome {@link BeeGenomeAutocraftLiveTest}).
 * The chassis species are all distinct, so each controller's active-species filter keeps its breeding population isolated on
 * the shared network. A plain creative frame per apiary makes each cycle instant. A genome is <em>delivered</em> when its
 * exact homozygous target princess (the canonical AE2 output) reaches the network; every genome is reachable by
 * construction, so the floor is that all of them deliver.
 *
 * <p><b>Storage must be bee cells.</b> A stock AE2 item cell caps at 63 distinct <em>types</em>, and this many concurrent
 * introgressions breed well over that (~120 distinct genomes). On a stock cell the network fills to 63 types and then
 * rejects every new one - bred carriers and even finished target princesses can no longer be inserted, so they jam in the
 * apiary product slots and no genome ever delivers. The byte-budgeted bee cell has no type cap (and charges bytes per
 * distinct genome, not per count, so ordering many copies never fills it) - exactly what this workload, and any real
 * player breeding many genomes, needs. This was the true cause of the historical "genomes never converge" strand: the
 * diploid hill-climb was fine all along; storage type exhaustion was silently jamming delivery.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeGenomeAutocraftStressTest {
	/**
	 * One stress run's shape. Both runs breed every genome to its target once and then stop (a genome autocraft delivers its
	 * canonical target out-of-band and only ever completes a single copy, so this is the natural unit). {@code soakMillis} is
	 * a wall-clock safety cap; each run actually finishes in ~2 s.
	 */
	private record StressConfig(String label, int genomeCap, int cpus, boolean distinctDonors, long soakMillis) {
	}

	/** Pure parents of each line stocked into the network - plenty for a one-copy hill-climb that never starves. */
	private static final int SEED_COUNT = 64;

	/** Isolated happy path: up to 12 genomes with distinct chassis AND distinct donors across 6 CPUs (no donor contention). */
	private static final StressConfig DISTINCT_DONORS = new StressConfig("distinct-donors", 12, 6, true, 60_000);
	/** Contended path: more genomes with donors allowed to repeat, so several breeding populations share a donor line. */
	private static final StressConfig SHARED_DONORS = new StressConfig("shared-donors", 16, 6, false, 60_000);

	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	// Layout mirrors the proven BeeAutocraftTreeTest: a creative energy cell at the origin corner, controllers along +x
	// (apiaries to the north at z=-1) attached to a z=1 cable backbone, and the drive + primary crafting CPU inline on the
	// z=0 row past the last controller. Extra single-block crafting CPUs sit spaced-two-apart at z=2 (each its own cluster)
	// to let several genomes breed at once.
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);

	private static BlockPos controllerPos(int i) {
		return new BlockPos(i + 1, 0, 0);
	}

	private static BlockPos apiaryPos(int i) {
		return new BlockPos(i + 1, 0, -1);
	}

	private static BlockPos drivePos(int n) {
		return new BlockPos(n + 1, 0, 0);
	}

	private static BlockPos primaryCpuPos(int n) {
		return new BlockPos(n + 2, 0, 0);
	}

	/** Extra isolated CPUs at z=2, spaced two apart (j >= 1, since CPU 0 is the inline primary). */
	private static BlockPos extraCpuPos(int j) {
		return new BlockPos(2 * j, 0, 2);
	}

	/** A dominant chassis line, a mutation-free recessive donor carrying one differing trait, and the homozygous target. */
	private record Intro(IBeeSpecies base, IBeeSpecies donor, IGenome target) {
	}

	// Generous tick budget: both runs are bounded by their config's soakMillis (wall clock), and the unthrottled server can
	// run many ticks inside that window - the tick timeout must sit above that so wall-clock always finishes first.
	@GameTest(template = "empty", timeoutTicks = 2_000_000)
	public static void autocraftManyGenomes(GameTestHelper helper) {
		runStress(helper, DISTINCT_DONORS);
	}

	@GameTest(template = "empty", timeoutTicks = 2_000_000)
	public static void autocraftManyGenomesSharedDonors(GameTestHelper helper) {
		runStress(helper, SHARED_DONORS);
	}

	private static void runStress(GameTestHelper helper, StressConfig cfg) {
		List<Intro> intros = findLiveIntrogressions(cfg.genomeCap(), cfg.distinctDonors());
		int n = intros.size();
		int cpus = Math.min(cfg.cpus(), n);

		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "genome_stress"));
		buildPlot(plot, intros, cpus);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		AEItemKey[] targetKey = new AEItemKey[n];
		for (int i = 0; i < n; i++) {
			targetKey[i] = BeeMutationPattern.canonicalKey(intros.get(i).base(), intros.get(i).target(), BeeLifeStage.PRINCESS);
		}
		ApiaryControllerBlockEntity controller0 = (ApiaryControllerBlockEntity) level.getBlockEntity(origin.offset(controllerPos(0)));
		IActionSource src = IActionSource.ofMachine(controller0);

		@SuppressWarnings("unchecked")
		Future<ICraftingPlan>[] planFuture = new Future[n];
		ICraftingPlan[] plan = new ICraftingPlan[n];
		boolean[] submitted = new boolean[n];
		boolean[] delivered = new boolean[n];
		int[] lastReported = {0, 0}; // {lastDone, ticksSinceProgress}
		long startTime = System.currentTimeMillis();
		System.out.println("[genome-stress] " + cfg.label() + ": breeding " + n + " genomes on " + cpus + " CPUs...");

		helper.startSequence()
			.thenIdle(40) // let the grid start booting
			// Seed only once the grid storage is actually up (this big grid boots slower than a single-controller one, so a
			// fixed idle can seed into a not-yet-ready network and silently drop it), and wait until the parents are indexed.
			.thenWaitUntil(() -> {
				MEStorage storage = gridStorage(level, origin);
				if (storage == null) {
					throw new GameTestAssertException("grid storage not ready to seed");
				}
				KeyCounter snap = new KeyCounter();
				storage.getAvailableStacks(snap);
				if (snap.get(BeeMutationPattern.canonicalKey(intros.get(0).base(), BeeLifeStage.PRINCESS)) <= 0) {
					seedParents(level, origin, intros, src);
					refreshProviders(level, origin, n);
					throw new GameTestAssertException("seeding parents into the network...");
				}
			})
			.thenWaitUntil(() -> driveJobs(level, origin, intros, targetKey, src, planFuture, plan, submitted, delivered, lastReported, startTime, cfg))
			.thenExecute(() -> {
				int reached = 0;
				for (boolean d : delivered) {
					if (d) {
						reached++;
					}
				}
				System.out.println("[genome-stress] " + cfg.label() + " finished: " + reached + "/" + n + " genomes delivered in "
						+ (System.currentTimeMillis() - startTime) + " ms");
				// Every attempted genome is reachable by construction and delivery is reliable, so the floor is all of them.
				if (reached < n) {
					helper.fail("genome stress delivered only " + reached + "/" + n + " targets: " + diagnose(level, origin, intros, delivered, submitted));
				}
			})
			.thenSucceed();
	}

	// --- self-scheduling job driver ------------------------------------------------------------------------------

	/**
	 * Runs every tick until every genome has delivered its target (or the safety cap elapses): for each not-yet-delivered
	 * genome, calculate its crafting plan and submit it whenever a CPU is free (a failed {@code submitJob} just means no CPU
	 * is available, so it retries), and mark it delivered once its exact homozygous target princess appears in the network.
	 * Throws (so the wait keeps polling) until the stop condition holds.
	 */
	private static void driveJobs(ServerLevel level, BlockPos origin, List<Intro> intros, AEItemKey[] targetKey,
			IActionSource src, Future<ICraftingPlan>[] planFuture, ICraftingPlan[] plan, boolean[] submitted,
			boolean[] delivered, int[] lastReported, long startTime, StressConfig cfg) {
		IGrid grid = grid(level, origin);
		if (grid == null) {
			throw new GameTestAssertException("grid not ready");
		}
		ICraftingService crafting = grid.getCraftingService();
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			throw new GameTestAssertException("grid storage not ready");
		}

		// One network snapshot per tick (not one per genome).
		KeyCounter snapshot = new KeyCounter();
		storage.getAvailableStacks(snapshot);

		int n = intros.size();
		int done = 0;
		for (int i = 0; i < n; i++) {
			// Delivered = the exact homozygous target princess (canonical AE2 output) is in the network. The controller emits
			// it the instant a bred princess is result-species and homozygous for every pinned trait, regardless of any
			// off-target chromosomes, so this is the true end-to-end signal (breed + deliver), not just a score check.
			if (delivered[i] || snapshot.get(targetKey[i]) > 0) {
				delivered[i] = true;
				done++;
				continue;
			}
			if (planFuture[i] == null) {
				ICraftingSimulationRequester simRequester = () -> src;
				planFuture[i] = crafting.beginCraftingCalculation(level, simRequester, targetKey[i], 1, CalculationStrategy.REPORT_MISSING_ITEMS);
			}
			if (plan[i] == null) {
				try {
					plan[i] = planFuture[i].get(0, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
					continue; // still calculating
				} catch (InterruptedException | ExecutionException e) {
					throw new GameTestAssertException("crafting plan failed for " + intros.get(i).base().id() + ": " + e);
				}
			}
			if (plan[i].simulation()) {
				// Parents not indexed yet (or offering not refreshed) - drop and retry next tick.
				planFuture[i] = null;
				plan[i] = null;
				continue;
			}
			if (!submitted[i]) {
				if (crafting.submitJob(plan[i], null, null, true, src).successful()) {
					submitted[i] = true;
				}
				// else: no free CPU right now, retry next tick
			}
		}

		if (done != lastReported[0]) {
			lastReported[0] = done;
			lastReported[1] = 0;
			System.out.println("[genome-stress] " + cfg.label() + " delivered " + done + "/" + n + " @ " + (System.currentTimeMillis() - startTime) + " ms");
		} else if (++lastReported[1] % 400 == 0) {
			System.out.println("[genome-stress] " + cfg.label() + " breeding " + done + "/" + n + " @ " + (System.currentTimeMillis() - startTime)
					+ " ms (network keys=" + snapshot.size() + ")");
		}

		// Stop when every genome has delivered, or when the safety cap elapses. Delivery is reliable (all genomes are
		// reachable and storage has no type cap), so this normally stops well before the cap; the floor is asserted after.
		boolean budgetElapsed = System.currentTimeMillis() - startTime >= cfg.soakMillis();
		if (done < n && !budgetElapsed) {
			throw new GameTestAssertException("delivered " + done + "/" + n + " genomes; " + diagnose(level, origin, intros, delivered, submitted));
		}
	}

	private static void seedParents(ServerLevel level, BlockPos origin, List<Intro> intros, IActionSource src) {
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			return;
		}
		// Plenty of pure chassis + donor stock for every genome so no hill-climb ever starves. Distinct chassis species keep
		// the populations isolated (per active-species filter); a shared donor (heavy config) is seeded once per genome using
		// it, so its combined stock scales with how many genomes draw on it.
		for (Intro intro : intros) {
			storage.insert(BeeMutationPattern.canonicalKey(intro.base(), BeeLifeStage.PRINCESS), SEED_COUNT, Actionable.MODULATE, src);
			storage.insert(BeeMutationPattern.canonicalKey(intro.donor(), BeeLifeStage.DRONE), SEED_COUNT, Actionable.MODULATE, src);
		}
	}

	private static void refreshProviders(ServerLevel level, BlockPos origin, int n) {
		for (int i = 0; i < n; i++) {
			if (level.getBlockEntity(origin.offset(controllerPos(i))) instanceof ApiaryControllerBlockEntity controller) {
				ICraftingProvider.requestUpdate(controller.getMainNode());
			}
		}
	}

	// --- plot ----------------------------------------------------------------------------------------------------

	private static void buildPlot(PlotBuilder plot, List<Intro> intros, int cpus) {
		int n = intros.size();
		plot.creativeEnergyCell(ENERGY);
		BeegisticsGridTests.forceInfiniteChannels(plot, ENERGY);
		// Cable backbone at z=1, spanning past the drive + primary CPU on the z=0 row. It also runs under the whole z=2 CPU
		// row (each extra CPU at x=2j joins the grid via the cable at x=2j,z=1), which with 6 CPUs sits within this span.
		for (int x = 0; x <= n + 2; x++) {
			plot.cable(new BlockPos(x, 0, 1));
		}
		for (int i = 0; i < n; i++) {
			plot.blockState(controllerPos(i), BeegisticsBlocks.APIARY_CONTROLLER.get().defaultBlockState());
			plot.blockState(apiaryPos(i), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState());
		}
		// Storage must be bee cells, not a stock AE2 item cell. A stock cell holds at most 63 distinct *types*
		// (MAX_ITEM_TYPES) regardless of byte size, and each concurrent introgression breeds several distinct genomes (seed
		// base/donor, het carriers, homozygous carriers, off-target recombinants) - well past 63 across all genomes. With one
		// stock cell the network fills to 63 types and then rejects every new one: bred carriers and even finished target
		// princesses can no longer be inserted, so they jam in the apiary product slots, breeding stalls, and jobs abandon
		// without ever delivering. The beegistics bee cell has no type cap (byte-budget only), which is exactly what this
		// many-genome workload needs - and what a player would actually store bees in. A full drive of them is generous byte
		// headroom (types are already unbounded) so a full network is never what fails the test.
		plot.blockEntity(drivePos(n), AEBlocks.DRIVE, d -> {
			for (int c = 0; c < 10; c++) {
				d.getInternalInventory().addItems(new ItemStack(BeegisticsItems.beeCell(BeeCellTier.T32K)));
			}
		});
		// Primary crafting CPU inline on the z=0 row (proven), plus extra isolated single-block CPUs spaced two apart at
		// z=2 - each its own cluster - so several genomes breed at once (the heavy config gives every genome its own CPU).
		plot.blockState(primaryCpuPos(n), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
		for (int j = 1; j < cpus; j++) {
			plot.blockState(extraCpuPos(j), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
		}

		plot.addPostInitAction((level, player, origin) -> {
			for (int i = 0; i < n; i++) {
				if (level.getBlockEntity(origin.offset(controllerPos(i))) instanceof ApiaryControllerBlockEntity controller) {
					controller.setMode(ControllerMode.AUTOCRAFT); // drive the AE2 crafting path, not standalone maintenance
					controller.getInternalInventory().setItemDirect(0, genomeCard(intros.get(i)));
				}
				if (level.getBlockEntity(origin.offset(apiaryPos(i))) instanceof Container apiary) {
					// A PLAIN creative frame: instant breeding in any climate, natural recombination (no species change).
					apiary.setItem(InventoryApiary.SLOT_FRAMES_1, ApicultureItems.FRAME_CREATIVE.stack());
				}
			}
		});
	}

	/** A Bee Pattern card carrying the intro's target (species + homozygous pins); the controller auto-resolves the donor. */
	private static ItemStack genomeCard(Intro intro) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(intro.base().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(intro.target()), Set.of(),
			BeeGenomeMutationPattern.pinnedChromosomes(intro.target()));
		ItemBeeFilterCard.setFilter(card, filter);
		return card;
	}

	// --- introgression finder ------------------------------------------------------------------------------------

	/**
	 * Collects up to {@code max} live introgressions, each a <b>distinct dominant chassis species</b> made homozygous for one
	 * trait a recessive, mutation-free donor carries. Distinct bases keep each genome's breeding population isolated on the
	 * shared network (each controller filters to its own active species). When {@code distinctDonors} is set each genome also
	 * gets its own donor line (no contention); when it is not, donors may repeat, so several genomes draw drones from the same
	 * shared donor stock - the busier, more player-like case. Deterministic order (sorted by species id) so the set is stable.
	 */
	private static List<Intro> findLiveIntrogressions(int max, boolean distinctDonors) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));

		List<Intro> out = new ArrayList<>();
		Set<IBeeSpecies> usedDonors = new HashSet<>();
		for (IBeeSpecies base : species) {
			if (out.size() >= max) {
				break;
			}
			if (!base.isDominant()) {
				continue;
			}
			IGenome baseGenome = base.getDefaultGenome();
			Intro found = findDonor(beeType, karyotype, speciesChromosome, base, baseGenome, species, distinctDonors ? usedDonors : Set.of());
			if (found == null) {
				continue;
			}
			// The card auto-resolves the donor deterministically, so pin the intro to the resolver's pick - what the
			// controller will breed and what we must seed. Enforce donor distinctness on the resolved donor, not the finder's.
			IBeeSpecies resolved = BeeGenomeMutationPattern.resolveDonor(found.target()).orElse(null);
			if (resolved == null || (distinctDonors && usedDonors.contains(resolved))) {
				continue;
			}
			out.add(new Intro(found.base(), resolved, found.target())); // one genome per distinct base
			usedDonors.add(resolved);
		}
		if (out.isEmpty()) {
			throw new IllegalStateException("no dominant chassis + mutation-free donor with a differing trait for the genome stress test");
		}
		return out;
	}

	@Nullable
	private static Intro findDonor(IBeeSpeciesType beeType, IKaryotype karyotype, IChromosome<?> speciesChromosome,
			IBeeSpecies base, IGenome baseGenome, List<IBeeSpecies> species, Set<IBeeSpecies> usedDonors) {
		for (IBeeSpecies donor : species) {
			if (donor == base || donor.isDominant() || usedDonors.contains(donor)
					|| !beeType.getMutations().getCombinations(base, donor).isEmpty()) {
				continue;
			}
			IGenome donorGenome = donor.getDefaultGenome();
			for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
				if (chromosome == speciesChromosome) {
					continue;
				}
				Object baseValue = baseGenome.getActiveValue(cast(chromosome));
				Object donorValue = donorGenome.getActiveValue(cast(chromosome));
				if (!Objects.equals(baseValue, donorValue)) {
					Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
					overrides.put(chromosome, Allele.of(donorValue, true));
					return new Intro(base, donor, baseGenome.copyWith(overrides));
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static IChromosome<Object> cast(IChromosome<?> chromosome) {
		return (IChromosome<Object>) chromosome;
	}

	// --- grid helpers --------------------------------------------------------------------------------------------

	@Nullable
	private static IGrid grid(ServerLevel level, BlockPos origin) {
		if (level.getBlockEntity(origin.offset(controllerPos(0))) instanceof ApiaryControllerBlockEntity controller) {
			return controller.getMainNode().getGrid();
		}
		return null;
	}

	@Nullable
	private static MEStorage gridStorage(ServerLevel level, BlockPos origin) {
		IGrid grid = grid(level, origin);
		return grid == null ? null : grid.getStorageService().getInventory();
	}

	private static String diagnose(ServerLevel level, BlockPos origin, List<Intro> intros, boolean[] delivered, boolean[] submitted) {
		StringBuilder sb = new StringBuilder("pending[");
		for (int i = 0; i < intros.size(); i++) {
			if (delivered[i]) {
				continue;
			}
			Intro intro = intros.get(i);
			sb.append(intro.base().id().getPath()).append("<-").append(intro.donor().id().getPath()).append("(sub=").append(submitted[i]);
			if (level.getBlockEntity(origin.offset(controllerPos(i))) instanceof ApiaryControllerBlockEntity c) {
				sb.append(" busy=").append(c.isBusy()).append(" apiary=").append(c.isApiaryConnected())
						.append(" job=").append(c.getCraftResult() == null ? "-" : c.getCraftResult().getPath())
						.append(" score=").append(c.getCraftBestScore()).append('/').append(c.getCraftMaxScore())
						.append(" idle=").append(c.getCraftIdleCycles());
			}
			sb.append(") ");
		}
		return sb.append(']').toString();
	}

	private BeeGenomeAutocraftStressTest() {
	}
}
