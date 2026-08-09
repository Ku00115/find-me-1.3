package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests the server-side teleport to a companion currently travelling forward. */
public record CompanionForwardTravelTeleportPacket(CompanionKind kind, UUID targetUuid)
        implements CustomPacketPayload {
    public static final Type<CompanionForwardTravelTeleportPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "forward_travel_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionForwardTravelTeleportPacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionForwardTravelTeleportPacket::encode,
                    CompanionForwardTravelTeleportPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

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
