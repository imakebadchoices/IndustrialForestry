package forestry.beegistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import appeng.api.AECapabilities;
import appeng.api.parts.PartModels;
import appeng.api.storage.StorageCells;
import appeng.items.parts.PartModelsHelper;

import forestry.beegistics.client.BeegisticsClientHandler;
import forestry.beegistics.crafting.BeeMutationPatternDecoder;
import forestry.beegistics.machine.ApiaryControllerBlockEntity;
import forestry.beegistics.machine.BeeAnalyzerBlockEntity;
import forestry.beegistics.machine.BeeRequesterBlockEntity;
import forestry.beegistics.network.BeegisticsNetwork;
import forestry.beegistics.terminal.ApiaristTerminalPart;
import forestry.beegistics.terminal.BeegisticsMenus;
import forestry.core.tab.ForestryCreativeTabs;

/**
 * Mod entry point for the standalone Beegistics add-on: Applied Energistics 2 integration for Forestry. Hard
 * dependencies on both {@code forestry} and {@code ae2} (declared in {@code neoforge.mods.toml}) mean this jar can
 * assume both are present - no soft {@code isLoaded} gating. Content is registered under the {@code beegistics}
 * namespace and reaches into Forestry's (public) internals the same way the Extra Bees add-on does.
 */
@Mod(Beegistics.NAMESPACE)
public final class Beegistics {
	public static final String NAMESPACE = "beegistics";

	public Beegistics(IEventBus modBus, ModContainer container) {
		BeegisticsItems.register(modBus);
		BeegisticsBlocks.register(modBus);
		BeegisticsBlockEntities.register(modBus);
		BeegisticsComponents.register(modBus);
		BeegisticsMenus.register(modBus);
		// AE2 freezes its part-model registry after the pre-init phase, so register our part models during construction.
		PartModels.registerModels(PartModelsHelper.createModels(ApiaristTerminalPart.class));
		modBus.addListener(Beegistics::onCommonSetup);
		modBus.addListener(Beegistics::onRegisterCapabilities);
		modBus.addListener(Beegistics::onBuildCreativeTab);
		modBus.addListener(BeegisticsNetwork::register);

		if (FMLEnvironment.dist == Dist.CLIENT) {
			BeegisticsClientHandler.init(modBus);
		}
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			// StorageCells.addCellHandler must run after AE2's own common setup, so defer to the synchronous work queue.
			StorageCells.addCellHandler(BeeCellHandler.INSTANCE);
			// Let AE2 crafting CPUs decode (and persist) our bee-mutation patterns.
			BeeMutationPatternDecoder.register();
			// AE2's AEBaseEntityBlock needs its block-entity type + tickers wired up; work is grid-driven, so no ticker.
			BeegisticsBlocks.BEE_ANALYZER.get().setBlockEntity(
					BeeAnalyzerBlockEntity.class, BeegisticsBlockEntities.BEE_ANALYZER.get(), null, null);
			BeegisticsBlocks.APIARY_CONTROLLER.get().setBlockEntity(
					ApiaryControllerBlockEntity.class, BeegisticsBlockEntities.APIARY_CONTROLLER.get(), null, null);
			BeegisticsBlocks.BEE_REQUESTER.get().setBlockEntity(
					BeeRequesterBlockEntity.class, BeegisticsBlockEntities.BEE_REQUESTER.get(), null, null);
		});
	}

	private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
		// Let adjacent cables discover the analyzer's grid node, and let pipes fill its honey buffer.
		event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BeegisticsBlockEntities.BEE_ANALYZER.get(),
				(be, context) -> be);
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BeegisticsBlockEntities.BEE_ANALYZER.get(),
				(be, context) -> be.getHoneyTank());
		// Let adjacent cables discover the controller's grid node.
		event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BeegisticsBlockEntities.APIARY_CONTROLLER.get(),
				(be, context) -> be);
		// Let adjacent cables discover the requester's grid node.
		event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BeegisticsBlockEntities.BEE_REQUESTER.get(),
				(be, context) -> be);
	}

	private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == ForestryCreativeTabs.APICULTURE.getKey()) {
			for (BeeCellTier tier : BeeCellTier.values()) {
				event.accept(BeegisticsItems.beeCell(tier));
			}
			event.accept(BeegisticsItems.beeFilterCard());
			event.accept(BeegisticsItems.apiaristTerminal());
			event.accept(BeegisticsBlocks.beeAnalyzerItem());
			event.accept(BeegisticsBlocks.apiaryControllerItem());
			event.accept(BeegisticsBlocks.beeRequesterItem());
		}
	}
}
