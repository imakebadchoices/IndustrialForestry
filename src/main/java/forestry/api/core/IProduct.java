package forestry.api.core;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import it.unimi.dsi.fastutil.Hash;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

import forestry.api.ForestryRegistries;

/**
 * Represents some item that has a set chance of being produced.
 *
 * @see Product The default implementation used in the majority of cases.
 */
public interface IProduct {
	/**
	 * Dispatch codec for products in datapack species definitions. An object with a {@code "type"} field
	 * is resolved against {@link ForestryRegistries#PRODUCT_TYPE}; for ergonomics and backwards
	 * compatibility, an object without a {@code "type"} field falls back to the plain {@link Product}
	 * shape ({@code {"item": ..., "chance": ...}}). Encoding always writes the explicit type form.
	 */
	// The alternative is lazy on purpose: IProduct has a default method, so constructing any Product forces
	// IProduct's static init while Product's own init is still in progress. Reading Product.CODEC eagerly here
	// would capture a null; deferring it until first (de)serialization sidesteps that class-init cycle.
	Codec<IProduct> CODEC = Codec.withAlternative(
		ForestryRegistries.PRODUCT_TYPE.byNameCodec().dispatch("type", IProduct::codec, Function.identity()),
		Codec.lazyInitialized(() -> Product.CODEC)
	);

	/**
	 * @return The serializer used to (de)serialize this product in a datapack species definition.
	 * Must be registered in {@link ForestryRegistries#PRODUCT_TYPE}.
	 */
	MapCodec<? extends IProduct> codec();

	/**
	 * A hashing strategy used for FastUtil custom hash collections.
	 * Currently, Forestry uses this to remove common products between species from the product list of a hybrid bee.
	 */
	Hash.Strategy<IProduct> ITEM_ONLY_STRATEGY = new Hash.Strategy<>() {
		@Override
		public int hashCode(@Nullable IProduct o) {
			return o == null ? 0 : o.item().hashCode();
		}

		@Override
		public boolean equals(@Nullable IProduct a, @Nullable IProduct b) {
			return (a == null || b == null) ? a == b : a.item() == b.item();
		}
	};

	// todo should this be replaced with is(ItemStack) and getIconStack() methods instead?

	/**
	 * Gets the item this product contains. In the case of a dynamic product, return an item that might
	 * be used to display it in a screen or for equality purposes in {@link #ITEM_ONLY_STRATEGY}.
	 *
	 * @return The item this product represents.
	 */
	Item item();

	/**
	 * @return The set chance of this product being produced.
	 */
	float chance();

	/**
	 * @return A new stack of this product. If your product is dynamic, return a "default" nonempty stack.
	 */
	ItemStack createStack();

	/**
	 * Used to produce a random variant of this product.
	 *
	 * @param random The random source. If no randomness is desired, call {@link #createStack} instead.
	 * @return A new stack of this product with potentially random properties.
	 */
	default ItemStack createRandomStack(RandomSource random) {
		return createStack();
	}
}
