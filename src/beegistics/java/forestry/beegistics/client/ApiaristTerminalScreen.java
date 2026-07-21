package forestry.beegistics.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.util.prioritylist.IPartitionList;

import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.beegistics.BeeKeys;
import forestry.beegistics.terminal.ApiaristTerminalMenu;

/**
 * The Apiarist's Terminal screen. It is the stock {@link MEStorageScreen} plus a column drawn beside the grid that holds
 * three things:
 * <ul>
 *     <li>a <b>visible-types toggle</b> - "Bees only" (the default for this terminal) vs. every key. Implemented as a
 *     client-side {@link IPartitionList} on the repo, so toggling is instant and needs no server round-trip;</li>
 *     <li>an <b>analyzer box</b> - the input/output slots of the host part's analyze inventory;</li>
 *     <li>a <b>genetics panel</b> - the hovered bee's chromosomes, decoded client-side, active/inactive alleles.</li>
 * </ul>
 *
 * <p>The column has a fixed position and width so a JEI handler ({@code BeeCellJeiPlugin}) can report it as an extra GUI
 * area and keep JEI's item list from overlapping it.
 */
public class ApiaristTerminalScreen extends MEStorageScreen<ApiaristTerminalMenu> {
	private static final int COLUMN_GAP = 6;
	private static final int PANEL_WIDTH = 174;
	private static final int PANEL_PAD = 6;
	private static final int LINE_HEIGHT = 10;

	private static final int TOGGLE_Y = 0;
	private static final int TOGGLE_H = 16;
	private static final int ANALYZER_TOP = TOGGLE_H + 4;
	private static final int ANALYZER_TITLE_Y = ANALYZER_TOP + 6;
	private static final int ANALYZER_SLOTS_Y = ANALYZER_TOP + 18;
	private static final int SLOT_SPACING = 44;
	private static final int GENETICS_Y = ANALYZER_SLOTS_Y + BeegisticsGuiStyle.SLOT_SIZE + 10;

	/** This terminal defaults to showing bees only; the toggle in the column flips it. */
	private boolean beesOnly = true;

	@Nullable
	private AEItemKey selectedBee;

	public ApiaristTerminalScreen(ApiaristTerminalMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
		super(menu, playerInventory, title, style);
	}

	@Override
	public void init() {
		super.init();
		positionAnalyzerSlots();
		applyVisibleTypes();
	}

	/** GUI-relative x where the side column begins. */
	private int columnX() {
		return this.imageWidth + COLUMN_GAP;
	}

	private void positionAnalyzerSlots() {
		Slot input = this.menu.getAnalyzerInputSlot();
		Slot output = this.menu.getAnalyzerOutputSlot();
		if (input == null || output == null) {
			return;
		}
		int bx = columnX() + PANEL_PAD;
		input.x = bx + 1;
		input.y = ANALYZER_SLOTS_Y + 1;
		output.x = bx + SLOT_SPACING + 1;
		output.y = ANALYZER_SLOTS_Y + 1;
	}

	// --- Visible types (bees only) ---

	@Override
	protected IPartitionList createPartitionList(List<ItemStack> viewCells) {
		IPartitionList viewCellList = super.createPartitionList(viewCells);
		return this.beesOnly ? new BeeOnlyPartitionList(viewCellList) : viewCellList;
	}

	private void applyVisibleTypes() {
		this.repo.setPartitionList(createPartitionList(this.menu.getViewCells()));
		this.repo.updateView();
	}

	private Rect2i toggleBounds() {
		return new Rect2i(this.leftPos + columnX(), this.topPos + TOGGLE_Y, PANEL_WIDTH, TOGGLE_H);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Rect2i toggle = toggleBounds();
		if (button == 0 && BeegisticsGuiStyle.contains(toggle, mouseX, mouseY)) {
			this.beesOnly = !this.beesOnly;
			applyVisibleTypes();
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	// --- Rendering ---

	@Override
	public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
		super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

		int colX = offsetX + columnX();
		drawToggle(guiGraphics, colX, offsetY + TOGGLE_Y, mouseX, mouseY);
		drawAnalyzerBox(guiGraphics, colX, offsetY + ANALYZER_TOP);

		updateSelection();
		if (this.selectedBee != null) {
			drawGeneticsInfo(guiGraphics, colX, offsetY + GENETICS_Y, this.selectedBee);
		}
	}

	private void drawToggle(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
		boolean hover = BeegisticsGuiStyle.contains(toggleBounds(), mouseX, mouseY);
		guiGraphics.fill(x, y, x + PANEL_WIDTH, y + TOGGLE_H, hover ? BeegisticsGuiStyle.PANEL_HOVER : BeegisticsGuiStyle.PANEL_BG);
		guiGraphics.renderOutline(x, y, PANEL_WIDTH, TOGGLE_H, BeegisticsGuiStyle.PANEL_BORDER);

		Component label = Component.translatable("gui.beegistics.apiarist_terminal.show").append(": ")
				.append(Component.translatable(this.beesOnly
						? "gui.beegistics.apiarist_terminal.show.bees"
						: "gui.beegistics.apiarist_terminal.show.all").withStyle(ChatFormatting.YELLOW));
		guiGraphics.drawString(this.font, label, x + PANEL_PAD, y + (TOGGLE_H - 8) / 2, BeegisticsGuiStyle.TEXT_LABEL, false);
	}

	private void drawAnalyzerBox(GuiGraphics guiGraphics, int colX, int colY) {
		int height = 18 + BeegisticsGuiStyle.SLOT_SIZE + PANEL_PAD;
		guiGraphics.fill(colX, colY, colX + PANEL_WIDTH, colY + height, BeegisticsGuiStyle.PANEL_BG);
		guiGraphics.renderOutline(colX, colY, PANEL_WIDTH, height, BeegisticsGuiStyle.PANEL_BORDER);

		guiGraphics.drawString(this.font, Component.translatable("gui.beegistics.apiarist_terminal.analyzer"),
				colX + PANEL_PAD, colY + 6, BeegisticsGuiStyle.TEXT_HEADER, false);

		int bx = colX + PANEL_PAD;
		int by = colY + 18;
		BeegisticsGuiStyle.slot(guiGraphics, bx, by);
		BeegisticsGuiStyle.slot(guiGraphics, bx + SLOT_SPACING, by);
		guiGraphics.drawString(this.font, "→", bx + BeegisticsGuiStyle.SLOT_SIZE + 8, by + 5, BeegisticsGuiStyle.TEXT_MUTED, false);
	}

	private void updateSelection() {
		if (this.hoveredSlot instanceof RepoSlot repoSlot) {
			GridInventoryEntry entry = repoSlot.getEntry();
			if (entry != null && entry.getWhat() instanceof AEItemKey itemKey && BeeKeys.isBee(itemKey)) {
				this.selectedBee = itemKey;
			}
		}
	}

	private void drawGeneticsInfo(GuiGraphics guiGraphics, int colX, int colY, AEItemKey bee) {
		ItemStack stack = bee.getReadOnlyStack();
		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		if (individual == null) {
			return;
		}

		List<Component> lines = buildLines(stack, individual);
		int height = PANEL_PAD * 2 + lines.size() * LINE_HEIGHT;

		guiGraphics.fill(colX, colY, colX + PANEL_WIDTH, colY + height, BeegisticsGuiStyle.PANEL_BG);
		guiGraphics.renderOutline(colX, colY, PANEL_WIDTH, height, BeegisticsGuiStyle.PANEL_BORDER);

		int textX = colX + PANEL_PAD;
		int textY = colY + PANEL_PAD;
		for (Component line : lines) {
			guiGraphics.drawString(this.font, line, textX, textY, 0xFFFFFF, false);
			textY += LINE_HEIGHT;
		}
	}

	/**
	 * @return The absolute bounds of the whole side column, so JEI leaves room for it. Fixed height (the toggle and
	 * analyzer box are always visible), independent of whether a bee is currently hovered.
	 */
	public Rect2i getPanelArea() {
		return new Rect2i(this.leftPos + columnX(), this.topPos, PANEL_WIDTH, this.imageHeight);
	}

	private static List<Component> buildLines(ItemStack stack, IIndividual individual) {
		List<Component> lines = new ArrayList<>();
		lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.YELLOW));

		if (!individual.isAnalyzed()) {
			lines.add(Component.literal(individual.getSpecies().getDisplayName().getString()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("gui.beegistics.apiarist_terminal.unanalyzed").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return lines;
		}

		IGenome genome = individual.getGenome();
		for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : genome.getChromosomes().entrySet()) {
			IChromosome<?> chromosome = entry.getKey();
			AllelePair<?> pair = entry.getValue();

			MutableComponent line = Component.translatable(chromosome.chromosomeTranslationKey())
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(alleleName(chromosome, pair.active()).withStyle(ChatFormatting.WHITE));

			if (!Objects.equals(pair.active().value(), pair.inactive().value())) {
				line.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY))
						.append(alleleName(chromosome, pair.inactive()).withStyle(ChatFormatting.GRAY));
			}
			lines.add(line);
		}
		return lines;
	}

	private static MutableComponent alleleName(IChromosome<?> chromosome, Allele<?> allele) {
		@SuppressWarnings("unchecked")
		IChromosome<Object> typed = (IChromosome<Object>) chromosome;
		Object value = allele.value();
		MutableComponent name = Component.translatableWithFallback(typed.translationKey(value), String.valueOf(value));
		return allele.dominant() ? name.withStyle(ChatFormatting.BOLD) : name;
	}

	/** A partition list that only lists bees, further narrowed by any active view-cell filter. */
	private static final class BeeOnlyPartitionList implements IPartitionList {
		@Nullable
		private final IPartitionList viewCells;

		BeeOnlyPartitionList(@Nullable IPartitionList viewCells) {
			this.viewCells = viewCells;
		}

		@Override
		public boolean isListed(AEKey what) {
			if (!BeeKeys.isBee(what)) {
				return false;
			}
			return this.viewCells == null || this.viewCells.isEmpty() || this.viewCells.isListed(what);
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public Iterable<AEKey> getItems() {
			// Predicate-based (all bees), so there is no finite key set to enumerate; defer to the view-cell list if any.
			return this.viewCells == null ? List.of() : this.viewCells.getItems();
		}
	}
}
