package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientRideHomeReadyState;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Starts client-side readiness checks for a player-only ride-home transfer. */
public record RideHomeDestinationPacket(UUID mountUuid,
                                        double startX, double startY, double startZ,
                                        double targetX, double targetY, double targetZ) {
public static void encode(RideHomeDestinationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.mountUuid);
        buffer.writeDouble(packet.startX);
        buffer.writeDouble(packet.startY);
        buffer.writeDouble(packet.startZ);
        buffer.writeDouble(packet.targetX);
        buffer.writeDouble(packet.targetY);
        buffer.writeDouble(packet.targetZ);
    }

    public static RideHomeDestinationPacket decode(FriendlyByteBuf buffer) {
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
