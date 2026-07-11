package forestry.apiculture.genetics;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.IForestryApi;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.core.HumidityType;
import forestry.api.core.IProduct;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.alleles.IAllele;
import forestry.api.genetics.alleles.IChromosome;

/**
 * A datapack-loadable definition of a bee species (breed), loaded through the {@code forestry:bee_species}
 * datapack registry (see {@link #REGISTRY_KEY}).
 * <p>
 * Data-only fields (colors, climate, products, genome) plus the behavioral escape hatches that are
 * expressible as dispatch codecs — dynamic {@link IProduct products} and {@link IBeeJubilance jubilance} —
 * are covered here. Mutations (and their conditions) live in their own registry in a later phase; a
 * definition without a {@code jubilance} block falls back to the default temperature/humidity jubilance.
 */
public record BeeSpeciesDefinition(
	String genus,
	String species,
	boolean dominant,
	Coloration coloration,
	TemperatureType temperature,
	HumidityType humidity,
	int complexity,
	boolean glint,
	boolean secret,
	String authority,
	int escritoireColor,
	List<IProduct> products,
	List<IProduct> specialties,
	IBeeJubilance jubilance,
	Map<IChromosome<?>, IAllele> genome
) {
	/**
	 * The datapack registry that holds every bee species definition. Entries live at
	 * {@code data/<namespace>/forestry/bee_species/<name>.json}.
	 */
	public static final ResourceKey<Registry<BeeSpeciesDefinition>> REGISTRY_KEY = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_species"));

	public static final TextColor DEFAULT_BODY = TextColor.fromRgb(0xffdc16);
	public static final TextColor DEFAULT_STRIPES = TextColor.fromRgb(0x000000);

	/**
	 * The three bee colors. Bundled into one record purely to keep the parent {@link #CODEC} within
	 * {@code RecordCodecBuilder}'s 16-field limit; its {@link #MAP_CODEC} reads/writes {@code outline},
	 * {@code body} and {@code stripes} at the top level, so the JSON stays flat.
	 */
	public record Coloration(TextColor outline, TextColor body, TextColor stripes) {
		public static final MapCodec<Coloration> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			TextColor.CODEC.fieldOf("outline").forGetter(Coloration::outline),
			TextColor.CODEC.optionalFieldOf("body", DEFAULT_BODY).forGetter(Coloration::body),
			TextColor.CODEC.optionalFieldOf("stripes", DEFAULT_STRIPES).forGetter(Coloration::stripes)
		).apply(instance, Coloration::new));
	}

	/**
	 * Resolves a chromosome by its ID against the runtime allele manager. Only invoked at
	 * (de)serialization time, which happens after all chromosomes have been registered.
	 */
	public static final Codec<IChromosome<?>> CHROMOSOME_CODEC = ResourceLocation.CODEC.comapFlatMap(
		id -> {
			IChromosome<?> chromosome = IForestryApi.INSTANCE.getAlleleManager().getChromosome(id);
			return chromosome != null ? DataResult.success(chromosome) : DataResult.error(() -> "Unknown chromosome: " + id);
		},
		IChromosome::id
	);

	public static final Codec<Map<IChromosome<?>, IAllele>> GENOME_CODEC = Codec.unboundedMap(CHROMOSOME_CODEC, IAllele.CODEC);

	public static final Codec<BeeSpeciesDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("genus").forGetter(BeeSpeciesDefinition::genus),
		Codec.STRING.fieldOf("species").forGetter(BeeSpeciesDefinition::species),
		Codec.BOOL.optionalFieldOf("dominant", false).forGetter(BeeSpeciesDefinition::dominant),
		Coloration.MAP_CODEC.forGetter(BeeSpeciesDefinition::coloration),
		enumCodec(TemperatureType.class).optionalFieldOf("temperature", TemperatureType.NORMAL).forGetter(BeeSpeciesDefinition::temperature),
		enumCodec(HumidityType.class).optionalFieldOf("humidity", HumidityType.NORMAL).forGetter(BeeSpeciesDefinition::humidity),
		Codec.INT.optionalFieldOf("complexity", 0).forGetter(BeeSpeciesDefinition::complexity),
		Codec.BOOL.optionalFieldOf("glint", false).forGetter(BeeSpeciesDefinition::glint),
		Codec.BOOL.optionalFieldOf("secret", false).forGetter(BeeSpeciesDefinition::secret),
		Codec.STRING.optionalFieldOf("authority", "Sengir").forGetter(BeeSpeciesDefinition::authority),
		Codec.INT.optionalFieldOf("escritoire_color", -1).forGetter(BeeSpeciesDefinition::escritoireColor),
		IProduct.CODEC.listOf().optionalFieldOf("products", List.of()).forGetter(BeeSpeciesDefinition::products),
		IProduct.CODEC.listOf().optionalFieldOf("specialties", List.of()).forGetter(BeeSpeciesDefinition::specialties),
		IBeeJubilance.CODEC.optionalFieldOf("jubilance", DefaultBeeJubilance.INSTANCE).forGetter(BeeSpeciesDefinition::jubilance),
		GENOME_CODEC.optionalFieldOf("genome", Map.of()).forGetter(BeeSpeciesDefinition::genome)
	).apply(instance, BeeSpeciesDefinition::new));

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> clazz) {
		E[] values = clazz.getEnumConstants();
		return Codec.STRING.comapFlatMap(
			name -> {
				for (E value : values) {
					if (value.name().equalsIgnoreCase(name)) {
						return DataResult.success(value);
					}
				}
				return DataResult.error(() -> "Unknown " + clazz.getSimpleName() + ": " + name);
			},
			value -> value.name().toLowerCase(Locale.ROOT)
		);
	}
}
