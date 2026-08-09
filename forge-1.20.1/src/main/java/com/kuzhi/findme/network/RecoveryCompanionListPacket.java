package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientCompanionState;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record RecoveryCompanionListPacket(List<Entry> entries) {
    public static void encode(RecoveryCompanionListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeUUID(entry.uuid);
            buffer.writeEnum(entry.kind);
            buffer.writeUtf(entry.entityType, 128);
            buffer.writeUtf(entry.name, 128);
            buffer.writeUUID(entry.recordId);
            buffer.writeVarInt(entry.previousTeamIndex);
            buffer.writeLong(entry.detectedAt);
            buffer.writeLong(entry.lastCheckedAt);
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeUtf(entry.reason, 128);
            buffer.writeUtf(entry.detail, 512);
            buffer.writeEnum(entry.sourceState);
            buffer.writeEnum(entry.moveType);
            buffer.writeNbt(entry.previewTag);
        }
    }

    public static RecoveryCompanionListPacket decode(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_ROSTER_ENTRIES,
                "recovery companion entry");
        ArrayList<Entry> entries = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readEnum(CompanionKind.class), buffer.readUtf(128),
                    buffer.readUtf(128), buffer.readUUID(), buffer.readVarInt(), buffer.readLong(),
                    buffer.readLong(), buffer.readUtf(128), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readUtf(128), buffer.readUtf(512),
                    buffer.readEnum(CompanionLifecycleState.class), buffer.readEnum(CompanionMoveType.class),
                    buffer.readNbt()));
        }
        return new RecoveryCompanionListPacket(entries);
    }

    public static void handle(RecoveryCompanionListPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionState.updateRecovery(packet.entries));
        context.setPacketHandled(true);
    }

    public record Entry(UUID uuid, CompanionKind kind, String entityType, String name, UUID recordId,
                        int previousTeamIndex, long detectedAt, long lastCheckedAt, String dimension,
                        double x, double y, double z, String reason, String detail,
                        CompanionLifecycleState sourceState, CompanionMoveType moveType, CompoundTag previewTag) {
    }
}
