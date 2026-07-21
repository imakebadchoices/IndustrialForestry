package forestry.extrabees.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import forestry.api.core.IProduct;
import forestry.api.core.ProductType;

/**
 * A product that resolves an item tag at runtime, yielding a random member of the tag. If no loaded
 * mod fills the tag it produces nothing, reproducing Binnie's {@code tryAddProduct} graceful-skip
 * for optional integrations. This lets Extra Bees combs output mod-agnostic {@code c:dusts/iron},
 * {@code c:gems/ruby}, etc. instead of hard-coding one mod's items.
 */
public record TagProduct(TagKey<Item> tag, int count, float chance) implements IProduct {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("extrabees", "tag");

	public static final MapCodec<TagProduct> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(TagProduct::tag),
		Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(TagProduct::count),
		Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(TagProduct::chance)
	).apply(instance, TagProduct::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TagProduct> STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(Registries.ITEM, rl), TagKey::location), TagProduct::tag,
		ByteBufCodecs.VAR_INT, TagProduct::count,
		ByteBufCodecs.FLOAT, TagProduct::chance,
		TagProduct::new
	);
	public static final ProductType<TagProduct> TYPE = new ProductType<>(MAP_CODEC, STREAM_CODEC);

	public TagProduct(TagKey<Item> tag, float chance) {
		this(tag, 1, chance);
	}

	@Override
	public ProductType<?> type() {
		return TYPE;
	}

	@Override
	public Item item() {
		return firstItem().orElse(Items.AIR);
	}

	@Override
	public ItemStack createStack() {
		return firstItem().map(item -> new ItemStack(item, this.count)).orElse(ItemStack.EMPTY);
	}

	@Override
	public ItemStack createRandomStack(RandomSource random) {
		return BuiltInRegistries.ITEM.getTag(this.tag)
			.flatMap(set -> set.getRandomElement(random))
			.map(holder -> new ItemStack(holder.value(), this.count))
			.orElse(ItemStack.EMPTY);
	}

	private java.util.Optional<Item> firstItem() {
		return BuiltInRegistries.ITEM.getTag(this.tag)
			.map(HolderSet.Named::stream)
			.flatMap(java.util.stream.Stream::findFirst)
			.map(Holder::value);
	}
}
