package forestry.core.features;

import forestry.api.genetics.IGenome;
import forestry.api.modules.ForestryModuleIds;
import forestry.mail.Letter;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Forestry-side {@link DataComponentType} registrations.
 *
 * <p>Registered here because the 1.21 NeoForge {@code FluidHandlerItemStack} /
 * {@code FluidHandlerItemStackSimple} constructors now require a
 * {@code Supplier<DataComponentType<SimpleFluidContent>>} for fluid storage on item stacks
 * (replacing the implicit NBT storage used in 1.20).
 */
@FeatureProvider
public class CoreDataComponents {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.CORE);
	private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = REGISTRY.getRegistry(Registries.DATA_COMPONENT_TYPE);

	/**
	 * Generic fluid content component used by Forestry fluid-holding items (pipettes, capsules, cans, etc.).
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENT =
		DATA_COMPONENT_TYPES.register(
			"fluid_content",
			() -> DataComponentType.<SimpleFluidContent>builder()
				.persistent(SimpleFluidContent.CODEC)
				.networkSynchronized(SimpleFluidContent.STREAM_CODEC)
				.build());

	/**
	 * Fluid + flavor carried by the generic {@code forestry:comb_extract} item (the propolis/honey-drop
	 * intermediary a fluid comb produces and a squeezer converts back into the fluid).
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<forestry.apiculture.CombExtract>> COMB_EXTRACT =
		DATA_COMPONENT_TYPES.register(
			"comb_extract",
			() -> DataComponentType.<forestry.apiculture.CombExtract>builder()
				.persistent(forestry.apiculture.CombExtract.CODEC)
				.networkSynchronized(forestry.apiculture.CombExtract.STREAM_CODEC)
				.build());

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<IGenome>> GENOME =
		DATA_COMPONENT_TYPES.register(
			"genome",
			() -> DataComponentType.<IGenome>builder()
				.persistent(IGenome.CODEC)
				.networkSynchronized(ByteBufCodecs.fromCodec(IGenome.CODEC))
				.cacheEncoding()
				.build());

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<IGenome>> MATE_GENOME =
		DATA_COMPONENT_TYPES.register(
			"mate_genome",
			() -> DataComponentType.<IGenome>builder()
				.persistent(IGenome.CODEC)
				.networkSynchronized(ByteBufCodecs.fromCodec(IGenome.CODEC))
				.cacheEncoding()
				.build());

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ANALYZED =
		booleanComponent("analyzed");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HEALTH =
		intComponent("health");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MAX_HEALTH =
		intComponent("max_health");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BEE_PRISTINE =
		booleanComponent("bee_pristine");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BEE_GENERATION =
		intComponent("bee_generation");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ALYZER_CHARGES =
		intComponent("alyzer_charges");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ITEM_INVENTORY_UID =
		intComponent("item_inventory_uid");

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Letter>> LETTER_DATA =
		DATA_COMPONENT_TYPES.register(
			"letter_data",
			() -> DataComponentType.<Letter>builder()
				.persistent(Letter.CODEC)
				.networkSynchronized(ByteBufCodecs.fromCodec(Letter.CODEC))
				.build());

	/**
	 * Identifies which comb variant a stack of the generic {@link forestry.apiculture.items.ItemBeeComb} is.
	 * The value is the ID of an entry in the {@code forestry:comb_type} datapack registry
	 * ({@link forestry.apiculture.CombTypeDefinition}); the comb item resolves its tint colors from that
	 * registry. A plain {@link ResourceLocation} (not a {@code Holder}) is used deliberately so a dangling
	 * reference degrades to a default-tinted comb instead of throwing, matching the project's crash-safety
	 * approach for datapack-driven genetics.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> COMB_TYPE =
		DATA_COMPONENT_TYPES.register(
			"comb_type",
			() -> DataComponentType.<ResourceLocation>builder()
				.persistent(ResourceLocation.CODEC)
				.networkSynchronized(ResourceLocation.STREAM_CODEC)
				.build());

	private static DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> booleanComponent(String id) {
		return DATA_COMPONENT_TYPES.register(id, () -> DataComponentType.<Boolean>builder()
			.persistent(com.mojang.serialization.Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
			.build());
	}

	private static DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> intComponent(String id) {
		return DATA_COMPONENT_TYPES.register(id, () -> DataComponentType.<Integer>builder()
			.persistent(com.mojang.serialization.Codec.INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT)
			.build());
	}

}
