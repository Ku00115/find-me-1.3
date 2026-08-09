package com.kuzhi.findme.network;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

/** Requests the server-side teleport to a companion currently travelling forward. */
public record CompanionForwardTravelTeleportPacket(CompanionKind kind, UUID targetUuid) {
    public static void encode(CompanionForwardTravelTeleportPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind);
        buffer.writeUUID(packet.targetUuid);
    }

    public static CompanionForwardTravelTeleportPacket decode(FriendlyByteBuf buffer) {
        return new CompanionForwardTravelTeleportPacket(buffer.readEnum(CompanionKind.class), buffer.readUUID());
    }

    public static void handle(CompanionForwardTravelTeleportPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionTacticalOrderService.teleportOwnerToForwardTravel(
                        context.getSender(), packet.kind, packet.targetUuid);
            }
        });
        context.setPacketHandled(true);
    }
}




