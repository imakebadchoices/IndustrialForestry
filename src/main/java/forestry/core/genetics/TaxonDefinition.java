package forestry.core.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import forestry.api.ForestryConstants;
import forestry.api.genetics.ITaxon;
import forestry.api.genetics.TaxonomicRank;

/**
 * A datapack-loadable definition of a taxon (a node in the classification tree), loaded through the
 * {@code forestry:taxon} datapack registry (see {@link #REGISTRY_KEY}). Each entry defines one taxon by
 * {@link #name} under an existing {@link #parent} taxon; its {@link TaxonomicRank rank} is derived from the
 * parent's rank ({@code parent.rank().next()}).
 * <p>
 * This exists because a species genome references its genus by name, and that genus <b>must</b> resolve to a
 * registered taxon at build time ({@link GeneticManager#getTaxon} throws otherwise, failing the whole datapack
 * reload). Code plugins define their taxa through {@link forestry.api.plugin.IGeneticRegistration#defineTaxon};
 * this is the datapack analogue, so a pack (e.g. Extra Bees' ~27 genera) can add genera as pure JSON without any
 * Java. The definitions are merged onto the code-registered taxa during the
 * {@linkplain forestry.apiimpl.plugin.PluginManager#reloadDatapackSpecies rebuild}, before species are built.
 * <p>
 * Kept intentionally minimal ({@code parent} + {@code name}, no default chromosomes): per the migration plan,
 * branch/genus allele templates are flattened into each species' genome by the generator rather than inherited
 * from the taxon, so a datapack taxon only needs to exist for genus resolution to succeed.
 */
public record TaxonDefinition(String parent, String name) {
	/**
	 * The datapack registry that holds every taxon definition. Entries live at
	 * {@code data/<namespace>/forestry/taxon/<name>.json}. Synced to clients because taxa are needed for the
	 * client-side species rebuild (a species' genus is resolved there too).
	 */
	public static final ResourceKey<Registry<TaxonDefinition>> REGISTRY_KEY = ResourceKey.createRegistryKey(ForestryConstants.forestry("taxon"));

	public static final Codec<TaxonDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("parent").forGetter(TaxonDefinition::parent),
		Codec.STRING.fieldOf("name").forGetter(TaxonDefinition::name)
	).apply(instance, TaxonDefinition::new));
}
