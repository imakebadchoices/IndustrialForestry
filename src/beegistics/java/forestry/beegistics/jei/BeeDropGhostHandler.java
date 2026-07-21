package forestry.beegistics.jei;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import forestry.beegistics.client.JeiBeeDropScreen;

/**
 * Lets a player drag a bee from JEI's ingredient list onto a {@link JeiBeeDropScreen}'s drop slot (the Bee Filter card's
 * sample slot). JEI itself drives the drag and calls {@link Target#accept} when the bee is dropped, so no event hooks,
 * input interception, or {@link mezz.jei.api.runtime.IJeiRuntime} are needed.
 */
public class BeeDropGhostHandler<T extends Screen & JeiBeeDropScreen> implements IGhostIngredientHandler<T> {
	@Override
	public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
		if (!gui.acceptsBeeDrop()) {
			return List.of();
		}
		Optional<ItemStack> stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
		if (stack.isEmpty() || !gui.isBeeStack(stack.get())) {
			return List.of();
		}
		return List.of(new Target<I>() {
			@Override
			public Rect2i getArea() {
				return gui.beeDropRect();
			}

			@Override
			public void accept(I dropped) {
				gui.acceptBeeDrop((ItemStack) dropped);
			}
		});
	}

	@Override
	public void onComplete() {
	}
}
