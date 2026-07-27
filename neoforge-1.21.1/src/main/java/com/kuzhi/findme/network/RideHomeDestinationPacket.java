package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientRideHomeReadyState;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Starts client-side readiness checks for a player-only ride-home transfer. */
public record RideHomeDestinationPacket(UUID mountUuid,
                                        double startX, double startY, double startZ,
                                        double targetX, double targetY, double targetZ)
        implements CustomPacketPayload {
    public static final Type<RideHomeDestinationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "ride_home_destination"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RideHomeDestinationPacket> STREAM_CODEC =
            NetworkCodecs.of(RideHomeDestinationPacket::encode, RideHomeDestinationPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RideHomeDestinationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.mountUuid);
        buffer.writeDouble(packet.startX);
        buffer.writeDouble(packet.startY);
        buffer.writeDouble(packet.startZ);
        buffer.writeDouble(packet.targetX);
        buffer.writeDouble(packet.targetY);
        buffer.writeDouble(packet.targetZ);
    }

    private static RideHomeDestinationPacket decode(FriendlyByteBuf buffer) {
        return new RideHomeDestinationPacket(buffer.readUUID(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(RideHomeDestinationPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientRideHomeReadyState.awaitDestination(packet.mountUuid,
                new Vec3(packet.startX, packet.startY, packet.startZ),
                new Vec3(packet.targetX, packet.targetY, packet.targetZ)));
        context.setPacketHandled(true);
    }
}
