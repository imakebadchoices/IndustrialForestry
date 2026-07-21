package forestry.beegistics.network;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the add-on's network payloads. Beegistics is its own {@code @Mod}, so it registers directly with NeoForge's
 * {@link PayloadRegistrar} rather than going through Forestry's packet framework.
 */
public final class BeegisticsNetwork {
	private BeegisticsNetwork() {
	}

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(SetBeeFilterPayload.TYPE, SetBeeFilterPayload.STREAM_CODEC, BeegisticsNetwork::handleSetBeeFilter);
	}

	private static void handleSetBeeFilter(SetBeeFilterPayload msg, IPayloadContext context) {
		context.enqueueWork(() -> SetBeeFilterPayload.handle(msg, (ServerPlayer) context.player()));
	}
}
