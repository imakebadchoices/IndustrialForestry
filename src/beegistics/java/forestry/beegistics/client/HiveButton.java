package forestry.beegistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A {@link Button} painted in the Beegistics hive style - a warm honey-bordered panel with honey text - so config
 * screens (e.g. the Bee Filter card) match the machine GUIs instead of using the vanilla grey button chrome. The label's
 * own {@code ChatFormatting} colours still win (active/inactive states stay green/red/grey); {@link BeegisticsGuiStyle#TEXT_LABEL}
 * is only the fallback tint.
 */
class HiveButton extends Button {
	private static final int FILL = 0xFF2E2114;
	private static final int FILL_HOVER = 0xFF3E2C18;
	private static final int FILL_DISABLED = 0xFF1C140C;

	HiveButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int fill = !this.active ? FILL_DISABLED : (isHoveredOrFocused() ? FILL_HOVER : FILL);
		guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, fill);
		guiGraphics.renderOutline(getX(), getY(), this.width, this.height, BeegisticsGuiStyle.PANEL_BORDER);

		var font = Minecraft.getInstance().font;
		int textY = getY() + (this.height - font.lineHeight) / 2 + 1;
		guiGraphics.drawCenteredString(font, getMessage(), getX() + this.width / 2, textY, BeegisticsGuiStyle.TEXT_LABEL);
	}
}
