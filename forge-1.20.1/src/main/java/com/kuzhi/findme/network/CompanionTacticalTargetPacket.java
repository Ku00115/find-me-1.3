package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientTacticalTargetOutlineState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record CompanionTacticalTargetPacket(int entityId, int durationTicks) {
    public static void encode(CompanionTacticalTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeVarInt(packet.durationTicks);
    }

    public static CompanionTacticalTargetPacket decode(FriendlyByteBuf buffer) {
        return new CompanionTacticalTargetPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(CompanionTacticalTargetPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientTacticalTargetOutlineState.update(packet.entityId(), packet.durationTicks()));
        context.setPacketHandled(true);
    }
}
