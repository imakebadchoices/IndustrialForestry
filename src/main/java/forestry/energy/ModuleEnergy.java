package forestry.energy;

import forestry.api.client.IClientModuleHandler;
import forestry.api.fuels.EngineBronzeFuel;
import forestry.api.fuels.EngineCopperFuel;
import forestry.api.fuels.FuelManager;
import forestry.api.modules.ForestryModule;
import forestry.api.modules.ForestryModuleIds;
import forestry.core.config.Constants;
import forestry.core.config.ForestryConfig;
import forestry.core.features.CoreItems;
import forestry.core.fluids.ForestryFluids;
import forestry.core.utils.datastructures.FluidMap;
import forestry.core.utils.datastructures.ItemStackMap;
import forestry.energy.client.EnergyClientHandler;
import forestry.energy.features.EnergyTiles;
import forestry.modules.BlankForestryModule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.function.Consumer;

@ForestryModule
public class ModuleEnergy extends BlankForestryModule {
	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyTiles.CLOCKWORK_ENGINE.tileType(), (tile, side) -> tile.getEnergyHandler(side));
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyTiles.BIOGAS_ENGINE.tileType(), (tile, side) -> tile.getEnergyHandler(side));
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyTiles.PEAT_ENGINE.tileType(), (tile, side) -> tile.getEnergyHandler(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, EnergyTiles.BIOGAS_ENGINE.tileType(), (tile, side) -> tile.getFluidHandler(side));
	}

	@Override
	public ResourceLocation getId() {
		return ForestryModuleIds.ENERGY;
	}

	@Override
	public void registerEvents(IEventBus modBus) {
		modBus.addListener(ModuleEnergy::registerCapabilities);
		// Re-read the per-fuel generation config once the SERVER config is (re)loaded.
		// setupApi() seeds defaults before this fires, so the fuel maps are always populated.
		modBus.addListener((ModConfigEvent.Loading event) -> reloadFuelsFromConfig(event));
		modBus.addListener((ModConfigEvent.Reloading event) -> reloadFuelsFromConfig(event));
	}

	// Heat dissipation multipliers per fuel (a heat mechanic, not tied to RF output, so kept as constants).
	private static final int DISSIPATION_NORMAL = 1;
	private static final int DISSIPATION_WATERY = 3;

	@Override
	public void setupApi() {
		FuelManager.biogasEngineFuel = new FluidMap<>();
		FuelManager.peatEngineFuel = new ItemStackMap<>();

		// Seed the fuel maps with the built-in defaults. This runs during registration,
		// before the SERVER config loads, so the maps (and the engines' tank filters,
		// which capture the key set) are always populated. reloadFuelsFromConfig() then
		// overwrites the RF/duration values in place once the config is available.
		putBiogas(ForestryFluids.BIOMASS.getFluid(), Constants.ENGINE_FUEL_VALUE_BIOMASS, Constants.ENGINE_CYCLE_DURATION_BIOMASS, DISSIPATION_NORMAL);
		putBiogas(ForestryFluids.BIO_ETHANOL.getFluid(), Constants.ENGINE_FUEL_VALUE_ETHANOL, Constants.ENGINE_CYCLE_DURATION_ETHANOL, DISSIPATION_NORMAL);
		putBiogas(Fluids.WATER, Constants.ENGINE_FUEL_VALUE_WATER, Constants.ENGINE_CYCLE_DURATION_WATER, DISSIPATION_WATERY);
		putBiogas(NeoForgeMod.MILK.get(), Constants.ENGINE_FUEL_VALUE_MILK, Constants.ENGINE_CYCLE_DURATION_MILK, DISSIPATION_WATERY);
		putBiogas(ForestryFluids.SEED_OIL.getFluid(), Constants.ENGINE_FUEL_VALUE_SEED_OIL, Constants.ENGINE_CYCLE_DURATION_SEED_OIL, DISSIPATION_NORMAL);
		putBiogas(ForestryFluids.HONEY.getFluid(), Constants.ENGINE_FUEL_VALUE_HONEY, Constants.ENGINE_CYCLE_DURATION_HONEY, DISSIPATION_NORMAL);
		putBiogas(ForestryFluids.JUICE.getFluid(), Constants.ENGINE_FUEL_VALUE_JUICE, Constants.ENGINE_CYCLE_DURATION_JUICE, DISSIPATION_NORMAL);

		putPeat(CoreItems.PEAT.stack(), Constants.ENGINE_COPPER_FUEL_VALUE_PEAT, Constants.ENGINE_COPPER_CYCLE_DURATION_PEAT);
		putPeat(CoreItems.BITUMINOUS_PEAT.stack(), Constants.ENGINE_COPPER_FUEL_VALUE_BITUMINOUS_PEAT, Constants.ENGINE_COPPER_CYCLE_DURATION_BITUMINOUS_PEAT);
	}

	private static void putBiogas(Fluid fluid, int powerPerCycle, int burnDuration, int dissipationMultiplier) {
		FuelManager.biogasEngineFuel.put(fluid, new EngineBronzeFuel(fluid, powerPerCycle, burnDuration, dissipationMultiplier));
	}

	private static void putPeat(ItemStack item, int powerPerCycle, int burnDuration) {
		FuelManager.peatEngineFuel.put(item, new EngineCopperFuel(item, powerPerCycle, burnDuration));
	}

	// Overwrites the fuel values (in place, keeping the same keys) from the per-fuel generation config.
	private static void reloadFuelsFromConfig(ModConfigEvent event) {
		if (event.getConfig().getType() != ModConfig.Type.SERVER) {
			return;
		}
		if (FuelManager.biogasEngineFuel == null || FuelManager.peatEngineFuel == null) {
			return; // setupApi() has not run yet; defaults will be seeded there.
		}

		ForestryConfig.Server config = ForestryConfig.SERVER;
		putBiogas(ForestryFluids.BIOMASS.getFluid(), config.biogasPowerBiomass.get(), config.biogasDurationBiomass.get(), DISSIPATION_NORMAL);
		putBiogas(ForestryFluids.BIO_ETHANOL.getFluid(), config.biogasPowerEthanol.get(), config.biogasDurationEthanol.get(), DISSIPATION_NORMAL);
		putBiogas(Fluids.WATER, config.biogasPowerWater.get(), config.biogasDurationWater.get(), DISSIPATION_WATERY);
		putBiogas(NeoForgeMod.MILK.get(), config.biogasPowerMilk.get(), config.biogasDurationMilk.get(), DISSIPATION_WATERY);
		putBiogas(ForestryFluids.SEED_OIL.getFluid(), config.biogasPowerSeedOil.get(), config.biogasDurationSeedOil.get(), DISSIPATION_NORMAL);
		putBiogas(ForestryFluids.HONEY.getFluid(), config.biogasPowerHoney.get(), config.biogasDurationHoney.get(), DISSIPATION_NORMAL);
		putBiogas(ForestryFluids.JUICE.getFluid(), config.biogasPowerJuice.get(), config.biogasDurationJuice.get(), DISSIPATION_NORMAL);

		putPeat(CoreItems.PEAT.stack(), config.peatPowerPeat.get(), config.peatDurationPeat.get());
		putPeat(CoreItems.BITUMINOUS_PEAT.stack(), config.peatPowerBituminous.get(), config.peatDurationBituminous.get());
	}

	@Override
	public void registerClientHandler(Consumer<IClientModuleHandler> registrar) {
		registrar.accept(new EnergyClientHandler());
	}
}
