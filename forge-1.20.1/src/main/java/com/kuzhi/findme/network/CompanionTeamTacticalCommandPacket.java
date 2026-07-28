package com.kuzhi.findme.network;

import com.kuzhi.findme.common.CompanionTeamCommandAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.server.lifecycle.CompanionTeamOrderService;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record CompanionTeamTacticalCommandPacket(CompanionTeamTarget target, int teamIndex,
                                                  CompanionTeamCommandAction action, BlockPos targetPos,
                                                  int targetEntityId) {
    public static void encode(CompanionTeamTacticalCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.target); buffer.writeVarInt(packet.teamIndex); buffer.writeEnum(packet.action);
        buffer.writeBlockPos(packet.targetPos == null ? BlockPos.ZERO : packet.targetPos);
        buffer.writeInt(packet.targetEntityId);
    }
    public static CompanionTeamTacticalCommandPacket decode(FriendlyByteBuf buffer) {
        return new CompanionTeamTacticalCommandPacket(buffer.readEnum(CompanionTeamTarget.class),
                buffer.readVarInt(), buffer.readEnum(CompanionTeamCommandAction.class), buffer.readBlockPos(), buffer.readInt());
    }
    public static void handle(CompanionTeamTacticalCommandPacket packet, Supplier<FindMeNetworkContext.Context> contexts) {
        FindMeNetworkContext.Context context = contexts.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) CompanionTeamOrderService.handle(context.getSender(), packet.target,
                    packet.teamIndex, packet.action, packet.targetPos, packet.targetEntityId);
        });
        context.setPacketHandled(true);
    }
}
