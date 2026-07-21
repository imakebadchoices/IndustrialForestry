package forestry.beegistics;

import javax.annotation.Nullable;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.api.stacks.AEItemKey;

import forestry.beegistics.crafting.BeeMutation;
import forestry.beegistics.crafting.BeeMutationPattern;
import forestry.core.items.ItemForestry;

/**
 * An AE2 encoded-pattern item that encodes a single Forestry bee mutation (see {@link BeeMutation}) so the
 * {@link forestry.beegistics.machine.ApiaryControllerBlockEntity Apiary Controller} can offer that mutation to the
 * network's autocrafting system. Decoded into a {@link forestry.beegistics.crafting.BeeMutationPattern}.
 *
 * <p>This is an <em>internal-only</em> carrier: it is never a player item (no recipe, creative-tab entry, or right-click
 * screen). The controller synthesizes these keys to advertise the species mutations along a genome target's ancestor
 * closure to AE2, which needs a per-mutation pattern-definition key that a {@link ItemBeeFilterCard Bee Pattern card}
 * cannot encode.
 */
public class ItemBeeMutationPattern extends ItemForestry {
	public ItemBeeMutationPattern() {
		super(new Item.Properties().stacksTo(1));
	}

	/** @return the mutation encoded on the given pattern, or {@code null} if it is blank. */
	@Nullable
	public static BeeMutation getMutation(ItemStack stack) {
		return stack.get(BeegisticsComponents.BEE_MUTATION.get());
	}

	/** Writes (or, when {@code null}, clears) the encoded mutation on the given pattern. */
	public static void setMutation(ItemStack stack, @Nullable BeeMutation mutation) {
		if (mutation == null) {
			stack.remove(BeegisticsComponents.BEE_MUTATION.get());
		} else {
			stack.set(BeegisticsComponents.BEE_MUTATION.get(), mutation);
		}
	}

	/**
	 * @return a copy of the given pattern stack marked as the <em>drone-primary</em> variant. Used internally by the
	 * controller to offer both a princess-primary and a drone-primary pattern per item, so AE2 can autocraft an
	 * intermediate species as either gender. Not used on player-held patterns.
	 */
	public static ItemStack withDronePrimary(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.set(BeegisticsComponents.PATTERN_DRONE_PRIMARY.get(), Unit.INSTANCE);
		return copy;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		BeeMutation mutation = getMutation(stack);
		if (mutation == null) {
			tooltip.add(Component.translatable("tooltip.beegistics.bee_mutation_pattern.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.add(Component.translatable("tooltip.beegistics.bee_mutation_pattern.parents",
			mutation.firstParent().toString(), mutation.secondParent().toString()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.beegistics.bee_mutation_pattern.result",
			mutation.result().toString()).withStyle(ChatFormatting.GRAY));

		// Surface the mutation's breeding conditions (temperature, night, biome, ...) so the player knows what the
		// controller's apiary must provide for this pattern to be craftable.
		BeeMutationPattern pattern = BeeMutationPattern.decode(AEItemKey.of(stack), null);
		if (pattern != null) {
			for (Component requirement : pattern.requirements()) {
				tooltip.add(Component.translatable("tooltip.beegistics.bee_mutation_pattern.requires", requirement).withStyle(ChatFormatting.GOLD));
			}
		}
	}
}
