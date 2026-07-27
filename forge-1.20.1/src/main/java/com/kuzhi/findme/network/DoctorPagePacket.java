package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import com.kuzhi.findme.common.DoctorArea;
import com.kuzhi.findme.common.DoctorIssueCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record DoctorPagePacket(UUID playerUuid, String playerName, String worst, int issueCount,
                               List<AreaStatus> areas, List<Issue> issues, List<Backup> backups,
                               int previewIndex, Counts current, Counts preview, BackupDetail backupDetail,
                               boolean success, String messageKey) {
    public static void encode(DoctorPagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerUuid);
        buffer.writeUtf(packet.playerName, 64);
        buffer.writeUtf(packet.worst, 24);
        buffer.writeVarInt(packet.issueCount);
        buffer.writeVarInt(packet.areas.size());
        for (AreaStatus area : packet.areas) {
            buffer.writeEnum(area.area);
            buffer.writeUtf(area.severity, 24);
            buffer.writeVarInt(area.issueCount);
        }
        buffer.writeVarInt(packet.issues.size());
        for (Issue issue : packet.issues) {
            buffer.writeUtf(issue.severity, 24);
            buffer.writeEnum(issue.area);
            buffer.writeEnum(issue.code);
            buffer.writeBoolean(issue.uuid != null);
            if (issue.uuid != null) buffer.writeUUID(issue.uuid);
            buffer.writeUtf(issue.targetName, 128);
            buffer.writeUtf(issue.entityType, 256);
            buffer.writeVarInt(issue.arguments.size());
            for (String argument : issue.arguments) buffer.writeUtf(argument, 256);
        }
        buffer.writeVarInt(packet.backups.size());
        for (Backup backup : packet.backups) {
            buffer.writeVarInt(backup.index);
            buffer.writeLong(backup.savedAt);
            buffer.writeUtf(backup.reason, 128);
            buffer.writeBoolean(backup.checksumValid);
            buffer.writeVarInt(Math.max(0, backup.formatVersion));
            buffer.writeBoolean(backup.manual);
        }
        buffer.writeInt(packet.previewIndex);
        writeCounts(buffer, packet.current);
        writeCounts(buffer, packet.preview);
        buffer.writeBoolean(packet.backupDetail != null);
        if (packet.backupDetail != null) writeDetail(buffer, packet.backupDetail);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.messageKey == null ? "" : packet.messageKey, 256);
    }

    public static DoctorPagePacket decode(FriendlyByteBuf buffer) {
        UUID playerUuid = buffer.readUUID();
        String playerName = buffer.readUtf(64);
        String worst = buffer.readUtf(24);
        int issueCount = buffer.readVarInt();
        int areaSize = buffer.readVarInt();
        ArrayList<AreaStatus> areas = new ArrayList<>(areaSize);
        for (int i = 0; i < areaSize; i++) {
            areas.add(new AreaStatus(buffer.readEnum(DoctorArea.class), buffer.readUtf(24), buffer.readVarInt()));
        }
        int issueSize = buffer.readVarInt();
        ArrayList<Issue> issues = new ArrayList<>(issueSize);
        for (int i = 0; i < issueSize; i++) {
            String severity = buffer.readUtf(24);
            DoctorArea area = buffer.readEnum(DoctorArea.class);
            DoctorIssueCode code = buffer.readEnum(DoctorIssueCode.class);
            UUID uuid = buffer.readBoolean() ? buffer.readUUID() : null;
            String targetName = buffer.readUtf(128);
            String entityType = buffer.readUtf(256);
            int argumentSize = buffer.readVarInt();
            ArrayList<String> arguments = new ArrayList<>(argumentSize);
            for (int argument = 0; argument < argumentSize; argument++) arguments.add(buffer.readUtf(256));
            issues.add(new Issue(severity, area, code, uuid, targetName, entityType, List.copyOf(arguments)));
        }
        int backupSize = buffer.readVarInt();
        ArrayList<Backup> backups = new ArrayList<>(backupSize);
        for (int i = 0; i < backupSize; i++) {
            backups.add(new Backup(buffer.readVarInt(), buffer.readLong(), buffer.readUtf(128), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readBoolean()));
        }
        int previewIndex = buffer.readInt();
        Counts current = readCounts(buffer);
        Counts preview = readCounts(buffer);
        BackupDetail detail = buffer.readBoolean() ? readDetail(buffer) : null;
        return new DoctorPagePacket(playerUuid, playerName, worst, issueCount, List.copyOf(areas), List.copyOf(issues),
                List.copyOf(backups), previewIndex, current, preview, detail, buffer.readBoolean(), buffer.readUtf(256));
    }

    private static void writeDetail(FriendlyByteBuf buffer, BackupDetail detail) {
        buffer.writeVarInt(detail.backupIndex);
        buffer.writeVarInt(detail.page);
        buffer.writeVarInt(detail.pageCount);
        buffer.writeVarInt(detail.totalChanges);
        buffer.writeVarInt(detail.rows.size());
        for (BackupComparisonRow row : detail.rows) {
            buffer.writeUtf(row.kind, 24);
            writeNullableRecord(buffer, row.backup);
            writeNullableRecord(buffer, row.current);
            buffer.writeBoolean(row.snapshotChanged);
        }
    }

    private static BackupDetail readDetail(FriendlyByteBuf buffer) {
        int backupIndex = buffer.readVarInt();
        int page = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        int total = buffer.readVarInt();
        int rowCount = buffer.readVarInt();
        ArrayList<BackupComparisonRow> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            rows.add(new BackupComparisonRow(buffer.readUtf(24), readNullableRecord(buffer),
                    readNullableRecord(buffer), buffer.readBoolean()));
        }
        return new BackupDetail(backupIndex, page, pageCount, total, List.copyOf(rows));
    }

    private static void writeNullableRecord(FriendlyByteBuf buffer, BackupRecord record) {
        buffer.writeBoolean(record != null);
        if (record != null) writeRecord(buffer, record);
    }

    private static BackupRecord readNullableRecord(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? readRecord(buffer) : null;
    }

    private static void writeRecord(FriendlyByteBuf buffer, BackupRecord record) {
        buffer.writeUUID(record.uuid);
        buffer.writeUtf(record.name, 128);
        buffer.writeUtf(record.entityType, 256);
        buffer.writeUtf(record.category, 32);
        buffer.writeUtf(record.lifecycle, 32);
        buffer.writeUtf(record.team, 192);
        buffer.writeUtf(record.home, 256);
        buffer.writeBoolean(record.stored);
        buffer.writeNbt(record.previewTag);
    }

    private static BackupRecord readRecord(FriendlyByteBuf buffer) {
        return new BackupRecord(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(256), buffer.readUtf(32),
                buffer.readUtf(32), buffer.readUtf(192), buffer.readUtf(256), buffer.readBoolean(), buffer.readNbt());
    }

    private static void writeCounts(FriendlyByteBuf buffer, Counts counts) {
        Counts value = counts == null ? Counts.EMPTY : counts;
        buffer.writeVarInt(value.mounts); buffer.writeVarInt(value.companions); buffer.writeVarInt(value.vehicles);
        buffer.writeVarInt(value.dead); buffer.writeVarInt(value.stored); buffer.writeVarInt(value.teams);
    }

    private static Counts readCounts(FriendlyByteBuf buffer) {
        return new Counts(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(DoctorPagePacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> FindMeAuiManageScreen.updateDoctor(packet));
        context.setPacketHandled(true);
    }

    public record AreaStatus(DoctorArea area, String severity, int issueCount) {}
    public record Issue(String severity, DoctorArea area, DoctorIssueCode code, UUID uuid, String targetName,
                        String entityType, List<String> arguments) {}
    public record Backup(int index, long savedAt, String reason, boolean checksumValid, int formatVersion,
                         boolean manual) {}
    public record BackupDetail(int backupIndex, int page, int pageCount, int totalChanges,
                               List<BackupComparisonRow> rows) {}
    public record BackupComparisonRow(String kind, BackupRecord backup, BackupRecord current, boolean snapshotChanged) {}
    public record BackupRecord(UUID uuid, String name, String entityType, String category, String lifecycle,
                               String team, String home, boolean stored, net.minecraft.nbt.CompoundTag previewTag) {
        public BackupRecord {
            previewTag = previewTag == null ? null : previewTag.copy();
        }
    }
    public record Counts(int mounts, int companions, int vehicles, int dead, int stored, int teams) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0, 0, 0);
    }
}
