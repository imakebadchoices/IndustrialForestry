package forestry.apiculture.features;

import com.mojang.serialization.MapCodec;

import forestry.api.ForestryConstants;
import forestry.api.ForestryRegistries;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.core.IProduct;
import forestry.api.modules.ForestryModuleIds;
import forestry.apiculture.genetics.DefaultBeeJubilance;
import forestry.apiculture.genetics.FireworkProduct;
import forestry.apiculture.genetics.HermitBeeJubilance;
import forestry.apiculture.genetics.RequiresResourceBeeJubilance;
import forestry.apiculture.genetics.effects.BonemealBeeEffect;
import forestry.apiculture.genetics.effects.DamageBeeEffect;
import forestry.apiculture.genetics.effects.EntityForceBeeEffect;
import forestry.apiculture.genetics.effects.FeedBeeEffect;
import forestry.apiculture.genetics.effects.FillFluidBeeEffect;
import forestry.apiculture.genetics.effects.FireworkBeeEffect;
import forestry.apiculture.genetics.effects.InjectEnergyBeeEffect;
import forestry.apiculture.genetics.effects.LightningBeeEffect;
import forestry.apiculture.genetics.effects.PlaceBlockBeeEffect;
import forestry.apiculture.genetics.effects.PotionBeeEffect;
import forestry.apiculture.genetics.effects.ProjectileBeeEffect;
import forestry.apiculture.genetics.effects.SpawnMobBeeEffect;
import forestry.apiculture.genetics.effects.TeleportBeeEffect;
import forestry.apiculture.genetics.effects.TransformBlockBeeEffect;
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
	public static final DeferredRegister<MapCodec<? extends IBeeEffect>> BEE_EFFECT_TYPES = REGISTRY.getRegistry(ForestryRegistries.Keys.BEE_EFFECT_TYPE);

	public static final DeferredHolder<Feature<?>, HiveDecorator> HIVE = FEATURES.register("hive", HiveDecorator::new);
	public static final DeferredHolder<StructurePoolElementType<?>, StructurePoolElementType<ApiaristPoolElement>> APIARIST_POOL_ELEMENT_TYPE = POOL_ELEMENT_TYPES.register("apiarist", () -> () -> ApiaristPoolElement.CODEC);

	// The Patriotic bee's dynamic firework product.
	public static final DeferredHolder<MapCodec<? extends IProduct>, MapCodec<FireworkProduct>> FIREWORK_PRODUCT = PRODUCT_TYPES.register("firework", () -> FireworkProduct.MAP_CODEC);

	public static final DeferredHolder<MapCodec<? extends IBeeJubilance>, MapCodec<DefaultBeeJubilance>> DEFAULT_JUBILANCE = JUBILANCE_TYPES.register("default", () -> DefaultBeeJubilance.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeJubilance>, MapCodec<HermitBeeJubilance>> HERMIT_JUBILANCE = JUBILANCE_TYPES.register("hermit", () -> HermitBeeJubilance.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeJubilance>, MapCodec<RequiresResourceBeeJubilance>> REQUIRES_RESOURCE_JUBILANCE = JUBILANCE_TYPES.register("requires_resource", () -> RequiresResourceBeeJubilance.MAP_CODEC);

	// Parameterized bee-effect primitives, dispatched by IBeeEffect#CODEC in datapack effect definitions.
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<PotionBeeEffect>> APPLY_POTION_EFFECT = BEE_EFFECT_TYPES.register("apply_potion", () -> PotionBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<SpawnMobBeeEffect>> SPAWN_MOB_EFFECT = BEE_EFFECT_TYPES.register("spawn_mob", () -> SpawnMobBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<DamageBeeEffect>> DAMAGE_ENTITIES_EFFECT = BEE_EFFECT_TYPES.register("damage_entities", () -> DamageBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<FeedBeeEffect>> FEED_EFFECT = BEE_EFFECT_TYPES.register("feed", () -> FeedBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<FireworkBeeEffect>> FIREWORK_EFFECT = BEE_EFFECT_TYPES.register("firework", () -> FireworkBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<LightningBeeEffect>> STRIKE_LIGHTNING_EFFECT = BEE_EFFECT_TYPES.register("strike_lightning", () -> LightningBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<TeleportBeeEffect>> TELEPORT_EFFECT = BEE_EFFECT_TYPES.register("teleport", () -> TeleportBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<EntityForceBeeEffect>> ENTITY_FORCE_EFFECT = BEE_EFFECT_TYPES.register("entity_force", () -> EntityForceBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<BonemealBeeEffect>> BONEMEAL_EFFECT = BEE_EFFECT_TYPES.register("bonemeal", () -> BonemealBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<ProjectileBeeEffect>> SPAWN_PROJECTILE_EFFECT = BEE_EFFECT_TYPES.register("spawn_projectile", () -> ProjectileBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<TransformBlockBeeEffect>> TRANSFORM_BLOCK_EFFECT = BEE_EFFECT_TYPES.register("transform_block", () -> TransformBlockBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<PlaceBlockBeeEffect>> PLACE_BLOCK_EFFECT = BEE_EFFECT_TYPES.register("place_block", () -> PlaceBlockBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<FillFluidBeeEffect>> FILL_FLUID_EFFECT = BEE_EFFECT_TYPES.register("fill_fluid", () -> FillFluidBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<InjectEnergyBeeEffect>> INJECT_ENERGY_EFFECT = BEE_EFFECT_TYPES.register("inject_energy", () -> InjectEnergyBeeEffect.MAP_CODEC);

	public static final ResourceKey<ConfiguredFeature<?, ?>> CONFIGURED_HIVE = ResourceKey.create(Registries.CONFIGURED_FEATURE, ForestryConstants.forestry("hive"));
	public static final ResourceKey<PlacedFeature> PLACED_HIVE = ResourceKey.create(Registries.PLACED_FEATURE, ForestryConstants.forestry("hive"));
}
