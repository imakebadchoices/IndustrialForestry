package forestry.apiimpl.plugin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import forestry.Forestry;
import forestry.api.IForestryApi;
import forestry.api.genetics.*;
import forestry.api.genetics.alleles.*;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.ISpeciesBuilder;
import forestry.core.genetics.MutationManager;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Base implementation of {@link ISpeciesBuilder} with common logic.
 *
 * @param <I> Interface type of the species builders used by this species registration.
 * @param <S> Interface type of the species registered by this species registration.
 * @param <B> The concrete type of the species builder used by this species registration.
 */
public abstract class SpeciesRegistration<I extends ISpeciesBuilder<? extends ISpeciesType<S, ?>, S, I>, S extends ISpecies<?>, B extends I> {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private final ModifiableRegistrar<ResourceLocation, I, B> species = new ModifiableRegistrar(ISpeciesBuilder.class);

	protected final ISpeciesType<S, ?> type;

	public SpeciesRegistration(ISpeciesType<S, ?> type) {
		this.type = type;
	}

	protected abstract B createSpeciesBuilder(ResourceLocation id, String genus, String species, MutationsRegistration mutations);

	protected I register(ResourceLocation id, String genus, String species) {
		return this.species.create(id, createSpeciesBuilder(id, genus, species, new MutationsRegistration(id)));
	}

	public void modifySpecies(ResourceLocation id, Consumer<I> action) {
		this.species.modify(id, action);
	}

	/**
	 * @return Whether a species with the given ID has already been registered in this pass. Used by the
	 * datapack loader to decide between adding a new species and modifying an existing (code-registered) one.
	 */
	public boolean isRegistered(ResourceLocation id) {
		return this.species.containsKey(id);
	}

	// Creates final map of species, the mutations manager, and populates the species chromosome
	public Pair<ImmutableMap<ResourceLocation, S>, IMutationManager<S>> buildAll() {
		IKaryotype karyotype = this.type.getKaryotype();
		IRegistryChromosome<? extends ISpecies<?>> speciesChromosome = karyotype.getSpeciesChromosome();

		ImmutableMap<ResourceLocation, S> allSpecies = this.species.build((id, builder) -> {
			// create default genome builder
			IGenomeBuilder defaultGenomeBuilder = karyotype.createGenomeBuilder();
			IGeneticManager geneticManager = IForestryApi.INSTANCE.getGeneticManager();
			// A datapack species may reference a genus that was never registered (e.g. its taxon failed to load).
			// Fall back to no ancestry rather than crashing the whole reload; the species still registers, just
			// without any taxon default alleles (Species also gives it a display-only genus).
			ITaxon[] ancestry;
			if (geneticManager.getTaxonSafe(builder.getGenus()) != null) {
				ancestry = geneticManager.getParentTaxa(builder.getGenus());
			} else {
				Forestry.LOGGER.warn("Species {} references unknown genus '{}'; registering it without taxon default alleles", id, builder.getGenus());
				ancestry = new ITaxon[0];
			}

			// apply default genomes from parent taxa
			for (ITaxon taxon : ancestry) {
				for (Map.Entry<IChromosome<?>, ITaxon.TaxonAllele> alleleEntry : taxon.alleles().entrySet()) {
					IAllele allele = alleleEntry.getValue().allele();
					IChromosome<?> chromosome = alleleEntry.getKey();

					if (karyotype.isAlleleValid(chromosome, allele.cast())) {
						defaultGenomeBuilder.set(alleleEntry.getKey(), allele.cast());
					} else {
						// If a taxa is shared by different species types, don't throw errors for incompatible default alleles
						Forestry.LOGGER.warn("Default allele set by taxon {} skipped for species {} due to being invalid for its karyotype", taxon.name(), id);
					}
				}
			}

			// set default chromosomes that weren't overridden by taxa
			defaultGenomeBuilder.setUnchecked(speciesChromosome, AllelePair.both(IForestryApi.INSTANCE.getAlleleManager().registryAllele(id, speciesChromosome)));
			defaultGenomeBuilder.setRemainingDefault();
			IGenome defaultGenome = builder.buildGenome(defaultGenomeBuilder);

			return builder.createSpeciesFactory().create(id, this.type.cast(), defaultGenome, builder);
		});

		// populate default species chromosome
		speciesChromosome.populate((ImmutableMap) allSpecies);

		// build mutations once species are available
		ImmutableList.Builder<IMutation<S>> mutations = new ImmutableList.Builder<>();
		for (Map.Entry<ResourceLocation, B> entry : this.species.getValues().entrySet()) {
			mutations.addAll(entry.getValue().buildMutations(this.type, allSpecies));
		}

		return Pair.of(allSpecies, new MutationManager<>(mutations.build()));
	}
}
