package forestry.beegistics;

import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;

import forestry.core.items.ItemForestry;

/**
 * An Applied Energistics 2 storage cell that holds Forestry bees. Bees are stored natively as {@code AEItemKey}s
 * (preserving the full {@code GENOME} identity) and the cell is driven by a custom {@link BeeCellHandler}/
 * {@link BeeCellInventory}.
 *
 * <p>Contents are persisted compactly as a {@link BeeCellContents} component (a genome delta encoding) instead of AE2's
 * {@code STORAGE_CELL_INV}, and each stored bee is charged its <em>realized</em> serialized size against the cell's byte
 * budget. A pure-species bee costs a handful of bytes; a heavily mutated mated queen a few dozen; identical bees stack
 * for free. The budget is literal bytes and is capped at {@link #MAX_SAFE_BYTES} so that a chest full of full cells can
 * never exceed the network packet limit.
 */
public class ItemBeeCell extends ItemForestry implements ICellWorkbenchItem {
	/**
	 * The largest byte budget any single cell is allowed, regardless of tier. A double chest (54 slots) of maxed cells
	 * is the largest container-content packet a player can trigger, so each cell must stay well under {@code 2 MiB / 54
	 * ≈ 38 KiB} of realized contents; this caps a cell at 32 KiB, leaving comfortable headroom for framing.
	 */
	public static final int MAX_SAFE_BYTES = 32 * 1024;

	private final BeeCellTier tier;

	public ItemBeeCell(BeeCellTier tier) {
		super(new Item.Properties().stacksTo(1));
		this.tier = tier;
	}

	public BeeCellTier getTier() {
		return this.tier;
	}

	/**
	 * @return The cell's byte budget: the tier's nominal size, capped at {@link #MAX_SAFE_BYTES} so no single cell can
	 * grow large enough to break the packet limit.
	 */
	public long getByteBudget(ItemStack stack) {
		return Math.min(this.tier.getBytes(), MAX_SAFE_BYTES);
	}

	// --- ICellWorkbenchItem: lets the AE2 Cell Workbench partition the cell ------------------------------------------
	// A bee cell partitions by *species* (see BeeCellInventory): the example bees placed in the config below define which
	// species the cell will accept. Exact-key partitioning would be meaningless for bees, since nearly every analyzed bee
	// is a unique genome key. Fuzzy mode and upgrades are therefore unused.

	@Override
	public ConfigInventory getConfigInventory(ItemStack is) {
		return CellConfig.create(Set.of(AEKeyType.items()), is);
	}

	@Override
	public IUpgradeInventory getUpgrades(ItemStack is) {
		return UpgradeInventories.empty();
	}

	@Override
	public FuzzyMode getFuzzyMode(ItemStack is) {
		return FuzzyMode.IGNORE_ALL;
	}

	@Override
	public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
		// Bee partitioning is species-based, so there is no fuzzy tier to configure.
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);

		BeeCellContents contents = stack.getOrDefault(BeegisticsComponents.BEE_CELL_CONTENTS.get(), BeeCellContents.EMPTY);

		tooltip.add(Component.translatable("tooltip.beegistics.bee_cell.bytes", contents.usedBytes(), getByteBudget(stack))
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.beegistics.bee_cell.stored", contents.totalCount(), contents.typeCount())
			.withStyle(ChatFormatting.GRAY));
	}
}
