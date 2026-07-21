package forestry.beegistics;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

/**
 * Teaches AE2 to treat {@link ItemBeeCell}s as storage cells. Registered via {@code StorageCells.addCellHandler} during
 * common setup (see {@link Beegistics}).
 */
public class BeeCellHandler implements ICellHandler {
	public static final BeeCellHandler INSTANCE = new BeeCellHandler();

	private BeeCellHandler() {
	}

	@Override
	public boolean isCell(ItemStack is) {
		return is.getItem() instanceof ItemBeeCell;
	}

	@Nullable
	@Override
	public StorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
		if (!isCell(is)) {
			return null;
		}
		return BeeCellInventory.createInventory(is, host);
	}
}
