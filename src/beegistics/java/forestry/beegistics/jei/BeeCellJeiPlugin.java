package forestry.beegistics.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import forestry.beegistics.BeeCellTier;
import forestry.beegistics.Beegistics;
import forestry.beegistics.BeegisticsBlocks;
import forestry.beegistics.BeegisticsItems;
import forestry.beegistics.client.ApiaristTerminalScreen;
import forestry.beegistics.client.BeeFilterScreen;
import forestry.beegistics.client.JeiBeeDropScreen;

import net.minecraft.client.gui.screens.Screen;

/**
 * Adds a JEI info page describing the bee cells. JEI is an optional dependency, so this plugin simply won't be loaded
 * when JEI is absent.
 */
@JeiPlugin
public class BeeCellJeiPlugin implements IModPlugin {
	@Override
	public ResourceLocation getPluginUid() {
		return ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "jei");
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<ItemStack> cells = new ArrayList<>();
		for (BeeCellTier tier : BeeCellTier.values()) {
			cells.add(new ItemStack(BeegisticsItems.beeCell(tier)));
		}
		registration.addIngredientInfo(cells, VanillaTypes.ITEM_STACK, Component.translatable("info.beegistics.bee_cell"));

		registration.addIngredientInfo(new ItemStack(BeegisticsBlocks.beeAnalyzerItem()), VanillaTypes.ITEM_STACK,
				Component.translatable("info.beegistics.bee_analyzer"));
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		// Report the Apiarist's Terminal side column (analyzer box + genetics panel) as an extra GUI area so JEI's item
		// list does not overlap it.
		registration.addGuiContainerHandler(ApiaristTerminalScreen.class, new IGuiContainerHandler<>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(ApiaristTerminalScreen screen) {
				return List.of(screen.getPanelArea());
			}
		});

		// The Bee Pattern card is a plain (menu-less) Screen, so JEI does not show its ingredient list beside it by
		// default. Report the panel's bounds so JEI stays open next to it, and expose the drop slot as a ghost-ingredient
		// target (drag a bee from the list onto it to set the filter).
		registerBeeDropScreen(registration, BeeFilterScreen.class);
	}

	private static <T extends Screen & JeiBeeDropScreen> void registerBeeDropScreen(IGuiHandlerRegistration registration, Class<T> screenClass) {
		registration.addGuiScreenHandler(screenClass, screen -> new BeeDropGuiProperties(
				screenClass, screen.panelLeft(), screen.panelTop(), screen.panelWidth(), screen.panelHeight(), screen.width, screen.height));
		registration.addGhostIngredientHandler(screenClass, new BeeDropGhostHandler<>());
	}
}
