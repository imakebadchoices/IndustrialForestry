package forestry.apiculture.genetics;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.IForestryApi;
import forestry.api.core.HumidityType;
import forestry.api.core.Product;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.alleles.IAllele;
import forestry.api.genetics.alleles.IChromosome;

/**
 * A datapack-loadable definition of a bee species (breed). Base Forestry's own bees are emitted
 * as JSON of this shape by {@code ForestryBeeSpeciesProvider}, so the built-ins double as worked
 * examples. Loaded through the {@code forestry:bee_species} datapack registry (see {@link #REGISTRY_KEY}).
 * <p>
 * Phase 1 covers the data-only fields. The behavioral escape hatches (custom jubilance, dynamic
 * products, mutation conditions) and mutations are handled in later phases; until then, definitions
 * fall back to the built-in defaults for those.
 */
public record BeeSpeciesDefinition(
	String genus,
	String species,
	boolean dominant,
	TextColor outline,
	TextColor body,
	TextColor stripes,
	TemperatureType temperature,
	HumidityType humidity,
	int complexity,
	boolean glint,
	boolean secret,
	String authority,
	int escritoireColor,
	List<Product> products,
	List<Product> specialties,
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
		TextColor.CODEC.fieldOf("outline").forGetter(BeeSpeciesDefinition::outline),
		TextColor.CODEC.optionalFieldOf("body", DEFAULT_BODY).forGetter(BeeSpeciesDefinition::body),
		TextColor.CODEC.optionalFieldOf("stripes", DEFAULT_STRIPES).forGetter(BeeSpeciesDefinition::stripes),
		enumCodec(TemperatureType.class).optionalFieldOf("temperature", TemperatureType.NORMAL).forGetter(BeeSpeciesDefinition::temperature),
		enumCodec(HumidityType.class).optionalFieldOf("humidity", HumidityType.NORMAL).forGetter(BeeSpeciesDefinition::humidity),
		Codec.INT.optionalFieldOf("complexity", 0).forGetter(BeeSpeciesDefinition::complexity),
		Codec.BOOL.optionalFieldOf("glint", false).forGetter(BeeSpeciesDefinition::glint),
		Codec.BOOL.optionalFieldOf("secret", false).forGetter(BeeSpeciesDefinition::secret),
		Codec.STRING.optionalFieldOf("authority", "Sengir").forGetter(BeeSpeciesDefinition::authority),
		Codec.INT.optionalFieldOf("escritoire_color", -1).forGetter(BeeSpeciesDefinition::escritoireColor),
		Product.CODEC.listOf().optionalFieldOf("products", List.of()).forGetter(BeeSpeciesDefinition::products),
		Product.CODEC.listOf().optionalFieldOf("specialties", List.of()).forGetter(BeeSpeciesDefinition::specialties),
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
