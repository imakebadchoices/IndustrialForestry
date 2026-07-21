package forestry.beegistics.jei;

import net.minecraft.client.gui.screens.Screen;

import mezz.jei.api.gui.handlers.IGuiProperties;

/**
 * Reports a menu-less Beegistics config screen's panel bounds to JEI (via {@code addGuiScreenHandler}) so JEI keeps its
 * ingredient list open beside the screen and positions it in the free area next to the panel rather than over it.
 */
public record BeeDropGuiProperties(
	Class<? extends Screen> screenClass,
	int guiLeft,
	int guiTop,
	int guiXSize,
	int guiYSize,
	int screenWidth,
	int screenHeight
) implements IGuiProperties {
}
