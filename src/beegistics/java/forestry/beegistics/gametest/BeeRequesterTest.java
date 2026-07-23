package forestry.beegistics.gametest;

import java.util.EnumSet;
import java.util.Optional;

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
import forestry.beegistics.BeeFilter;
import forestry.beegistics.BeeKeys;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.ItemBeeFilterCard;
import forestry.beegistics.ItemBeeMutationPattern;
import forestry.beegistics.crafting.BeeMutation;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.BeeRequesterBlockEntity;
import forestry.beegistics.machine.ControllerMode;
import forestry.beegistics.machine.RequesterStatus;
import forestry.core.utils.SpeciesUtil;

/**
 * End-to-end proof that the {@link BeeRequesterBlockEntity Bee Requester} is a self-driving demand generator: it never
 * breeds anything itself, but placed on a network with an Autocraft {@link ApiaryControllerBlockEntity Apiary
 * Controller} it submits the crafting job that breeds its target and delivers the result into network storage.
 *
 * <p>Layout mirrors {@link BeeAutocraftTest}: one energized ad-hoc ME grid with a creative-framed apiary and an
 * Autocraft controller (loaded with the species mutation pattern), a drive with a cell, and a 1k crafting storage
 * forming a CPU - <em>plus</em> a Bee Requester loaded with a pure-species card for the mutation result and a maintain
 * threshold of 1. The two base parents are seeded, then the test just idles: unlike the autocraft test it submits <b>no
 * job by hand</b> - success is that the requester notices the empty stock, drives the craft, and the result species
 * appears in the network on its own.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BeeRequesterTest {
	private static final BlockPos ENERGY = new BlockPos(0, 0, 0);
	private static final BlockPos CONTROLLER = new BlockPos(1, 0, 0);
	private static final BlockPos DRIVE = new BlockPos(2, 0, 0);
	private static final BlockPos CRAFTING = new BlockPos(3, 0, 0);
	private static final BlockPos REQUESTER = new BlockPos(4, 0, 0);
	private static final BlockPos APIARY = new BlockPos(1, 0, -1);
	private static final BlockPos ORIGIN_IN_STRUCTURE = new BlockPos(4, 2, 4);

	@GameTest(template = "empty", timeoutTicks = 6000)
	public static void requesterDrivesItsOwnCraft(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IMutation<IBeeSpecies> mutation = pickMutation(beeType);
		IBeeSpecies first = mutation.getFirstParent();
		IBeeSpecies second = mutation.getSecondParent();
		IBeeSpecies result = mutation.getResult();

		Plot plot = new Plot(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "requester"));
		buildPlot(plot, mutation, result);

		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(ORIGIN_IN_STRUCTURE);
		Player fakePlayer = Platform.getFakePlayer(level, null);
		plot.build(level, fakePlayer, origin);

		ApiaryControllerBlockEntity controllerBe = (ApiaryControllerBlockEntity) level.getBlockEntity(origin.offset(CONTROLLER));
		IActionSource src = IActionSource.ofMachine(controllerBe);

		helper.startSequence()
			.thenIdle(80) // let the grid boot and allocate channels
			.thenExecute(() -> seedParents(level, origin, first, second, src))
			.thenExecute(() -> refreshProvider(level, origin))
			// No manual submit: the requester itself notices the shortfall and drives the job.
			.thenWaitUntil(() -> {
				MEStorage storage = gridStorage(level, origin);
				if (storage == null || !networkHasSpecies(storage, result)) {
					throw new GameTestAssertException("requester has not delivered its target yet: " + diagnose(level, origin, result));
				}
			})
			.thenExecute(() -> {
				// Once the target is stocked, the requester settles back to idle rather than pinning a job forever.
				if (level.getBlockEntity(origin.offset(REQUESTER)) instanceof BeeRequesterBlockEntity requester) {
					helper.assertTrue(requester.getStatus() != RequesterStatus.SCHEDULED, "requester should not still be scheduling once stocked");
				}
			})
			.thenSucceed();
	}

	// --- plot --------------------------------------------------------------------------------------------------------

	private static void buildPlot(PlotBuilder plot, IMutation<IBeeSpecies> mutation, IBeeSpecies result) {
		plot.creativeEnergyCell(ENERGY);
		BeegisticsGridTests.forceInfiniteChannels(plot, ENERGY);
		plot.cable(new BlockPos(0, 0, 1));
		plot.cable(new BlockPos(1, 0, 1));
		plot.cable(new BlockPos(2, 0, 1));
		plot.cable(new BlockPos(3, 0, 1));
		plot.cable(new BlockPos(4, 0, 1));

		plot.blockState(CONTROLLER, BeegisticsBlocks.APIARY_CONTROLLER.get().defaultBlockState());
		plot.blockEntity(DRIVE, AEBlocks.DRIVE, drive -> drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack()));
		plot.blockState(CRAFTING, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
		plot.blockState(REQUESTER, BeegisticsBlocks.BEE_REQUESTER.get().defaultBlockState());
		plot.blockState(APIARY, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("forestry:apiary")).defaultBlockState());

		plot.addPostInitAction((level, player, origin) -> {
			if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
				controller.setMode(ControllerMode.AUTOCRAFT); // expose the pattern to AE2's autocrafting
				controller.getInternalInventory().setItemDirect(0, encodedPattern(mutation));
			}
			if (level.getBlockEntity(origin.offset(REQUESTER)) instanceof BeeRequesterBlockEntity requester) {
				requester.getInternalInventory().setItemDirect(0, speciesCard(result));
				requester.setThreshold(1);
			}
			if (level.getBlockEntity(origin.offset(APIARY)) instanceof Container apiary) {
				apiary.setItem(InventoryApiary.SLOT_FRAMES_1, creativeFrame());
			}
		});
	}

	/** A pure-species Bee Pattern card (species only, no trait pins) - the requester's maintain target. */
	private static ItemStack speciesCard(IBeeSpecies species) {
		ItemStack card = new ItemStack(BeegisticsItems.beeFilterCard());
		BeeFilter filter = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.of(species.id()), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		ItemBeeFilterCard.setFilter(card, filter);
		return card;
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

	private static void seedParents(ServerLevel level, BlockPos origin, IBeeSpecies first, IBeeSpecies second, IActionSource src) {
		MEStorage storage = gridStorage(level, origin);
		if (storage == null) {
			return;
		}
		storage.insert(BeeMutationPattern.canonicalKey(first, BeeLifeStage.PRINCESS), 64, Actionable.MODULATE, src);
		storage.insert(BeeMutationPattern.canonicalKey(second, BeeLifeStage.DRONE), 64, Actionable.MODULATE, src);
	}

	private static void refreshProvider(ServerLevel level, BlockPos origin) {
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity controller) {
			appeng.api.networking.crafting.ICraftingProvider.requestUpdate(controller.getMainNode());
		}
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
		throw new IllegalStateException("No unconditional bee mutation available for the requester test");
	}

	// --- assertion helpers -------------------------------------------------------------------------------------------

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

	private static String diagnose(ServerLevel level, BlockPos origin, IBeeSpecies result) {
		StringBuilder sb = new StringBuilder("target=").append(result.id());
		if (level.getBlockEntity(origin.offset(REQUESTER)) instanceof BeeRequesterBlockEntity r) {
			sb.append(" reqStatus=").append(r.getStatus()).append(" known=").append(r.getKnownCount());
		}
		if (level.getBlockEntity(origin.offset(CONTROLLER)) instanceof ApiaryControllerBlockEntity c) {
			sb.append(" ctrlBusy=").append(c.isBusy()).append(" patterns=").append(c.getAvailablePatterns().size());
		}
		return sb.toString();
	}

	private BeeRequesterTest() {
	}
}
