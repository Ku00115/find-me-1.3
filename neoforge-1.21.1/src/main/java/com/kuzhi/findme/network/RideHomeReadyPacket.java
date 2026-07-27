package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Confirms that the client has compiled the visible destination route. */
public record RideHomeReadyPacket(UUID mountUuid) implements CustomPacketPayload {
    public static final Type<RideHomeReadyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "ride_home_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RideHomeReadyPacket> STREAM_CODEC =
            NetworkCodecs.of(RideHomeReadyPacket::encode, RideHomeReadyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RideHomeReadyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.mountUuid);
    }

    private static RideHomeReadyPacket decode(FriendlyByteBuf buffer) {
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
