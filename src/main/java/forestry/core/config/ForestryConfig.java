package forestry.core.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.UnknownNullability;

public class ForestryConfig {
	private static final ModConfigSpec CLIENT_SPEC;
	private static final ModConfigSpec SERVER_SPEC;
	public static final Client CLIENT;
	public static final Server SERVER;

	public static class Client {
		// Misc
		public final ModConfigSpec.BooleanValue showParticles;
		public final ModConfigSpec.BooleanValue enableHints;
		public final ModConfigSpec.BooleanValue enableGlints;
		// Mail
		public final ModConfigSpec.BooleanValue mailAlertsEnabled;
		// JEI Bees
		public final ModConfigSpec.BooleanValue showRequirements;
		public final ModConfigSpec.BooleanValue showSecretMutations;
		public final ModConfigSpec.BooleanValue identifyGenome;

		public Client(ModConfigSpec.Builder builder) {
			this.showParticles = builder
				.comment("Whether any of Forestry's particles are rendered.")
				.define("particles", true);
			this.enableHints = builder
				.comment("Whether the \"Did you know?\" ledgers are shown in Forestry menus.")
				.define("enable_hints", true);
			this.enableGlints = builder
				.comment("Whether certain specimens, like Imperial Bees, will have enchantment glints on them.")
				.define("enable_glints", true);

			this.mailAlertsEnabled = builder
				.comment("Whether alerts are enabled for Forestry's mail system.")
				.define("mail_alerts_enable", true);

			builder.push("jei_bees");
			this.showRequirements = builder
				.comment("Set to false to disable display of mutation requirements")
				.define("show_requirements", true);
			this.showSecretMutations = builder
				.comment("Set to false to disable display of secret mutations")
				.define("show_secret_mutations", true);
			this.identifyGenome = builder
				.comment("Set to false to disable showing identified genome in recipes")
				.define("show_analyzed_tooltip", true);
			builder.pop();
		}
	}

	public static class Server {
		// Genetics
		public final ModConfigSpec.DoubleValue researchMutationBoostMultiplier;
		public final ModConfigSpec.DoubleValue maxResearchMutationBoostPercent;
		// Bees
		public final ModConfigSpec.BooleanValue pollinateVanillaLeaves;
		public final ModConfigSpec.DoubleValue wildHiveSpawnRate;
		public final ModConfigSpec.BooleanValue useHaploidDrones;
		// Trees
		public final ModConfigSpec.DoubleValue treesSpawnNaturally;
		// Butterflies
		public final ModConfigSpec.BooleanValue disableButterflySpawning;
		public final ModConfigSpec.IntValue butterflyClusterLimit;
		public final ModConfigSpec.IntValue butterflyClusterWidth;
		public final ModConfigSpec.IntValue butterflyClusterHeight;
		// Farms
		public final ModConfigSpec.IntValue multiFarmSize;
		public final ModConfigSpec.BooleanValue squareMultiFarms;
		public final ModConfigSpec.IntValue legacyFarmsPlanterRings;
		public final ModConfigSpec.BooleanValue legacyFarmsUseRings;
		public final ModConfigSpec.IntValue legacyFarmsRingSize;
		// Misc
		public final ModConfigSpec.BooleanValue enableBackpackResupply;
		public final ModConfigSpec.BooleanValue spawnTinOre;
		public final ModConfigSpec.BooleanValue spawnApatiteOre;
		public final ModConfigSpec.DoubleValue escritoireBountyMultiplier;
		// Energy - consumption
		public final ModConfigSpec.DoubleValue energyDemandModifier;
		// Energy - generation (biogas engine fuels: RF/tick + burn duration in work cycles per bucket)
		public final ModConfigSpec.IntValue biogasPowerBiomass;
		public final ModConfigSpec.IntValue biogasDurationBiomass;
		public final ModConfigSpec.IntValue biogasPowerEthanol;
		public final ModConfigSpec.IntValue biogasDurationEthanol;
		public final ModConfigSpec.IntValue biogasPowerSeedOil;
		public final ModConfigSpec.IntValue biogasDurationSeedOil;
		public final ModConfigSpec.IntValue biogasPowerHoney;
		public final ModConfigSpec.IntValue biogasDurationHoney;
		public final ModConfigSpec.IntValue biogasPowerJuice;
		public final ModConfigSpec.IntValue biogasDurationJuice;
		public final ModConfigSpec.IntValue biogasPowerMilk;
		public final ModConfigSpec.IntValue biogasDurationMilk;
		public final ModConfigSpec.IntValue biogasPowerWater;
		public final ModConfigSpec.IntValue biogasDurationWater;
		// Energy - generation (peat engine fuels: RF/tick + burn duration in work cycles per item)
		public final ModConfigSpec.IntValue peatPowerPeat;
		public final ModConfigSpec.IntValue peatDurationPeat;
		public final ModConfigSpec.IntValue peatPowerBituminous;
		public final ModConfigSpec.IntValue peatDurationBituminous;

		public Server(ModConfigSpec.Builder builder) {
			// Genetics
			builder.push("genetics");
			this.researchMutationBoostMultiplier = builder
				.comment("When a player researches a mutation using the Escritoire, mutation chances for hives owned by that player are multiplied by this factor, with the increase in chance limited to the value set in \"research_mutation_boost_multiplier\".")
				.defineInRange("research_mutation_boost_multiplier", 1.5, 1.0, 1000.0);
			this.maxResearchMutationBoostPercent = builder
				.comment("When a player researchs a mutation using the Escritoire, mutation chances for hives owned by that player are multiplied by a certain factor, with the increase in chance capped to this value.")
				.defineInRange("max_research_mutation_boost_percent", 5.0, 0.0, 100.0);
			builder.pop();

			// Bees
			builder.push("bees");
			this.pollinateVanillaLeaves = builder
				.comment("Whether bees and butterflies can pollinate Vanilla leaves. Might be undesirable for builds that rely on leaves.")
				.define("pollinate_vanilla_leaves", true);
			this.wildHiveSpawnRate = builder
				.comment("The base chance for a wild beehive to spawn naturally.")
				.defineInRange("wild_hive_spawn_rate", 1.0, 0.0, 1000.0);
			this.useHaploidDrones = builder
				.comment("In real life, drone bees are haploid, which means they only carry one set of chromosomes. If this option is enabled, only a drone's active alleles will be used for inheritance, making drones effectively haploid. This CHANGES Forestry's bee breeding mechanics.")
				.define("use_haploid_drones", true);
			builder.pop();

			// Farming
			builder.push("farming");
			this.multiFarmSize = builder
				.comment("")
				.defineInRange("multiblock_farm_size", 2, 1, 10);
			this.squareMultiFarms = builder
				.comment("Whether Forestry multiblock farms have square shaped farmlands instead of the default diamond shape.")
				.define("square_multiblock_farms", false);
			this.legacyFarmsPlanterRings = builder
				.comment("Sets the size of the farmland that is used by all legacy (single block) farms.")
				.defineInRange("legacy_farms_planter_rings", 4, 1, 10);
			this.legacyFarmsUseRings = builder
				.comment("Whether legacy (single block) farms use a ring layout. The farmland size of the ring layout is always one block smaller.")
				.define("legacy_farms_use_rings", true);
			this.legacyFarmsRingSize = builder
				.comment("Sets the size of the inner ring of the ring layout.")
				.defineInRange("legacy_farms_ring_size", 4, 1, 10);
			builder.pop();

			// Trees
			builder.push("trees");
			this.treesSpawnNaturally = builder
				.comment("Multiplies the chance of a Forestry tree spawning in the wild. Set to 0 to disable Forestry tree spawning.")
				.defineInRange("tree_spawn_chance_modifier", 0.0f, 0.0f, 1000000.0f);
			builder.pop();

			// Butterflies
			builder.push("butterflies");
			this.disableButterflySpawning = builder
				.comment("Whether butterflies can spawn from Forestry leaves.")
				.define("disable_butterfly_spawning", false);
			this.butterflyClusterLimit = builder
				.comment("The maximum number of butterflies that can spawn in the same area or cluster.")
				.defineInRange("butterfly_cluster_limit", 20, 1, 2000);
			this.butterflyClusterWidth = builder
				.comment("The width of the cluster area used when checking if the \"butterfly_cluster_limit\" has been reached.")
				.defineInRange("butterfly_cluster_width", 128, 0, 2000);
			this.butterflyClusterHeight = builder
				.comment("The height of the cluster area used when checking if the \"butterfly_cluster_limit\" has been reached.")
				.defineInRange("butterfly_cluster_height", 64, 0, 2000);
			builder.pop();

			// Misc
			this.enableBackpackResupply = builder
				.comment("Whether backpacks can have their resupply mode enabled, which stocks a player's inventory using blocks from the backpack's inventory.")
				.define("enable_backpack_resupply", true);
			this.spawnTinOre = builder
				.comment("Whether Tin Ore veins generate naturally in the Overworld.")
				.define("spawn_tin_ore", true);
			this.spawnApatiteOre = builder
				.comment("Whether Apatite Ore veins generate naturally in the Overworld.")
				.define("spawn_apatite_ore", true);
			this.escritoireBountyMultiplier = builder
				.comment("Multiplies the chance of a reward from winning escritoire game(does not affect mutation notes)")
				.defineInRange("escritoire_bounty_multiplier", 1f, 0.0f, 1000f);

			// Energy
			builder.push("energy");
			this.energyDemandModifier = builder
				.comment("Global multiplier applied to the energy (RF) every Forestry machine consumes to do work. 1.0 is the default modern-FE balance; lower is cheaper, higher is more demanding.")
				.defineInRange("energy_demand_modifier", 1.0, 0.0, 1000.0);

			builder
				.comment("Per-fuel generation settings for Forestry engines. 'power' is the RF/tick produced while a fuel burns; 'duration' is how many work cycles a single bucket (biogas) or item (peat) lasts. Total RF per unit = power * duration.")
				.push("generation");

			builder.push("biogas_engine");
			this.biogasPowerBiomass = builder.comment("Biomass RF/tick.").defineInRange("biomass_power", Constants.ENGINE_FUEL_VALUE_BIOMASS, 0, 1_000_000);
			this.biogasDurationBiomass = builder.comment("Biomass burn duration (work cycles per bucket).").defineInRange("biomass_duration", Constants.ENGINE_CYCLE_DURATION_BIOMASS, 1, 1_000_000);
			this.biogasPowerEthanol = builder.comment("Bio-ethanol RF/tick.").defineInRange("bio_ethanol_power", Constants.ENGINE_FUEL_VALUE_ETHANOL, 0, 1_000_000);
			this.biogasDurationEthanol = builder.comment("Bio-ethanol burn duration (work cycles per bucket).").defineInRange("bio_ethanol_duration", Constants.ENGINE_CYCLE_DURATION_ETHANOL, 1, 1_000_000);
			this.biogasPowerSeedOil = builder.comment("Seed oil RF/tick.").defineInRange("seed_oil_power", Constants.ENGINE_FUEL_VALUE_SEED_OIL, 0, 1_000_000);
			this.biogasDurationSeedOil = builder.comment("Seed oil burn duration (work cycles per bucket).").defineInRange("seed_oil_duration", Constants.ENGINE_CYCLE_DURATION_SEED_OIL, 1, 1_000_000);
			this.biogasPowerHoney = builder.comment("Honey RF/tick.").defineInRange("honey_power", Constants.ENGINE_FUEL_VALUE_HONEY, 0, 1_000_000);
			this.biogasDurationHoney = builder.comment("Honey burn duration (work cycles per bucket).").defineInRange("honey_duration", Constants.ENGINE_CYCLE_DURATION_HONEY, 1, 1_000_000);
			this.biogasPowerJuice = builder.comment("Juice RF/tick.").defineInRange("juice_power", Constants.ENGINE_FUEL_VALUE_JUICE, 0, 1_000_000);
			this.biogasDurationJuice = builder.comment("Juice burn duration (work cycles per bucket).").defineInRange("juice_duration", Constants.ENGINE_CYCLE_DURATION_JUICE, 1, 1_000_000);
			this.biogasPowerMilk = builder.comment("Milk RF/tick.").defineInRange("milk_power", Constants.ENGINE_FUEL_VALUE_MILK, 0, 1_000_000);
			this.biogasDurationMilk = builder.comment("Milk burn duration (work cycles per bucket).").defineInRange("milk_duration", Constants.ENGINE_CYCLE_DURATION_MILK, 1, 1_000_000);
			this.biogasPowerWater = builder.comment("Water RF/tick (used as engine coolant/low-grade fuel).").defineInRange("water_power", Constants.ENGINE_FUEL_VALUE_WATER, 0, 1_000_000);
			this.biogasDurationWater = builder.comment("Water burn duration (work cycles per bucket).").defineInRange("water_duration", Constants.ENGINE_CYCLE_DURATION_WATER, 1, 1_000_000);
			builder.pop();

			builder.push("peat_engine");
			this.peatPowerPeat = builder.comment("Peat RF/tick.").defineInRange("peat_power", Constants.ENGINE_COPPER_FUEL_VALUE_PEAT, 0, 1_000_000);
			this.peatDurationPeat = builder.comment("Peat burn duration (work cycles per item).").defineInRange("peat_duration", Constants.ENGINE_COPPER_CYCLE_DURATION_PEAT, 1, 1_000_000);
			this.peatPowerBituminous = builder.comment("Bituminous peat RF/tick.").defineInRange("bituminous_peat_power", Constants.ENGINE_COPPER_FUEL_VALUE_BITUMINOUS_PEAT, 0, 1_000_000);
			this.peatDurationBituminous = builder.comment("Bituminous peat burn duration (work cycles per item).").defineInRange("bituminous_peat_duration", Constants.ENGINE_COPPER_CYCLE_DURATION_BITUMINOUS_PEAT, 1, 1_000_000);
			builder.pop();

			builder.pop(); // generation
			builder.pop(); // energy
		}
	}

	/**
	 * Global multiplier applied to machine energy consumption. Returns the default (1.0) when the
	 * SERVER config has not been loaded yet, since energy-storing tiles can be constructed during
	 * client setup (e.g. the item BEWLR renderer) before configs are available.
	 */
	public static double energyDemandModifier() {
		return SERVER_SPEC.isLoaded() ? SERVER.energyDemandModifier.get() : 1.0;
	}

	public static void register(@UnknownNullability ModContainer ctx) {
		ctx.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
		ctx.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
	}

	static {
		{
			Pair<Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Client::new);
			CLIENT = specPair.getLeft();
			CLIENT_SPEC = specPair.getRight();
		}
		{
			Pair<Server, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Server::new);
			SERVER = specPair.getLeft();
			SERVER_SPEC = specPair.getRight();
		}
	}
}
