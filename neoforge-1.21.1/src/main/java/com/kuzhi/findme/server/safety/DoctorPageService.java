package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.DoctorCommandAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.BackupWarehousePacket;
import com.kuzhi.findme.network.DoctorCommandPacket;
import com.kuzhi.findme.network.DoctorPagePacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Structured AUI adapter over the existing Doctor and backup transaction services. */
public final class DoctorPageService {
    private DoctorPageService() {}

    public static void handle(ServerPlayer requester, DoctorCommandPacket packet) {
        FindMeMod.LOGGER.info("[FindMe backup-ui] server request player={} action={} index={} expectedSavedAt={} detailPage={}",
                requester.getUUID(), packet.action(), packet.backupIndex(), packet.expectedSavedAt(), packet.detailPage());
        FindMeDebugLogger.info("backup-ui", "request player={} action={} index={} expectedSavedAt={}",
                requester.getUUID(), packet.action(), packet.backupIndex(), packet.expectedSavedAt());
        ServerPlayer target = resolveTarget(requester, packet.targetPlayer());
        if (target == null) {
            FindMeMod.LOGGER.warn("[FindMe backup-ui] server target unavailable requester={} target={}",
                    requester.getUUID(), packet.targetPlayer());
            send(requester, requester, false, "screen.find_me.doctor.message.target_unavailable");
            return;
        }
        boolean success = true;
        String message = "screen.find_me.doctor.message.refreshed";
        switch (packet.action()) {
            case CREATE_BACKUP -> {
                success = CompanionSafetyService.createBackup(target, "aui_manual") > 0;
                message = success ? "screen.find_me.doctor.message.backup_created" : "screen.find_me.doctor.message.backup_empty";
            }
            case OPEN_BACKUP_WAREHOUSE -> {
                sendBackupWarehouse(requester, target, packet.backupIndex(), packet.expectedSavedAt());
                return;
            }
            case RESTORE_BACKUP -> {
                PlayerCompanionData data = CompanionDataService.data(target);
                boolean identityMatches = data.backupAt(packet.backupIndex())
                        .filter(data::backupChecksumMatches)
                        .filter(backup -> backup.savedAt() == packet.expectedSavedAt())
                        .isPresent();
                success = identityMatches && CompanionSafetyService.restoreBackup(target, packet.backupIndex(), packet.expectedSavedAt()) > 0;
                message = success ? "screen.find_me.doctor.message.backup_restored" : "screen.find_me.doctor.message.restore_refused";
            }
            case RENAME_BACKUP -> {
                PlayerCompanionData data = CompanionDataService.data(target);
                success = data.renameManualBackup(packet.backupIndex(), packet.expectedSavedAt(), packet.backupName());
                if (success) {
                    CompanionDataService.save(target, data);
                }
                message = success ? "screen.find_me.doctor.message.backup_renamed"
                        : "screen.find_me.doctor.message.backup_unavailable";
            }
            case DELETE_BACKUP -> {
                PlayerCompanionData data = CompanionDataService.data(target);
                success = data.deleteBackup(packet.backupIndex(), packet.expectedSavedAt());
                if (success) {
                    CompanionDataService.save(target, data);
                }
                message = success ? "screen.find_me.doctor.message.backup_deleted"
                        : "screen.find_me.doctor.message.backup_unavailable";
            }
            case SYNC -> { }
        }
        send(requester, target, success, message);
    }

    private static ServerPlayer resolveTarget(ServerPlayer requester, java.util.UUID targetUuid) {
        if (targetUuid == null || targetUuid.equals(requester.getUUID())) return requester;
        if (!requester.hasPermissions(2)) return null;
        return requester.getServer().getPlayerList().getPlayer(targetUuid);
    }

    private static void send(ServerPlayer viewer, ServerPlayer target, boolean success, String message) {
        PlayerCompanionData data = CompanionDataService.data(target);
        ArrayList<DoctorPagePacket.Backup> backups = new ArrayList<>();
        List<PlayerCompanionData.BackupEntry> source = data.backupList();
        for (int i = 0; i < source.size(); i++) {
            PlayerCompanionData.BackupEntry backup = source.get(i);
            backups.add(new DoctorPagePacket.Backup(i, backup.savedAt(), backup.createdAtEpochMillis(), backup.reason(),
                    data.backupChecksumMatches(backup), backup.formatVersion(), backup.manual()));
        }
        FindMeMod.LOGGER.info("[FindMe backup-ui] server response viewer={} target={} success={} records={} message={}",
                viewer.getUUID(), target.getUUID(), success, backups.size(), message);
        ModNetwork.sendToPlayer(viewer, new DoctorPagePacket(target.getUUID(), target.getGameProfile().getName(),
                List.copyOf(backups), success, message));
    }

    private static void sendBackupWarehouse(ServerPlayer viewer, ServerPlayer target, int index, long expectedSavedAt) {
        PlayerCompanionData data = CompanionDataService.data(target);
        Optional<PlayerCompanionData.BackupEntry> maybeBackup = data.backupAt(index)
                .filter(data::backupChecksumMatches)
                .filter(backup -> expectedSavedAt <= 0L || backup.savedAt() == expectedSavedAt);
        if (maybeBackup.isEmpty()) {
            FindMeDebugLogger.info("backup-ui", "snapshot rejected player={} index={} expectedSavedAt={} available={}",
                    target.getUUID(), index, expectedSavedAt, data.backupList().size());
            ModNetwork.sendToPlayer(viewer, new BackupWarehousePacket(index, expectedSavedAt, List.of(), false,
                    "screen.find_me.doctor.message.backup_unavailable"));
            return;
        }

        PlayerCompanionData snapshot = new PlayerCompanionData();
        PlayerCompanionData.BackupEntry backup = maybeBackup.get();
        snapshot.restoreBackup(backup);
        ArrayList<BackupWarehousePacket.Entry> entries = new ArrayList<>();
        CompoundTag previewContents = backup.state().getCompound("backupContents");
        ListTag previewEntries = previewContents.getList("entries", 10);
        for (int i = 0; i < previewEntries.size(); i++) {
            CompoundTag entry = previewEntries.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            CompanionKind kind;
            try {
                kind = CompanionKind.valueOf(entry.getString("kind"));
            } catch (IllegalArgumentException ignored) {
                kind = CompanionKind.COMPANION;
            }
            entries.add(new BackupWarehousePacket.Entry(kind, entry.getBoolean("vehicle"), entry.getUUID("uuid"),
                    entry.getString("name"), entry.getString("entityType"), entry.getBoolean("alive"),
                    entry.contains("preview", 10) ? entry.getCompound("preview") : null));
        }
        if (entries.isEmpty()) {
            for (CompanionKind kind : CompanionKind.values()) {
                for (java.util.UUID uuid : snapshot.list(kind)) {
                    entries.add(snapshotEntry(snapshot, kind, false, uuid));
                }
            }
            for (java.util.UUID uuid : snapshot.vehicleList()) {
                entries.add(snapshotEntry(snapshot, CompanionKind.MOUNT, true, uuid));
            }
        }
        FindMeDebugLogger.info("backup-ui", "snapshot ready player={} index={} entries={} previewEntries={}",
                target.getUUID(), index, entries.size(), previewEntries.size());
        ModNetwork.sendToPlayer(viewer, new BackupWarehousePacket(index, backup.savedAt(), List.copyOf(entries), true, ""));
    }

    private static BackupWarehousePacket.Entry snapshotEntry(PlayerCompanionData data, CompanionKind kind,
                                                             boolean vehicle, java.util.UUID uuid) {
        CompoundTag stored = data.rawStoredEntityForDiagnostics(uuid).orElse(null);
        String type = stored == null ? "" : CompanionEntitySnapshots.storedEntityType(stored);
        String name = data.displayName(uuid).orElseGet(() -> stored == null
                ? uuid.toString().substring(0, 8)
                : CompanionEntitySnapshots.storedEntityName(stored, uuid));
        CompoundTag preview = stored == null ? null : CompanionEntitySnapshots.previewStoredEntityTag(stored);
        boolean alive = stored != null && !stored.getBoolean("CompanionRescueDead");
        return new BackupWarehousePacket.Entry(kind, vehicle, uuid, name, type, alive, preview);
    }
}
