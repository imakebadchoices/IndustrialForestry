package forestry.beegistics.machine;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;

/**
 * Menu for the {@link ApiaryControllerBlockEntity}. Exposes both slot groups - the Autocraft double row of Bee Pattern
 * cards and the two Standalone breeding-card slots (princess + drone) - and mirrors the controller's live status
 * (whether an apiary is attached, which mode it runs in, and the active job) to the client via {@link GuiSync} fields.
 * Only the group belonging to the current mode is kept enabled (the {@link ApiaryControllerScreen} hides the other), so
 * a card is only ever placed or shift-clicked into the active mode's slots. The mode toggle is edited client-side and
 * pushed back through a client action.
 */
public class ApiaryControllerMenu extends AEBaseMenu {
	private static final String ACTION_SET_MODE = "setMode";

	// Machine-side semantics positioned by the style JSON (assets/ae2/screens/beegistics_apiary_controller.json).
	public static final SlotSemantic PATTERN = SlotSemantics.register("BEEGISTICS_PATTERN", false);
	public static final SlotSemantic BREEDING = SlotSemantics.register("BEEGISTICS_BREEDING", false);

	private final ApiaryControllerBlockEntity controller;
	/** The Autocraft grid slots and the two Standalone breeding-card slots; only the active mode's group stays enabled. */
	private final AppEngSlot[] patternSlots;
	private final AppEngSlot[] breedingSlots;

	@GuiSync(1)
	public int usableApiaries = 0;
	@GuiSync(11)
	public int contendedApiaries = 0;
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
	/** Standalone mode only: whether an apiary is occupied or a matching bee is available to stock, vs. no matching bees. */
	@GuiSync(12)
	public boolean perpetualBreeding = false;

	public ApiaryControllerMenu(MenuType<?> menuType, int id, Inventory ip, ApiaryControllerBlockEntity controller) {
		super(menuType, id, ip, controller);
		this.controller = controller;

		InternalInventory patterns = controller.getPatternInventory();
		this.patternSlots = new AppEngSlot[patterns.size()];
		for (int slot = 0; slot < patterns.size(); slot++) {
			AppEngSlot s = new AppEngSlot(patterns, slot);
			addSlot(s, PATTERN);
			this.patternSlots[slot] = s;
		}

		// The princess slot is added first so a stage-agnostic card shift-clicks into it before the drone slot.
		AppEngSlot princess = new AppEngSlot(controller.getPrincessCardInventory(), 0);
		princess.setEmptyTooltip(() -> List.of(Component.translatable("gui.beegistics.apiary_controller.slot.princess")));
		AppEngSlot drone = new AppEngSlot(controller.getDroneCardInventory(), 0);
		drone.setEmptyTooltip(() -> List.of(Component.translatable("gui.beegistics.apiary_controller.slot.drone")));
		addSlot(princess, BREEDING);
		addSlot(drone, BREEDING);
		this.breedingSlots = new AppEngSlot[]{princess, drone};

		createPlayerInventorySlots(ip);

		registerClientAction(ACTION_SET_MODE, Integer.class, this::setModeOrdinal);
	}

	/** @return the controller's current mode as reflected to the client (or the live value server-side). */
	public ControllerMode mode() {
		return ControllerMode.byOrdinal(this.mode);
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
			this.mode = this.controller.getMode().ordinal();
			this.crafting = this.controller.isBusy();
			this.craftRemaining = this.controller.getCraftRemaining();
			this.apiaryTemperature = this.controller.getApiaryTemperature() == null ? -1 : this.controller.getApiaryTemperature().ordinal();
			this.apiaryHumidity = this.controller.getApiaryHumidity() == null ? -1 : this.controller.getApiaryHumidity().ordinal();
			this.perpetualBreeding = this.controller.isPerpetualBreeding();
			// Only the current mode's slot group accepts cards: disabling the other rejects placement and shift-click
			// (mayPlace) server-side, so a card can never land in a hidden slot. The screen mirrors this visually.
			boolean autocraft = this.controller.getMode() == ControllerMode.AUTOCRAFT;
			for (AppEngSlot slot : this.patternSlots) {
				slot.setSlotEnabled(autocraft);
			}
			for (AppEngSlot slot : this.breedingSlots) {
				slot.setSlotEnabled(!autocraft);
			}
		}
		super.broadcastChanges();
	}
}
