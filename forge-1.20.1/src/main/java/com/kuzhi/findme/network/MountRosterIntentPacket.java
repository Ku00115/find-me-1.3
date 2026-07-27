package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.server.command.MountRosterTransactionService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MountRosterIntentPacket(UUID requestId, MountRosterAction action,
                                      MountRosterSource destinationSource, UUID targetUuid,
                                      int sourceSlot, int teamIndex, long expectedRevision) {
public static void encode(MountRosterIntentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId);
        buffer.writeEnum(packet.action);
        buffer.writeEnum(packet.destinationSource);
        buffer.writeUUID(packet.targetUuid);
        buffer.writeVarInt(packet.sourceSlot);
        buffer.writeVarInt(packet.teamIndex);
        buffer.writeLong(packet.expectedRevision);
    }

    public static MountRosterIntentPacket decode(FriendlyByteBuf buffer) {
        return new MountRosterIntentPacket(buffer.readUUID(), buffer.readEnum(MountRosterAction.class),
                buffer.readEnum(MountRosterSource.class), buffer.readUUID(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readLong());
    }

    public static void handle(MountRosterIntentPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                MountRosterTransactionService.begin(context.getSender(), packet.requestId, packet.action,
                        packet.destinationSource, packet.targetUuid, packet.sourceSlot,
                        packet.teamIndex, packet.expectedRevision);
            }
        });
        context.setPacketHandled(true);
    }
}
