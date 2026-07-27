package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CompanionTacticalCommandPacket(CompanionKind kind, java.util.UUID targetUuid,
                                              CompanionTacticalAction action, BlockPos targetPos,
                                              int targetEntityId) {
public static void encode(CompanionTacticalCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind);
        buffer.writeUUID(packet.targetUuid);
        buffer.writeEnum(packet.action);
        buffer.writeBlockPos(packet.targetPos == null ? BlockPos.ZERO : packet.targetPos);
        buffer.writeInt(packet.targetEntityId);
    }

    public static CompanionTacticalCommandPacket decode(FriendlyByteBuf buffer) {
        return new CompanionTacticalCommandPacket(buffer.readEnum(CompanionKind.class), buffer.readUUID(),
                buffer.readEnum(CompanionTacticalAction.class), buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(CompanionTacticalCommandPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionTacticalOrderService.handle(context.getSender(), packet.kind, packet.targetUuid,
                        packet.action, packet.targetPos, packet.targetEntityId);
            }
        });
        context.setPacketHandled(true);
    }
}
