package forestry.apiimpl;

import com.google.common.collect.ImmutableMap;
import forestry.api.genetics.*;
import forestry.core.genetics.Taxon;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;
import java.util.Collection;

public class GeneticManager implements IGeneticManager {
	private final ImmutableMap<String, ITaxon> taxa;
	private final ImmutableMap<ResourceLocation, ISpeciesType<?, ?>> speciesTypes;
	@Nullable
	private ImmutableMap<ISpeciesType<?, ?>, IMutationManager<?>> mutationsByType;

	public GeneticManager(ImmutableMap<String, ITaxon> taxa, ImmutableMap<ResourceLocation, ISpeciesType<?, ?>> speciesTypes) {
		this.taxa = taxa;
		this.speciesTypes = speciesTypes;
	}

	@Override
	public ITaxon getTaxon(String name) {
		ITaxon taxon = this.taxa.get(name);
		if (taxon == null) {
			throw new IllegalStateException("No taxon was registered with name '" + name + "'");
		}
		return taxon;
	}

	@Override
	public ITaxon[] getParentTaxa(String name) {
		ITaxon taxon = getTaxon(name);
		int ordinal = taxon.rank().ordinal();
		ITaxon[] taxa = new Taxon[1 + ordinal];

		for (int i = ordinal; i >= 0; i--) {
			taxa[i] = taxon;
			taxon = taxon.parent();
		}

		return taxa;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S extends ISpecies<?>> IMutationManager<S> getMutations(ISpeciesType<?, ?> speciesType) {
		if (this.mutationsByType == null) {
			throw new IllegalStateException("Mutations have not been registered yet");
		}
		IMutationManager<?> manager = this.mutationsByType.get(speciesType);
		if (manager == null) {
			throw new IllegalStateException("Invalid or unregistered species type");
		}
		return (IMutationManager<S>) manager;
	}

	@Override
	public ISpeciesType<?, ?> getSpeciesType(ResourceLocation speciesTypeId) {
		ISpeciesType<?, ?> type = this.speciesTypes.get(speciesTypeId);
		if (type == null) {
			throw new IllegalStateException("No species type was registered with ID: " + speciesTypeId);
		}
		return type;
	}

	@Nullable
	@Override
	public ISpeciesType<?, ?> getSpeciesTypeSafe(ResourceLocation speciesTypeId) {
		return this.speciesTypes.get(speciesTypeId);
	}

	@Override
	public Collection<ISpeciesType<?, ?>> getSpeciesTypes() {
		return this.speciesTypes.values();
	}

	@ApiStatus.Internal
	public void setMutations(ImmutableMap<ISpeciesType<?, ?>, IMutationManager<?>> mutationsByType) {
		this.mutationsByType = mutationsByType;
	}

	/**
	 * Replaces the mutation manager for a single species type, preserving the others. Used by the
	 * datapack loader when it rebuilds one species type's breeds on reload.
	 */
	@ApiStatus.Internal
	public void setMutationsForType(ISpeciesType<?, ?> speciesType, IMutationManager<?> mutations) {
		if (this.mutationsByType == null) {
			throw new IllegalStateException("Mutations have not been registered yet");
		}
		java.util.IdentityHashMap<ISpeciesType<?, ?>, IMutationManager<?>> map = new java.util.IdentityHashMap<>(this.mutationsByType);
		map.put(speciesType, mutations);
		this.mutationsByType = ImmutableMap.copyOf(map);
	}
}
