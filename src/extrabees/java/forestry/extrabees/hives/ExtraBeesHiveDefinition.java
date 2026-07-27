package forestry.extrabees.hives;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.hives.IHiveDefinition;
import forestry.api.apiculture.hives.IHivePlacement;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.core.ToleranceType;
import forestry.api.genetics.ClimateHelper;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.core.utils.SpeciesUtil;
import forestry.extrabees.ExtraBees;
import forestry.extrabees.features.ExtraBeesBlocks;

/**
 * The wild hives Extra Bees generates. Both seat the hive in a cave wall rather than hanging it from a ceiling
 * (see {@link CaveWallHivePlacement}); they differ only in the material they grow out of.
 * <p>
 * Extra Bees used to need four hives. Two of them are gone: the water and nether lines now root on base's Aquatic
 * and Embittered hive bees, so only Rock and Marble still need a home of their own.
 */
public enum ExtraBeesHiveDefinition implements IHiveDefinition {
	/**
	 * Ordinary stone, at any depth. The rock bee sits upstream of the whole metal/gem/mineral tree, so this is
	 * deliberately the common one - a player who spends any time in caves should trip over it.
	 */
	ROCK(ExtraBeesBlocks.ROCK_HIVE, "rock", 3.0f, blockTag("hive_grounds/rock")),
	/**
	 * Calcite and diorite. Calcite occurs almost exclusively as the middle shell of an amethyst geode, which makes
	 * the good version of this find a real landmark; diorite is the everyday fallback that keeps the classical line
	 * from being gated behind geode hunting. Rarer than Rock because its anchor materials are far less common.
	 */
	MARBLE(ExtraBeesBlocks.MARBLE_HIVE, "marble", 10.0f, blockTag("hive_grounds/marble"));

	private final net.neoforged.neoforge.registries.DeferredHolder<Block, ?> hiveBlock;
	private final ResourceLocation speciesId;
	private final float defaultGenChance;
	private final IHivePlacement placement;

	ExtraBeesHiveDefinition(net.neoforged.neoforge.registries.DeferredHolder<Block, ?> hiveBlock, String speciesPath, float defaultGenChance, TagKey<Block> anchors) {
		this.hiveBlock = hiveBlock;
		this.speciesId = ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, speciesPath);
		this.defaultGenChance = defaultGenChance;
		this.placement = new CaveWallHivePlacement(anchors);
	}

	private static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ExtraBees.NAMESPACE, path));
	}

	public ResourceLocation id() {
		return this.speciesId;
	}

	/** The generation chance this hive is registered with, kept next to the rest of its definition. */
	public float defaultGenChance() {
		return this.defaultGenChance;
	}

	@Override
	public IHivePlacement getHiveGen() {
		return this.placement;
	}

	@Override
	public BlockState getBlockState() {
		return this.hiveBlock.get().defaultBlockState();
	}

	@Override
	public boolean isGoodBiome(Holder<Biome> biome) {
		// Overworld stone only: the Nether and End have their own hives, and their stone is not ours.
		return !biome.is(BiomeTags.IS_NETHER) && !biome.is(BiomeTags.IS_END);
	}

	@Override
	public boolean isGoodHumidity(HumidityType humidity) {
		IBeeSpecies species = SpeciesUtil.getBeeSpecies(this.speciesId);
		ToleranceType tolerance = species.getDefaultGenome().getActiveValue(BeeChromosomes.HUMIDITY_TOLERANCE);
		return ClimateHelper.isWithinLimits(humidity, species.getHumidity(), tolerance);
	}

	@Override
	public boolean isGoodTemperature(TemperatureType temperature) {
		IBeeSpecies species = SpeciesUtil.getBeeSpecies(this.speciesId);
		ToleranceType tolerance = species.getDefaultGenome().getActiveValue(BeeChromosomes.TEMPERATURE_TOLERANCE);
		return ClimateHelper.isWithinLimits(temperature, species.getTemperature(), tolerance);
	}

	@Override
	public void postGen(WorldGenLevel level, RandomSource rand, BlockPos pos) {
	}
}
