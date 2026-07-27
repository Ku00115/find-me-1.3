package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.command.CompanionCommandHandler;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CompanionWheelIntentPacket(UUID requestId, CompanionKind kind, CompanionAction action,
                                         UUID targetUuid, long expectedRevision) {
public static void encode(CompanionWheelIntentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId);
        buffer.writeEnum(packet.kind);
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.targetUuid);
        buffer.writeLong(packet.expectedRevision);
    }

    public static CompanionWheelIntentPacket decode(FriendlyByteBuf buffer) {
        return new CompanionWheelIntentPacket(buffer.readUUID(), buffer.readEnum(CompanionKind.class),
                buffer.readEnum(CompanionAction.class), buffer.readUUID(), buffer.readLong());
    }

    public static void handle(CompanionWheelIntentPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionCommandHandler.handleWheelIntent(context.getSender(), packet.requestId, packet.kind,
                        packet.action, packet.targetUuid, packet.expectedRevision);
            }
        });
        context.setPacketHandled(true);
    }
}
