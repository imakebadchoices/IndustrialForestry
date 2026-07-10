package forestry.storage.gui;

import forestry.core.config.Constants;
import forestry.core.gui.GuiForestry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class GuiBackpack extends GuiForestry<ContainerBackpack> {
	// Height of the backing texture. The default GuiForestry blit assumes a 256x256
	// sheet, which is fine until the window grows past 256px tall (the 9x9 tier), so
	// we track the real texture height and blit against it explicitly.
	private final int textureHeight;

	public GuiBackpack(ContainerBackpack container, Inventory inv, Component title) {
		super(getTextureString(container), container, inv, title);
		ContainerBackpack.Size size = container.getSize();

		switch (size) {
			case T2 -> {
				this.imageWidth = 176;
				this.imageHeight = 192;
			}
			case T3 -> {
				this.imageWidth = 176;
				this.imageHeight = 210;
			}
			case T4 -> {
				this.imageWidth = 176;
				this.imageHeight = 264;
			}
			default -> {
			}
		}

		this.textureHeight = this.imageHeight > 256 ? 512 : 256;
	}

	private static String getTextureString(ContainerBackpack container) {
		return switch (container.getSize()) {
			case T2 -> Constants.TEXTURE_PATH_GUI + "/backpack_t2.png";
			case T3 -> Constants.TEXTURE_PATH_GUI + "/backpack_t3.png";
			case T4 -> Constants.TEXTURE_PATH_GUI + "/backpack_t4.png";
			default -> Constants.TEXTURE_PATH_GUI + "/backpack.png";
		};
	}

	@Override
	protected void drawBackground(GuiGraphics transform) {
		transform.blit(this.textureFile, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, this.textureHeight);
	}

	@Override
	protected void addLedgers() {

	}
}
