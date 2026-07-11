package forestry.api;

import com.mojang.serialization.MapCodec;

import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.circuits.ICircuit;
import forestry.api.core.IProduct;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.ISpeciesType;
import forestry.api.mail.IPostalCarrier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class ForestryRegistries {
	public static final Registry<ICircuit> CIRCUIT = new RegistryBuilder<>(Keys.CIRCUIT_TYPE)
		.sync(true)
		.create();

	public static final Registry<IPostalCarrier> POSTAL_CARRIER = new RegistryBuilder<>(Keys.POSTAL_CARRIER)
		.sync(true)
		.create();

	public static final Registry<ISpeciesType<?, ?>> SPECIES_TYPE = new RegistryBuilder<>(Keys.SPECIES_TYPE)
		.sync(true)
		.create();

	/**
	 * Serializer registry for {@link IProduct} types, used to dispatch the datapack {@link IProduct#CODEC}.
	 * Not synced: the codecs are resolved by ID on both sides at (de)serialization time, so clients only
	 * need the registry populated (which happens at mod init), not sent over the network.
	 */
	public static final Registry<MapCodec<? extends IProduct>> PRODUCT_TYPE = new RegistryBuilder<>(Keys.PRODUCT_TYPE)
		.create();

	/**
	 * Serializer registry for {@link IBeeJubilance} types, used to dispatch {@link IBeeJubilance#CODEC}.
	 * Not synced, for the same reason as {@link #PRODUCT_TYPE}.
	 */
	public static final Registry<MapCodec<? extends IBeeJubilance>> BEE_JUBILANCE_TYPE = new RegistryBuilder<>(Keys.BEE_JUBILANCE_TYPE)
		.create();

	/**
	 * Serializer registry for {@link IMutationCondition} types, used to dispatch {@link IMutationCondition#CODEC}
	 * in datapack mutation definitions. Not synced, for the same reason as {@link #PRODUCT_TYPE}.
	 */
	public static final Registry<MapCodec<? extends IMutationCondition>> BEE_MUTATION_CONDITION_TYPE = new RegistryBuilder<>(Keys.BEE_MUTATION_CONDITION_TYPE)
		.create();

	/**
	 * Serializer registry for {@link IBeeEffect} primitive types, used to dispatch {@link IBeeEffect#CODEC}
	 * in datapack effect definitions. Not synced, for the same reason as {@link #PRODUCT_TYPE}.
	 */
	public static final Registry<MapCodec<? extends IBeeEffect>> BEE_EFFECT_TYPE = new RegistryBuilder<>(Keys.BEE_EFFECT_TYPE)
		.create();

	public static class Keys {
		public static final ResourceKey<Registry<ICircuit>> CIRCUIT_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("circuit"));
		public static final ResourceKey<Registry<IPostalCarrier>> POSTAL_CARRIER = ResourceKey.createRegistryKey(ForestryConstants.forestry("postal_carrier"));
		public static final ResourceKey<Registry<ISpeciesType<?, ?>>> SPECIES_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("species_type"));
		public static final ResourceKey<Registry<MapCodec<? extends IProduct>>> PRODUCT_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("product_type"));
		public static final ResourceKey<Registry<MapCodec<? extends IBeeJubilance>>> BEE_JUBILANCE_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_jubilance_type"));
		public static final ResourceKey<Registry<MapCodec<? extends IMutationCondition>>> BEE_MUTATION_CONDITION_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_mutation_condition_type"));
		public static final ResourceKey<Registry<MapCodec<? extends IBeeEffect>>> BEE_EFFECT_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_effect_type"));
	}
}
