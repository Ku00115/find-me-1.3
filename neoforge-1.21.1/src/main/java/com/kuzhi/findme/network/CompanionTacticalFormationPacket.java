package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientTacticalFormationState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionTacticalFormationPacket(UUID operationUuid, CompanionDeploymentPlan.Intent intent,
                                                double centerX, double centerY, double centerZ,
                                                float centerRadius, int durationTicks,
                                                List<Member> members) implements CustomPacketPayload {
    public static final Type<CompanionTacticalFormationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_tactical_formation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTacticalFormationPacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionTacticalFormationPacket::encode, CompanionTacticalFormationPacket::decode);

    public CompanionTacticalFormationPacket {
        members = members == null ? List.of() : List.copyOf(members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(CompanionTacticalFormationPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.operationUuid);
        buffer.writeEnum(packet.intent);
        buffer.writeDouble(packet.centerX);
        buffer.writeDouble(packet.centerY);
        buffer.writeDouble(packet.centerZ);
        buffer.writeFloat(packet.centerRadius);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeCollection(packet.members, (target, member) -> {
            target.writeDouble(member.x); target.writeDouble(member.y); target.writeDouble(member.z);
            target.writeFloat(member.radius); target.writeVarInt(member.revealOffsetTicks);
            target.writeEnum(member.moveType);
        });
    }

    public static CompanionTacticalFormationPacket decode(FriendlyByteBuf buffer) {
        UUID operationUuid = buffer.readUUID();
        CompanionDeploymentPlan.Intent intent = buffer.readEnum(CompanionDeploymentPlan.Intent.class);
        double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
        float radius = buffer.readFloat();
        int duration = buffer.readVarInt();
        List<Member> members = buffer.readCollection(ArrayList::new, source -> new Member(
                source.readDouble(), source.readDouble(), source.readDouble(), source.readFloat(),
                source.readVarInt(), source.readEnum(CompanionMoveType.class)));
        return new CompanionTacticalFormationPacket(operationUuid, intent, x, y, z, radius, duration, members);
    }

    public static void handle(CompanionTacticalFormationPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientTacticalFormationState.start(packet));
        context.setPacketHandled(true);
    }

    public record Member(double x, double y, double z, float radius, int revealOffsetTicks,
                         CompanionMoveType moveType) { }
}
