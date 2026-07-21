package forestry.beegistics;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import forestry.api.genetics.IGenome;
import forestry.beegistics.crafting.BeeGenomeMutationPattern;
import forestry.core.items.ItemForestry;

/**
 * An Applied Energistics 2 style configuration card that encodes a {@link BeeFilter} in its
 * {@link BeegisticsComponents#BEE_FILTER} data component. This is the reusable primitive that bee-aware automation
 * consumes: terminals search by it, level emitters watch for matching keys, and the apiary controller uses it to pull
 * the right princess/drone from the network.
 *
 * <p>The card itself is dumb - it only carries the predicate. Configuring the filter (a GUI) and the parts that read it
 * are separate pieces of the add-on.
 */
public class ItemBeeFilterCard extends ItemForestry {
	public ItemBeeFilterCard() {
		super(new Item.Properties());
	}

	/**
	 * @return The filter encoded on the given card, or {@link BeeFilter#EMPTY} if unset.
	 */
	public static BeeFilter getFilter(ItemStack stack) {
		return stack.getOrDefault(BeegisticsComponents.BEE_FILTER.get(), BeeFilter.EMPTY);
	}

	/**
	 * Writes the given filter onto the card, removing the component entirely when the filter is empty so that a blank
	 * card round-trips to a bare item (and blank cards stack).
	 */
	public static void setFilter(ItemStack stack, BeeFilter filter) {
		if (filter.isEmpty()) {
			stack.remove(BeegisticsComponents.BEE_FILTER.get());
		} else {
			stack.set(BeegisticsComponents.BEE_FILTER.get(), filter);
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			// Referenced only on the client dist, so the client-only screen class never loads on a dedicated server.
			forestry.beegistics.client.BeeFilterScreen.open(hand, stack);
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		BeeFilter filter = getFilter(stack);
		filter.describe(tooltip);
		// The donor(s) that supply the pinned traits are chosen implicitly at craft time from whatever the network already
		// has, so we never name one (any name would be wrong once the player has a cheaper option). We only flag the case
		// that matters: a genome target no bee can supply the traits for, which can therefore never be bred.
		Optional<IGenome> target = BeeGenomeMutationPattern.targetFrom(filter);
		if (target.isPresent() && BeeGenomeMutationPattern.resolveDonor(target.get()).isEmpty()) {
			tooltip.add(Component.translatable("tooltip.beegistics.bee_genome_mutation_pattern.unreachable").withStyle(ChatFormatting.RED));
		}
	}
}
