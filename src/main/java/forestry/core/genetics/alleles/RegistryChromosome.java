package forestry.core.genetics.alleles;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import forestry.api.genetics.alleles.IAllele;
import forestry.api.genetics.alleles.IRegistryAllele;
import forestry.api.genetics.alleles.IRegistryAlleleValue;
import forestry.api.genetics.alleles.IRegistryChromosome;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;

public class RegistryChromosome<V extends IRegistryAlleleValue> extends ValueChromosome<V> implements IRegistryChromosome<V> {
	private final HashMap<ResourceLocation, IRegistryAllele<V>> alleles = new HashMap<>();
	@Nullable
	private ImmutableMap<ResourceLocation, V> registry;
	@Nullable
	private IdentityHashMap<V, ResourceLocation> reverseLookup;

	public RegistryChromosome(ResourceLocation id, Class<V> valueClass) {
		super(id, valueClass);
	}

	@Override
	public boolean isValidAllele(IAllele allele) {
		Preconditions.checkState(this.registry != null, "Registry not yet populated");
		return this.registry.containsKey(allele.alleleId());
	}

	@Override
	public V get(ResourceLocation id) {
		Preconditions.checkState(this.registry != null, "Registry not yet populated");
		V value = this.registry.get(id);
		if (value == null) {
			throw new RuntimeException("No allele registered for chromosome " + this.id + " with ID: " + id);
		}
		return value;
	}

	@Nullable
	@Override
	public V getSafe(ResourceLocation id) {
		Preconditions.checkState(this.registry != null, "Registry not yet populated");
		return this.registry.get(id);
	}

	@Override
	public Collection<V> values() {
		Preconditions.checkState(this.registry != null, "Registry not yet populated");

		return this.registry.values();
	}

	@Override
	public Collection<IRegistryAllele<V>> alleles() {
		Preconditions.checkState(this.registry != null, "Registry not yet populated");

		return Collections.unmodifiableCollection(this.alleles.values());
	}

	@Override
	public ResourceLocation getId(V value) {
		Preconditions.checkState(this.reverseLookup != null, "Registry not yet populated");

		return this.reverseLookup.get(value);
	}

	@Override
	public void populate(ImmutableMap<ResourceLocation, V> registry) {
		// Re-entrant: a datapack-driven rebuild re-runs species registration and re-populates this
		// chromosome. Reset cached values on existing alleles so overridden IDs resolve to the rebuilt
		// value. Alleles created during the current pass have a null cache already, so this is safe.
		if (this.registry != null) {
			for (IRegistryAllele<V> allele : this.alleles.values()) {
				if (allele instanceof RegistryAllele<V> registryAllele) {
					registryAllele.resetCachedValue();
				}
			}
		}

		this.registry = registry;
		this.reverseLookup = new IdentityHashMap<>(registry.size());

		for (Map.Entry<ResourceLocation, V> entry : registry.entrySet()) {
			this.reverseLookup.put(entry.getValue(), entry.getKey());
		}

		// Drop alleles whose value is no longer registered — e.g. a species removed by a datapack rebuild.
		// Leaving them would let stale alleles be resolved (RegistryAllele.value() throws) and would trip the
		// missing-value verification in AlleleManager.setRegistrationState.
		this.alleles.keySet().removeIf(alleleId -> !registry.containsKey(alleleId));
	}

	@Override
	public boolean isPopulated() {
		return this.registry != null;
	}

	/**
	 * Resets this chromosome to its unpopulated state so a datapack-driven rebuild can re-run species
	 * registration. While unpopulated, {@link forestry.core.genetics.Karyotype#isAlleleValid} treats any
	 * allele as valid, which is what lets {@code buildAll} construct genomes for newly added species before
	 * the chromosome is re-populated with the full set. Cached values on existing alleles are cleared so
	 * overridden IDs resolve to the rebuilt value.
	 */
	public void reset() {
		if (this.registry != null) {
			for (IRegistryAllele<V> allele : this.alleles.values()) {
				if (allele instanceof RegistryAllele<V> registryAllele) {
					registryAllele.resetCachedValue();
				}
			}
		}
		this.registry = null;
		this.reverseLookup = null;
	}

	// called by RegistryAllele
	void add(ResourceLocation id, RegistryAllele<V> allele) {
		this.alleles.put(id, allele);
	}
}
