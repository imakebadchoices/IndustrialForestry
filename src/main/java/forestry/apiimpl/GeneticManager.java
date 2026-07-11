package forestry.apiimpl;

import com.google.common.collect.ImmutableMap;
import forestry.Forestry;
import forestry.api.genetics.*;
import forestry.api.genetics.alleles.IChromosome;
import forestry.core.genetics.Taxon;
import forestry.core.genetics.TaxonDefinition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeneticManager implements IGeneticManager {
	// The code-registered taxa, captured at construction. Datapack taxa are always merged on top of this
	// snapshot (never the previously-merged result), so removing a datapack cleanly reverts to code taxa.
	private final ImmutableMap<String, ITaxon> baseTaxa;
	// Base taxa plus any datapack-defined taxa from the most recent reload. Read from gameplay threads,
	// replaced from the datapack reload thread.
	private volatile ImmutableMap<String, ITaxon> taxa;
	private final ImmutableMap<ResourceLocation, ISpeciesType<?, ?>> speciesTypes;
	@Nullable
	private ImmutableMap<ISpeciesType<?, ?>, IMutationManager<?>> mutationsByType;

	public GeneticManager(ImmutableMap<String, ITaxon> taxa, ImmutableMap<ResourceLocation, ISpeciesType<?, ?>> speciesTypes) {
		this.baseTaxa = taxa;
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

	@Nullable
	@Override
	public ITaxon getTaxonSafe(String name) {
		return this.taxa.get(name);
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
	 * Merges datapack-defined taxa onto the code-registered taxa. Called during the datapack species rebuild,
	 * before species are built, so a datapack species' genus (which must resolve to a registered taxon, see
	 * {@link #getTaxon}) can be a genus added purely from JSON. Always merges onto the immutable code-taxa
	 * snapshot, so passing an empty collection reverts to exactly the built-in taxonomy.
	 * <p>
	 * Each definition's rank is derived from its parent's rank. Definitions are resolved in dependency order via
	 * a fixpoint, so a datapack may define a taxon and its parent taxon in any order. A definition whose parent
	 * never resolves (or whose parent is a genus, which cannot have children) is skipped with a warning rather
	 * than crashing the reload — matching the crash-safety handling of dangling datapack references elsewhere. A
	 * species referencing such a skipped genus will still fail its own build, so the generator is responsible for
	 * emitting a taxon for every genus it uses.
	 */
	@ApiStatus.Internal
	public void applyDatapackTaxa(Collection<TaxonDefinition> definitions) {
		if (definitions.isEmpty()) {
			this.taxa = this.baseTaxa;
			return;
		}

		Map<String, ITaxon> merged = new LinkedHashMap<>(this.baseTaxa);
		ArrayList<TaxonDefinition> pending = new ArrayList<>(definitions);

		boolean progress = true;
		while (progress && !pending.isEmpty()) {
			progress = false;
			Iterator<TaxonDefinition> it = pending.iterator();
			while (it.hasNext()) {
				TaxonDefinition def = it.next();
				ITaxon parent = merged.get(def.parent());
				if (parent == null) {
					// Parent not resolved yet; maybe a later iteration (another datapack taxon) will define it.
					continue;
				}
				it.remove();
				progress = true;

				if (parent.rank() == TaxonomicRank.GENUS) {
					Forestry.LOGGER.warn("Datapack taxon '{}' skipped: its parent '{}' is a genus, which cannot have sub-taxa", def.name(), def.parent());
				} else if (merged.containsKey(def.name())) {
					Forestry.LOGGER.warn("Datapack taxon '{}' skipped: a taxon with that name is already registered", def.name());
				} else {
					merged.put(def.name(), new Taxon(def.name(), parent.rank().next(), parent, new IdentityHashMap<IChromosome<?>, ITaxon.TaxonAllele>()));
				}
			}
		}

		for (TaxonDefinition def : pending) {
			Forestry.LOGGER.warn("Datapack taxon '{}' skipped: parent taxon '{}' was never registered", def.name(), def.parent());
		}

		this.taxa = ImmutableMap.copyOf(merged);
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
