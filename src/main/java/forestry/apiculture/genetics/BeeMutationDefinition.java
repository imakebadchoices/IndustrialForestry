package forestry.apiculture.genetics;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.alleles.IAllele;
import forestry.api.genetics.alleles.IChromosome;

/**
 * A datapack-loadable definition of a bee mutation, loaded through the {@code forestry:bee_mutation}
 * datapack registry (see {@link #REGISTRY_KEY}). A mutation crosses two parent species and, with some
 * {@code chance} (modified by any {@code conditions}), yields the {@code result} species.
 * <p>
 * Parents and result are kept as {@link ResourceLocation species IDs} and only resolved to species during
 * the rebuild, once every species (code + datapack) exists — see {@link DatapackBeePlugin}. Behavioral
 * restrictions are the {@link IMutationCondition} dispatch family; {@code special_alleles} force specific
 * alleles onto the mutation result (e.g. a rare variant), reusing the species genome shape.
 */
public record BeeMutationDefinition(
	ResourceLocation firstParent,
	ResourceLocation secondParent,
	ResourceLocation result,
	float chance,
	List<IMutationCondition> conditions,
	Map<IChromosome<?>, IAllele> specialAlleles
) {
	/**
	 * The datapack registry that holds every bee mutation definition. Entries live at
	 * {@code data/<namespace>/forestry/bee_mutation/<name>.json}.
	 */
	public static final ResourceKey<Registry<BeeMutationDefinition>> REGISTRY_KEY = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_mutation"));

	public static final Codec<BeeMutationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("first_parent").forGetter(BeeMutationDefinition::firstParent),
		ResourceLocation.CODEC.fieldOf("second_parent").forGetter(BeeMutationDefinition::secondParent),
		ResourceLocation.CODEC.fieldOf("result").forGetter(BeeMutationDefinition::result),
		Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(BeeMutationDefinition::chance),
		IMutationCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(BeeMutationDefinition::conditions),
		BeeSpeciesDefinition.GENOME_CODEC.optionalFieldOf("special_alleles", Map.of()).forGetter(BeeMutationDefinition::specialAlleles)
	).apply(instance, BeeMutationDefinition::new));
}
