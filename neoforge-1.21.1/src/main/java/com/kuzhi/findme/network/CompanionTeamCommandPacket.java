package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.server.command.CompanionTeamCommandHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionTeamCommandPacket(CompanionTeamAction action, CompanionTeamTarget target, int teamIndex, int secondaryIndex, String value, List<UUID> uuids) implements CustomPacketPayload {
    public static final Type<CompanionTeamCommandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_team_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTeamCommandPacket> STREAM_CODEC = NetworkCodecs.of(CompanionTeamCommandPacket::encode, CompanionTeamCommandPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(CompanionTeamCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeEnum(packet.target == null ? CompanionTeamTarget.MOUNT : packet.target);
        buffer.writeVarInt(packet.teamIndex);
        buffer.writeVarInt(packet.secondaryIndex);
        buffer.writeUtf(packet.value == null ? "" : packet.value, 64);
        buffer.writeVarInt(packet.uuids.size());
        for (UUID uuid : packet.uuids) {
            buffer.writeUUID(uuid);
        }
    }

    public static CompanionTeamCommandPacket decode(FriendlyByteBuf buffer) {
        CompanionTeamAction action = buffer.readEnum(CompanionTeamAction.class);
        CompanionTeamTarget target = buffer.readEnum(CompanionTeamTarget.class);
        int teamIndex = buffer.readVarInt();
        int secondaryIndex = buffer.readVarInt();
        String value = buffer.readUtf(64);
        int size = buffer.readVarInt();
        ArrayList<UUID> uuids = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            uuids.add(buffer.readUUID());
        }
        return new CompanionTeamCommandPacket(action, target, teamIndex, secondaryIndex, value, uuids);
    }

    public static void handle(CompanionTeamCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionTeamCommandHandler.handle(context.getSender(), packet.action, packet.target, packet.teamIndex, packet.secondaryIndex, packet.value, packet.uuids);
            }
        });
        context.setPacketHandled(true);
    }

}
