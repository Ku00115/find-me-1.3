package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientMountRosterTransactionState;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.common.WheelIntentPhase;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record MountRosterIntentResultPacket(UUID requestId, MountRosterAction action,
                                             MountRosterSource destinationSource, UUID targetUuid,
                                             long serverRevision, WheelIntentPhase phase, String reason) {
public static void encode(MountRosterIntentResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId);
        buffer.writeEnum(packet.action);
        buffer.writeEnum(packet.destinationSource);
        buffer.writeUUID(packet.targetUuid);
        buffer.writeLong(packet.serverRevision);
        buffer.writeEnum(packet.phase);
        buffer.writeUtf(packet.reason, 128);
    }

    public static MountRosterIntentResultPacket decode(FriendlyByteBuf buffer) {
        return new MountRosterIntentResultPacket(buffer.readUUID(), buffer.readEnum(MountRosterAction.class),
                buffer.readEnum(MountRosterSource.class), buffer.readUUID(), buffer.readLong(),
                buffer.readEnum(WheelIntentPhase.class), buffer.readUtf(128));
    }

    public static void handle(MountRosterIntentResultPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientMountRosterTransactionState.acceptResult(packet));
        context.setPacketHandled(true);
    }
}
