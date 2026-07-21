package forestry.extrabees.genetics.effects;

import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import forestry.api.ForestryRegistries;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.extrabees.ExtraBees;

/**
 * Registers the Extra Bees bee-effect primitives on Forestry's {@link ForestryRegistries#BEE_EFFECT_TYPE} dispatch,
 * under the {@code extrabees} namespace. These are the parameterized primitives Forestry deleted from core once its
 * own bees stopped using them ("no built-in consumer"); Extra Bees is that consumer, so it carries them itself and
 * its datapack effect definitions reference them as {@code extrabees:<name>}.
 *
 * <p>Registration goes through a plain NeoForge {@link DeferredRegister} on the add-on's mod bus rather than
 * Forestry's {@code FeatureRegistry} (which is keyed to the {@code forestry} mod container), mirroring
 * {@code ExtraBeesItems}.
 */
public final class ExtraBeesBeeEffectTypes {
	public static final DeferredRegister<MapCodec<? extends IBeeEffect>> BEE_EFFECT_TYPES = DeferredRegister.create(ForestryRegistries.Keys.BEE_EFFECT_TYPE, ExtraBees.NAMESPACE);

	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<SpawnMobBeeEffect>> SPAWN_MOB = BEE_EFFECT_TYPES.register("spawn_mob", () -> SpawnMobBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<FireworkBeeEffect>> FIREWORK = BEE_EFFECT_TYPES.register("firework", () -> FireworkBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<LightningBeeEffect>> STRIKE_LIGHTNING = BEE_EFFECT_TYPES.register("strike_lightning", () -> LightningBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<EntityForceBeeEffect>> ENTITY_FORCE = BEE_EFFECT_TYPES.register("entity_force", () -> EntityForceBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<BonemealBeeEffect>> BONEMEAL = BEE_EFFECT_TYPES.register("bonemeal", () -> BonemealBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<ProjectileBeeEffect>> SPAWN_PROJECTILE = BEE_EFFECT_TYPES.register("spawn_projectile", () -> ProjectileBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<PlaceBlockBeeEffect>> PLACE_BLOCK = BEE_EFFECT_TYPES.register("place_block", () -> PlaceBlockBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<FillFluidBeeEffect>> FILL_FLUID = BEE_EFFECT_TYPES.register("fill_fluid", () -> FillFluidBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<InjectEnergyBeeEffect>> INJECT_ENERGY = BEE_EFFECT_TYPES.register("inject_energy", () -> InjectEnergyBeeEffect.MAP_CODEC);

	public static void register(IEventBus modBus) {
		BEE_EFFECT_TYPES.register(modBus);
	}

	private ExtraBeesBeeEffectTypes() {
	}
}
