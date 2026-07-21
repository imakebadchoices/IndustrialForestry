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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.crafting.IPatternDetails;
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
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
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
 * Phase 2 coverage: a Bee Pattern card makes the Apiary Controller <b>auto-offer the species-mutation subtree</b> needed
 * to breed the card's chassis (and its resolved donor), so AE2 can compose species-creation with trait-introgression
 * without the player pre-encoding a single mutation pattern. The end-to-end recursive breed itself is composition of two
 * already-proven halves - {@link BeeAutocraftTreeTest} (species recursion over canonical keys) and
 * {@link BeeCardGenomeAutocraftTest} (card -> genome) - so here we assert the offering that glues them.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeCardTreeTest {
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);
	private static final BlockPos CONTROLLER = new BlockPos(1, 0, 0);
	private static final BlockPos DRIVE = new BlockPos(2, 0, 0);
	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	/** A genome target whose chassis is born of an unconditional mutation, so its ancestor closure is non-empty. */
	@GameTest(template = "empty", timeoutTicks = 200000)
	public static void offersSpeciesClosureForGenomeCard(GameTestHelper helper) {
		IGenome target = findMutationBornTarget();
		if (target == null) {
			helper.fail("no dominant mutation-born chassis with a reachable donor in the registry");
			return;
		}
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(target.getActiveSpecies().id()),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(target), Set.of(),
			BeeGenomeMutationPattern.pinnedChromosomes(target));
		runOfferingAssertion(helper, filter, "genome card");
	}

	/** A pure-species card (species, no trait pins) offers just that species' mutation closure - autocraft-a-species. */
	@GameTest(template = "empty", timeoutTicks = 200000)
	public static void offersSpeciesClosureForPureSpeciesCard(GameTestHelper helper) {
		IBeeSpecies species = firstUnconditionalMutationBornSpecies();
		if (species == null) {
			helper.fail("no unconditional mutation-born species in the registry");
			return;
		}
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(species.id()), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), Set.of());
		runOfferingAssertion(helper, filter, "pure-species card");
	}

	private static void runOfferingAssertion(GameTestHelper helper, BeeFilter filter, String label) {
		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "card_tree_offer"));
		buildPlot(plot, filter);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		helper.startSequence()
			.thenIdle(80) // let the grid boot so the provider is registered
			.thenWaitUntil(() -> {
				if (!(level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller)) {
					throw new GameTestAssertException("controller not ready");
				}
				long speciesPatterns = 0;
				for (IPatternDetails p : controller.getAvailablePatterns()) {
					if (p instanceof BeeMutationPattern) {
						speciesPatterns++;
					}
				}
				if (speciesPatterns == 0) {
					throw new GameTestAssertException(label + " offered no auto species-mutation patterns (offered="
							+ controller.getAvailablePatterns().size() + ")");
				}
			})
			.thenSucceed();
	}

	private static void buildPlot(PlotBuilder plot, BeeFilter filter) {
		plot.creativeEnergyCell(ENERGY);
		plot.cable(new BlockPos(0, 0, 1));
		plot.cable(new BlockPos(1, 0, 1));
		plot.cable(new BlockPos(2, 0, 1));
		plot.blockState(CONTROLLER, BeegisticsBlocks.APIARY_CONTROLLER.get().defaultBlockState());
		plot.blockEntity(DRIVE, AEBlocks.DRIVE, drive -> drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack()));
		plot.addPostInitAction((level, player, origin) -> {
			if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
				controller.setMode(ControllerMode.AUTOCRAFT); // expose the card to AE2's autocrafting
				ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
				ItemBeeFilterCard.setFilter(card, filter);
				controller.getInternalInventory().setItemDirect(0, card);
			}
		});
	}

	// --- registry finders ---------------------------------------------------------------------------------------

	/** @return a genome target on a dominant, unconditional-mutation-born chassis with a reachable donor, or null. */
	@Nullable
	private static IGenome findMutationBornTarget() {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IKaryotype karyotype = beeType.getKaryotype();
		IChromosome<?> speciesChromosome = karyotype.getSpeciesChromosome();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));
		for (IBeeSpecies base : species) {
			if (!base.isDominant() || !hasUnconditionalAncestor(beeType, base)) {
				continue;
			}
			IGenome baseGenome = base.getDefaultGenome();
			for (IBeeSpecies donor : species) {
				if (donor == base || donor.isDominant() || !beeType.getMutations().getCombinations(base, donor).isEmpty()) {
					continue;
				}
				IGenome donorGenome = donor.getDefaultGenome();
				for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
					if (chromosome == speciesChromosome
							|| Objects.equals(baseGenome.getActiveValue(chromosome), donorGenome.getActiveValue(chromosome))) {
						continue;
					}
					Map<IChromosome<?>, Allele<?>> overrides = new IdentityHashMap<>();
					overrides.put(chromosome, Allele.of(donorGenome.getActiveValue(chromosome), true));
					IGenome target = baseGenome.copyWith(overrides);
					if (BeeGenomeMutationPattern.resolveDonor(target).isPresent()) {
						return target;
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private static IBeeSpecies firstUnconditionalMutationBornSpecies() {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		List<IBeeSpecies> species = new ArrayList<>(beeType.getAllSpecies());
		species.sort(Comparator.comparing(s -> s.id().toString()));
		for (IBeeSpecies s : species) {
			if (hasUnconditionalAncestor(beeType, s)) {
				return s;
			}
		}
		return null;
	}

	/** Whether some mutation producing {@code species} is unconditional (so it is offered without a climate-matched apiary). */
	private static boolean hasUnconditionalAncestor(IBeeSpeciesType beeType, IBeeSpecies species) {
		for (IMutation<IBeeSpecies> m : beeType.getMutations().getMutationsInto(species)) {
			if (m.getConditions().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private BeeCardTreeTest() {
	}
}
