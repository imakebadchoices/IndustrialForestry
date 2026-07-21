package forestry.beegistics.crafting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The identity of a single bee mutation, stored in the {@code beegistics:bee_mutation} data component on an
 * {@link forestry.beegistics.ItemBeeMutationPattern}. It names the mutation by its three species ids so a
 * {@link BeeMutationPattern} can be reconstructed - and its canonical parent/result {@code AEItemKey}s derived - from
 * the item alone, without embedding any genome bytes.
 *
 * @param firstParent  The id of one parent species (bred as a princess by the controller).
 * @param secondParent The id of the other parent species (bred as a drone).
 * @param result       The id of the species this mutation produces.
 */
public record BeeMutation(ResourceLocation firstParent, ResourceLocation secondParent, ResourceLocation result) {
	public static final Codec<BeeMutation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("first_parent").forGetter(BeeMutation::firstParent),
		ResourceLocation.CODEC.fieldOf("second_parent").forGetter(BeeMutation::secondParent),
		ResourceLocation.CODEC.fieldOf("result").forGetter(BeeMutation::result)
	).apply(instance, BeeMutation::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BeeMutation> STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC, BeeMutation::firstParent,
		ResourceLocation.STREAM_CODEC, BeeMutation::secondParent,
		ResourceLocation.STREAM_CODEC, BeeMutation::result,
		BeeMutation::new);
}
