package forestry.beegistics.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.apiculture.InventoryBeeHousing;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.ItemBeeMutationPattern;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.beegistics.crafting.BeeMutation;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.ControllerMode;
import forestry.core.utils.SpeciesUtil;

/**
 * Guards the Apiary Controller. Coverage is layered:
 * <ul>
 *     <li><b>Pure decision logic</b> - {@link ApiaryControllerBlockEntity#findParent} / {@code countMatching} against
 *     hand-built network contents.</li>
 *     <li><b>AE2 wiring</b> - the placed block forms a grid node, and a mutation is offered/withheld by apiary climate
 *     (crafting mode).</li>
 *     <li><b>Live genome loop</b> - a real Forestry apiary is placed next to the controller and a genome breeding job is
 *     driven ({@link ApiaryControllerBlockEntity#startGenomeJob} then {@link ApiaryControllerBlockEntity#runCraftTick})
 *     against an in-memory {@link MEStorage}. This exercises staging genome parents across every usable apiary in
 *     parallel, and the contention rule that leaves a shared apiary driven by neither controller.</li>
 *     <li><b>Perpetual (Standalone) loop</b> - {@link ApiaryControllerBlockEntity#runPerpetual} stocks and harvests a
 *     species card, and flushes stray apiary products.</li>
 * </ul>
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ApiaryControllerTest {
	private static final BlockPos CONTROLLER_POS = new BlockPos(1, 2, 1);
	private static final BlockPos APIARY_POS = new BlockPos(2, 2, 1); // +X neighbour of the controller

	// --- helpers -------------------------------------------------------------------------------------------------

	private static IBeeSpecies species(ResourceLocation id) {
		return SpeciesUtil.BEE_TYPE.get().getSpecies(id);
	}

	private static BeeFilter speciesFilter(IBeeSpecies s) {
		return new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(s.id()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	private static ItemStack beeStack(IBeeSpecies s, BeeLifeStage stage) {
		return s.createIndividual(s.getDefaultGenome()).createStack(stage);
	}

	private static AEItemKey beeKey(IBeeSpecies s, BeeLifeStage stage) {
		return AEItemKey.of(beeStack(s, stage));
	}

	/** Places a controller and a real apiary side by side and returns handles to both. */
	private static Rig place(GameTestHelper helper) {
		helper.setBlock(CONTROLLER_POS, BeegisticsBlocks.APIARY_CONTROLLER.get());
		helper.setBlock(APIARY_POS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")));

		BlockEntity controllerBe = helper.getBlockEntity(CONTROLLER_POS);
		BlockEntity apiaryBe = helper.getBlockEntity(APIARY_POS);
		helper.assertTrue(controllerBe instanceof ApiaryControllerBlockEntity, "controller block entity should be present");
		helper.assertTrue(apiaryBe instanceof IBeeHousing && apiaryBe instanceof Container, "apiary should be a bee-housing container");
		return new Rig((ApiaryControllerBlockEntity) controllerBe, (IBeeHousing) apiaryBe, (Container) apiaryBe);
	}

	/** Loads a genome Bee Pattern card (species + one homozygous pin) into the controller's single pattern inventory. */
	private static void loadGenomeCard(ApiaryControllerBlockEntity controller, GenomeIntro intro) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(intro.base().id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(intro.target()), Set.of(),
			BeeGenomeMutationPattern.pinnedChromosomes(intro.target()));
		ItemBeeFilterCard.setFilter(card, filter);
		controller.getInternalInventory().setItemDirect(0, card);
	}

	/** Loads a plain species Bee Pattern card (species only, no trait pins) - the target of a perpetual standalone loop. */
	private static void loadSpeciesCard(ApiaryControllerBlockEntity controller, IBeeSpecies species) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		ItemBeeFilterCard.setFilter(card, speciesFilter(species));
		controller.getInternalInventory().setItemDirect(0, card);
	}

	/** Seeds the base princesses + auto-resolved donor drones a standalone genome job stages as parents. */
	private static void seedGenomeParents(TestNetwork network, GenomeIntro intro, int count) {
		network.seed(beeKey(intro.base(), BeeLifeStage.PRINCESS), count);
		network.seed(beeKey(intro.donor(), BeeLifeStage.DRONE), count);
	}

	/** Initiates a genome job for the intro's target, then runs one craft tick (staging parents into every usable apiary). */
	private static void driveGenomeJob(ApiaryControllerBlockEntity controller, TestNetwork network, GenomeIntro intro) {
		controller.startGenomeJob(intro.target(), intro.base(), intro.donor(), 1);
		controller.runCraftTick(network);
	}

	private static void assertBee(GameTestHelper helper, ItemStack stack, IBeeSpecies species, BeeLifeStage stage, String what) {
		helper.assertTrue(!stack.isEmpty(), what + " should be stocked");
		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		helper.assertTrue(individual != null && individual.getSpecies() == species, what + " should be the right species");
		helper.assertTrue(IIndividualHandlerItem.getLifeStage(stack) == stage, what + " should be a " + stage.getSerializedName());
	}

	private record Rig(ApiaryControllerBlockEntity controller, IBeeHousing housing, Container apiary) {
		IBeeHousingInventory bees() {
			return housing.getBeeInventory();
		}
	}

	/** A base (chassis) species, its auto-resolved donor, and a homozygous target genome pinning one donor trait. */
	private record GenomeIntro(IBeeSpecies base, IBeeSpecies donor, IGenome target) {
	}

	/** Finds a dominant chassis, a recessive donor differing on one trait, and the resulting homozygous target genome. */
	private static GenomeIntro findGenomeIntro() {
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
					Object bv = baseGenome.getActiveValue(cast(chromosome));
					Object dv = donorGenome.getActiveValue(cast(chromosome));
					if (Objects.equals(bv, dv)) {
						continue;
					}
					Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
					overrides.put(chromosome, Allele.of(dv, true));
					IGenome target = baseGenome.copyWith(overrides);
					IBeeSpecies resolved = BeeGenomeMutationPattern.resolveDonor(target).orElse(null);
					if (resolved != null) {
						return new GenomeIntro(base, resolved, target);
					}
				}
			}
		}
		throw new IllegalStateException("no reachable genome introgression for the apiary controller test");
	}

	@SuppressWarnings("unchecked")
	private static IChromosome<Object> cast(IChromosome<?> chromosome) {
		return (IChromosome<Object>) chromosome;
	}

	/** A trivial unbounded in-memory {@link MEStorage} standing in for the ME network. */
	private static final class TestNetwork implements MEStorage {
		private final KeyCounter contents = new KeyCounter();

		void seed(AEKey key, long amount) {
			this.contents.add(key, amount);
		}

		long count(AEKey key) {
			return this.contents.get(key);
		}

		@Override
		public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
			if (mode == Actionable.MODULATE) {
				this.contents.add(what, amount);
			}
			return amount; // the stand-in network always accepts
		}

		@Override
		public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
			long take = Math.min(this.contents.get(what), amount);
			if (take > 0 && mode == Actionable.MODULATE) {
				this.contents.remove(what, take);
			}
			return take;
		}

		@Override
		public void getAvailableStacks(KeyCounter out) {
			out.addAll(this.contents);
		}

		@Override
		public Component getDescription() {
			return Component.empty();
		}
	}

	// --- pure decision logic -------------------------------------------------------------------------------------

	/** findParent picks the network bee matching both the filter's species and the required life stage, ignoring others. */
	@GameTest(template = "empty")
	public static void findParentSelectsSpeciesAndStage(GameTestHelper helper) {
		IBeeSpecies forest = species(ForestryBeeSpecies.FOREST);
		IBeeSpecies meadows = species(ForestryBeeSpecies.MEADOWS);

		AEItemKey forestPrincess = beeKey(forest, BeeLifeStage.PRINCESS);
		AEItemKey forestDrone = beeKey(forest, BeeLifeStage.DRONE);
		AEItemKey meadowsDrone = beeKey(meadows, BeeLifeStage.DRONE);

		KeyCounter contents = new KeyCounter();
		contents.add(forestPrincess, 2);
		contents.add(forestDrone, 5);
		contents.add(meadowsDrone, 3);

		BeeFilter forestOnly = speciesFilter(forest);

		helper.assertTrue(ApiaryControllerBlockEntity.findParent(contents, forestOnly, BeeLifeStage.PRINCESS) == forestPrincess,
				"should pull the Forest princess for the princess slot");
		helper.assertTrue(ApiaryControllerBlockEntity.findParent(contents, forestOnly, BeeLifeStage.DRONE) == forestDrone,
				"should pull the Forest drone for the drone slot, not the Meadows drone");
		helper.assertTrue(ApiaryControllerBlockEntity.findParent(contents, forestOnly, BeeLifeStage.QUEEN) == null,
				"should not substitute a princess when a queen is requested");

		helper.succeed();
	}

	/** countMatching totals only the bees matching the target filter, across stages, which drives the maintain threshold. */
	@GameTest(template = "empty")
	public static void countMatchingTotalsTargetSpecies(GameTestHelper helper) {
		IBeeSpecies forest = species(ForestryBeeSpecies.FOREST);
		IBeeSpecies meadows = species(ForestryBeeSpecies.MEADOWS);

		KeyCounter contents = new KeyCounter();
		contents.add(beeKey(forest, BeeLifeStage.DRONE), 7);
		contents.add(beeKey(meadows, BeeLifeStage.PRINCESS), 2);
		contents.add(beeKey(meadows, BeeLifeStage.DRONE), 4);

		helper.assertTrue(ApiaryControllerBlockEntity.countMatching(contents, speciesFilter(meadows)) == 6,
				"should count both Meadows bees (2 + 4) and ignore the Forest drones");
		helper.assertTrue(ApiaryControllerBlockEntity.countMatching(contents, speciesFilter(forest)) == 7,
				"should count the Forest drones");

		helper.succeed();
	}

	// --- AE2 wiring ----------------------------------------------------------------------------------------------

	/** The placed block forms a grid node so cables can find it. */
	@GameTest(template = "empty")
	public static void controllerFormsGridNode(GameTestHelper helper) {
		helper.setBlock(CONTROLLER_POS, BeegisticsBlocks.APIARY_CONTROLLER.get());

		BlockPos absPos = helper.absolutePos(CONTROLLER_POS);
		IInWorldGridNodeHost host = helper.getLevel().getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, absPos, null);
		helper.assertTrue(host != null, "Apiary Controller must expose IN_WORLD_GRID_NODE_HOST so cables can find its node");

		helper.succeedWhen(() -> {
			IGridNode node = host.getGridNode(Direction.UP);
			helper.assertTrue(node != null, "Apiary Controller should form a grid node after readying");
		});
	}

	// --- live standalone loop against a real apiary --------------------------------------------------------------

	/** With base + donor in the network, a standalone genome job stages the base princess and donor drone into the apiary. */
	@GameTest(template = "empty")
	public static void standaloneStagesGenomeParentsIntoApiary(GameTestHelper helper) {
		Rig rig = place(helper);
		GenomeIntro intro = findGenomeIntro();
		loadGenomeCard(rig.controller(), intro);

		TestNetwork network = new TestNetwork();
		seedGenomeParents(network, intro, 3);

		driveGenomeJob(rig.controller(), network, intro);

		assertBee(helper, rig.bees().getQueen(), intro.base(), BeeLifeStage.PRINCESS, "queen slot");
		assertBee(helper, rig.bees().getDrone(), intro.donor(), BeeLifeStage.DRONE, "drone slot");
		helper.assertTrue(network.count(beeKey(intro.base(), BeeLifeStage.PRINCESS)) == 2, "one base princess should have been pulled");
		helper.assertTrue(network.count(beeKey(intro.donor(), BeeLifeStage.DRONE)) == 2, "one donor drone should have been pulled");
		helper.assertTrue(rig.controller().isApiaryConnected(), "the controller should report the adjacent apiary as connected");

		helper.succeed();
	}

	/** Offspring and produce sitting in the apiary's output slots are drained into the network by a perpetual step. */
	@GameTest(template = "empty")
	public static void drainMovesApiaryProductsIntoNetwork(GameTestHelper helper) {
		Rig rig = place(helper);
		IBeeSpecies forest = species(ForestryBeeSpecies.FOREST);

		ItemStack offspringPrincess = beeStack(forest, BeeLifeStage.PRINCESS);
		ItemStack offspringDrone = beeStack(forest, BeeLifeStage.DRONE);
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1, offspringPrincess);
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1 + 1, offspringDrone);

		TestNetwork network = new TestNetwork();
		rig.controller().runPerpetual(network); // idle (no cards), but still flushes stray products

		helper.assertTrue(rig.apiary().getItem(InventoryBeeHousing.SLOT_PRODUCT_1).isEmpty(), "product slot 1 should be drained");
		helper.assertTrue(rig.apiary().getItem(InventoryBeeHousing.SLOT_PRODUCT_1 + 1).isEmpty(), "product slot 2 should be drained");
		helper.assertTrue(network.count(beeKey(forest, BeeLifeStage.PRINCESS)) == 1, "drained princess should be in the network");
		helper.assertTrue(network.count(beeKey(forest, BeeLifeStage.DRONE)) == 1, "drained drone should be in the network");

		helper.succeed();
	}

	// --- standalone (perpetual) mode -----------------------------------------------------------------------------

	/**
	 * Standalone (perpetual) mode keeps the apiary stocked with a princess + drone matching a loaded card (same species)
	 * and harvests every product back to the network - a self-sustaining breeder that neither maintains a count nor
	 * autocrafts. One {@link ApiaryControllerBlockEntity#runPerpetual} step drains a leftover product and stocks both parents.
	 */
	@GameTest(template = "empty")
	public static void perpetualModeStocksAndHarvests(GameTestHelper helper) {
		Rig rig = place(helper);
		IBeeSpecies forest = species(ForestryBeeSpecies.FOREST);
		loadSpeciesCard(rig.controller(), forest);
		rig.controller().setMode(ControllerMode.STANDALONE);

		TestNetwork network = new TestNetwork();
		network.seed(beeKey(forest, BeeLifeStage.PRINCESS), 2);
		network.seed(beeKey(forest, BeeLifeStage.DRONE), 2);
		// A leftover product sitting in the apiary is harvested to the network in the same step.
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1, beeStack(forest, BeeLifeStage.DRONE));

		rig.controller().runPerpetual(network);

		assertBee(helper, rig.bees().getQueen(), forest, BeeLifeStage.PRINCESS, "queen slot");
		assertBee(helper, rig.bees().getDrone(), forest, BeeLifeStage.DRONE, "drone slot");
		helper.assertTrue(rig.apiary().getItem(InventoryBeeHousing.SLOT_PRODUCT_1).isEmpty(), "the apiary product slot should be drained");
		// One princess pulled to stock the queen (2 -> 1); drones: 2 seeded + 1 harvested product - 1 pulled to mate = 2.
		helper.assertTrue(network.count(beeKey(forest, BeeLifeStage.PRINCESS)) == 1, "one princess should have been pulled to stock the queen slot");
		helper.assertTrue(network.count(beeKey(forest, BeeLifeStage.DRONE)) == 2, "a drone was pulled to mate while the harvested product drone was returned");
		// A stocked queen is active breeding, so the GUI shows the green "Breeding" line.
		helper.assertTrue(rig.controller().isPerpetualBreeding(), "stocking a queen counts as active breeding for the GUI status");

		helper.succeed();
	}

	/**
	 * A linked apiary with no bee in the network matching a loaded card (and an empty apiary) has nothing to breed:
	 * {@link ApiaryControllerBlockEntity#isPerpetualBreeding()} reports false so the GUI warns that no bees match the
	 * filters instead of showing a green "Breeding" just because an apiary is attached.
	 */
	@GameTest(template = "empty")
	public static void perpetualReportsNoBeesWhenNetworkEmpty(GameTestHelper helper) {
		Rig rig = place(helper);
		loadSpeciesCard(rig.controller(), species(ForestryBeeSpecies.FOREST));
		rig.controller().setMode(ControllerMode.STANDALONE);

		TestNetwork network = new TestNetwork(); // no bees seeded

		rig.controller().runPerpetual(network);

		helper.assertTrue(rig.bees().getQueen().isEmpty(), "the apiary stays empty with no matching bees to stock");
		helper.assertTrue(!rig.controller().isPerpetualBreeding(), "no matching bees means the breeder reports no-bees, not breeding");

		helper.succeed();
	}

	// --- multi-apiary parallelism + contention -------------------------------------------------------------------

	/**
	 * A single controller drives <em>every</em> adjacent apiary in one step, so one genome job breeds in parallel across
	 * all of them - mirroring how an AE2 pattern provider spreads a request over every connected machine.
	 */
	@GameTest(template = "empty")
	public static void parallelJobStocksAllApiaries(GameTestHelper helper) {
		// Controller in the middle with an apiary on either side (-X and +X); each apiary touches only this controller.
		BlockPos controllerPos = new BlockPos(2, 2, 1);
		BlockPos apiaryA = new BlockPos(1, 2, 1);
		BlockPos apiaryB = new BlockPos(3, 2, 1);
		helper.setBlock(controllerPos, BeegisticsBlocks.APIARY_CONTROLLER.get());
		helper.setBlock(apiaryA, apiaryBlock());
		helper.setBlock(apiaryB, apiaryBlock());

		ApiaryControllerBlockEntity controller = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerPos);
		GenomeIntro intro = findGenomeIntro();
		loadGenomeCard(controller, intro);

		TestNetwork network = new TestNetwork();
		seedGenomeParents(network, intro, 4);

		driveGenomeJob(controller, network, intro);

		helper.assertTrue(controller.getUsableApiaryCount() == 2, "controller should drive both adjacent apiaries");
		helper.assertTrue(controller.getContendedApiaryCount() == 0, "neither apiary is shared, so none is contended");
		for (BlockPos pos : new BlockPos[]{apiaryA, apiaryB}) {
			IBeeHousing housing = (IBeeHousing) helper.getBlockEntity(pos);
			assertBee(helper, housing.getBeeInventory().getQueen(), intro.base(), BeeLifeStage.PRINCESS, "queen slot of apiary at " + pos);
			assertBee(helper, housing.getBeeInventory().getDrone(), intro.donor(), BeeLifeStage.DRONE, "drone slot of apiary at " + pos);
		}
		// Two base princesses and two donor drones (one per apiary) were pulled from the shared snapshot without double-claiming.
		helper.assertTrue(network.count(beeKey(intro.base(), BeeLifeStage.PRINCESS)) == 2, "two base princesses should have been pulled (one per apiary)");
		helper.assertTrue(network.count(beeKey(intro.donor(), BeeLifeStage.DRONE)) == 2, "two donor drones should have been pulled (one per apiary)");

		helper.succeed();
	}

	/**
	 * An apiary sandwiched between two controllers is <b>contended</b>: both controllers report it contended and neither
	 * stocks it, so they never race to breed the same apiary's workload. The apiary stays empty.
	 */
	@GameTest(template = "empty")
	public static void contendedApiaryIsDrivenByNeither(GameTestHelper helper) {
		BlockPos controllerA = new BlockPos(1, 2, 1);
		BlockPos sharedApiary = new BlockPos(2, 2, 1);
		BlockPos controllerB = new BlockPos(3, 2, 1);
		helper.setBlock(controllerA, BeegisticsBlocks.APIARY_CONTROLLER.get());
		helper.setBlock(sharedApiary, apiaryBlock());
		helper.setBlock(controllerB, BeegisticsBlocks.APIARY_CONTROLLER.get());

		ApiaryControllerBlockEntity a = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerA);
		ApiaryControllerBlockEntity b = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerB);
		GenomeIntro intro = findGenomeIntro();
		for (ApiaryControllerBlockEntity c : new ApiaryControllerBlockEntity[]{a, b}) {
			loadGenomeCard(c, intro);
		}

		TestNetwork network = new TestNetwork();
		seedGenomeParents(network, intro, 4);

		driveGenomeJob(a, network, intro);
		driveGenomeJob(b, network, intro);

		IBeeHousing housing = (IBeeHousing) helper.getBlockEntity(sharedApiary);
		helper.assertTrue(housing.getBeeInventory().getQueen().isEmpty(), "contended apiary's queen slot must stay empty");
		helper.assertTrue(housing.getBeeInventory().getDrone().isEmpty(), "contended apiary's drone slot must stay empty");
		helper.assertTrue(a.getUsableApiaryCount() == 0 && b.getUsableApiaryCount() == 0, "neither controller should have a usable apiary");
		helper.assertTrue(a.getContendedApiaryCount() == 1 && b.getContendedApiaryCount() == 1, "both controllers should report one contended apiary");
		helper.assertTrue(network.count(beeKey(intro.base(), BeeLifeStage.PRINCESS)) == 4, "no base princess should have been pulled for a contended apiary");

		helper.succeed();
	}

	private static BlockState apiaryBlock() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState();
	}

	// --- alveary multiblock --------------------------------------------------------------------------------------

	/** Relative base of a 3x3x3 alveary; the arena around it is air, satisfying the entrance/air-ring rule. */
	private static final BlockPos ALVEARY_BASE = new BlockPos(5, 1, 5);

	/** Builds a minimal valid 3x3x3 plain alveary with an oak-slab cap and returns its member positions (absolute). */
	private static java.util.List<BlockPos> buildAlveary(GameTestHelper helper, BlockPos base) {
		BlockState plain = forestry.apiculture.features.ApicultureBlocks.ALVEARY.get(forestry.apiculture.blocks.BlockAlveary.Type.PLAIN).defaultState();
		BlockState slab = net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState();
		java.util.List<BlockPos> members = new java.util.ArrayList<>();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				for (int y = 0; y < 3; y++) {
					BlockPos rel = base.offset(x, y, z);
					helper.setBlock(rel, plain);
					members.add(helper.absolutePos(rel));
				}
			}
		}
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(base.offset(x, 3, z), slab); // slab cap at maxY + 1
			}
		}
		return members;
	}

	/** @return whether the alveary member block at the (relative) position is part of an assembled multiblock. */
	private static boolean alvearyAssembled(GameTestHelper helper, BlockPos memberRel) {
		if (helper.getBlockEntity(memberRel) instanceof forestry.api.multiblock.IMultiblockComponent component) {
			return component.getMultiblockLogic().getController().isAssembled();
		}
		return false;
	}

	/** A controller drives an assembled alveary like an apiary: stocks the shared queen/drone and counts it as one usable housing. */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void drivesAssembledAlvearyAsOneHousing(GameTestHelper helper) {
		BlockPos controllerRel = ALVEARY_BASE.offset(-1, 1, 1); // west neighbour of an alveary block, mid-height
		BlockPos memberRel = ALVEARY_BASE.offset(0, 1, 1);       // the face it touches
		helper.setBlock(controllerRel, BeegisticsBlocks.APIARY_CONTROLLER.get());
		buildAlveary(helper, ALVEARY_BASE);

		ApiaryControllerBlockEntity controller = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerRel);
		GenomeIntro intro = findGenomeIntro();
		loadGenomeCard(controller, intro);

		TestNetwork network = new TestNetwork();
		seedGenomeParents(network, intro, 3);

		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(alvearyAssembled(helper, memberRel), "alveary should assemble"))
				.thenExecute(() -> {
					driveGenomeJob(controller, network, intro);

					IBeeHousing alveary = (IBeeHousing) helper.getBlockEntity(memberRel);
					assertBee(helper, alveary.getBeeInventory().getQueen(), intro.base(), BeeLifeStage.PRINCESS, "alveary queen slot");
					assertBee(helper, alveary.getBeeInventory().getDrone(), intro.donor(), BeeLifeStage.DRONE, "alveary drone slot");
					helper.assertTrue(controller.getUsableApiaryCount() == 1, "the alveary should count as exactly one usable housing");
					helper.assertTrue(controller.getContendedApiaryCount() == 0, "a solely-owned alveary is not contended");
					helper.assertTrue(network.count(beeKey(intro.base(), BeeLifeStage.PRINCESS)) == 2, "one base princess pulled for the alveary");
					helper.assertTrue(network.count(beeKey(intro.donor(), BeeLifeStage.DRONE)) == 2, "one donor drone pulled for the alveary");
				})
				.thenSucceed();
	}

	/** An unassembled alveary block (an {@code IBeeHousing}+{@code Container} with only a fake inventory) is skipped, not stocked. */
	@GameTest(template = "empty")
	public static void skipsUnassembledAlvearyBlock(GameTestHelper helper) {
		BlockPos controllerRel = new BlockPos(1, 2, 1);
		BlockPos lonePlainRel = new BlockPos(2, 2, 1); // a single alveary block, never assembled into a structure
		helper.setBlock(controllerRel, BeegisticsBlocks.APIARY_CONTROLLER.get());
		helper.setBlock(lonePlainRel, forestry.apiculture.features.ApicultureBlocks.ALVEARY.get(forestry.apiculture.blocks.BlockAlveary.Type.PLAIN).defaultState());

		ApiaryControllerBlockEntity controller = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerRel);
		helper.assertTrue(helper.getBlockEntity(lonePlainRel) instanceof IBeeHousing && helper.getBlockEntity(lonePlainRel) instanceof Container,
				"a lone alveary block is still an IBeeHousing container");
		helper.assertTrue(!alvearyAssembled(helper, lonePlainRel), "a lone alveary block must not be assembled");

		GenomeIntro intro = findGenomeIntro();
		loadGenomeCard(controller, intro);

		TestNetwork network = new TestNetwork();
		seedGenomeParents(network, intro, 3);

		driveGenomeJob(controller, network, intro);

		IBeeHousing lone = (IBeeHousing) helper.getBlockEntity(lonePlainRel);
		helper.assertTrue(lone.getBeeInventory().getQueen().isEmpty(), "an unassembled alveary's queen slot must stay empty");
		helper.assertTrue(controller.getUsableApiaryCount() == 0, "an unassembled alveary block is not a usable housing");
		helper.assertTrue(network.count(beeKey(intro.base(), BeeLifeStage.PRINCESS)) == 3, "no base princess should be pulled for an unassembled alveary");

		helper.succeed();
	}

	/** Two controllers on different faces of one alveary both report it contended and neither drives it (contention spans the whole structure). */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void twoControllersContendOneAlveary(GameTestHelper helper) {
		BlockPos controllerARel = ALVEARY_BASE.offset(-1, 1, 1); // west of the -X face
		BlockPos controllerBRel = ALVEARY_BASE.offset(3, 1, 1);  // east of the +X face (a different member)
		BlockPos memberRel = ALVEARY_BASE.offset(0, 1, 1);
		helper.setBlock(controllerARel, BeegisticsBlocks.APIARY_CONTROLLER.get());
		helper.setBlock(controllerBRel, BeegisticsBlocks.APIARY_CONTROLLER.get());
		buildAlveary(helper, ALVEARY_BASE);

		ApiaryControllerBlockEntity a = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerARel);
		ApiaryControllerBlockEntity b = (ApiaryControllerBlockEntity) helper.getBlockEntity(controllerBRel);
		GenomeIntro intro = findGenomeIntro();
		for (ApiaryControllerBlockEntity c : new ApiaryControllerBlockEntity[]{a, b}) {
			loadGenomeCard(c, intro);
		}

		TestNetwork network = new TestNetwork();
		seedGenomeParents(network, intro, 4);

		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(alvearyAssembled(helper, memberRel), "alveary should assemble"))
				.thenExecute(() -> {
					driveGenomeJob(a, network, intro);
					driveGenomeJob(b, network, intro);

					IBeeHousing alveary = (IBeeHousing) helper.getBlockEntity(memberRel);
					helper.assertTrue(alveary.getBeeInventory().getQueen().isEmpty(), "a contended alveary's queen slot must stay empty");
					helper.assertTrue(a.getUsableApiaryCount() == 0 && b.getUsableApiaryCount() == 0, "neither controller may drive a contended alveary");
					helper.assertTrue(a.getContendedApiaryCount() == 1 && b.getContendedApiaryCount() == 1, "both controllers report the alveary contended");
					helper.assertTrue(network.count(beeKey(intro.base(), BeeLifeStage.PRINCESS)) == 4, "no base princess pulled for a contended alveary");
				})
				.thenSucceed();
	}

	// --- crafting mode: climate gating ---------------------------------------------------------------------------

	/**
	 * In crafting mode the controller offers a mutation to the crafting system only if its adjacent apiary can currently
	 * breed it: an unconditional mutation is always offered, but a conditional mutation whose conditions this apiary/world
	 * cannot satisfy ({@code canBreedAt == false}) is withheld - so a crafting job can never stall on an impossible condition.
	 */
	@GameTest(template = "empty")
	public static void conditionalGatedByApiaryClimate(GameTestHelper helper) {
		Rig rig = place(helper);
		rig.controller().setMode(ControllerMode.AUTOCRAFT);
		net.minecraft.world.level.Level level = helper.getLevel();

		IMutation<IBeeSpecies> unconditional = firstUnconditional();
		rig.controller().getInternalInventory().setItemDirect(0, patternStack(unconditional));

		// Find a conditional mutation this apiary/world cannot satisfy right now (distinct result from the unconditional).
		IMutation<IBeeSpecies> gated = null;
		for (IMutation<IBeeSpecies> m : SpeciesUtil.BEE_TYPE.get().getMutations().getAllMutations()) {
			if (m.getConditions().isEmpty() || m.getFirstParent().equals(m.getSecondParent())
					|| m.getResult().id().equals(unconditional.getResult().id())) {
				continue;
			}
			BeeMutationPattern pattern = BeeMutationPattern.decode(AEItemKey.of(patternStack(m)), level);
			if (pattern != null && !pattern.canBreedAt(level, rig.housing().getCoordinates(), rig.housing())) {
				gated = m;
				break;
			}
		}
		if (gated == null) {
			// No apiary-unsatisfiable conditional mutation exists in this world; nothing to gate, trivially satisfied.
			helper.succeed();
			return;
		}
		rig.controller().getInternalInventory().setItemDirect(1, patternStack(gated));

		if (!offersResult(rig.controller(), unconditional.getResult().id())) {
			helper.fail("controller did not offer its unconditional mutation");
			return;
		}
		if (offersResult(rig.controller(), gated.getResult().id())) {
			helper.fail("controller offered a mutation its apiary cannot breed");
			return;
		}
		helper.succeed();
	}

	private static boolean offersResult(ApiaryControllerBlockEntity controller, ResourceLocation resultId) {
		for (var details : controller.getAvailablePatterns()) {
			if (details instanceof BeeMutationPattern pattern && pattern.getResult().id().equals(resultId)) {
				return true;
			}
		}
		return false;
	}

	private static IMutation<IBeeSpecies> firstUnconditional() {
		for (IMutation<IBeeSpecies> m : SpeciesUtil.BEE_TYPE.get().getMutations().getAllMutations()) {
			if (m.getConditions().isEmpty() && !m.getFirstParent().equals(m.getSecondParent())) {
				return m;
			}
		}
		throw new IllegalStateException("No unconditional bee mutation available");
	}

	private static BeeMutation mutationComponent(IMutation<IBeeSpecies> m) {
		return new BeeMutation(m.getFirstParent().id(), m.getSecondParent().id(), m.getResult().id());
	}

	private static ItemStack patternStack(IMutation<IBeeSpecies> m) {
		ItemStack stack = new ItemStack(BeegisticsItems.beeMutationPattern());
		ItemBeeMutationPattern.setMutation(stack, mutationComponent(m));
		return stack;
	}
}
