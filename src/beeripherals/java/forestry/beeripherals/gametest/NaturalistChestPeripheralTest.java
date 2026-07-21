package forestry.beeripherals.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.api.peripheral.WorkMonitor;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.apiculture.blocks.NaturalistChestBlockType;
import forestry.beeripherals.Beeripherals;
import forestry.beeripherals.NaturalistChestPeripheral;
import forestry.core.features.CoreBlocks;
import forestry.core.tiles.TileNaturalistChest;
import forestry.core.utils.SpeciesUtil;

/**
 * End-to-end coverage for the {@link NaturalistChestPeripheral}. Runs on the gametest server (which loads CC: Tweaked
 * as a runtime dependency), so it exercises the real chain: a placed apiarist chest, the {@link PeripheralCapability}
 * registration, live inventory reads, the fuzzy {@code searchSpecimen} filter, and {@code pushItems}/{@code pullItems}
 * against a second inventory. The only stubbed seam is the computer/network layer ({@link IComputerAccess}), which the
 * move methods use purely to resolve the other inventory by name.
 */
@GameTestHolder(Beeripherals.NAMESPACE)
@PrefixGameTestTemplate(false)
public class NaturalistChestPeripheralTest {
	/** The capability resolves to our peripheral, which surfaces the full analyzed genome as Lua tables. */
	@GameTest(template = "empty")
	public static void peripheralExposesAnalyzedGenome(GameTestHelper helper) throws LuaException {
		NaturalistChestPeripheral peripheral = placeApiaristChest(helper, new BlockPos(2, 2, 2), analyzedBee(true));

		if (!peripheral.getType().equals("bee_chest")) {
			helper.fail("expected peripheral type 'bee_chest', got '" + peripheral.getType() + "'");
			return;
		}

		Map<String, Object> specimen = peripheral.getSpecimen(1);
		if (specimen == null) {
			helper.fail("getSpecimen(1) returned nil for an occupied slot");
			return;
		}
		if (!Boolean.TRUE.equals(specimen.get("analyzed"))) {
			helper.fail("specimen was not reported as analyzed");
			return;
		}
		if (!(specimen.get("species") instanceof Map<?, ?> species) || species.get("name") == null) {
			helper.fail("specimen is missing species info");
			return;
		}
		if (!(specimen.get("active") instanceof Map<?, ?> active) || !active.containsKey("lifespan") || !active.containsKey("speed")) {
			helper.fail("analyzed genome is missing beealyzer traits");
			return;
		}

		// An empty slot reads back as nil; an out-of-range slot is an error.
		if (peripheral.getSpecimen(2) != null) {
			helper.fail("expected nil for empty slot 2");
			return;
		}
		if (!throwsLua(() -> peripheral.getSpecimen(9999))) {
			helper.fail("expected an out-of-range slot to raise a Lua error");
			return;
		}

		List<Map<String, Object>> all = peripheral.getSpecimens();
		if (all.size() != 1 || !Integer.valueOf(1).equals(all.get(0).get("slot"))) {
			helper.fail("getSpecimens should have returned exactly the occupied slot 1");
			return;
		}

		helper.succeed();
	}

	/** searchSpecimen matches strings fuzzily and numbers by value, and only exposes genome traits once analyzed. */
	@GameTest(template = "empty")
	public static void searchMatchesFuzzilyAndRespectsAnalysis(GameTestHelper helper) throws LuaException {
		NaturalistChestPeripheral analyzed = placeApiaristChest(helper, new BlockPos(2, 2, 2), analyzedBee(true));

		Map<String, Object> specimen = analyzed.getSpecimen(1);
		String name = (String) ((Map<?, ?>) specimen.get("species")).get("name");
		String fragment = name.substring(0, Math.min(3, name.length()));
		Number lifespan = (Number) ((Map<?, ?>) specimen.get("active")).get("lifespan");

		if (matchCount(analyzed, Map.of("species", fragment), 1)) {
			helper.fail("fuzzy species substring search should return the one specimen");
			return;
		}
		if (matchCount(analyzed, Map.of("lifespan", lifespan.doubleValue()), 1)) {
			helper.fail("numeric lifespan search should return the one specimen");
			return;
		}
		if (matchCount(analyzed, Map.of(), 1)) {
			helper.fail("an empty criteria table should match every specimen");
			return;
		}
		if (matchCount(analyzed, Map.of("species", "definitely-not-a-bee"), 0)) {
			helper.fail("a nonsense criterion should match nothing");
			return;
		}

		// Analysis gating: species is known without analysis, but genome traits are not.
		NaturalistChestPeripheral raw = placeApiaristChest(helper, new BlockPos(2, 2, 5), analyzedBee(false));
		if (matchCount(raw, Map.of("species", fragment), 1)) {
			helper.fail("species search should still find an unanalyzed bee");
			return;
		}
		if (matchCount(raw, Map.of("lifespan", lifespan.doubleValue()), 0)) {
			helper.fail("a genome-trait search must not match an unanalyzed bee");
			return;
		}

		helper.succeed();
	}

	/** pushItems moves a specimen from the chest into another inventory resolved by peripheral name. */
	@GameTest(template = "empty")
	public static void pushItemsMovesSpecimenOut(GameTestHelper helper) throws LuaException {
		BlockPos chestRel = new BlockPos(2, 2, 2);
		BlockPos destRel = new BlockPos(5, 2, 2);
		NaturalistChestPeripheral peripheral = placeApiaristChest(helper, chestRel, analyzedBee(true));
		helper.setBlock(destRel, Blocks.CHEST);
		BlockEntity dest = helper.getBlockEntity(destRel);

		int moved = peripheral.pushItems(computerExposing("dest", wrap(dest)), "dest", 1, Optional.empty(), Optional.empty());
		if (moved != 1) {
			helper.fail("expected to push 1 item, pushed " + moved);
			return;
		}
		if (!chestSlot(helper, chestRel, 0).isEmpty()) {
			helper.fail("source slot was not emptied after push");
			return;
		}
		if (totalItems((Container) dest) != 1) {
			helper.fail("expected exactly 1 item in the destination after push");
			return;
		}

		helper.succeed();
	}

	/** pullItems moves a specimen from another inventory into the chest, honouring the chest's membership filter. */
	@GameTest(template = "empty")
	public static void pullItemsMovesSpecimenIn(GameTestHelper helper) throws LuaException {
		BlockPos chestRel = new BlockPos(2, 2, 2);
		BlockPos srcRel = new BlockPos(5, 2, 2);
		NaturalistChestPeripheral peripheral = placeApiaristChest(helper, chestRel, ItemStack.EMPTY);
		helper.setBlock(srcRel, Blocks.CHEST);
		BlockEntity src = helper.getBlockEntity(srcRel);
		((Container) src).setItem(0, analyzedBee(true));

		int moved = peripheral.pullItems(computerExposing("src", wrap(src)), "src", 1, Optional.empty(), Optional.empty());
		if (moved != 1) {
			helper.fail("expected to pull 1 item, pulled " + moved);
			return;
		}
		if (chestSlot(helper, chestRel, 0).isEmpty()) {
			helper.fail("chest did not receive the pulled specimen");
			return;
		}
		if (!((Container) src).getItem(0).isEmpty()) {
			helper.fail("source slot was not emptied after pull");
			return;
		}

		helper.succeed();
	}

	/* ===================== helpers ===================== */

	// Places an apiarist chest, seeds slot 0 with the given stack, and returns the peripheral resolved via the
	// registered CC capability - failing loudly if that capability is absent (i.e. CC: Tweaked did not load).
	private static NaturalistChestPeripheral placeApiaristChest(GameTestHelper helper, BlockPos rel, ItemStack content) {
		helper.setBlock(rel, CoreBlocks.NATURALIST_CHEST.get(NaturalistChestBlockType.APIARIST_CHEST).defaultState());
		if (!(helper.getBlockEntity(rel) instanceof TileNaturalistChest chest)) {
			throw new GameTestAssertException("apiarist chest block entity missing at " + rel);
		}
		if (!content.isEmpty()) {
			chest.getInternalInventory().setItem(0, content);
		}
		ServerLevel level = helper.getLevel();
		IPeripheral peripheral = level.getCapability(PeripheralCapability.get(), helper.absolutePos(rel), null);
		if (!(peripheral instanceof NaturalistChestPeripheral naturalist)) {
			throw new GameTestAssertException("naturalist chest peripheral capability not present - is CC: Tweaked loaded?");
		}
		return naturalist;
	}

	private static ItemStack analyzedBee(boolean analyzed) {
		IBeeSpecies species = SpeciesUtil.BEE_TYPE.get().getDefaultSpecies();
		IBee bee = species.createIndividual();
		if (analyzed) {
			bee.analyze();
		}
		return bee.createStack(BeeLifeStage.QUEEN);
	}

	private static ItemStack chestSlot(GameTestHelper helper, BlockPos rel, int slot) {
		return ((TileNaturalistChest) helper.getBlockEntity(rel)).getInternalInventory().getItem(slot);
	}

	private static int totalItems(Container container) {
		int count = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			count += container.getItem(i).getCount();
		}
		return count;
	}

	private static boolean matchCount(NaturalistChestPeripheral peripheral, Map<?, ?> criteria, int expected) {
		return peripheral.searchSpecimen(criteria).size() != expected;
	}

	private static boolean throwsLua(LuaCall call) {
		try {
			call.run();
			return false;
		} catch (LuaException expected) {
			return true;
		}
	}

	@FunctionalInterface
	private interface LuaCall {
		void run() throws LuaException;
	}

	// Minimal IPeripheral over a block entity, enough for the move methods to pull its item-handler capability.
	private static IPeripheral wrap(BlockEntity blockEntity) {
		return new IPeripheral() {
			@Override
			public String getType() {
				return "inventory";
			}

			@Override
			public Object getTarget() {
				return blockEntity;
			}

			@Override
			public boolean equals(IPeripheral other) {
				return other == this;
			}
		};
	}

	// Minimal IComputerAccess that only knows how to resolve the single named peripheral the move methods ask for.
	private static IComputerAccess computerExposing(String name, IPeripheral peripheral) {
		return new IComputerAccess() {
			@Override
			public String mount(String desiredLocation, Mount mount, String driveName) {
				throw new UnsupportedOperationException();
			}

			@Override
			public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void unmount(String location) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int getID() {
				return 0;
			}

			@Override
			public void queueEvent(String event, Object... arguments) {
			}

			@Override
			public String getAttachmentName() {
				return "test";
			}

			@Override
			public Map<String, IPeripheral> getAvailablePeripherals() {
				return Map.of(name, peripheral);
			}

			@Override
			public IPeripheral getAvailablePeripheral(String peripheralName) {
				return name.equals(peripheralName) ? peripheral : null;
			}

			@Override
			public WorkMonitor getMainThreadMonitor() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
