package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientCompanionWheelController;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.WheelIntentPhase;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CompanionWheelIntentResultPacket(UUID requestId, CompanionKind kind, CompanionAction action,
                                                UUID targetUuid, long serverRevision,
                                                WheelIntentPhase phase, String reason) {
public static void encode(CompanionWheelIntentResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId);
        buffer.writeEnum(packet.kind);
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.targetUuid);
        buffer.writeLong(packet.serverRevision);
        buffer.writeEnum(packet.phase);
        buffer.writeUtf(packet.reason, 128);
    }

    public static CompanionWheelIntentResultPacket decode(FriendlyByteBuf buffer) {
        return new CompanionWheelIntentResultPacket(buffer.readUUID(), buffer.readEnum(CompanionKind.class),
                buffer.readEnum(CompanionAction.class), buffer.readUUID(), buffer.readLong(),
                buffer.readEnum(WheelIntentPhase.class), buffer.readUtf(128));
    }

    public static void handle(CompanionWheelIntentResultPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionWheelController.acceptResult(packet));
        context.setPacketHandled(true);
    }
}
