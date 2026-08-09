package com.kuzhi.findme.network;

import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record DoctorPagePacket(UUID playerUuid, String playerName, List<Backup> backups,
                               boolean success, String messageKey) {
    public static void encode(DoctorPagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerUuid);
        buffer.writeUtf(packet.playerName, 64);
        buffer.writeVarInt(packet.backups.size());
        for (Backup backup : packet.backups) {
            buffer.writeVarInt(backup.index);
            buffer.writeLong(backup.savedAt);
            buffer.writeLong(backup.createdAtEpochMillis);
            buffer.writeUtf(backup.reason, 128);
            buffer.writeBoolean(backup.checksumValid);
            buffer.writeVarInt(Math.max(0, backup.formatVersion));
            buffer.writeBoolean(backup.manual);
        }
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.messageKey == null ? "" : packet.messageKey, 256);
    }

    public static DoctorPagePacket decode(FriendlyByteBuf buffer) {
        UUID playerUuid = buffer.readUUID();
        String playerName = buffer.readUtf(64);
        int backupSize = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_PAGE_ENTRIES,
                "doctor backup");
        ArrayList<Backup> backups = new ArrayList<>(PacketDecodeLimits.initialCapacity(backupSize));
        for (int i = 0; i < backupSize; i++) {
            backups.add(new Backup(buffer.readVarInt(), buffer.readLong(), buffer.readLong(), buffer.readUtf(128),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return new DoctorPagePacket(playerUuid, playerName, List.copyOf(backups), buffer.readBoolean(), buffer.readUtf(256));
    }

    public static void handle(DoctorPagePacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> FindMeAuiManageScreen.updateDoctor(packet));
        context.setPacketHandled(true);
    }

    public record Backup(int index, long savedAt, long createdAtEpochMillis, String reason, boolean checksumValid,
                         int formatVersion, boolean manual) {}
}
