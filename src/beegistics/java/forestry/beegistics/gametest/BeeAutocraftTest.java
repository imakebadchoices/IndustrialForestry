package forestry.beegistics.gametest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
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
 * The real proof of Phase 2 autocrafting: a request for a pure mutation species in AE2's crafting system drives the
 * Apiary Controller to breed it on a real apiary and deliver the canonical result key back to the network.
 *
 * <p>On one energized ad-hoc ME grid: an Apiary Controller (loaded with a {@link BeeMutationPattern}) next to a
 * creative-framed {@code forestry:apiary}, a drive with an item cell, and a single 1k crafting storage forming a CPU.
 * The two pure base parents are seeded into the network, then a crafting job for the pure result species is submitted
 * through {@link ICraftingService}. The controller's {@link ApiaryControllerBlockEntity#pushPattern} breeds the
 * mutation and injects the canonical output; success is that the result species appears in the network. The creative
 * frame forces the mutation each cycle so the test is deterministic.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeAutocraftTest {
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);
	private static final BlockPos CONTROLLER = new BlockPos(1, 0, 0);
	private static final BlockPos DRIVE = new BlockPos(2, 0, 0);
	private static final BlockPos CRAFTING = new BlockPos(3, 0, 0);
	private static final BlockPos APIARY = new BlockPos(1, 0, -1);
	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	@GameTest(template = "empty", timeoutTicks = 6000)
	public static void autocraftSingleMutation(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IMutation<IBeeSpecies> mutation = pickMutation(beeType);
		IBeeSpecies first = mutation.getFirstParent();
		IBeeSpecies second = mutation.getSecondParent();
		IBeeSpecies result = mutation.getResult();

		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "autocraft"));
		buildPlot(plot, mutation);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		AEItemKey resultKey = BeeMutationPattern.canonicalKey(result, BeeLifeStage.PRINCESS);
		// The simulation requester derives the grid node from the action source's machine, so bind the source to the
		// controller (an IActionHost) - a source with no machine makes AE2 skip all patterns.
		ApiaryControllerBlockEntity controllerBe = (ApiaryControllerBlockEntity) level.getBlockEntity(origin.offset(CONTROLLER));
		IActionSource src = IActionSource.ofMachine(controllerBe);
		// Mutable job state threaded through the wait steps.
		Future<ICraftingPlan>[] planFuture = new Future[1];
		ICraftingPlan[] plan = new ICraftingPlan[1];
		boolean[] submitted = {false};

		helper.startSequence()
			.thenIdle(80) // let the grid boot and allocate channels
			.thenExecute(() -> seedParents(level, origin, first, second, src))
			.thenExecute(() -> refreshProvider(level, origin))
			.thenWaitUntil(() -> beginAndSubmit(level, origin, resultKey, src, planFuture, plan, submitted, mutation))
			.thenWaitUntil(() -> {
				MEStorage storage = gridStorage(level, origin);
				if (storage == null || !networkHasSpecies(storage, result)) {
					throw new GameTestAssertException("result not delivered yet: " + diagnose(level, origin, mutation));
				}
			})
			.thenSucceed();
	}

	// --- job driving ---------------------------------------------------------------------------------------------

	/** Begins the crafting calculation, polls the plan, and submits it. Throws (to retry) until the job is submitted. */
	private static void beginAndSubmit(ServerLevel level, BlockPos origin, AEItemKey resultKey, IActionSource src,
			Future<ICraftingPlan>[] planFuture, ICraftingPlan[] plan, boolean[] submitted, IMutation<IBeeSpecies> mutation) {
		IGrid grid = grid(level, origin);
		if (grid == null) {
			throw new GameTestAssertException("grid not ready");
		}
		ICraftingService crafting = grid.getCraftingService();
		if (planFuture[0] == null) {
			ICraftingSimulationRequester simRequester = () -> src;
			planFuture[0] = crafting.beginCraftingCalculation(level, simRequester, resultKey, 1, CalculationStrategy.REPORT_MISSING_ITEMS);
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
			// Recalculate next tick; a simulation means the pattern/inputs were not yet resolvable.
			planFuture[0] = null;
			plan[0] = null;
			throw new GameTestAssertException("crafting plan is a simulation: " + diagnose(level, origin, mutation));
		}
		if (!submitted[0]) {
			ICraftingSubmitResult result = crafting.submitJob(plan[0], null, null, true, src);
			if (!result.successful()) {
				throw new GameTestAssertException("failed to submit crafting job");
			}
			submitted[0] = true;
		}
	}

	/** Forces the crafting service to (re)read the controller's offered patterns once the grid is up. */
	private static void refreshProvider(ServerLevel level, BlockPos origin) {
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
			appeng.api.networking.crafting.ICraftingProvider.requestUpdate(controller.getMainNode());
		}
	}

	private static void seedParents(ServerLevel level, BlockPos origin, IBeeSpecies first, IBeeSpecies second, IActionSource src) {
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			return;
		}
		storage.insert(BeeMutationPattern.canonicalKey(first, BeeLifeStage.PRINCESS), 64, Actionable.MODULATE, src);
		storage.insert(BeeMutationPattern.canonicalKey(second, BeeLifeStage.DRONE), 64, Actionable.MODULATE, src);
	}

	// --- plot ----------------------------------------------------------------------------------------------------

	private static void buildPlot(PlotBuilder plot, IMutation<IBeeSpecies> mutation) {
		plot.creativeEnergyCell(ENERGY);
		BeegisticsGridTests.forceInfiniteChannels(plot, ENERGY);
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
				controller.setMode(ControllerMode.AUTOCRAFT); // expose the pattern to AE2's autocrafting
				controller.getInternalInventory().setItemDirect(0, encodedPattern(mutation));
			}
			if (level.getBlockEntity(origin.offset(APIARY)) instanceof Container apiary) {
				apiary.setItem(InventoryApiary.SLOT_FRAMES_1, creativeFrame());
			}
		});
	}

	private static ItemStack encodedPattern(IMutation<IBeeSpecies> mutation) {
		ItemStack stack = new ItemStack(BeegisticsItems.beeMutationPattern());
		ItemBeeMutationPattern.setMutation(stack, new BeeMutation(mutation.getFirstParent().id(), mutation.getSecondParent().id(), mutation.getResult().id()));
		return stack;
	}

	private static ItemStack creativeFrame() {
		ItemStack frame = ApicultureItems.FRAME_CREATIVE.stack();
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ItemCreativeHiveFrame.NBT_FORCE_MUTATIONS, true);
		frame.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return frame;
	}

	private static IMutation<IBeeSpecies> pickMutation(IBeeSpeciesType beeType) {
		for (IMutation<IBeeSpecies> m : beeType.getMutations().getAllMutations()) {
			if (m.getConditions().isEmpty()
					&& !m.getResult().equals(m.getFirstParent())
					&& !m.getResult().equals(m.getSecondParent())
					&& !m.getFirstParent().equals(m.getSecondParent())) {
				return m;
			}
		}
		throw new IllegalStateException("No unconditional bee mutation available for the autocraft test");
	}

	// --- assertion helpers ---------------------------------------------------------------------------------------

	@javax.annotation.Nullable
	private static IGrid grid(ServerLevel level, BlockPos origin) {
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
			return controller.getMainNode().getGrid();
		}
		return null;
	}

	@javax.annotation.Nullable
	private static MEStorage gridStorage(ServerLevel level, BlockPos origin) {
		IGrid grid = grid(level, origin);
		return grid == null ? null : grid.getStorageService().getInventory();
	}

	private static boolean networkHasSpecies(MEStorage storage, IBeeSpecies species) {
		KeyCounter all = new KeyCounter();
		storage.getAvailableStacks(all);
		for (var entry : all) {
			if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key && BeeKeys.isBee(key)) {
				IIndividual individual = IIndividualHandlerItem.getIndividual(key.getReadOnlyStack());
				if (individual != null && individual.getSpecies().id().equals(species.id())) {
					return true;
				}
			}
		}
		return false;
	}

	private static String diagnose(ServerLevel level, BlockPos origin, IMutation<IBeeSpecies> mutation) {
		StringBuilder sb = new StringBuilder("mutation=").append(mutation.getFirstParent().id()).append("+")
				.append(mutation.getSecondParent().id()).append("->").append(mutation.getResult().id());
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity c) {
			sb.append(" apiaryConnected=").append(c.isApiaryConnected()).append(" busy=").append(c.isBusy())
					.append(" patterns=").append(c.getAvailablePatterns().size());
		}
		MEStorage storage = gridStorage(level, origin);
		if (storage != null) {
			KeyCounter all = new KeyCounter();
			storage.getAvailableStacks(all);
			sb.append(" net[first=").append(count(all, mutation.getFirstParent())).append(" second=").append(count(all, mutation.getSecondParent()))
					.append(" result=").append(count(all, mutation.getResult())).append("]");
		}
		return sb.toString();
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

	private BeeAutocraftTest() {
	}
}
