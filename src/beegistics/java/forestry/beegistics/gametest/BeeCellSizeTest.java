package forestry.beegistics.gametest;

import java.util.Map;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.netty.buffer.Unpooled;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;

import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.beegistics.BeeCellInventory;
import forestry.beegistics.BeeCellTier;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsItems;
import forestry.core.features.CoreDataComponents;
import forestry.core.utils.SpeciesUtil;

/**
 * Guards against the "book ban": a single item whose data components serialize to more than the network can carry, which
 * disconnects any client the item is sent to. The worst case for a bee storage cell is the largest tier filled to
 * capacity with the maximum number of distinct, maximally-heavy bees - here, analyzed <em>mated queens</em>, which carry
 * two full genomes each ({@code GENOME} + {@code MATE_GENOME}).
 *
 * <p>These tests are the spec the compact bee-cell storage is being developed against (see {@code plan.md}): they will
 * fail until the cell stores its contents in a compact, delta-against-species-default form with realized-byte-cost
 * accounting. The strict bar is {@link #doubleChestOfMaxCellsFitsInPacket}: a double chest full of maxed cells - the
 * single largest vanilla container-content packet a player can trigger - must stay under the packet limit.
 */
@GameTestHolder(Beegistics.NAMESPACE)
@PrefixGameTestTemplate(false)
public class BeeCellSizeTest {
	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * Vanilla's maximum packet size (and the NBT read quota used when decoding item components from the network): a
	 * single client-bound packet larger than this drops the connection.
	 */
	private static final int MAX_PACKET_SIZE = 2 * 1024 * 1024; // 2 MiB

	/** Slots in a double chest - the largest single-container {@code ClientboundContainerSetContentPacket} a player opens. */
	private static final int DOUBLE_CHEST_SLOTS = 54;

	private record MaxCell(int serializedBytes, int storedBees) {}

	/** A single maxed 32k cell must be well under the packet limit (so holding one, or a drive of them, is always safe). */
	@GameTest(template = "empty")
	public static void maxEntropyCellFitsInPacket(GameTestHelper helper) {
		MaxCell cell = buildMaxedCell(helper);

		LOGGER.info("Max-entropy 32k bee cell: {} distinct mated queens, serializes to {} bytes ({} KiB), packet limit {} bytes",
				cell.storedBees(), cell.serializedBytes(), cell.serializedBytes() / 1024, MAX_PACKET_SIZE);

		if (cell.serializedBytes() >= MAX_PACKET_SIZE) {
			helper.fail(String.format(
					"A single max-entropy 32k bee cell serializes to %d bytes (%d distinct mated queens) - over the %d-byte packet "
							+ "limit; such a cell would disconnect any client it is sent to",
					cell.serializedBytes(), cell.storedBees(), MAX_PACKET_SIZE));
			return;
		}
		helper.succeed();
	}

	/**
	 * The strict bar: a double chest (54 slots) full of maxed cells must fit in one container-content packet. This is the
	 * largest such packet a player can make the server send, so if this holds, holding cells in any inventory/chest/drive
	 * is safe.
	 */
	@GameTest(template = "empty")
	public static void doubleChestOfMaxCellsFitsInPacket(GameTestHelper helper) {
		MaxCell cell = buildMaxedCell(helper);
		// A ClientboundContainerSetContentPacket of 54 identical maxed cells is ~54x one cell (plus negligible framing);
		// compute it arithmetically rather than allocating gigabytes of buffer.
		long total = (long) DOUBLE_CHEST_SLOTS * cell.serializedBytes();

		LOGGER.info("Double chest of maxed 32k bee cells: {} slots x {} bytes = {} bytes ({} KiB), packet limit {} bytes",
				DOUBLE_CHEST_SLOTS, cell.serializedBytes(), total, total / 1024, MAX_PACKET_SIZE);

		if (total >= MAX_PACKET_SIZE) {
			helper.fail(String.format(
					"A double chest of %d maxed 32k bee cells serializes to %d bytes (%d bytes each) - over the %d-byte packet "
							+ "limit; opening such a chest would disconnect the player",
					DOUBLE_CHEST_SLOTS, total, cell.serializedBytes(), MAX_PACKET_SIZE));
			return;
		}
		helper.succeed();
	}

	/** Fills a 32k cell with as many distinct, maximally-heavy bees as its budget allows and measures the item's synced size. */
	private static MaxCell buildMaxedCell(GameTestHelper helper) {
		RegistryAccess registries = helper.getLevel().registryAccess();

		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IBeeSpecies forest = beeType.getSpecies(ForestryBeeSpecies.FOREST);
		IGenome base = forest.getDefaultGenome();

		ItemStack cellStack = new ItemStack(BeegisticsItems.beeCell(BeeCellTier.T32K));
		// A no-op save provider means the cell caches its contents and only writes the component when we persist() below,
		// instead of rewriting the whole list on every one of thousands of inserts.
		BeeCellInventory inv = BeeCellInventory.createInventory(cellStack, () -> {});
		if (inv == null) {
			helper.fail("Could not create a bee cell inventory for the 32k tier");
			throw new AssertionError("unreachable");
		}

		IActionSource source = IActionSource.empty();
		int stored = 0;
		for (int i = 0; i < 20000; i++) {
			// Unique primary genome (a distinct SPEED value) guarantees a distinct AEItemKey; a mate genome + analysis
			// make each stored bee as heavy as a real bee gets. All share one genus, maximizing the distinct-key count.
			IGenome variant = base.copyWith(Map.of(BeeChromosomes.SPEED, Allele.of((float) (i + 1), true)));
			IBee bee = forest.createIndividual(variant);
			bee.analyze();
			ItemStack beeStack = bee.createStack(BeeLifeStage.QUEEN);
			beeStack.set(CoreDataComponents.MATE_GENOME.get(), variant);

			if (inv.insert(AEItemKey.of(beeStack), 1, Actionable.MODULATE, source) == 0) {
				break; // cell is full
			}
			stored++;
		}
		inv.persist();

		if (stored < 100) {
			helper.fail("Expected the 32k cell to hold thousands of distinct bees, but only stored " + stored);
			throw new AssertionError("unreachable");
		}

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
		ItemStack.STREAM_CODEC.encode(buf, cellStack);
		return new MaxCell(buf.readableBytes(), stored);
	}
}
