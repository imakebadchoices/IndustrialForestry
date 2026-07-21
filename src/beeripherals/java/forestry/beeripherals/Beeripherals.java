package forestry.beeripherals;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import dan200.computercraft.api.peripheral.PeripheralCapability;

import forestry.core.features.CoreTiles;

/**
 * Mod entry point for the standalone Beeripherals add-on: CC: Tweaked integration for Forestry. Hard dependencies on
 * both {@code forestry} and {@code computercraft} (declared in {@code neoforge.mods.toml}) mean this jar can assume both
 * are present - no soft {@code isLoaded} gating. It registers the {@link NaturalistChestPeripheral} on Forestry's three
 * naturalist chests so a wired/adjacent computer can wrap them and read their contents.
 */
@Mod(Beeripherals.NAMESPACE)
public final class Beeripherals {
	public static final String NAMESPACE = "beeripherals";

	public Beeripherals(IEventBus modBus, ModContainer container) {
		modBus.addListener(Beeripherals::onRegisterCapabilities);
	}

	private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(PeripheralCapability.get(), CoreTiles.APIARIST_CHEST.tileType(), (tile, side) -> new NaturalistChestPeripheral(tile));
		event.registerBlockEntity(PeripheralCapability.get(), CoreTiles.ARBORIST_CHEST.tileType(), (tile, side) -> new NaturalistChestPeripheral(tile));
		event.registerBlockEntity(PeripheralCapability.get(), CoreTiles.LEPIDOPTERIST_CHEST.tileType(), (tile, side) -> new NaturalistChestPeripheral(tile));
	}
}
