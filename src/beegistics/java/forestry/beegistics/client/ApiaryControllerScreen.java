package forestry.beegistics.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.function.Supplier;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.beegistics.machine.ApiaryControllerMenu;
import forestry.beegistics.machine.ControllerMode;

/**
 * Screen for the Apiary Controller. Built on {@link AEBaseScreen} so it wires up to AE2's slot handling, but painted in
 * the Beegistics "hive" style ({@link BeegisticsGuiStyle}) so it reads as a sibling of the Apiarist's Terminal rather
 * than a stock AE2 machine GUI: dark warm-brown panels, honey-brown borders and honey text on a warm background.
 *
 * <p>The controller holds a single double row of Bee Pattern cards (positioned by the style JSON); their wells, the
 * panel frame, a mode cycle button and the status readout are drawn here in the background layer. The button cycles the
 * three {@link ControllerMode modes}; the status shows the apiary link plus a per-mode readout - the maintain
 * threshold + count (Requester), the perpetual-breeding state (Standalone), or the active craft job (Autocraft), each
 * with the apiary climate. The status is a single full-width column so no line ever overflows the panel.
 */
public class ApiaryControllerScreen extends AEBaseScreen<ApiaryControllerMenu> {
	private static final int MARGIN = 6;

	// The double-row card region: the panel behind the two rows of nine slots the style JSON places. ZONE_Y/ZONE_H are
	// tuned so the 36px-tall well block (two 18px rows, tops at slot y=30 in the style JSON) sits vertically centred with
	// even ~4px margins - matching the horizontal margins - rather than kissing the bottom border.
	private static final int ZONE_X = 13;
	private static final int ZONE_Y = 24;
	private static final int ZONE_W = 174;
	private static final int ZONE_H = 46;

	// Full-width status panel with a single stacked column of readout lines.
	private static final int STATUS_X = MARGIN;
	private static final int STATUS_Y = 76;
	private static final int STATUS_W = 188;
	private static final int STATUS_H = 50;
	private static final int STATUS_LINE_X = 15;
	private static final int STATUS_LINE_DY = 11;
	private static final int STATUS_LINES = 4;

	private static final int TITLE_X = 10;
	private static final int TITLE_Y = 7;

	// The mode toggle button, right-aligned on the header row.
	private static final int MODE_BTN_W = 66;
	private static final int MODE_BTN_H = 14;
	private static final int MODE_BTN_X = 200 - MARGIN - MODE_BTN_W;
	private static final int MODE_BTN_Y = 6;

	private EditBox thresholdField;
	private Button modeButton;

	public ApiaryControllerScreen(ApiaryControllerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
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
		this.thresholdField = new EditBox(this.font, this.leftPos + STATUS_LINE_X + 46, this.topPos + statusLineY(3) - 2, 46, 12, Component.translatable("gui.beegistics.apiary_controller.threshold"));
		this.thresholdField.setMaxLength(6);
		this.thresholdField.setTextColor(BeegisticsGuiStyle.TEXT_LABEL);
		this.thresholdField.setValue(Integer.toString(this.menu.threshold));
		this.thresholdField.setResponder(this::onThresholdTyped);
		this.thresholdField.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
		addRenderableWidget(this.thresholdField);

		// A plain Button keeps the "focused" highlight after a click until focus moves elsewhere, so the button stays lit
		// once the mouse leaves it. Report never-focused so it lights on hover only (isHoveredOrFocused collapses to hover).
		this.modeButton = new Button(this.leftPos + MODE_BTN_X, this.topPos + MODE_BTN_Y, MODE_BTN_W, MODE_BTN_H, modeLabel(), b -> this.menu.cycleMode(), Supplier::get) {
			@Override
			public boolean isFocused() {
				return false;
			}
		};
		addRenderableWidget(this.modeButton);
	}

	/** The single cycle button's caption: the name of the mode the controller is currently in. */
	private Component modeLabel() {
		String key = switch (this.menu.mode()) {
			case REQUESTER -> "gui.beegistics.apiary_controller.mode.requester";
			case STANDALONE -> "gui.beegistics.apiary_controller.mode.standalone";
			case AUTOCRAFT -> "gui.beegistics.apiary_controller.mode.autocraft";
		};
		return Component.translatable(key);
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

		// The editable maintain threshold only applies to Requester mode; the button caption mirrors the live mode.
		this.thresholdField.visible = this.menu.mode() == ControllerMode.REQUESTER;
		this.modeButton.setMessage(modeLabel());

		// The card region panel, the status panel, and a backdrop behind the player inventory.
		BeegisticsGuiStyle.panel(guiGraphics, offsetX + ZONE_X, offsetY + ZONE_Y, ZONE_W, ZONE_H);
		BeegisticsGuiStyle.panel(guiGraphics, offsetX + STATUS_X, offsetY + STATUS_Y, STATUS_W, STATUS_H);
		drawPlayerInventoryPanel(guiGraphics, offsetX, offsetY);

		// A warm slot well behind every slot (machine slots and player inventory alike).
		for (Slot slot : this.menu.slots) {
			BeegisticsGuiStyle.slot(guiGraphics, offsetX + slot.x - 1, offsetY + slot.y - 1);
		}

		// One top-left title only (the block name); the double row of card slots below is self-evidently the patterns, so a
		// separate "Bee Patterns" sub-header here would just overlap the title in the same corner.
		guiGraphics.drawString(this.font, Component.translatable("block.beegistics.apiary_controller"), offsetX + TITLE_X, offsetY + TITLE_Y, BeegisticsGuiStyle.TEXT_TITLE, false);

		// Status, line 0 (shared by every mode): the apiary link (count linked, contention warning, or none).
		statusLine(guiGraphics, apiaryStatus(), 0, offsetX, offsetY);

		switch (this.menu.mode()) {
			case REQUESTER -> drawRequesterStatus(guiGraphics, offsetX, offsetY);
			case STANDALONE -> drawStandaloneStatus(guiGraphics, offsetX, offsetY);
			case AUTOCRAFT -> drawCraftStatus(guiGraphics, offsetX, offsetY);
		}
	}

	/**
	 * Line 0 of the status column: how many apiaries this controller drives, a contention warning when an adjacent
	 * apiary is shared with another controller (and so used by neither), or "no apiary".
	 */
	private Component apiaryStatus() {
		if (this.menu.usableApiaries > 0) {
			return Component.translatable("gui.beegistics.apiary_controller.apiary.connected", this.menu.usableApiaries).withStyle(ChatFormatting.GREEN);
		}
		if (this.menu.contendedApiaries > 0) {
			return Component.translatable("gui.beegistics.apiary_controller.apiary.contended").withStyle(ChatFormatting.GOLD);
		}
		return Component.translatable("gui.beegistics.apiary_controller.apiary.none").withStyle(ChatFormatting.RED);
	}

	/** Requester mode: job state (breeding or idle), maintained count, then the editable threshold row. */
	private void drawRequesterStatus(GuiGraphics guiGraphics, int offsetX, int offsetY) {
		Component state = this.menu.crafting
				? Component.translatable("gui.beegistics.apiary_controller.crafting", this.menu.craftRemaining).withStyle(ChatFormatting.GREEN)
				: Component.translatable("gui.beegistics.apiary_controller.state.paused").withStyle(ChatFormatting.GRAY);
		statusLine(guiGraphics, state, 1, offsetX, offsetY);
		statusLine(guiGraphics, Component.translatable("gui.beegistics.apiary_controller.count", this.menu.targetCount), 2, offsetX, offsetY);
		statusLine(guiGraphics, Component.translatable("gui.beegistics.apiary_controller.threshold"), 3, offsetX, offsetY);
	}

	/**
	 * Standalone (perpetual) mode: idle (no apiary), actively breeding (an apiary is occupied or a matching bee was
	 * stocked), or - a linked apiary with nothing to feed it - a red warning that no bees match the loaded filters. The
	 * old readout showed a green "Breeding" whenever an apiary was merely linked, even with an empty apiary and no stock.
	 */
	private void drawStandaloneStatus(GuiGraphics guiGraphics, int offsetX, int offsetY) {
		Component state;
		if (this.menu.usableApiaries <= 0) {
			state = Component.translatable("gui.beegistics.apiary_controller.perpetual.idle").withStyle(ChatFormatting.GRAY);
		} else if (this.menu.perpetualBreeding) {
			state = Component.translatable("gui.beegistics.apiary_controller.perpetual.active").withStyle(ChatFormatting.GREEN);
		} else {
			state = Component.translatable("gui.beegistics.apiary_controller.perpetual.no_bees").withStyle(ChatFormatting.RED);
		}
		statusLine(guiGraphics, state, 1, offsetX, offsetY);
		drawClimate(guiGraphics, 2, offsetX, offsetY);
	}

	/** Autocraft mode: job state, then the apiary climate + day/night. */
	private void drawCraftStatus(GuiGraphics guiGraphics, int offsetX, int offsetY) {
		Component job = this.menu.crafting
				? Component.translatable("gui.beegistics.apiary_controller.crafting", this.menu.craftRemaining).withStyle(ChatFormatting.GREEN)
				: Component.translatable("gui.beegistics.apiary_controller.idle").withStyle(ChatFormatting.GRAY);
		statusLine(guiGraphics, job, 1, offsetX, offsetY);
		drawClimate(guiGraphics, 2, offsetX, offsetY);
	}

	/** Draws the driven apiary's climate on status line {@code index} and its day/night on {@code index + 1} (nothing if no apiary). */
	private void drawClimate(GuiGraphics guiGraphics, int index, int offsetX, int offsetY) {
		if (this.menu.usableApiaries > 0 && this.menu.apiaryTemperature >= 0 && this.menu.apiaryHumidity >= 0) {
			String temp = TemperatureType.VALUES.get(this.menu.apiaryTemperature).name();
			String humid = HumidityType.VALUES.get(this.menu.apiaryHumidity).name();
			statusLine(guiGraphics, Component.translatable("gui.beegistics.apiary_controller.climate", temp, humid), index, offsetX, offsetY);
			Component time = Component.translatable(this.menu.apiaryDay
					? "gui.beegistics.apiary_controller.time.day"
					: "gui.beegistics.apiary_controller.time.night");
			statusLine(guiGraphics, Component.translatable("gui.beegistics.apiary_controller.time", time), index + 1, offsetX, offsetY);
		}
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
