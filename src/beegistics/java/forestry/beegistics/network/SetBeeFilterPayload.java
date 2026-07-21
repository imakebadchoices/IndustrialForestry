package forestry.beegistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import forestry.beegistics.Beegistics;
import forestry.beegistics.BeeFilter;
import forestry.beegistics.ItemBeeFilterCard;

/**
 * Serverbound: the client's {@code BeeFilterScreen} pushes the edited {@link BeeFilter} onto the {@link ItemBeeFilterCard}
 * the player is holding. The card is a held item, so a hand flag is all the server needs to locate it.
 */
public record SetBeeFilterPayload(boolean mainHand, BeeFilter filter) implements CustomPacketPayload {
	public static final Type<SetBeeFilterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Beegistics.NAMESPACE, "set_bee_filter"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SetBeeFilterPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, SetBeeFilterPayload::mainHand,
		BeeFilter.STREAM_CODEC, SetBeeFilterPayload::filter,
		SetBeeFilterPayload::new);

	public static void handle(SetBeeFilterPayload msg, ServerPlayer player) {
		InteractionHand hand = msg.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() instanceof ItemBeeFilterCard) {
			ItemBeeFilterCard.setFilter(stack, msg.filter());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
