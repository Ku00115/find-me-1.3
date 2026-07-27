package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Confirms that the client has compiled the visible destination route. */
public record RideHomeReadyPacket(UUID mountUuid) {
public static void encode(RideHomeReadyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.mountUuid);
    }

    public static RideHomeReadyPacket decode(FriendlyByteBuf buffer) {
        return new RideHomeReadyPacket(buffer.readUUID());
    }

    public static void handle(RideHomeReadyPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionRideHomeJourneyService.onClientReady(context.getSender(), packet.mountUuid);
            }
        });
        context.setPacketHandled(true);
    }
}
