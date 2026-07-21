package forestry.beegistics.machine;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu for the {@link ApiaryControllerBlockEntity}. Exposes the single double row of Bee Pattern card slots and mirrors
 * the controller's live status - whether an apiary is attached, which mode it runs in, and the active job / maintain
 * threshold - to the client via {@link GuiSync} fields. The mode toggle and the standalone threshold are edited
 * client-side and pushed back through client actions.
 */
public class ApiaryControllerMenu extends AEBaseMenu {
	private static final String ACTION_SET_THRESHOLD = "setThreshold";
	private static final String ACTION_SET_MODE = "setMode";

	// Machine-side semantics positioned by the style JSON (assets/ae2/screens/beegistics_apiary_controller.json).
	public static final SlotSemantic PATTERN = SlotSemantics.register("BEEGISTICS_PATTERN", false);

	private final ApiaryControllerBlockEntity controller;

	@GuiSync(1)
	public int usableApiaries = 0;
	@GuiSync(11)
	public int contendedApiaries = 0;
	@GuiSync(2)
	public long targetCount = 0;
	@GuiSync(3)
	public int threshold = 1;
	/** The controller's {@link ControllerMode} as an ordinal (GuiSync can't carry the enum directly). */
	@GuiSync(5)
	public int mode = ControllerMode.DEFAULT.ordinal();
	@GuiSync(6)
	public boolean crafting = false;
	@GuiSync(7)
	public long craftRemaining = 0;
	/** Adjacent apiary climate for the GUI readout: enum ordinals, or -1 when no apiary. */
	@GuiSync(8)
	public int apiaryTemperature = -1;
	@GuiSync(9)
	public int apiaryHumidity = -1;
	@GuiSync(10)
	public boolean apiaryDay = true;
	/** Standalone mode only: whether an apiary is occupied or a matching bee is available to stock, vs. no matching bees. */
	@GuiSync(12)
	public boolean perpetualBreeding = false;

	public ApiaryControllerMenu(MenuType<?> menuType, int id, Inventory ip, ApiaryControllerBlockEntity controller) {
		super(menuType, id, ip, controller);
		this.controller = controller;

		InternalInventory patterns = controller.getInternalInventory();
		for (int slot = 0; slot < patterns.size(); slot++) {
			addSlot(new AppEngSlot(patterns, slot), PATTERN);
		}

		createPlayerInventorySlots(ip);

		registerClientAction(ACTION_SET_THRESHOLD, Integer.class, this::setThreshold);
		registerClientAction(ACTION_SET_MODE, Integer.class, this::setModeOrdinal);
	}

	/** @return the controller's current mode as reflected to the client (or the live value server-side). */
	public ControllerMode mode() {
		return ControllerMode.byOrdinal(this.mode);
	}

	/** Sets the stop-at threshold - routed to the server via a client action when called client-side. */
	public void setThreshold(int value) {
		if (isClientSide()) {
			sendClientAction(ACTION_SET_THRESHOLD, value);
			return;
		}
		this.controller.setTargetThreshold(value);
	}

	/** Advances to the next mode in the cycle (the GUI's single mode button) - routed to the server client-side. */
	public void cycleMode() {
		setMode(mode().next());
	}

	/** Sets the controller's mode - routed to the server via a client action (carrying the ordinal) when called client-side. */
	public void setMode(ControllerMode mode) {
		if (isClientSide()) {
			sendClientAction(ACTION_SET_MODE, mode.ordinal());
			return;
		}
		this.controller.setMode(mode);
	}

	/** Server-side client-action target: applies a mode requested by ordinal from the client. */
	private void setModeOrdinal(int ordinal) {
		this.controller.setMode(ControllerMode.byOrdinal(ordinal));
	}

	@Override
	public void broadcastChanges() {
		if (isServerSide()) {
			this.usableApiaries = this.controller.getUsableApiaryCount();
			this.contendedApiaries = this.controller.getContendedApiaryCount();
			this.targetCount = this.controller.getMatchingTargetCount();
			this.threshold = this.controller.getTargetThreshold();
			this.mode = this.controller.getMode().ordinal();
			this.crafting = this.controller.isBusy();
			this.craftRemaining = this.controller.getCraftRemaining();
			this.apiaryTemperature = this.controller.getApiaryTemperature() == null ? -1 : this.controller.getApiaryTemperature().ordinal();
			this.apiaryHumidity = this.controller.getApiaryHumidity() == null ? -1 : this.controller.getApiaryHumidity().ordinal();
			this.apiaryDay = this.controller.isApiaryDay();
			this.perpetualBreeding = this.controller.isPerpetualBreeding();
		}
		super.broadcastChanges();
	}
}
