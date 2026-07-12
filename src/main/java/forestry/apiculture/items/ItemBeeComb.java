package forestry.apiculture.items;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import forestry.apiculture.CombTypeDefinition;
import forestry.core.features.CoreDataComponents;
import forestry.core.items.ItemForestry;
import forestry.core.items.definitions.IColoredItem;
import forestry.core.utils.RecipeUtils;

/**
 * The one generic comb item. Every visual/identity variant lives in the {@code forestry:comb_type} datapack
 * registry ({@link CombTypeDefinition}) and is stamped onto a stack by the {@code forestry:comb_type} data
 * component ({@link CoreDataComponents#COMB_TYPE}). This is the exact trick {@code ItemBeeGE} uses to render
 * 70+ datapack species from a single item — so the 88 Extra Bees combs become pure data, not 88 registrations.
 *
 * <p>Colour resolution mirrors the legacy {@link ItemHoneyComb}: the shared model's layer 1 is tinted with the
 * comb type's primary colour and layer 0 with the secondary colour. A missing/dangling comb type degrades to a
 * neutral tint rather than crashing (crash-safety parity with the datapack-driven genetics).
 */
public class ItemBeeComb extends ItemForestry implements IColoredItem {
	// Fallback tints when the stack has no comb_type component or it fails to resolve.
	private static final int DEFAULT_PRIMARY = 0xffdc16;
	private static final int DEFAULT_SECONDARY = 0xffffff;

	public ItemBeeComb() {
		super(new Item.Properties());
	}

	@Nullable
	public static ResourceLocation getCombTypeId(ItemStack stack) {
		return stack.get(CoreDataComponents.COMB_TYPE);
	}

	@Nullable
	private static CombTypeDefinition resolveCombType(ItemStack stack) {
		ResourceLocation id = getCombTypeId(stack);
		if (id == null) {
			return null;
		}
		RegistryAccess registryAccess = RecipeUtils.getRegistryAccess();
		if (registryAccess == null) {
			return null;
		}
		return registryAccess.registry(CombTypeDefinition.REGISTRY_KEY)
			.map(registry -> registry.get(id))
			.orElse(null);
	}

	@Override
	public Component getName(ItemStack stack) {
		ResourceLocation id = getCombTypeId(stack);
		if (id != null) {
			// Name keyed on the comb id, e.g. "comb.extrabees.oil" — supplied by the pack's bundled lang.
			return Component.translatable(Util.makeDescriptionId("comb", id));
		}
		return super.getName(stack);
	}

	/**
	 * Attribute a comb to the mod that defines its {@code comb_type} (e.g. Extra Bees combs show as
	 * {@code @extrabees} in JEI/creative), not to Forestry which only ships the one generic item. Mirrors
	 * {@code ItemGE.getCreatorModId} for bees.
	 */
	@Override
	public String getCreatorModId(ItemStack stack) {
		ResourceLocation id = getCombTypeId(stack);
		return id != null ? id.getNamespace() : super.getCreatorModId(stack);
	}

	@Override
	public int getColorFromItemStack(ItemStack stack, int tintIndex) {
		CombTypeDefinition type = resolveCombType(stack);
		if (type == null) {
			return tintIndex == 1 ? DEFAULT_PRIMARY : DEFAULT_SECONDARY;
		}
		return tintIndex == 1 ? type.primaryColor().getValue() : type.secondaryColor().getValue();
	}
}
