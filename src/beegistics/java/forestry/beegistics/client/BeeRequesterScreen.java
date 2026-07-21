package forestry.beegistics.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import forestry.beegistics.machine.BeeRequesterMenu;

/**
 * Screen for the Bee Requester. Painted in the Beegistics "hive" style ({@link BeegisticsGuiStyle}) like the Apiary
 * Controller (whose texture it reuses for now), but far simpler: a single target-card slot, and a status panel showing
 * the maintain state, the current network count, and the editable maintain threshold.
 */
public class BeeRequesterScreen extends AEBaseScreen<BeeRequesterMenu> {
	private static final int MARGIN = 6;

	// Panel framing the single target-card slot the style JSON places (slot top-left is at 92,30).
	private static final int ZONE_X = 85;
	private static final int ZONE_Y = 24;
	private static final int ZONE_W = 30;
	private static final int ZONE_H = 30;

	// Full-width status panel with a stacked column of readout lines.
	private static final int STATUS_X = MARGIN;
	private static final int STATUS_Y = 66;
	private static final int STATUS_W = 188;
	private static final int STATUS_H = 50;
	private static final int STATUS_LINE_X = 15;
	private static final int STATUS_LINE_DY = 11;
	private static final int STATUS_LINES = 3;

	private static final int TITLE_X = 10;
	private static final int TITLE_Y = 7;

	private EditBox thresholdField;

	public BeeRequesterScreen(BeeRequesterMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
		super(menu, playerInventory, title, style);
	}

	/** Y of status line {@code index}, with the whole {@link #STATUS_LINES}-line block vertically centred in the panel. */
	private int statusLineY(int index) {
		int block = (STATUS_LINES - 1) * STATUS_LINE_DY + this.font.lineHeight;
		int line0 = STATUS_Y + (STATUS_H - block) / 2;
		return line0 + index * STATUS_LINE_DY;
	}

	@Override
	protected void init() {
		super.init();
		this.thresholdField = new EditBox(this.font, this.leftPos + STATUS_LINE_X + 46, this.topPos + statusLineY(2) - 2, 46, 12, Component.translatable("gui.beegistics.bee_requester.threshold"));
		this.thresholdField.setMaxLength(6);
		this.thresholdField.setTextColor(BeegisticsGuiStyle.TEXT_LABEL);
		this.thresholdField.setValue(Integer.toString(this.menu.threshold));
		this.thresholdField.setResponder(this::onThresholdTyped);
		this.thresholdField.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
		addRenderableWidget(this.thresholdField);
	}

	private void onThresholdTyped(String value) {
		if (value.isEmpty()) {
			return;
		}
		try {
			this.menu.setThreshold(Math.max(1, Integer.parseInt(value)));
		} catch (NumberFormatException ignored) {
			// Filtered to digits, but guard against overflow on very long input.
		}
	}

	@Override
	public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTick) {
		super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTick);

		BeegisticsGuiStyle.panel(guiGraphics, offsetX + ZONE_X, offsetY + ZONE_Y, ZONE_W, ZONE_H);
		BeegisticsGuiStyle.panel(guiGraphics, offsetX + STATUS_X, offsetY + STATUS_Y, STATUS_W, STATUS_H);
		drawPlayerInventoryPanel(guiGraphics, offsetX, offsetY);

		for (Slot slot : this.menu.slots) {
			BeegisticsGuiStyle.slot(guiGraphics, offsetX + slot.x - 1, offsetY + slot.y - 1);
		}

		guiGraphics.drawString(this.font, Component.translatable("block.beegistics.bee_requester"), offsetX + TITLE_X, offsetY + TITLE_Y, BeegisticsGuiStyle.TEXT_TITLE, false);

		statusLine(guiGraphics, statusText(), 0, offsetX, offsetY);
		statusLine(guiGraphics, Component.translatable("gui.beegistics.bee_requester.count", this.menu.knownCount), 1, offsetX, offsetY);
		statusLine(guiGraphics, Component.translatable("gui.beegistics.bee_requester.threshold"), 2, offsetX, offsetY);
	}

	/** The maintain-state line: colour-coded per {@link forestry.beegistics.machine.RequesterStatus}. */
	private Component statusText() {
		return switch (this.menu.status()) {
			case IDLE -> Component.translatable("gui.beegistics.bee_requester.status.idle").withStyle(ChatFormatting.GRAY);
			case SCHEDULED -> Component.translatable("gui.beegistics.bee_requester.status.scheduled").withStyle(ChatFormatting.GREEN);
			case CRAFTING -> Component.translatable("gui.beegistics.bee_requester.status.crafting").withStyle(ChatFormatting.GREEN);
			case MISSING -> Component.translatable("gui.beegistics.bee_requester.status.missing").withStyle(ChatFormatting.RED);
			case NO_CPU -> Component.translatable("gui.beegistics.bee_requester.status.no_cpu").withStyle(ChatFormatting.GOLD);
		};
	}

	/** Frames the player inventory + hotbar as one warm panel, sized from the actual slot bounds. */
	private void drawPlayerInventoryPanel(GuiGraphics guiGraphics, int offsetX, int offsetY) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (Slot slot : this.menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				continue;
			}
			minX = Math.min(minX, slot.x);
			minY = Math.min(minY, slot.y);
			maxX = Math.max(maxX, slot.x + 16);
			maxY = Math.max(maxY, slot.y + 16);
		}
		if (minX == Integer.MAX_VALUE) {
			return;
		}
		BeegisticsGuiStyle.panel(guiGraphics, offsetX + minX - 5, offsetY + minY - 5, (maxX - minX) + 10, (maxY - minY) + 10);
	}

	private void statusLine(GuiGraphics g, Component text, int index, int offsetX, int offsetY) {
		g.drawString(this.font, text, offsetX + STATUS_LINE_X, offsetY + statusLineY(index), BeegisticsGuiStyle.TEXT_LABEL, false);
	}
}
