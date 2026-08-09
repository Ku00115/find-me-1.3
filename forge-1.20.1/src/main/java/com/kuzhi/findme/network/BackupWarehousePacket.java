package com.kuzhi.findme.network;

import com.kuzhi.findme.client.FindMeAuiManageScreen;
import com.kuzhi.findme.common.CompanionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Read-only warehouse contents reconstructed from one saved backup state. */
public record BackupWarehousePacket(int backupIndex, long savedAt, List<Entry> entries,
                                    boolean success, String messageKey) {
    public static void encode(BackupWarehousePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.backupIndex);
        buffer.writeLong(packet.savedAt);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeEnum(entry.kind);
            buffer.writeBoolean(entry.vehicle);
            buffer.writeUUID(entry.uuid);
            buffer.writeUtf(entry.name, 128);
            buffer.writeUtf(entry.entityType, 128);
            buffer.writeBoolean(entry.alive);
            buffer.writeNbt(entry.previewTag);
        }
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.messageKey == null ? "" : packet.messageKey, 256);
    }

    public static BackupWarehousePacket decode(FriendlyByteBuf buffer) {
        int backupIndex = buffer.readVarInt();
        long savedAt = buffer.readLong();
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_ROSTER_ENTRIES,
                "backup warehouse entry");
        ArrayList<Entry> entries = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readEnum(CompanionKind.class), buffer.readBoolean(), buffer.readUUID(),
                    buffer.readUtf(128), buffer.readUtf(128), buffer.readBoolean(), buffer.readNbt()));
        }
        return new BackupWarehousePacket(backupIndex, savedAt, List.copyOf(entries), buffer.readBoolean(), buffer.readUtf(256));
    }

    public static void handle(BackupWarehousePacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> FindMeAuiManageScreen.updateBackupWarehouse(packet));
        context.setPacketHandled(true);
    }

    public record Entry(CompanionKind kind, boolean vehicle, UUID uuid, String name, String entityType,
                        boolean alive, CompoundTag previewTag) {
        public Entry {
            name = name == null ? "" : name;
            entityType = entityType == null ? "" : entityType;
            previewTag = previewTag == null ? null : previewTag.copy();
        }
    }
}
