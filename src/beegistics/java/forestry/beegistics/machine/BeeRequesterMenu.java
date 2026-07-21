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
 * Menu for the {@link BeeRequesterBlockEntity}. Exposes the single target-card slot and mirrors the requester's live
 * maintain state - the current {@link RequesterStatus}, the matching count in the network and the editable threshold -
 * to the client through {@link GuiSync} fields. The threshold is edited client-side and pushed back via a client action.
 */
public class BeeRequesterMenu extends AEBaseMenu {
	private static final String ACTION_SET_THRESHOLD = "setThreshold";

	// Machine-side semantic positioned by the style JSON (assets/ae2/screens/beegistics_bee_requester.json).
	public static final SlotSemantic TARGET = SlotSemantics.register("BEEGISTICS_REQUEST_TARGET", false);

	private final BeeRequesterBlockEntity requester;

	@GuiSync(1)
	public int threshold = 1;
	@GuiSync(2)
	public long knownCount = 0;
	/** The requester's {@link RequesterStatus} as an ordinal (GuiSync can't carry the enum directly). */
	@GuiSync(3)
	public int status = RequesterStatus.IDLE.ordinal();

	public BeeRequesterMenu(MenuType<?> menuType, int id, Inventory ip, BeeRequesterBlockEntity requester) {
		super(menuType, id, ip, requester);
		this.requester = requester;

		InternalInventory inv = requester.getInternalInventory();
		addSlot(new AppEngSlot(inv, 0), TARGET);

		createPlayerInventorySlots(ip);

		registerClientAction(ACTION_SET_THRESHOLD, Integer.class, this::setThreshold);
	}

	/** @return the requester's current status as reflected to the client (or the live value server-side). */
	public RequesterStatus status() {
		return RequesterStatus.byOrdinal(this.status);
	}

	/** Sets the maintain threshold - routed to the server via a client action when called client-side. */
	public void setThreshold(int value) {
		if (isClientSide()) {
			sendClientAction(ACTION_SET_THRESHOLD, value);
			return;
		}
		this.requester.setThreshold(value);
	}

	@Override
	public void broadcastChanges() {
		if (isServerSide()) {
			this.threshold = this.requester.getThreshold();
			this.knownCount = this.requester.getKnownCount();
			this.status = this.requester.getStatus().ordinal();
		}
		super.broadcastChanges();
	}
}
