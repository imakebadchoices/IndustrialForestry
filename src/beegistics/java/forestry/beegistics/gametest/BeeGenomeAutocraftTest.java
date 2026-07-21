package forestry.beegistics.gametest;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.apiculture.InventoryBeeHousing;
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

import forestry.api.apiculture.IBeeHousing;

/**
 * Guards the genome-autocrafting path (the §5 follow-on): the Apiary Controller offering, accepting, and executing a
 * {@link BeeGenomeMutationPattern} that breeds an arbitrary homozygous genome (a min-maxed queen), not just a pure
 * species.
 *
 * <p>Coverage is deterministic (no reliance on RNG-driven breeding convergence, which the standing-loop hill-climb the
 * craft job reuses is already trusted to do):
 * <ul>
 *     <li><b>Reachability</b> - {@link BeeGenomeMutationPattern#pinnedChromosomes}/{@link BeeGenomeMutationPattern#isReachable}
 *     and that an unreachable pattern is withheld ({@code decode} returns null) while a reachable one is offered.</li>
 *     <li><b>Bootstrap selection</b> - the pure base princess + pure donor drone AE2 hands to {@code pushPattern} are
 *     staged into a real apiary by {@code restockCraftParents}.</li>
 *     <li><b>Delivery</b> - a bred bee matching the target homozygously is delivered as the canonical target key and
 *     closes the job (injecting the finished genome deterministically, since real fixation is many RNG cycles).</li>
 * </ul>
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeGenomeAutocraftTest {
	private static final BlockPos CONTROLLER_POS = new BlockPos(1, 2, 1);
	private static final BlockPos APIARY_POS = new BlockPos(2, 2, 1);

	/** A base line, a donor line, and target genomes: one reachable (donor carries the pin), one not (or null). */
	private record Intro(IBeeSpecies base, IBeeSpecies donor, IGenome reachableTarget, @Nullable IGenome unreachableTarget) {
	}

	// --- reachability --------------------------------------------------------------------------------------------

	/** pinnedChromosomes flags exactly the traits that differ from the base default; isReachable checks the donor carries them. */
	@GameTest(template = "empty")
	public static void reachabilityComputedFromDefaults(GameTestHelper helper) {
		Intro intro = findIntrogression();

		var pinned = BeeGenomeMutationPattern.pinnedChromosomes(intro.reachableTarget());
		helper.assertTrue(!pinned.isEmpty(), "the reachable target should pin at least one trait away from the base default");
		helper.assertTrue(BeeGenomeMutationPattern.isReachable(intro.reachableTarget(), intro.donor()),
				"a target pinned to the donor's own default value must be reachable");
		helper.assertTrue(!BeeGenomeMutationPattern.isReachable(intro.reachableTarget(), intro.base()),
				"the base line does not carry the pinned trait, so it alone cannot reach the target");

		if (intro.unreachableTarget() != null) {
			helper.assertTrue(!BeeGenomeMutationPattern.isReachable(intro.unreachableTarget(), intro.donor()),
					"a target pinned to a value on neither base nor donor default must be unreachable");
		}
		helper.succeed();
	}

	/** A reachable genome pattern is offered to the crafting system; an unreachable one decodes to null and is withheld. */
	@GameTest(template = "empty")
	public static void reachableOfferedUnreachableWithheld(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();

		rig.controller().getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));
		helper.assertTrue(offersGenomeFor(rig.controller(), intro.base()),
				"a reachable genome pattern should be offered to the crafting system");

		// "Unreachable" now means no species anywhere can supply the pins (the donor is auto-resolved from the whole
		// registry, not a named one), so only assert the withholding when the target genuinely has no candidate donor.
		if (intro.unreachableTarget() != null && BeeGenomeMutationPattern.resolveDonor(intro.unreachableTarget()).isEmpty()) {
			BeeGenomeMutationPattern decoded = BeeGenomeMutationPattern.decode(
					AEItemKey.of(genomeCard(intro.unreachableTarget())), helper.getLevel());
			helper.assertTrue(decoded == null, "an unreachable genome card must decode to null so it is never offered");
		}
		helper.succeed();
	}

	// --- bootstrap selection -------------------------------------------------------------------------------------

	/** The pure base princess + pure donor drone AE2 hands to pushPattern are staged into the apiary by the craft loop. */
	@GameTest(template = "empty")
	public static void bootstrapsParentsFromPushedInputs(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();
		ApiaryControllerBlockEntity controller = rig.controller();
		controller.getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));

		IPatternDetails pattern = genomePatternOffered(controller);
		helper.assertTrue(pattern != null, "the genome pattern should be offered");

		// The inputs AE2 extracts for a genome pattern: a pure base princess and a pure donor drone.
		KeyCounter inputs = new KeyCounter();
		inputs.add(BeeMutationPattern.canonicalKey(intro.base(), BeeLifeStage.PRINCESS), 1);
		inputs.add(BeeMutationPattern.canonicalKey(intro.donor(), BeeLifeStage.DRONE), 1);
		helper.assertTrue(controller.pushPattern(pattern, new KeyCounter[]{inputs}), "pushPattern should accept the genome job");
		helper.assertTrue(controller.isBusy(), "the controller should now be busy with the genome job");

		// One craft tick with an empty apiary stages the buffered parents: base as the princess, donor as the drone.
		TestNetwork network = new TestNetwork();
		controller.runCraftTick(network);

		assertSpecies(helper, rig.bees().getQueen(), intro.base(), BeeLifeStage.PRINCESS, "the staged princess should be the base line");
		assertSpecies(helper, rig.bees().getDrone(), intro.donor(), BeeLifeStage.DRONE, "the staged drone should be the donor line");
		helper.succeed();
	}

	// --- delivery ------------------------------------------------------------------------------------------------

	/** A bred bee that has reached the target homozygously is delivered as the canonical target key and closes the job. */
	@GameTest(template = "empty")
	public static void deliversCanonicalTargetAndFinishes(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();
		ApiaryControllerBlockEntity controller = rig.controller();
		IBeeSpecies result = intro.base();
		controller.getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));

		IPatternDetails pattern = genomePatternOffered(controller);
		helper.assertTrue(pattern != null, "the genome pattern should be offered");
		controller.pushPattern(pattern, new KeyCounter[]{new KeyCounter()});
		helper.assertTrue(controller.isBusy(), "the controller should be busy after accepting the job");

		// Simulate the RNG event the real apiary would eventually produce: a finished, exactly-on-target princess sitting in
		// the product slots. (The scoring hill-climb that actually reaches this state is the trusted standing-loop logic.)
		ItemStack finished = result.createStack(result.createIndividual(intro.reachableTarget()), BeeLifeStage.PRINCESS);
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1, finished);

		TestNetwork network = new TestNetwork();
		controller.runCraftTick(network);

		AEItemKey targetKey = BeeMutationPattern.canonicalKey(result, intro.reachableTarget(), BeeLifeStage.PRINCESS);
		helper.assertTrue(network.count(targetKey) == 1, "the canonical target genome should be delivered to the network exactly once");
		helper.assertTrue(!controller.isBusy(), "delivering the last output should finish (clear) the job");
		helper.assertTrue(rig.apiary().getItem(InventoryBeeHousing.SLOT_PRODUCT_1).isEmpty(), "the delivered bee should be consumed from the apiary");
		helper.succeed();
	}

	/** A finished target DRONE is not the (princess) output: it is not delivered, but recycled into the network population. */
	@GameTest(template = "empty")
	public static void targetDroneRecycledNotDelivered(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();
		ApiaryControllerBlockEntity controller = rig.controller();
		IBeeSpecies result = intro.base();
		controller.getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));
		controller.pushPattern(genomePatternOffered(controller), new KeyCounter[]{new KeyCounter()});

		// An exactly-on-target DRONE in the products: the output is a princess, so this must not close the job.
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1,
				result.createStack(result.createIndividual(intro.reachableTarget()), BeeLifeStage.DRONE));

		TestNetwork network = new TestNetwork();
		controller.runCraftTick(network);

		AEItemKey princessKey = BeeMutationPattern.canonicalKey(result, intro.reachableTarget(), BeeLifeStage.PRINCESS);
		AEItemKey droneKey = BeeMutationPattern.canonicalKey(result, intro.reachableTarget(), BeeLifeStage.DRONE);
		helper.assertTrue(network.count(princessKey) == 0, "a target drone must not be delivered as the princess output");
		helper.assertTrue(network.count(droneKey) == 1, "a target drone is recycled into the network breeding population, not delivered");
		helper.assertTrue(controller.isBusy(), "the job stays open until a target princess is bred");
		helper.succeed();
	}

	/** Every drained bee and non-bee produce is pushed to the network - the genome job's breeding population. */
	@GameTest(template = "empty")
	public static void offspringAndJunkPushedToNetwork(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();
		ApiaryControllerBlockEntity controller = rig.controller();
		controller.getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));
		controller.pushPattern(genomePatternOffered(controller), new KeyCounter[]{new KeyCounter()});

		ItemStack baseDrone = intro.base().createStack(BeeLifeStage.DRONE); // a bred bee (breeding stock)
		ItemStack junk = new ItemStack(Items.APPLE);                        // not an individual = produce
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1, baseDrone);
		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1 + 1, junk);

		TestNetwork network = new TestNetwork();
		controller.runCraftTick(network);

		helper.assertTrue(network.count(AEItemKey.of(intro.base().createStack(BeeLifeStage.DRONE))) == 1,
				"a bred bee is recycled into the network breeding population");
		helper.assertTrue(network.count(AEItemKey.of(new ItemStack(Items.APPLE))) == 1, "non-bee produce is pushed to the network");
		helper.assertTrue(rig.apiary().getItem(InventoryBeeHousing.SLOT_PRODUCT_1).isEmpty()
				&& rig.apiary().getItem(InventoryBeeHousing.SLOT_PRODUCT_1 + 1).isEmpty(), "both product slots should be drained");
		helper.succeed();
	}

	/** Restock stages the highest-scoring base princess from the network population (the hill-climb picks the best carrier). */
	@GameTest(template = "empty")
	public static void bestScoringPrincessSelected(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();
		ApiaryControllerBlockEntity controller = rig.controller();
		IBeeSpecies result = intro.base();
		controller.getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));
		controller.pushPattern(genomePatternOffered(controller), new KeyCounter[]{new KeyCounter()});

		// The network holds two base princesses - a pure one (score 0) and one already at the target (max score) - and a donor drone.
		TestNetwork network = new TestNetwork();
		IActionSource src = IActionSource.empty();
		network.insert(BeeMutationPattern.canonicalKey(result, BeeLifeStage.PRINCESS), 1, Actionable.MODULATE, src);
		network.insert(BeeMutationPattern.canonicalKey(result, intro.reachableTarget(), BeeLifeStage.PRINCESS), 1, Actionable.MODULATE, src);
		network.insert(BeeMutationPattern.canonicalKey(intro.donor(), BeeLifeStage.DRONE), 1, Actionable.MODULATE, src);

		controller.runCraftTick(network);

		ItemStack queen = rig.bees().getQueen();
		helper.assertTrue(!queen.isEmpty(), "a princess should be staged");
		var individual = forestry.api.genetics.capability.IIndividualHandlerItem.getIndividual(queen);
		helper.assertTrue(individual != null && individual.getGenome().isSameAlleles(intro.reachableTarget()),
				"the higher-scoring (on-target) princess should be staged over the pure one");
		helper.succeed();
	}

	/** A saved genome job restores its target genome on load: it still delivers the exact target key, not the pure default. */
	@GameTest(template = "empty")
	public static void persistedGenomeJobStillDelivers(GameTestHelper helper) {
		Rig rig = place(helper);
		Intro intro = findIntrogression();
		ApiaryControllerBlockEntity controller = rig.controller();
		IBeeSpecies result = intro.base();
		controller.getInternalInventory().setItemDirect(0, genomeCard(intro.reachableTarget()));
		controller.pushPattern(genomePatternOffered(controller), new KeyCounter[]{new KeyCounter()});

		// Round-trip the block entity's NBT (a chunk save/load) and confirm the genome job survived.
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = new CompoundTag();
		controller.saveAdditional(tag, registries);
		controller.loadTag(tag, registries);
		helper.assertTrue(controller.isBusy(), "the genome job should survive a save/load");

		rig.apiary().setItem(InventoryBeeHousing.SLOT_PRODUCT_1,
				result.createStack(result.createIndividual(intro.reachableTarget()), BeeLifeStage.PRINCESS));
		TestNetwork network = new TestNetwork();
		controller.runCraftTick(network);

		AEItemKey targetKey = BeeMutationPattern.canonicalKey(result, intro.reachableTarget(), BeeLifeStage.PRINCESS);
		AEItemKey defaultKey = BeeMutationPattern.canonicalKey(result, BeeLifeStage.PRINCESS);
		helper.assertTrue(network.count(targetKey) == 1, "a reloaded genome job must still deliver the exact target genome");
		helper.assertTrue(network.count(defaultKey) == 0, "it must not fall back to the pure default genome (target genome was lost)");
		helper.succeed();
	}

	// --- introgression finder ------------------------------------------------------------------------------------

	/**
	 * Finds a base line, a donor line, and target genomes off a real trait chromosome. Picks the non-species chromosome
	 * with the most distinct default values across species so a third (unreachable) value is usually available.
	 */
	private static Intro findIntrogression() {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();

		for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
			if (chromosome == speciesChromosome) {
				continue;
			}
			LinkedHashMap<Object, IBeeSpecies> byValue = new LinkedHashMap<>();
			for (IBeeSpecies species : beeType.getAllSpecies()) {
				byValue.putIfAbsent(species.getDefaultGenome().getActiveValue(cast(chromosome)), species);
			}
			if (byValue.size() < 2) {
				continue;
			}
			List<Map.Entry<Object, IBeeSpecies>> entries = new ArrayList<>(byValue.entrySet());
			for (Map.Entry<Object, IBeeSpecies> baseEntry : entries) {
				for (Map.Entry<Object, IBeeSpecies> donorEntry : entries) {
					if (donorEntry == baseEntry) {
						continue;
					}
					IBeeSpecies base = baseEntry.getValue();
					IGenome reachable = pin(base, chromosome, donorEntry.getKey());
					// The controller auto-resolves the donor, so pick an introgression that actually resolves one.
					if (BeeGenomeMutationPattern.resolveDonor(reachable).isEmpty()) {
						continue;
					}
					Object otherValue = null;
					for (Map.Entry<Object, IBeeSpecies> e : entries) {
						if (e != baseEntry && e != donorEntry) {
							otherValue = e.getKey();
							break;
						}
					}
					IGenome unreachable = otherValue == null ? null : pin(base, chromosome, otherValue);
					return new Intro(base, donorEntry.getValue(), reachable, unreachable);
				}
			}
		}
		throw new IllegalStateException("no reachable introgression across bee species for the genome autocraft test");
	}

	/** @return the base species' default genome with one chromosome pinned homozygous to {@code value}. */
	private static IGenome pin(IBeeSpecies base, IChromosome<?> chromosome, Object value) {
		Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
		overrides.put(chromosome, Allele.of(value, true));
		return base.getDefaultGenome().copyWith(overrides);
	}

	@SuppressWarnings("unchecked")
	private static IChromosome<Object> cast(IChromosome<?> chromosome) {
		return (IChromosome<Object>) chromosome;
	}

	// --- rig / helpers -------------------------------------------------------------------------------------------

	/** A Bee Pattern card carrying {@code target} as species + homozygous pins; the controller auto-resolves the donor. */
	private static ItemStack genomeCard(IGenome target) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(target.getActiveSpecies().id()),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(target), Set.of(),
			BeeGenomeMutationPattern.pinnedChromosomes(target));
		ItemBeeFilterCard.setFilter(card, filter);
		return card;
	}

	@Nullable
	private static IPatternDetails genomePatternOffered(ApiaryControllerBlockEntity controller) {
		for (IPatternDetails details : controller.getAvailablePatterns()) {
			if (details instanceof BeeGenomeMutationPattern) {
				return details;
			}
		}
		return null;
	}

	private static boolean offersGenomeFor(ApiaryControllerBlockEntity controller, IBeeSpecies result) {
		for (IPatternDetails details : controller.getAvailablePatterns()) {
			if (details instanceof BeeGenomeMutationPattern pattern && pattern.getResult().id().equals(result.id())) {
				return true;
			}
		}
		return false;
	}

	private static Rig place(GameTestHelper helper) {
		helper.setBlock(CONTROLLER_POS, BeegisticsBlocks.APIARY_CONTROLLER.get());
		helper.setBlock(APIARY_POS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")));
		BlockEntity controllerBe = helper.getBlockEntity(CONTROLLER_POS);
		BlockEntity apiaryBe = helper.getBlockEntity(APIARY_POS);
		helper.assertTrue(controllerBe instanceof ApiaryControllerBlockEntity, "controller block entity should be present");
		helper.assertTrue(apiaryBe instanceof IBeeHousing && apiaryBe instanceof Container, "apiary should be a bee-housing container");
		// These tests drive the AE2 crafting path (offer/push patterns), so the controller must be in crafting mode.
		((ApiaryControllerBlockEntity) controllerBe).setMode(ControllerMode.AUTOCRAFT);
		return new Rig((ApiaryControllerBlockEntity) controllerBe, (IBeeHousing) apiaryBe, (Container) apiaryBe);
	}

	private static void assertSpecies(GameTestHelper helper, ItemStack stack, IBeeSpecies species, BeeLifeStage stage, String what) {
		helper.assertTrue(!stack.isEmpty(), what + " (stocked)");
		var individual = forestry.api.genetics.capability.IIndividualHandlerItem.getIndividual(stack);
		helper.assertTrue(individual != null && individual.getSpecies().id().equals(species.id()), what + " (species)");
		helper.assertTrue(forestry.api.genetics.capability.IIndividualHandlerItem.getLifeStage(stack) == stage, what + " (stage)");
	}

	private record Rig(ApiaryControllerBlockEntity controller, IBeeHousing housing, Container apiary) {
		forestry.api.apiculture.IBeeHousingInventory bees() {
			return housing.getBeeInventory();
		}
	}

	/** A trivial unbounded in-memory {@link MEStorage} standing in for the ME network (mirrors ApiaryControllerTest). */
	private static final class TestNetwork implements MEStorage {
		private final KeyCounter contents = new KeyCounter();

		long count(AEKey key) {
			return this.contents.get(key);
		}

		@Override
		public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
			if (mode == Actionable.MODULATE) {
				this.contents.add(what, amount);
			}
			return amount;
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

	private BeeGenomeAutocraftTest() {
	}
}
