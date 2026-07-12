package forestry.apiculture.items;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import forestry.apiculture.CombExtract;
import forestry.core.features.CoreDataComponents;
import forestry.core.items.ItemForestry;
import forestry.core.items.definitions.IColoredItem;

/**
 * The one generic fluid-intermediary item. A "fluid comb" centrifuges into this carrying a
 * {@link CombExtract} component (the fluid it holds + a propolis/honey-drop flavor), and the squeezer turns
 * it back into that fluid ({@code TileSqueezer} builds a dynamic recipe from the component — no per-fluid
 * recipe or registry). The item auto-tints from the carried fluid's colour, so oil/creosote/milk extracts are
 * visually distinct with zero per-variant data.
 */
public class ItemCombExtract extends ItemForestry implements IColoredItem {
	private static final int DEFAULT_TINT = 0xffffff;

	public ItemCombExtract() {
		super(new Item.Properties());
	}

	@Nullable
	public static CombExtract getExtract(ItemStack stack) {
		return stack.get(CoreDataComponents.COMB_EXTRACT);
	}

	@Override
	public Component getName(ItemStack stack) {
		CombExtract extract = getExtract(stack);
		if (extract != null && !extract.fluid().isEmpty()) {
			// Name keyed on source + flavor + fluid, e.g. "comb_extract.extrabees.honey_drop.minecraft.milk"
			// — supplied by the pack's bundled lang (deterministic from the component, no embedded name).
			ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(extract.fluid().getFluid());
			String key = String.join(".", "comb_extract", extract.source().orElse("forestry"),
				extract.flavor(), fluidId.getNamespace(), fluidId.getPath());
			return Component.translatable(key);
		}
		return super.getName(stack);
	}

	/**
	 * Attribute an extract to the pack that produced it (its {@link CombExtract#source()} — e.g. Extra Bees
	 * extracts group under {@code @extrabees}), not to Forestry which only ships the one generic item.
	 */
	@Override
	public String getCreatorModId(ItemStack stack) {
		CombExtract extract = getExtract(stack);
		if (extract != null && extract.source().isPresent()) {
			return extract.source().get();
		}
		return super.getCreatorModId(stack);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public int getColorFromItemStack(ItemStack stack, int tintIndex) {
		CombExtract extract = getExtract(stack);
		if (extract == null) {
			return DEFAULT_TINT;
		}
		// prefer the baked comb colour (cohesive with the comb the extract came from); fall back to the
		// fluid's own tint for extracts that don't carry a colour.
		if (extract.color().isPresent()) {
			return extract.color().get().getValue();
		}
		if (extract.fluid().isEmpty()) {
			return DEFAULT_TINT;
		}
		FluidStack fluid = extract.fluid();
		return IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
	}
}
