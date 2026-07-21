package forestry.beegistics.client;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by the menu-less Bee Filter card screen so it can accept a bee dragged from JEI's ingredient list. The JEI
 * handlers (see the beegistics JEI plugin) report the panel bounds so JEI stays open beside the screen and expose the
 * drop slot as a ghost-ingredient target. No JEI types appear here, so the screen stays loadable when JEI is absent.
 */
public interface JeiBeeDropScreen {
	int panelLeft();

	int panelTop();

	int panelWidth();

	int panelHeight();

	/** @return whether a bee may be dropped right now (e.g. the tab that owns the slot is showing). */
	boolean acceptsBeeDrop();

	/** @return the screen-space rectangle of the drop slot. */
	Rect2i beeDropRect();

	/** @return whether the given stack is a Forestry bee. */
	boolean isBeeStack(ItemStack stack);

	/** Handle a bee dropped onto (or dragged into) the drop slot. */
	void acceptBeeDrop(ItemStack stack);
}
