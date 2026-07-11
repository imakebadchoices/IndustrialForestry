package forestry.apiculture.genetics;

import java.util.Map;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import forestry.Forestry;
import forestry.api.ForestryConstants;
import forestry.api.core.IProduct;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IAllele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.plugin.IApicultureRegistration;
import forestry.api.plugin.IBeeSpeciesBuilder;
import forestry.api.plugin.IForestryPlugin;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.IMutationBuilder;
import forestry.apiimpl.plugin.ApicultureRegistration;

/**
 * A synthetic {@link IForestryPlugin} that contributes datapack-loaded bee breeds and mutations during a
 * {@linkplain forestry.apiimpl.plugin.PluginManager#reloadDatapackSpecies rebuild}. It is not
 * discovered via {@code ServiceLoader}; the reload driver appends it to the plugin list <em>after</em>
 * the code plugins so its entries add new content or override existing entries last.
 * <p>
 * Definitions fully specify their data, so species overrides use replace semantics (products/specialties
 * are cleared first, and every genome chromosome from the definition is set). Base Forestry's own bees are
 * shipped as definitions too, so applying them over the code-registered species is a net no-op — which
 * is also what makes reloads self-resetting when a datapack that overrode a breed is removed.
 * <p>
 * Mutations are additive: each {@link BeeMutationDefinition} is translated into the result species'
 * {@link forestry.api.plugin.IMutationsRegistration}. Because this plugin runs last, every code + datapack
 * species already exists, so parent/result references resolve; a dangling reference is skipped and logged
 * rather than crashing the rebuild.
 */
public class DatapackBeePlugin implements IForestryPlugin {
	private static final ResourceLocation ID = ForestryConstants.forestry("datapack_bees");

	private final Registry<BeeSpeciesDefinition> definitions;
	private final Registry<BeeMutationDefinition> mutations;

	public DatapackBeePlugin(Registry<BeeSpeciesDefinition> definitions, Registry<BeeMutationDefinition> mutations) {
		this.definitions = definitions;
		this.mutations = mutations;
	}

	@Override
	public void registerApiculture(IApicultureRegistration apiculture) {
		ApicultureRegistration registration = (ApicultureRegistration) apiculture;

		for (Map.Entry<ResourceKey<BeeSpeciesDefinition>, BeeSpeciesDefinition> entry : this.definitions.entrySet()) {
			ResourceLocation id = entry.getKey().location();
			BeeSpeciesDefinition definition = entry.getValue();
			registration.registerOrModify(id, definition.genus(), definition.species(), definition.dominant(), definition.coloration().outline(), builder -> apply(builder, definition));
		}

		// Mutations run after every species (code + datapack) is registered, so parents/result resolve.
		for (Map.Entry<ResourceKey<BeeMutationDefinition>, BeeMutationDefinition> entry : this.mutations.entrySet()) {
			applyMutation(registration, entry.getKey().location(), entry.getValue());
		}
	}

	private static void applyMutation(ApicultureRegistration registration, ResourceLocation mutationId, BeeMutationDefinition definition) {
		ResourceLocation first = definition.firstParent();
		ResourceLocation second = definition.secondParent();
		ResourceLocation result = definition.result();

		if (first.equals(second)) {
			Forestry.LOGGER.error("Skipping datapack bee mutation {}: both parents are the same species ({})", mutationId, first);
			return;
		}
		if (!registration.isRegistered(result)) {
			Forestry.LOGGER.error("Skipping datapack bee mutation {}: result species {} is not registered", mutationId, result);
			return;
		}
		if (!registration.isRegistered(first) || !registration.isRegistered(second)) {
			Forestry.LOGGER.error("Skipping datapack bee mutation {}: parent species {} or {} is not registered", mutationId, first, second);
			return;
		}

		registration.modifySpecies(result, builder -> builder.addMutations(reg -> {
			if (reg.get(first, second) != null) {
				Forestry.LOGGER.error("Skipping datapack bee mutation {}: a mutation between {} and {} already exists", mutationId, first, second);
				return;
			}
			IMutationBuilder mutation = reg.add(first, second, definition.chance());
			for (IMutationCondition condition : definition.conditions()) {
				mutation.addMutationCondition(condition);
			}
			definition.specialAlleles().forEach((chromosome, allele) -> addSpecialAllele(mutation, chromosome, allele));
		}));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void addSpecialAllele(IMutationBuilder mutation, IChromosome<?> chromosome, IAllele allele) {
		mutation.addSpecialAllele((IChromosome) chromosome, allele);
	}

	private static void apply(IBeeSpeciesBuilder builder, BeeSpeciesDefinition definition) {
		builder.setDominant(definition.dominant())
			.setOutline(definition.coloration().outline())
			.setBody(definition.coloration().body())
			.setStripes(definition.coloration().stripes())
			.setTemperature(definition.temperature())
			.setHumidity(definition.humidity())
			.setComplexity(definition.complexity())
			.setGlint(definition.glint())
			.setSecret(definition.secret())
			.setAuthority(definition.authority())
			.setJubilance(definition.jubilance());

		if (definition.escritoireColor() != -1) {
			builder.setEscritoireColor(TextColor.fromRgb(definition.escritoireColor()));
		}

		builder.clearProducts();
		for (IProduct product : definition.products()) {
			builder.addProduct(product);
		}
		builder.clearSpecialties();
		for (IProduct specialty : definition.specialties()) {
			builder.addSpecialty(specialty);
		}

		// Definitions carry the full genome, so setting each chromosome (both alleles) reproduces the
		// species exactly; on the modify path this overrides the code-set alleles chromosome-by-chromosome.
		builder.setGenome(genome -> definition.genome().forEach((chromosome, allele) -> setChromosome(genome, chromosome, allele)));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setChromosome(IGenomeBuilder genome, IChromosome<?> chromosome, IAllele allele) {
		genome.setUnchecked((IChromosome) chromosome, AllelePair.both(allele));
	}

	@Override
	public ResourceLocation id() {
		return ID;
	}
}
