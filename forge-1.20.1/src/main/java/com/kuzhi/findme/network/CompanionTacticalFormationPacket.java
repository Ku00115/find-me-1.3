package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientTacticalFormationState;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record CompanionTacticalFormationPacket(UUID operationUuid, CompanionDeploymentPlan.Intent intent,
                                                double centerX, double centerY, double centerZ,
                                                float centerRadius, int durationTicks,
                                                List<Member> members) {
    public CompanionTacticalFormationPacket {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static void encode(CompanionTacticalFormationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.operationUuid);
        buffer.writeEnum(packet.intent);
        buffer.writeDouble(packet.centerX);
        buffer.writeDouble(packet.centerY);
        buffer.writeDouble(packet.centerZ);
        buffer.writeFloat(packet.centerRadius);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeCollection(packet.members, (target, member) -> {
            target.writeDouble(member.x);
            target.writeDouble(member.y);
            target.writeDouble(member.z);
            target.writeFloat(member.radius);
            target.writeVarInt(member.revealOffsetTicks);
            target.writeEnum(member.moveType);
        });
    }

    public static CompanionTacticalFormationPacket decode(FriendlyByteBuf buffer) {
        UUID operationUuid = buffer.readUUID();
        CompanionDeploymentPlan.Intent intent = buffer.readEnum(CompanionDeploymentPlan.Intent.class);
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        float radius = buffer.readFloat();
        int duration = buffer.readVarInt();
        int memberCount = PacketDecodeLimits.readCount(buffer,
                PacketDecodeLimits.MAX_TACTICAL_FORMATION_MEMBERS, "tactical formation member");
        List<Member> members = new ArrayList<>(PacketDecodeLimits.initialCapacity(memberCount));
        for (int i = 0; i < memberCount; i++) {
            members.add(new Member(
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readEnum(com.kuzhi.findme.common.CompanionMoveType.class)));
        }
        return new CompanionTacticalFormationPacket(operationUuid, intent, x, y, z, radius, duration, members);
    }

    public static void handle(CompanionTacticalFormationPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientTacticalFormationState.start(packet));
        context.setPacketHandled(true);
    }

    public record Member(double x, double y, double z, float radius, int revealOffsetTicks,
                         com.kuzhi.findme.common.CompanionMoveType moveType) {
    }
}
