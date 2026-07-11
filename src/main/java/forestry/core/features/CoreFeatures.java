package forestry.core.features;

import com.mojang.serialization.MapCodec;
import forestry.api.ForestryConstants;
import forestry.api.ForestryRegistries;
import forestry.api.core.IProduct;
import forestry.api.core.Product;
import forestry.api.genetics.IMutationCondition;
import forestry.api.modules.ForestryModuleIds;
import forestry.core.genetics.mutations.MutationConditionBiome;
import forestry.core.genetics.mutations.MutationConditionCave;
import forestry.core.genetics.mutations.MutationConditionDaytime;
import forestry.core.genetics.mutations.MutationConditionHumidity;
import forestry.core.genetics.mutations.MutationConditionRequiresResource;
import forestry.core.genetics.mutations.MutationConditionTemperature;
import forestry.core.genetics.mutations.MutationConditionTimeLimited;
import forestry.core.worldgen.ForestryBiomeModifier;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.ModFeatureRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@FeatureProvider
public class CoreFeatures {
	private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS = ModFeatureRegistry.get(ForestryModuleIds.CORE).getRegistry(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS);
	private static final DeferredRegister<MapCodec<? extends IProduct>> PRODUCT_TYPES = ModFeatureRegistry.get(ForestryModuleIds.CORE).getRegistry(ForestryRegistries.Keys.PRODUCT_TYPE);
	private static final DeferredRegister<MapCodec<? extends IMutationCondition>> MUTATION_CONDITION_TYPES = ModFeatureRegistry.get(ForestryModuleIds.CORE).getRegistry(ForestryRegistries.Keys.BEE_MUTATION_CONDITION_TYPE);

	// The default product type: a plain item stack with a chance. See IProduct#CODEC for the JSON shape.
	public static final DeferredHolder<MapCodec<? extends IProduct>, MapCodec<Product>> ITEM_PRODUCT = PRODUCT_TYPES.register("item", () -> Product.MAP_CODEC);

	// Mutation condition types, dispatched by IMutationCondition#CODEC in datapack mutation definitions.
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionTemperature>> TEMPERATURE_CONDITION = MUTATION_CONDITION_TYPES.register("temperature", () -> MutationConditionTemperature.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionHumidity>> HUMIDITY_CONDITION = MUTATION_CONDITION_TYPES.register("humidity", () -> MutationConditionHumidity.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionBiome>> BIOME_CONDITION = MUTATION_CONDITION_TYPES.register("biome", () -> MutationConditionBiome.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionDaytime>> DAYTIME_CONDITION = MUTATION_CONDITION_TYPES.register("daytime", () -> MutationConditionDaytime.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionTimeLimited>> TIME_LIMITED_CONDITION = MUTATION_CONDITION_TYPES.register("time_limited", () -> MutationConditionTimeLimited.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionRequiresResource>> REQUIRES_RESOURCE_CONDITION = MUTATION_CONDITION_TYPES.register("requires_resource", () -> MutationConditionRequiresResource.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IMutationCondition>, MapCodec<MutationConditionCave>> CAVE_CONDITION = MUTATION_CONDITION_TYPES.register("cave", () -> MutationConditionCave.MAP_CODEC);

	public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_APATITE = ResourceKey.create(Registries.CONFIGURED_FEATURE, ForestryConstants.forestry("ore_apatite"));
	public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_TIN = ResourceKey.create(Registries.CONFIGURED_FEATURE, ForestryConstants.forestry("ore_tin"));

	public static final ResourceKey<PlacedFeature> PLACED_APATITE = ResourceKey.create(Registries.PLACED_FEATURE, ForestryConstants.forestry("ore_apatite"));
	public static final ResourceKey<PlacedFeature> PLACED_TIN = ResourceKey.create(Registries.PLACED_FEATURE, ForestryConstants.forestry("ore_tin"));

	// Responsible for hives + trees
	private static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ForestryBiomeModifier>> FORESTRY = BIOME_MODIFIERS.register("forestry", () -> ForestryBiomeModifier.CODEC);
}
