package forestry.apiculture.features;

import com.mojang.serialization.MapCodec;

import forestry.api.ForestryConstants;
import forestry.api.ForestryRegistries;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.core.IProduct;
import forestry.api.modules.ForestryModuleIds;
import forestry.apiculture.genetics.DefaultBeeJubilance;
import forestry.apiculture.genetics.FireworkProduct;
import forestry.apiculture.genetics.HermitBeeJubilance;
import forestry.apiculture.genetics.RequiresResourceBeeJubilance;
import forestry.apiculture.hives.HiveDecorator;
import forestry.core.worldgen.ApiaristPoolElement;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@FeatureProvider
public class ApicultureFeatures {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.APICULTURE);

	public static final DeferredRegister<Feature<?>> FEATURES = REGISTRY.getRegistry(Registries.FEATURE);
	public static final DeferredRegister<StructurePoolElementType<?>> POOL_ELEMENT_TYPES = REGISTRY.getRegistry(Registries.STRUCTURE_POOL_ELEMENT);
	public static final DeferredRegister<MapCodec<? extends IProduct>> PRODUCT_TYPES = REGISTRY.getRegistry(ForestryRegistries.Keys.PRODUCT_TYPE);
	public static final DeferredRegister<MapCodec<? extends IBeeJubilance>> JUBILANCE_TYPES = REGISTRY.getRegistry(ForestryRegistries.Keys.BEE_JUBILANCE_TYPE);

	public static final DeferredHolder<Feature<?>, HiveDecorator> HIVE = FEATURES.register("hive", HiveDecorator::new);
	public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<ApiaristPoolElement>> APIARIST_POOL_ELEMENT_TYPE = POOL_ELEMENT_TYPES.register("apiarist", () -> () -> ApiaristPoolElement.CODEC);

	// The Patriotic bee's dynamic firework product.
	public static final DeferredHolder<MapCodec<? extends IProduct>, MapCodec<FireworkProduct>> FIREWORK_PRODUCT = PRODUCT_TYPES.register("firework", () -> FireworkProduct.MAP_CODEC);

	public static final DeferredHolder<MapCodec<? extends IBeeJubilance>, MapCodec<DefaultBeeJubilance>> DEFAULT_JUBILANCE = JUBILANCE_TYPES.register("default", () -> DefaultBeeJubilance.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeJubilance>, MapCodec<HermitBeeJubilance>> HERMIT_JUBILANCE = JUBILANCE_TYPES.register("hermit", () -> HermitBeeJubilance.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeJubilance>, MapCodec<RequiresResourceBeeJubilance>> REQUIRES_RESOURCE_JUBILANCE = JUBILANCE_TYPES.register("requires_resource", () -> RequiresResourceBeeJubilance.MAP_CODEC);

	public static final ResourceKey<ConfiguredFeature<?, ?>> CONFIGURED_HIVE = ResourceKey.create(Registries.CONFIGURED_FEATURE, ForestryConstants.forestry("hive"));
	public static final ResourceKey<PlacedFeature> PLACED_HIVE = ResourceKey.create(Registries.PLACED_FEATURE, ForestryConstants.forestry("hive"));
}
