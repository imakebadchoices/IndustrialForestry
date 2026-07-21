package forestry.beegistics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.ILifeStage;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.core.features.CoreDataComponents;

/**
 * A serializable predicate over Forestry bees, stored in a data component on the {@link ItemBeeFilterCard}. This is the
 * core matching primitive the rest of the add-on's automation builds on: because every bee in an ME network is an
 * ordinary {@link AEItemKey} carrying its full {@code GENOME}, anything that reads {@link AEKey}s (terminals, level
 * emitters, export filters) can evaluate a {@code BeeFilter} against network contents directly.
 *
 * <p>Unlike AE2's stock exact/fuzzy matching, a {@code BeeFilter} expresses genetic intent: "any drone of genus Apis",
 * "the species that a mutation produces" (whose exact genome key is unknown ahead of time), pure vs. hybrid, and so on.
 * Every field is optional; an unset field imposes no constraint, so the {@link #EMPTY empty} filter matches any bee.
 *
 * @param stages          The life stages to accept (princess/drone/queen/larva). Empty means any stage.
 * @param species         If present, the required <em>active</em> (expressed) species id.
 * @param genus           If present, the required genus name (matched case-insensitively).
 * @param inactiveSpecies If present, the required <em>inactive</em> species id - lets a filter target hybrids carrying a
 *                        particular recessive species.
 * @param analyzed        If present, requires the bee to be analyzed ({@code true}) or unanalyzed ({@code false}).
 * @param pristine        If present, requires the bee to be pristine/pure-bred ({@code true}) or ignoble ({@code false}).
 * @param genomeTemplate  If present, supplies the "wanted" allele values that {@code requiredActive}/
 *                        {@code requiredHomozygous} reference. Values are read via the typed genome codec, so any allele
 *                        type (speed, lifespan, tolerance, effect id, ...) is expressed without stringifying.
 * @param requiredActive  Chromosome ids (by {@link IChromosome#id()}) whose <em>active</em> (expressed) value must equal
 *                        the template's - a phenotype constraint ("expresses this trait", hidden alleles ignored).
 * @param requiredHomozygous Chromosome ids that must be <em>homozygous</em> for the template's value (both alleles equal
 *                        it) - a true-breeding constraint, the unit the trait-breeding driver drives toward.
 */
public record BeeFilter(
	Set<BeeLifeStage> stages,
	Optional<ResourceLocation> species,
	Optional<String> genus,
	Optional<ResourceLocation> inactiveSpecies,
	Optional<Boolean> analyzed,
	Optional<Boolean> pristine,
	Optional<IGenome> genomeTemplate,
	Set<ResourceLocation> requiredActive,
	Set<ResourceLocation> requiredHomozygous
) {
	/** A filter with no constraints - matches any bee. Also the default value of the card's data component. */
	public static final BeeFilter EMPTY = new BeeFilter(EnumSet.noneOf(BeeLifeStage.class), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Set.of(), Set.of());

	private static final Codec<BeeLifeStage> STAGE_CODEC = StringRepresentable.fromEnum(BeeLifeStage::values);

	public static final Codec<BeeFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		STAGE_CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(filter -> new ArrayList<>(filter.stages)),
		ResourceLocation.CODEC.optionalFieldOf("species").forGetter(BeeFilter::species),
		Codec.STRING.optionalFieldOf("genus").forGetter(BeeFilter::genus),
		ResourceLocation.CODEC.optionalFieldOf("inactive_species").forGetter(BeeFilter::inactiveSpecies),
		Codec.BOOL.optionalFieldOf("analyzed").forGetter(BeeFilter::analyzed),
		Codec.BOOL.optionalFieldOf("pristine").forGetter(BeeFilter::pristine),
		IGenome.CODEC.optionalFieldOf("genome_template").forGetter(BeeFilter::genomeTemplate),
		ResourceLocation.CODEC.listOf().optionalFieldOf("required_active", List.of()).forGetter(filter -> new ArrayList<>(filter.requiredActive)),
		ResourceLocation.CODEC.listOf().optionalFieldOf("required_homozygous", List.of()).forGetter(filter -> new ArrayList<>(filter.requiredHomozygous))
	).apply(instance, (stages, species, genus, inactiveSpecies, analyzed, pristine, genomeTemplate, requiredActive, requiredHomozygous) ->
		new BeeFilter(toStageSet(stages), species, genus, inactiveSpecies, analyzed, pristine, genomeTemplate, toIdSet(requiredActive), toIdSet(requiredHomozygous))));

	public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, BeeFilter> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	private static Set<BeeLifeStage> toStageSet(List<BeeLifeStage> stages) {
		return stages.isEmpty() ? EnumSet.noneOf(BeeLifeStage.class) : EnumSet.copyOf(stages);
	}

	private static Set<ResourceLocation> toIdSet(List<ResourceLocation> ids) {
		return ids.isEmpty() ? Set.of() : new LinkedHashSet<>(ids);
	}

	/** Convenience constructor for the common species/stage predicate with no per-chromosome trait constraints. */
	public BeeFilter(Set<BeeLifeStage> stages, Optional<ResourceLocation> species, Optional<String> genus, Optional<ResourceLocation> inactiveSpecies, Optional<Boolean> analyzed, Optional<Boolean> pristine) {
		this(stages, species, genus, inactiveSpecies, analyzed, pristine, Optional.empty(), Set.of(), Set.of());
	}

	/**
	 * @return {@code true} if this filter imposes no constraints and therefore matches every bee.
	 */
	public boolean isEmpty() {
		return this.stages.isEmpty() && this.species.isEmpty() && this.genus.isEmpty()
			&& this.inactiveSpecies.isEmpty() && this.analyzed.isEmpty() && this.pristine.isEmpty()
			&& this.requiredActive.isEmpty() && this.requiredHomozygous.isEmpty();
	}

	/**
	 * @return {@code true} if the given key is a bee that satisfies every constraint of this filter.
	 */
	public boolean matches(AEKey key) {
		return key instanceof AEItemKey itemKey && matches(itemKey.getReadOnlyStack());
	}

	/**
	 * @return {@code true} if the given stack is a bee that satisfies every constraint of this filter.
	 */
	public boolean matches(ItemStack stack) {
		ISpeciesType<?, ?> type = IIndividualHandlerItem.getSpeciesType(stack);
		if (type == null || !type.id().equals(ForestrySpeciesTypes.BEE)) {
			return false;
		}

		if (!this.stages.isEmpty()) {
			ILifeStage stage = IIndividualHandlerItem.getLifeStage(stack);
			if (!(stage instanceof BeeLifeStage beeStage) || !this.stages.contains(beeStage)) {
				return false;
			}
		}

		IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
		if (individual == null) {
			return false;
		}
		if (this.species.isPresent() && !individual.getSpecies().id().equals(this.species.get())) {
			return false;
		}
		if (this.genus.isPresent() && !individual.getSpecies().getGenus().name().equalsIgnoreCase(this.genus.get())) {
			return false;
		}
		if (this.inactiveSpecies.isPresent() && !individual.getInactiveSpecies().id().equals(this.inactiveSpecies.get())) {
			return false;
		}
		if (this.analyzed.isPresent() && individual.isAnalyzed() != this.analyzed.get()) {
			return false;
		}
		if (this.pristine.isPresent() && stack.getOrDefault(CoreDataComponents.BEE_PRISTINE.get(), false) != this.pristine.get()) {
			return false;
		}
		if (!matchesTraits(individual.getGenome())) {
			return false;
		}
		return true;
	}

	/**
	 * @return {@code true} if the given genome satisfies every per-chromosome allele constraint. An empty template (or no
	 * required chromosomes) imposes nothing. Values are compared by {@link Objects#equals} against the template's, so no
	 * allele type is stringified. A required chromosome absent from the bee's karyotype fails the match.
	 */
	@SuppressWarnings("unchecked")
	private boolean matchesTraits(IGenome genome) {
		if (this.genomeTemplate.isEmpty() || (this.requiredActive.isEmpty() && this.requiredHomozygous.isEmpty())) {
			return true;
		}
		IGenome template = this.genomeTemplate.get();
		for (ResourceLocation id : this.requiredActive) {
			IChromosome<Object> chromosome = (IChromosome<Object>) genome.getKaryotype().getChromosome(id);
			if (chromosome == null || !Objects.equals(genome.getActiveValue(chromosome), template.getActiveValue(chromosome))) {
				return false;
			}
		}
		for (ResourceLocation id : this.requiredHomozygous) {
			IChromosome<Object> chromosome = (IChromosome<Object>) genome.getKaryotype().getChromosome(id);
			if (chromosome == null) {
				return false;
			}
			Object want = template.getActiveValue(chromosome);
			if (!Objects.equals(genome.getActiveValue(chromosome), want) || !Objects.equals(genome.getInactiveValue(chromosome), want)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Appends a human-readable, one-line-per-constraint description of this filter to the given tooltip list.
	 */
	public void describe(List<Component> tooltip) {
		if (isEmpty()) {
			tooltip.add(Component.translatable("tooltip.beegistics.bee_filter.any").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!this.stages.isEmpty()) {
			String stages = this.stages.stream().map(BeeLifeStage::getSerializedName).reduce((a, b) -> a + ", " + b).orElse("");
			tooltip.add(Component.translatable("tooltip.beegistics.bee_filter.stages", stages).withStyle(ChatFormatting.GRAY));
		}
		this.species.ifPresent(id -> tooltip.add(Component.translatable("tooltip.beegistics.bee_filter.species", id.toString()).withStyle(ChatFormatting.GRAY)));
		this.genus.ifPresent(name -> tooltip.add(Component.translatable("tooltip.beegistics.bee_filter.genus", name).withStyle(ChatFormatting.GRAY)));
		this.inactiveSpecies.ifPresent(id -> tooltip.add(Component.translatable("tooltip.beegistics.bee_filter.inactive_species", id.toString()).withStyle(ChatFormatting.GRAY)));
		this.analyzed.ifPresent(value -> tooltip.add(Component.translatable(value ? "tooltip.beegistics.bee_filter.analyzed" : "tooltip.beegistics.bee_filter.unanalyzed").withStyle(ChatFormatting.GRAY)));
		this.pristine.ifPresent(value -> tooltip.add(Component.translatable(value ? "tooltip.beegistics.bee_filter.pristine" : "tooltip.beegistics.bee_filter.ignoble").withStyle(ChatFormatting.GRAY)));
		this.genomeTemplate.ifPresent(template -> {
			for (ResourceLocation id : this.requiredHomozygous) {
				describeTrait(tooltip, template, id, "tooltip.beegistics.bee_filter.trait_pure");
			}
			for (ResourceLocation id : this.requiredActive) {
				if (!this.requiredHomozygous.contains(id)) {
					describeTrait(tooltip, template, id, "tooltip.beegistics.bee_filter.trait_active");
				}
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void describeTrait(List<Component> tooltip, IGenome template, ResourceLocation id, String key) {
		IChromosome<?> chromosome = template.getKaryotype().getChromosome(id);
		if (chromosome == null) {
			return;
		}
		Component name = Component.translatable(chromosome.chromosomeTranslationKey());
		Component value = Component.translatable(((IChromosome) chromosome).translationKey(template.getActiveValue(chromosome)));
		tooltip.add(Component.translatable(key, name, value).withStyle(ChatFormatting.GRAY));
	}
}
