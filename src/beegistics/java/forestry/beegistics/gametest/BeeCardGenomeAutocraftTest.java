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
import appeng.api.networking.crafting.ICraftingSubmitResult;
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
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.inventory.InventoryApiary;
import forestry.beegistics.Beegistics;
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
 * The Phase 1 proof of the "Bee Pattern" unification: a plain {@link ItemBeeFilterCard} carrying a genome target (species
 * + one homozygous trait pin) placed in the Apiary Controller's pattern port autocrafts that min-maxed queen - with the
 * donor <b>auto-resolved</b> from the registry, never named by the player. Mirrors {@link BeeGenomeAutocraftLiveTest} but
 * drives a filter card instead of a hand-encoded genome pattern.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeCardGenomeAutocraftTest {
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);
	private static final BlockPos CONTROLLER = new BlockPos(1, 0, 0);
	private static final BlockPos DRIVE = new BlockPos(2, 0, 0);
	private static final BlockPos CRAFTING = new BlockPos(3, 0, 0);
	private static final BlockPos APIARY = new BlockPos(1, 0, -1);
	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	/** A chosen chassis, the exact homozygous target, and the donor the resolver picked (what we must seed). */
	private record CardIntro(IBeeSpecies base, IGenome target, IBeeSpecies donor) {
	}

	@GameTest(template = "empty", timeoutTicks = 200000)
	public static void autocraftGenomeFromCard(GameTestHelper helper) {
		runAutocraft(helper, findCardIntro(), "card_genome_autocraft");
	}

	/**
	 * The donor is chosen from what the network actually has, not forced to one species: here we stock a <em>non-easiest</em>
	 * candidate donor (and never the easiest), and the card still delivers - proving AE2 satisfies the fuzzy donor slot from
	 * whatever carrier is in stock rather than autocrafting the resolver's default pick.
	 */
	@GameTest(template = "empty", timeoutTicks = 200000)
	public static void autocraftUsesStockedNonEasiestDonor(GameTestHelper helper) {
		runAutocraft(helper, findMultiDonorCardIntro(), "card_stocked_donor");
	}

	private static void runAutocraft(GameTestHelper helper, @Nullable CardIntro intro, String plotName) {
		if (intro == null) {
			helper.fail("no suitable reachable introgression in the registry to test with");
			return;
		}

		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, plotName));
		buildPlot(plot, intro);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		AEItemKey targetKey = BeeMutationPattern.canonicalKey(intro.base(), intro.target(), BeeLifeStage.PRINCESS);
		ApiaryControllerBlockEntity controllerBe = (ApiaryControllerBlockEntity) level.getBlockEntity(origin.offset(CONTROLLER));
		IActionSource src = IActionSource.ofMachine(controllerBe);
		Future<ICraftingPlan>[] planFuture = new Future[1];
		ICraftingPlan[] plan = new ICraftingPlan[1];
		boolean[] submitted = {false};

		helper.startSequence()
			.thenIdle(80)
			.thenExecute(() -> seedParents(level, origin, intro, src))
			.thenExecute(() -> refreshProvider(level, origin))
			.thenWaitUntil(() -> beginAndSubmit(level, origin, targetKey, src, planFuture, plan, submitted, intro))
			.thenWaitUntil(() -> {
				MEStorage storage = gridStorage(level, origin);
				if (storage == null || !networkHasTarget(storage, intro.target())) {
					throw new GameTestAssertException("target genome not delivered yet: " + diagnose(level, origin, intro, targetKey));
				}
			})
			.thenSucceed();
	}

	// --- job driving --------------------------------------------------------------------------------------------

	private static void beginAndSubmit(ServerLevel level, BlockPos origin, AEItemKey targetKey, IActionSource src,
			Future<ICraftingPlan>[] planFuture, ICraftingPlan[] plan, boolean[] submitted, CardIntro intro) {
		IGrid grid = grid(level, origin);
		if (grid == null) {
			throw new GameTestAssertException("grid not ready");
		}
		ICraftingService crafting = grid.getCraftingService();
		if (planFuture[0] == null) {
			ICraftingSimulationRequester simRequester = () -> src;
			planFuture[0] = crafting.beginCraftingCalculation(level, simRequester, targetKey, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
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
			throw new GameTestAssertException("crafting plan is a simulation: " + diagnose(level, origin, intro, targetKey));
		}
		if (!submitted[0]) {
			ICraftingSubmitResult result = crafting.submitJob(plan[0], null, null, true, src);
			if (!result.successful()) {
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

	private static void seedParents(ServerLevel level, BlockPos origin, CardIntro intro, IActionSource src) {
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			return;
		}
		// The chassis line plus the donor the CONTROLLER will pick (resolveDonor is deterministic, so we agree with it).
		storage.insert(BeeMutationPattern.canonicalKey(intro.base(), BeeLifeStage.PRINCESS), 64, Actionable.MODULATE, src);
		storage.insert(BeeMutationPattern.canonicalKey(intro.donor(), BeeLifeStage.DRONE), 64, Actionable.MODULATE, src);
	}

	// --- plot ---------------------------------------------------------------------------------------------------

	private static void buildPlot(PlotBuilder plot, CardIntro intro) {
		plot.creativeEnergyCell(ENERGY);
		plot.cable(new BlockPos(0, 0, 1));
		plot.cable(new BlockPos(1, 0, 1));
		plot.cable(new BlockPos(2, 0, 1));
		plot.cable(new BlockPos(3, 0, 1));

		plot.blockState(CONTROLLER, BeegisticsBlocks.APIARY_CONTROLLER.get().defaultBlockState());
		plot.blockEntity(DRIVE, AEBlocks.DRIVE, drive -> drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack()));
		plot.blockState(CRAFTING, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
		plot.blockState(APIARY, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState());

		plot.addPostInitAction((level, player, origin) -> {
			if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
				controller.setMode(ControllerMode.AUTOCRAFT); // expose the card to AE2's autocrafting
				controller.getInternalInventory().setItemDirect(0, patternCard(intro));
			}
			if (level.getBlockEntity(origin.offset(APIARY)) instanceof Container apiary) {
				apiary.setItem(InventoryApiary.SLOT_FRAMES_1, ApicultureItems.FRAME_CREATIVE.stack());
			}
		});
	}

	/** A Bee Pattern card: species + the pinned trait(s), homozygous. The controller derives target + donor from this. */
	private static ItemStack patternCard(CardIntro intro) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(intro.base().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(intro.target()), Set.of(),
			BeeGenomeMutationPattern.pinnedChromosomes(intro.target()));
		ItemBeeFilterCard.setFilter(card, filter);
		return card;
	}

	// --- introgression finder -----------------------------------------------------------------------------------

	@Nullable
	private static CardIntro findCardIntro() {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));
		for (IBeeSpecies base : species) {
			if (!base.isDominant()) {
				continue;
			}
			IGenome baseGenome = base.getDefaultGenome();
			for (IBeeSpecies donor : species) {
				if (donor == base || donor.isDominant() || !beeType.getMutations().getCombinations(base, donor).isEmpty()) {
					continue;
				}
				IGenome donorGenome = donor.getDefaultGenome();
				for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
					if (chromosome == speciesChromosome) {
						continue;
					}
					if (Objects.equals(baseGenome.getActiveValue(chromosome), donorGenome.getActiveValue(chromosome))) {
						continue;
					}
					Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
					overrides.put(chromosome, Allele.of(donorGenome.getActiveValue(chromosome), true));
					IGenome target = baseGenome.copyWith(overrides);
					IBeeSpecies resolved = BeeGenomeMutationPattern.resolveDonor(target).orElse(null);
					if (resolved != null) {
						return new CardIntro(base, target, resolved);
					}
				}
			}
		}
		return null;
	}

	/** Like {@link #findCardIntro} but requires >= 2 candidate donors, and seeds the HARDEST one (never the easiest). */
	@Nullable
	private static CardIntro findMultiDonorCardIntro() {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));
		for (IBeeSpecies base : species) {
			if (!base.isDominant()) {
				continue;
			}
			IGenome baseGenome = base.getDefaultGenome();
			for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
				if (chromosome == speciesChromosome) {
					continue;
				}
				for (IBeeSpecies donor : species) {
					if (donor == base || donor.isDominant()
							|| Objects.equals(baseGenome.getActiveValue(chromosome), donor.getDefaultGenome().getActiveValue(chromosome))) {
						continue;
					}
					Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
					overrides.put(chromosome, Allele.of(donor.getDefaultGenome().getActiveValue(chromosome), true));
					IGenome target = baseGenome.copyWith(overrides);
					List<IBeeSpecies> candidates = BeeGenomeMutationPattern.candidateDonors(target);
					if (candidates.size() >= 2) {
						// Seed the last (hardest to obtain) - the resolver's default is candidates.get(0).
						return new CardIntro(base, target, candidates.get(candidates.size() - 1));
					}
				}
			}
		}
		return null;
	}

	// --- grid helpers -------------------------------------------------------------------------------------------

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

	private static String diagnose(ServerLevel level, BlockPos origin, CardIntro intro, AEItemKey targetKey) {
		StringBuilder sb = new StringBuilder("target=").append(intro.base().id()).append(" via auto-donor ").append(intro.donor().id());
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity c) {
			sb.append(" apiaryConnected=").append(c.isApiaryConnected()).append(" busy=").append(c.isBusy())
					.append(" patterns=").append(c.getAvailablePatterns().size()).append(" remaining=").append(c.getCraftRemaining())
					.append(" bestScore=").append(c.getCraftBestScore()).append('/').append(c.getCraftMaxScore());
		}
		MEStorage storage = gridStorage(level, origin);
		if (storage != null) {
			KeyCounter all = new KeyCounter();
			storage.getAvailableStacks(all);
			sb.append(" targetKeyInNet=").append(all.get(targetKey)).append(" netKeys=").append(all.size());
		}
		return sb.toString();
	}

	private BeeCardGenomeAutocraftTest() {
	}
}
