package forestry.extrabees.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.IForestryApi;
import forestry.api.apiculture.hives.IHive;
import forestry.api.apiculture.hives.IHiveDrop;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.apiculture.hives.HiveDecorator;
import forestry.apiculture.tiles.TileHive;
import forestry.extrabees.ExtraBees;
import forestry.extrabees.features.ExtraBeesBlocks;
import forestry.extrabees.hives.CaveWallHivePlacement;
import forestry.extrabees.hives.ExtraBeesHiveDefinition;

/**
 * Covers the two wild hives Extra Bees still needs of its own, Rock and Marble. The water and nether hives are gone -
 * those lines root on base's Aquatic and Embittered hive bees now - so these two are the only thing standing between
 * a fresh world and the ~48 metal/gem/mineral species downstream of the rock and marble bees.
 * <p>
 * The end-to-end test is the important one. An add-on hive block cannot reuse base's hive
 * {@code BlockEntityType} (its {@code validBlocks} set is immutable and lists only base's blocks, and
 * {@code BlockEntity}'s constructor throws on a state its type rejects), so Extra Bees registers its own. That is
 * invisible until a hive is actually placed and its tile is read back, which is exactly what
 * {@link #hivePlacesWithWorkingBlockEntity} does.
 */
@GameTestHolder(ExtraBees.NAMESPACE)
@PrefixGameTestTemplate(false)
public class ExtraBeesHiveTest {
	/** Every Extra Bees hive block resolves, carries a block item, and is accepted by our hive block entity type. */
	@GameTest(template = "empty")
	public static void hiveBlocksRegistered(GameTestHelper helper) {
		for (ExtraBeesHiveDefinition definition : ExtraBeesHiveDefinition.values()) {
			BlockState state = definition.getBlockState();
			ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			if (!blockId.getNamespace().equals(ExtraBees.NAMESPACE)) {
				helper.fail("Hive " + definition + " is not backed by an extrabees block (got " + blockId + ")");
				return;
			}
			Item item = BuiltInRegistries.ITEM.get(blockId);
			if (item == null || item == net.minecraft.world.item.Items.AIR) {
				helper.fail("Hive block " + blockId + " has no block item");
				return;
			}
			if (!ExtraBeesBlocks.HIVE_TILE.get().isValid(state)) {
				helper.fail("Hive block " + blockId + " is not in the extrabees hive block entity type's valid blocks");
				return;
			}
		}
		helper.succeed();
	}

	/** Both hives are registered with the hive manager and drop their own bee. */
	@GameTest(template = "empty")
	public static void hivesRegisteredWithDrops(GameTestHelper helper) {
		for (ExtraBeesHiveDefinition definition : ExtraBeesHiveDefinition.values()) {
			IHive hive = IForestryApi.INSTANCE.getHiveManager().getHives().stream()
				.filter(h -> h.getHiveBlockState().equals(definition.getBlockState()))
				.findFirst().orElse(null);
			if (hive == null) {
				helper.fail("Hive " + definition.id() + " was not registered with the hive manager");
				return;
			}
			List<IHiveDrop> drops = IForestryApi.INSTANCE.getHiveManager().getDrops(definition.id());
			if (drops.isEmpty()) {
				helper.fail("Hive " + definition.id() + " has no drops, so breaking it would yield no bees");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * Each hive matches at least one real biome under the exact three predicates
	 * {@code ForestryBiomeModifier} gates on. Those run at biome-modification time and read the species' climate out
	 * of the datapack registry, so this catches both a species that is not loaded yet (the predicates would throw)
	 * and a climate window so narrow that the hive is registered but can never be added to any biome.
	 */
	@GameTest(template = "empty")
	public static void hivesMatchAtLeastOneBiome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);

		for (ExtraBeesHiveDefinition definition : ExtraBeesHiveDefinition.values()) {
			int matches = 0;
			for (Holder<Biome> biome : biomes.asHolderIdMap()) {
				TemperatureType temperature = IForestryApi.INSTANCE.getClimateManager().getTemperature(biome);
				HumidityType humidity = IForestryApi.INSTANCE.getClimateManager().getHumidity(biome);
				if (definition.isGoodBiome(biome) && definition.isGoodTemperature(temperature) && definition.isGoodHumidity(humidity)) {
					matches++;
				}
			}
			if (matches == 0) {
				helper.fail("Hive " + definition.id() + " matches no biome, so the biome modifier would never add it "
					+ "and the hive could never generate");
				return;
			}
		}
		helper.succeed();
	}

	/**
	 * The cave-wall placement seats a hive in an anchor block that faces onto open space, and rejects one that is
	 * fully buried. This is the rule Binnie's rock hive used; the modern part is that it scans the whole column
	 * rather than sampling a single y above 0.
	 */
	@GameTest(template = "empty")
	public static void caveWallPlacementFindsExposedStoneOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos exposed = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos buried = helper.absolutePos(new BlockPos(5, 2, 2));

		// A 3x3x3 block of stone around each, then carve a cave face next to the "exposed" one only.
		fillStone(level, exposed);
		fillStone(level, buried);
		level.setBlock(exposed.east(), Blocks.CAVE_AIR.defaultBlockState(), 3);

		CaveWallHivePlacement placement = (CaveWallHivePlacement) ExtraBeesHiveDefinition.ROCK.getHiveGen();
		if (!placement.isValidLocation(level, exposed)) {
			helper.fail("Cave-wall placement rejected stone with an open cave face beside it");
			return;
		}
		if (placement.isValidLocation(level, buried)) {
			helper.fail("Cave-wall placement accepted fully buried stone, so hives would generate sealed in rock");
			return;
		}
		if (placement.isValidLocation(level, exposed.above(3))) {
			helper.fail("Cave-wall placement accepted a non-anchor block (air) as a hive seat");
			return;
		}
		helper.succeed();
	}

	/**
	 * The whole chain: generate a hive through the same {@code HiveDecorator} entry point worldgen uses, then read
	 * the block entity back. Catches a hive block whose block entity type rejects it - the tile would be dropped and
	 * the hive would sit inert, holding no bees and never swarming.
	 */
	@GameTest(template = "empty")
	public static void hivePlacesWithWorkingBlockEntity(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos seat = helper.absolutePos(new BlockPos(3, 2, 3));

		fillStone(level, seat);
		level.setBlock(seat.east(), Blocks.CAVE_AIR.defaultBlockState(), 3);

		IHive hive = IForestryApi.INSTANCE.getHiveManager().getHives().stream()
			.filter(h -> h.getHiveBlockState().equals(ExtraBeesHiveDefinition.ROCK.getBlockState()))
			.findFirst().orElseThrow();

		if (!HiveDecorator.tryGenHive(level, level.random, seat.getX(), seat.getZ(), hive)) {
			helper.fail("HiveDecorator refused to generate the rock hive on a valid exposed-stone seat");
			return;
		}

		BlockPos placed = findHive(level, seat);
		if (placed == null) {
			helper.fail("Rock hive reported as generated but no hive block is present near the seat");
			return;
		}
		BlockEntity tile = level.getBlockEntity(placed);
		if (!(tile instanceof TileHive)) {
			helper.fail("Rock hive at " + placed + " has no TileHive - its block entity type rejected the block state, "
				+ "so the hive would hold no bees (got " + (tile == null ? "null" : tile.getClass().getSimpleName()) + ")");
			return;
		}
		helper.succeed();
	}

	/** Fills a 3x3x3 cube of stone centred on {@code centre}. */
	private static void fillStone(ServerLevel level, BlockPos centre) {
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
		}
	}

	/** The decorator picks its own x/z within the chunk, so sweep the neighbourhood for the placed hive. */
	private static BlockPos findHive(ServerLevel level, BlockPos near) {
		BlockState hiveState = ExtraBeesHiveDefinition.ROCK.getBlockState();
		for (BlockPos pos : BlockPos.betweenClosed(near.offset(-16, -8, -16), near.offset(16, 8, 16))) {
			if (level.getBlockState(pos).is(hiveState.getBlock())) {
				return pos.immutable();
			}
		}
		return null;
	}
}
