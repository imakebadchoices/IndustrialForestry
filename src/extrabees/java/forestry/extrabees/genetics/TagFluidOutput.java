package forestry.extrabees.genetics;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import forestry.api.core.FluidProductType;
import forestry.api.core.IFluidProduct;

/**
 * A squeezer fluid output that resolves a fluid tag at runtime, yielding a fixed amount of the first fluid in the
 * tag. If no loaded mod fills the tag it resolves to {@link FluidStack#EMPTY} and the squeezer refuses the recipe.
 * This lets Extra Bees combs squeeze into mod-agnostic fluids ({@code c:crude_oil}, {@code c:diesel}, ...) instead
 * of hard-coding one mod's fluid - the fluid-side analogue of {@link TagProduct}.
 */
public record TagFluidOutput(TagKey<Fluid> tag, int amount) implements IFluidProduct {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("extrabees", "tag");

	public static final MapCodec<TagFluidOutput> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		TagKey.codec(Registries.FLUID).fieldOf("tag").forGetter(TagFluidOutput::tag),
		ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(TagFluidOutput::amount)
	).apply(instance, TagFluidOutput::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, TagFluidOutput> STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(Registries.FLUID, rl), TagKey::location), TagFluidOutput::tag,
		ByteBufCodecs.VAR_INT, TagFluidOutput::amount,
		TagFluidOutput::new
	);
	public static final FluidProductType<TagFluidOutput> TYPE = new FluidProductType<>(MAP_CODEC, STREAM_CODEC);

	@Override
	public FluidStack createFluidStack() {
		return BuiltInRegistries.FLUID.getTag(this.tag)
			.flatMap(PreferredMember::first)
			.map(holder -> new FluidStack(holder.value(), this.amount))
			.orElse(FluidStack.EMPTY);
	}

	@Override
	public FluidProductType<?> type() {
		return TYPE;
	}
}
