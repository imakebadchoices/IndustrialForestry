package forestry.beegistics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import forestry.api.genetics.IIndividual;
import forestry.api.genetics.capability.IIndividualHandlerItem;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;

/**
 * An AE2 storage cell inventory for Forestry bees. Bees are held in memory as native {@link AEKey}s (preserving the full
 * {@code GENOME} identity), but persisted through {@link BeeCellContents}, a compact delta encoding, rather than AE2's
 * {@code STORAGE_CELL_INV}.
 *
 * <p><b>Realized-byte accounting.</b> Unlike a stock cell (a fixed count of type "slots"), a bee cell is limited purely
 * by bytes: each distinct bee costs its <em>actual</em> {@link BeeCellContents#entryByteSize serialized size} against the
 * tier's {@link ItemBeeCell#getByteBudget byte budget}, and additional copies of an already-stored bee are free (they
 * only grow a stack count). Because the budget is literal bytes, a filled cell can never exceed that many bytes of
 * contents, so the cell item is hard-bounded in size no matter how genetically diverse it is - which is what keeps a
 * chest full of cells under the network packet limit (see {@code BeeCellSizeTest}).
 */
public class BeeCellInventory implements StorageCell {
	@Nullable
	private final ISaveProvider container;
	private final ItemStack i;
	private final ItemBeeCell cellType;
	private final long byteBudget;

	// Species whitelist derived from the cell's workbench partition config; null = not yet computed, empty = no partition.
	@Nullable
	private Set<ResourceLocation> allowedSpecies;

	@Nullable
	private Object2LongMap<AEKey> storedAmounts;
	// Cached realized byte cost of each distinct stored key, so accounting never re-encodes on the hot path.
	@Nullable
	private Object2LongMap<AEKey> keyBytes;
	private long usedBytes;
	private long storedCount;
	private boolean isPersisted = true;

	private BeeCellInventory(ItemBeeCell cellType, ItemStack stack, @Nullable ISaveProvider container) {
		this.i = stack;
		this.cellType = cellType;
		this.container = container;
		this.byteBudget = cellType.getByteBudget(stack);
	}

	@Nullable
	public static BeeCellInventory createInventory(ItemStack stack, @Nullable ISaveProvider container) {
		Objects.requireNonNull(stack, "Cannot create cell inventory for null itemstack");
		if (!(stack.getItem() instanceof ItemBeeCell cellType)) {
			return null;
		}
		return new BeeCellInventory(cellType, stack, container);
	}

	private Object2LongMap<AEKey> getCellItems() {
		if (this.storedAmounts == null) {
			this.storedAmounts = new Object2LongOpenHashMap<>();
			this.keyBytes = new Object2LongOpenHashMap<>();
			long count = 0;
			long bytes = 0;
			BeeCellContents contents = this.i.getOrDefault(BeegisticsComponents.BEE_CELL_CONTENTS.get(), BeeCellContents.EMPTY);
			for (BeeCellContents.Entry entry : contents.entries()) {
				this.storedAmounts.put(entry.key(), entry.amount());
				long entryBytes = BeeCellContents.entryByteSize(entry.key());
				this.keyBytes.put(entry.key(), entryBytes);
				bytes += entryBytes;
				count += entry.amount();
			}
			this.usedBytes = bytes;
			this.storedCount = count;
		}
		return this.storedAmounts;
	}

	private long getFreeBytes() {
		getCellItems();
		return this.byteBudget - this.usedBytes;
	}

	/**
	 * The species this cell has been partitioned to accept via the AE2 Cell Workbench, derived from the active species of
	 * each example bee placed in the cell's config. Empty means no partition (any bee is accepted). Species-based rather
	 * than exact-key because nearly every analyzed bee is a unique genome, so an exact-key whitelist would be useless.
	 */
	private Set<ResourceLocation> getAllowedSpecies() {
		if (this.allowedSpecies == null) {
			this.allowedSpecies = new HashSet<>();
			for (AEKey key : this.cellType.getConfigInventory(this.i).keySet()) {
				if (key instanceof AEItemKey itemKey) {
					IIndividual individual = IIndividualHandlerItem.getIndividual(itemKey.getReadOnlyStack());
					if (individual != null) {
						this.allowedSpecies.add(individual.getSpecies().id());
					}
				}
			}
		}
		return this.allowedSpecies;
	}

	@Override
	public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
		if (amount == 0 || !(what instanceof AEItemKey itemKey) || !BeeKeys.isBee(what)) {
			return 0;
		}

		// Respect the workbench partition: if configured, only accept bees of a whitelisted species.
		Set<ResourceLocation> allowed = getAllowedSpecies();
		if (!allowed.isEmpty()) {
			IIndividual individual = IIndividualHandlerItem.getIndividual(itemKey.getReadOnlyStack());
			if (individual == null || !allowed.contains(individual.getSpecies().id())) {
				return 0;
			}
		}

		long current = getCellItems().getLong(what);

		// A brand-new bee must fit its full realized cost; extra copies of a stored bee cost nothing.
		if (current <= 0) {
			long cost = BeeCellContents.entryByteSize(itemKey);
			if (cost > getFreeBytes()) {
				return 0;
			}
			if (mode == Actionable.MODULATE) {
				this.keyBytes.put(what, cost);
				this.usedBytes += cost;
			}
		}

		if (mode == Actionable.MODULATE) {
			getCellItems().put(what, current + amount);
			this.storedCount += amount;
			markDirty();
		}
		return amount;
	}

	@Override
	public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
		long current = getCellItems().getLong(what);
		if (current <= 0) {
			return 0;
		}

		long extracted = Math.min(amount, current);
		if (mode == Actionable.MODULATE) {
			if (extracted >= current) {
				getCellItems().remove(what, current);
				this.usedBytes -= this.keyBytes.removeLong(what);
			} else {
				getCellItems().put(what, current - extracted);
			}
			this.storedCount -= extracted;
			markDirty();
		}
		return extracted;
	}

	@Override
	public void getAvailableStacks(KeyCounter out) {
		for (var entry : Object2LongMaps.fastIterable(getCellItems())) {
			out.add(entry.getKey(), entry.getLongValue());
		}
	}

	@Override
	public CellState getStatus() {
		getCellItems();
		if (this.storedCount == 0) {
			return CellState.EMPTY;
		}
		if (getFreeBytes() <= 0) {
			return CellState.FULL;
		}
		return CellState.NOT_EMPTY;
	}

	@Override
	public double getIdleDrain() {
		return 1.0 + this.cellType.getTier().ordinal() * 0.5;
	}

	@Override
	public Component getDescription() {
		return this.i.getHoverName();
	}

	private void markDirty() {
		this.isPersisted = false;
		if (this.container != null) {
			this.container.saveChanges();
		} else {
			// No host to persist us, so write straight to the stack.
			persist();
		}
	}

	@Override
	public void persist() {
		if (this.isPersisted) {
			return;
		}

		List<BeeCellContents.Entry> entries = new ArrayList<>(getCellItems().size());
		for (var entry : Object2LongMaps.fastIterable(getCellItems())) {
			long amount = entry.getLongValue();
			if (amount > 0 && entry.getKey() instanceof AEItemKey itemKey) {
				entries.add(new BeeCellContents.Entry(itemKey, amount));
			}
		}

		if (entries.isEmpty()) {
			this.i.remove(BeegisticsComponents.BEE_CELL_CONTENTS.get());
		} else {
			this.i.set(BeegisticsComponents.BEE_CELL_CONTENTS.get(), new BeeCellContents(entries));
		}

		this.isPersisted = true;
	}
}
