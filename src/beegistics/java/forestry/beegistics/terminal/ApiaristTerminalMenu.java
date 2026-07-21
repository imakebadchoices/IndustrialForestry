package forestry.beegistics.terminal;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ITerminalHost;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu for the Apiarist's Terminal. It is an {@link MEStorageMenu} - inheriting all grid monitoring, search, sorting,
 * and extraction - plus two extra slots (an analyze input and output) backed by the host part's analyze inventory. The
 * separate menu type is what lets the client bind our own {@link ApiaristTerminalScreen} (genetics panel + analyzer box)
 * instead of the stock terminal screen.
 */
public class ApiaristTerminalMenu extends MEStorageMenu {
	// Custom semantics deliberately absent from the terminal style JSON, so AEBaseScreen leaves their slot coordinates
	// alone and our screen positions them itself (beside the grid, in the genetics column).
	public static final SlotSemantic ANALYZER_INPUT = SlotSemantics.register("BEEGISTICS_ANALYZER_INPUT", false);
	public static final SlotSemantic ANALYZER_OUTPUT = SlotSemantics.register("BEEGISTICS_ANALYZER_OUTPUT", false);

	private final Slot analyzerInputSlot;
	private final Slot analyzerOutputSlot;

	public ApiaristTerminalMenu(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host) {
		super(menuType, id, ip, host);

		if (host instanceof IApiaristTerminalHost analyzerHost) {
			InternalInventory inv = analyzerHost.getAnalyzerInventory();
			this.analyzerInputSlot = new AppEngSlot(inv, ApiaristTerminalPart.SLOT_INPUT);
			this.analyzerOutputSlot = new AppEngSlot(inv, ApiaristTerminalPart.SLOT_OUTPUT);
			addSlot(this.analyzerInputSlot, ANALYZER_INPUT);
			addSlot(this.analyzerOutputSlot, ANALYZER_OUTPUT);
		} else {
			this.analyzerInputSlot = null;
			this.analyzerOutputSlot = null;
		}
	}

	public Slot getAnalyzerInputSlot() {
		return this.analyzerInputSlot;
	}

	public Slot getAnalyzerOutputSlot() {
		return this.analyzerOutputSlot;
	}
}
