package forestry.core.genetics;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.Forestry;
import forestry.api.IForestryApi;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.alleles.*;
import forestry.api.plugin.IChromosomeBuilder;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.IKaryotypeBuilder;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;

public class Karyotype implements IKaryotype {
	private final ImmutableMap<IChromosome<?>, ImmutableSet<? extends IAllele>> chromosomes;
	private final IRegistryChromosome<? extends ISpecies<?>> speciesChromosome;
	private final ImmutableMap<IChromosome<?>, ? extends IAllele> defaultAlleles;
	private final ResourceLocation defaultSpecies;
	private final ResourceLocation id;
	private final Set<IChromosome<?>> weaklyInheritedChromosomes;
	private final Codec<IGenome> genomeCodec;

	// Used in Karyotype.Builder
	public Karyotype(ResourceLocation id, ImmutableMap<IChromosome<?>, ImmutableSet<? extends IAllele>> chromosomes, ImmutableMap<IChromosome<?>, ? extends IAllele> defaultAlleles, ResourceLocation defaultSpecies, Set<IChromosome<?>> weaklyInheritedChromosomes) {
		this.id = id;
		this.chromosomes = chromosomes;
		this.speciesChromosome = (IRegistryChromosome<? extends ISpecies<?>>) chromosomes.keySet().asList().get(0);
		this.defaultAlleles = defaultAlleles;
		this.defaultSpecies = defaultSpecies;
		this.weaklyInheritedChromosomes = weaklyInheritedChromosomes;

		// Decodes leniently: entries whose chromosome or allele ID no longer resolves (e.g. a saved bee whose
		// species was removed with a datapack) are dropped rather than failing the whole decode, and
		// sanitizeAlleles then fills the gaps from the default species. A strict codec would throw here — and,
		// because the genome data component syncs via ByteBufCodecs.fromCodec (getOrThrow), that would hard-crash
		// the client when the orphaned item is sent over the network instead of degrading to the default bee.
		this.genomeCodec = Codec.unboundedMap(ResourceLocation.CODEC, RawAllelePair.CODEC)
			.xmap(this::resolveGenome, Karyotype::encodeGenome);
	}

	// The serialized shape of a single chromosome's allele pair; keys/values stay as raw IDs so an unknown
	// one can be detected and skipped during resolution rather than aborting the decode.
	private record RawAllelePair(ResourceLocation active, ResourceLocation inactive) {
		static final Codec<RawAllelePair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.fieldOf("active").forGetter(RawAllelePair::active),
			ResourceLocation.CODEC.fieldOf("inactive").forGetter(RawAllelePair::inactive)
		).apply(instance, RawAllelePair::new));
	}

	private IGenome resolveGenome(Map<ResourceLocation, RawAllelePair> raw) {
		IAlleleManager alleleManager = IForestryApi.INSTANCE.getAlleleManager();
		Map<IChromosome<?>, AllelePair<?>> resolved = new IdentityHashMap<>(raw.size());

		for (Map.Entry<ResourceLocation, RawAllelePair> entry : raw.entrySet()) {
			IChromosome<?> chromosome = alleleManager.getChromosome(entry.getKey());
			if (chromosome == null) {
				Forestry.LOGGER.warn("Dropping unknown chromosome '{}' while loading a genome for karyotype {}; it will fall back to the default.", entry.getKey(), this.id);
				continue;
			}
			IAllele active = alleleManager.getAllele(entry.getValue().active());
			IAllele inactive = alleleManager.getAllele(entry.getValue().inactive());
			if (active == null || inactive == null || !isResolvable(chromosome, active) || !isResolvable(chromosome, inactive)) {
				Forestry.LOGGER.warn("Dropping chromosome '{}' with unresolved allele(s) '{}'/'{}' while loading a genome for karyotype {}; it will fall back to the default. Was a datapack breed removed?", entry.getKey(), entry.getValue().active(), entry.getValue().inactive(), this.id);
				continue;
			}
			resolved.put(chromosome, new AllelePair<>(active, inactive));
		}

		return Genome.sanitizeAlleles(this, resolved);
	}

	// A registry allele (e.g. a species allele) can linger in the allele manager after its value was removed
	// by a datapack rebuild within the same game session: the object is kept so it can be re-attached if the
	// datapack comes back (see AlleleManager#registryAllele). getAllele then returns a non-null-but-stale
	// allele whose value() would throw. Reject it here so the chromosome falls back to the default instead.
	private static boolean isResolvable(IChromosome<?> chromosome, IAllele allele) {
		return !(chromosome instanceof IRegistryChromosome<?> registry) || registry.isValidAllele(allele);
	}

	private static Map<ResourceLocation, RawAllelePair> encodeGenome(IGenome genome) {
		Map<ResourceLocation, RawAllelePair> raw = new LinkedHashMap<>();
		for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : genome.getChromosomes().entrySet()) {
			AllelePair<?> pair = entry.getValue();
			raw.put(entry.getKey().id(), new RawAllelePair(pair.active().alleleId(), pair.inactive().alleleId()));
		}
		return raw;
	}

	@Override
	public ResourceLocation id() {
		return this.id;
	}

	@Override
	public ImmutableList<IChromosome<?>> getChromosomes() {
		// asList caches the returned list, no allocations to worry about
		return this.chromosomes.keySet().asList();
	}

	@Override
	public boolean contains(IChromosome<?> chromosome) {
		return this.chromosomes.containsKey(chromosome);
	}

	@Override
	public IRegistryChromosome<? extends ISpecies<?>> getSpeciesChromosome() {
		return this.speciesChromosome;
	}

	@Override
	public int size() {
		return this.chromosomes.size();
	}

	@Override
	public <A extends IAllele> boolean isAlleleValid(IChromosome<A> chromosome, A allele) {
		ImmutableSet<? extends IAllele> validAlleles = this.chromosomes.get(chromosome);

		if (validAlleles != null) {
			if (chromosome instanceof IRegistryChromosome<?> registry) {
				return !registry.isPopulated() || registry.isValidAllele(allele);
			} else {
				return validAlleles.contains(allele);
			}
		}

		return false;
	}

	@Override
	public <A extends IAllele> boolean isChromosomeValid(IChromosome<A> chromosome) {
		return this.chromosomes.containsKey(chromosome);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <A extends IAllele> A getDefaultAllele(IChromosome<A> chromosome) {
		A allele = (A) this.defaultAlleles.get(chromosome);
		if (allele == null) {
			throw new IllegalArgumentException("Chromosome is not valid");
		}
		return allele;
	}

	@Override
	public boolean isWeaklyInherited(IChromosome<?> chromosome) {
		return this.weaklyInheritedChromosomes.contains(chromosome);
	}

	@SuppressWarnings({"DataFlowIssue", "unchecked"})
	@Override
	public <A extends IAllele> Collection<A> getAlleles(IChromosome<A> chromosome) {
		Preconditions.checkArgument(isChromosomeValid(chromosome), "Chromosome not present in karyotype");

		ImmutableSet<? extends IAllele> validAlleles = this.chromosomes.get(chromosome);
		if (validAlleles.isEmpty()) {
			return (Collection<A>) ((IRegistryChromosome<?>) chromosome).alleles();
		} else {
			return (Collection<A>) validAlleles.asList();
		}
	}

	@Override
	public ImmutableMap<IChromosome<?>, ? extends IAllele> getDefaultAlleles() {
		return this.defaultAlleles;
	}

	@Override
	public IGenomeBuilder createGenomeBuilder() {
		return new Genome.Builder(this);
	}

	@Override
	public ResourceLocation getDefaultSpecies() {
		return this.defaultSpecies;
	}

	@Override
	public Codec<IGenome> getGenomeCodec() {
		return this.genomeCodec;
	}

	public static class Builder implements IKaryotypeBuilder {
		private final LinkedHashMap<IChromosome<?>, ChromosomeBuilder<?>> chromosomes = new LinkedHashMap<>();
		@Nullable
		private IRegistryChromosome<? extends ISpecies<?>> speciesChromosome;
		@Nullable
		private ResourceLocation defaultSpeciesId;

		@Override
		public void setSpecies(IRegistryChromosome<? extends ISpecies<?>> species, ResourceLocation defaultId) {
			if (this.speciesChromosome != null && this.speciesChromosome != species) {
				throw new IllegalStateException("The species chromosome for this karyotype has already been set: " + this.speciesChromosome.id() + ", but tried setting to " + species.id());
			} else {
				this.speciesChromosome = species;
				this.defaultSpeciesId = defaultId;
			}
		}

		@Override
		public void set(IRegistryChromosome<?> chromosome, ResourceLocation defaultId) {
			this.chromosomes.computeIfAbsent(chromosome, key -> new ChromosomeBuilder<>(chromosome));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <A extends IAllele> IChromosomeBuilder<A> get(IChromosome<A> chromosome) {
			return (IChromosomeBuilder<A>) this.chromosomes.computeIfAbsent(chromosome, key -> new ChromosomeBuilder<>(chromosome));
		}

		public Karyotype build(ResourceLocation id) {
			Preconditions.checkState(this.defaultSpeciesId != null && this.speciesChromosome != null, "IKaryotypeBuilder is missing a species chromosome.");

			ImmutableMap.Builder<IChromosome<?>, ImmutableSet<? extends IAllele>> permittedAlleles = ImmutableMap.builderWithExpectedSize(this.chromosomes.size() + 1);
			ImmutableMap.Builder<IChromosome<?>, IAllele> defaultAlleles = ImmutableMap.builderWithExpectedSize(this.chromosomes.size() + 1);
			Set<IChromosome<?>> weaklyInheritedChromosomes = Collections.newSetFromMap(new IdentityHashMap<>());

			// Species chromosome goes first
			permittedAlleles.put(this.speciesChromosome, ImmutableSet.of());
			defaultAlleles.put(this.speciesChromosome, IForestryApi.INSTANCE.getAlleleManager().registryAllele(this.defaultSpeciesId, this.speciesChromosome));

			for (Map.Entry<IChromosome<?>, ChromosomeBuilder<?>> entry : this.chromosomes.entrySet()) {
				IChromosome<?> chromosome = entry.getKey();
				ChromosomeBuilder<?> builder = entry.getValue();
				ImmutableSet<? extends IAllele> permitted = builder.alleles.build();
				// registry alleles are added later
				if (!(chromosome instanceof IRegistryChromosome<?>) && permitted.isEmpty()) {
					throw new IllegalStateException("Chromosome missing permitted alleles in karyotype.");
				}
				if (builder.defaultAllele == null) {
					throw new IllegalStateException("Chromosome \"" + chromosome.id() + "\" has no default allele. Please set one in the karyotype for the species " + this.speciesChromosome.id());
				}
				permittedAlleles.put(chromosome, permitted);
				defaultAlleles.put(chromosome, builder.defaultAllele);

				if (builder.weaklyInherited) {
					weaklyInheritedChromosomes.add(chromosome);
				}
			}

			return new Karyotype(id, permittedAlleles.build(), defaultAlleles.build(), this.defaultSpeciesId, weaklyInheritedChromosomes);
		}
	}
}
