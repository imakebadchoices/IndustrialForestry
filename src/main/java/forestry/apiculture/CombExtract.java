package forestry.apiculture;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The data-component payload of the one generic {@code forestry:comb_extract} item — the fluid intermediary
 * a "fluid comb" centrifuges into and a squeezer turns back into its {@link #fluid()}. This is how Extra Bees'
 * propolis/honey-drop-to-fluid step becomes pure data: no per-fluid item or registry, the fluid is carried on
 * the stack and the item auto-tints from it.
 *
 * <p>{@link #flavor()} is purely cosmetic (the propolis vs. honey-drop appearance/name and the squeeze time —
 * Binnie squeezed propolis at 20 ticks, honey drops at 10); the produced fluid is identical either way.
 */
public record CombExtract(FluidStack fluid, String flavor, Optional<String> source, Optional<TextColor> color) {
	public static final String PROPOLIS = "propolis";
	public static final String HONEY_DROP = "honey_drop";

	private static final StreamCodec<ByteBuf, TextColor> COLOR_STREAM_CODEC =
		ByteBufCodecs.INT.map(TextColor::fromRgb, TextColor::getValue);

	public static final Codec<CombExtract> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		FluidStack.CODEC.fieldOf("fluid").forGetter(CombExtract::fluid),
		Codec.STRING.optionalFieldOf("flavor", PROPOLIS).forGetter(CombExtract::flavor),
		// namespace the extract is attributed to in JEI/creative (getCreatorModId); the fluid comb's pack
		Codec.STRING.optionalFieldOf("source").forGetter(CombExtract::source),
		// render tint, baked from the source comb's colour so the extract reads as cohesive with its comb
		// (rather than the wildly-varying per-mod fluid tint); falls back to the fluid tint when absent
		TextColor.CODEC.optionalFieldOf("color").forGetter(CombExtract::color)
	).apply(instance, CombExtract::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, CombExtract> STREAM_CODEC = StreamCodec.composite(
		FluidStack.STREAM_CODEC, CombExtract::fluid,
		ByteBufCodecs.STRING_UTF8, CombExtract::flavor,
		ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), CombExtract::source,
		ByteBufCodecs.optional(COLOR_STREAM_CODEC), CombExtract::color,
		CombExtract::new
	);

	/** Ticks to squeeze this extract — matches Binnie (propolis 20, honey drop 10). */
	public int squeezeTime() {
		return HONEY_DROP.equals(this.flavor) ? 10 : 20;
	}

	/**
	 * A stable value-based key for JEI subtypes / dedup. Needed because {@link FluidStack} has no value-based
	 * {@code hashCode}/{@code equals}, so a {@code CombExtract} record can't be used as a map key directly.
	 */
	public String subtypeKey() {
		return this.flavor + "|" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(this.fluid.getFluid())
			+ "|" + this.fluid.getAmount() + "|" + this.source.orElse("");
	}
}
