package forestry.beegistics.terminal;

import appeng.api.inventories.InternalInventory;

/**
 * Implemented by the {@link ApiaristTerminalPart} so its menu can reach the two-slot analyze inventory (input + output)
 * without the menu depending on the concrete part class.
 */
public interface IApiaristTerminalHost {
	InternalInventory getAnalyzerInventory();
}
