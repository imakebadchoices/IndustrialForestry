package forestry.apiculture;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.IFlowerType;

/**
 * A datapack-loadable definition of a bee flower type, loaded through the {@code forestry:flower_type}
 * datapack registry (see {@link #REGISTRY_KEY}). Each entry becomes a flower allele whose ID is the entry
 * key; a species genome references it through the {@code forestry:flower_type} chromosome. The
 * {@linkplain forestry.apiculture.genetics.DatapackBeePlugin rebuild} turns each definition into a
 * {@link DataFlowerType} and feeds it into {@link forestry.api.plugin.IApicultureRegistration#registerFlowerType}
 * before species are built.
 * <p>
 * A flower type is a plain block predicate, so it is fully data-drivable: {@code accepted} is the set of
 * blocks (given as an inline list and/or block tag) a queen accepts as a flower, and {@code plantable} is an
 * optional list of block states the queen may plant near the hive (empty = non-planting). This single
 * parameterized shape covers the Extra Bees flower types (dead bush, rock, wood, sapling, …) as pure JSON.
 */
public record FlowerTypeDefinition(String type, HolderSet<Block> accepted, List<BlockState> plantable, boolean dominant) {
	/** The default, pure block/tag predicate flower (a {@link DataFlowerType}). */
	public static final String TYPE_BLOCK = "block";
	/** Extra Bees' FRUIT flower — accepts fruit-bearing block entities (a {@link FruitFlowerType}). */
	public static final String TYPE_FRUIT = "fruit";

	/**
	 * The datapack registry that holds every flower type definition. Entries live at
	 * {@code data/<namespace>/forestry/flower_type/<name>.json}. Synced to clients because the flower_type
	 * chromosome is populated during the client-side species rebuild, so the alleles must exist there too.
	 */
	public static final ResourceKey<Registry<FlowerTypeDefinition>> REGISTRY_KEY = ResourceKey.createRegistryKey(ForestryConstants.forestry("flower_type"));

	public static final Codec<FlowerTypeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("type", TYPE_BLOCK).forGetter(FlowerTypeDefinition::type),
		RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("accepted", HolderSet.direct()).forGetter(FlowerTypeDefinition::accepted),
		BlockState.CODEC.listOf().optionalFieldOf("plantable", List.of()).forGetter(FlowerTypeDefinition::plantable),
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(FlowerTypeDefinition::dominant)
	).apply(instance, FlowerTypeDefinition::new));

	/**
	 * @return The runtime {@link IFlowerType} this definition describes. Most flowers are a pure block/tag
	 * predicate ({@link DataFlowerType}); {@code type: "fruit"} is the one code-shaped exception
	 * ({@link FruitFlowerType}). Add another case here (not a new registry) for any future bespoke flower.
	 */
	public IFlowerType asFlowerType() {
		return switch (this.type) {
			case TYPE_FRUIT -> new FruitFlowerType(this.dominant);
			default -> new DataFlowerType(this.accepted, this.plantable, this.dominant);
		};
	}
}
