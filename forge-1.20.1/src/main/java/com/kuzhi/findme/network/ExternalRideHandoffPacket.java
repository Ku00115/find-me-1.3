package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientExternalRideHandoffState;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ExternalRideHandoffPacket(UUID targetUuid, CompanionMoveType sourceMoveType,
                                        CompanionMoveType targetMoveType,
                                        double velocityX, double velocityY, double velocityZ,
                                        float yRot, float xRot) {
public static void encode(ExternalRideHandoffPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.targetUuid);
        buffer.writeEnum(packet.sourceMoveType);
        buffer.writeEnum(packet.targetMoveType);
        buffer.writeDouble(packet.velocityX);
        buffer.writeDouble(packet.velocityY);
        buffer.writeDouble(packet.velocityZ);
        buffer.writeFloat(packet.yRot);
        buffer.writeFloat(packet.xRot);
    }

    public static ExternalRideHandoffPacket decode(FriendlyByteBuf buffer) {
        return new ExternalRideHandoffPacket(buffer.readUUID(), buffer.readEnum(CompanionMoveType.class),
                buffer.readEnum(CompanionMoveType.class),
                buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(ExternalRideHandoffPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientExternalRideHandoffState.queue(packet.targetUuid, packet.sourceMoveType,
                packet.targetMoveType,
                new Vec3(packet.velocityX, packet.velocityY, packet.velocityZ), packet.yRot, packet.xRot));
        context.setPacketHandled(true);
    }
}
