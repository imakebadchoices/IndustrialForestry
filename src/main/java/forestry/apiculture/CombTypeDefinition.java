package forestry.apiculture;

import java.util.Optional;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;

import forestry.api.ForestryConstants;

/**
 * A datapack-loadable definition of a bee comb variant, loaded through the {@code forestry:comb_type}
 * datapack registry (see {@link #REGISTRY_KEY}). It is the comb analogue of {@code forestry:bee_species}:
 * one generic {@link forestry.apiculture.items.ItemBeeComb} is registered in code, and every visual/identity
 * variant lives here as pure data, referenced on a stack by the {@code forestry:comb_type} data component
 * ({@link forestry.core.features.CoreDataComponents#COMB_TYPE}).
 * <p>
 * The comb item's {@link forestry.core.items.definitions.IColoredItem#getColorFromItemStack} reads the
 * component id, looks the definition up in this registry, and tints the shared comb model with
 * {@link #primaryColor()} (layer 1) and {@link #secondaryColor()} (layer 0) — a direct copy of how
 * {@code ItemBeeGE} renders 70+ datapack species from one item. So the 88 Extra Bees combs collapse to
 * {@code data/<namespace>/forestry/comb_type/*.json} plus one shared model.
 * <p>
 * Only appearance lives here; a comb's <em>products</em> need no new type — a species product is just
 * {@code {"item": "forestry:comb", "tag": {"forestry:comb_type": "<pack>:<id>"}, "chance": …}} (the
 * {@link forestry.api.core.Product} patch), and a centrifuge <em>input</em> uses a component-aware
 * {@code neoforge:components} ingredient keyed on the same component.
 */
public record CombTypeDefinition(TextColor primaryColor, TextColor secondaryColor, Optional<CombExtract> extract) {
	/**
	 * The datapack registry that holds every comb type definition. Entries live at
	 * {@code data/<namespace>/forestry/comb_type/<name>.json}. Synced to clients because the comb item
	 * resolves its tint colors from this registry at render time.
	 */
	public static final ResourceKey<Registry<CombTypeDefinition>> REGISTRY_KEY = ResourceKey.createRegistryKey(ForestryConstants.forestry("comb_type"));

	public static final Codec<CombTypeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		TextColor.CODEC.fieldOf("primary_color").forGetter(CombTypeDefinition::primaryColor),
		TextColor.CODEC.fieldOf("secondary_color").forGetter(CombTypeDefinition::secondaryColor),
		// name comes from a lang key (comb.<ns>.<path>) shipped with the pack's assets, not embedded here.
		// Drop an extract whose fluid didn't resolve (an optional mod that isn't installed — CombExtract.FLUID_CODEC
		// leaves it EMPTY): the comb keeps its colours/identity, only the now-inert fluid intermediary goes away.
		CombExtract.CODEC.optionalFieldOf("extract")
			.xmap(extract -> extract.filter(e -> !e.fluid().isEmpty()), Function.identity())
			.forGetter(CombTypeDefinition::extract)
	).apply(instance, CombTypeDefinition::new));
}
