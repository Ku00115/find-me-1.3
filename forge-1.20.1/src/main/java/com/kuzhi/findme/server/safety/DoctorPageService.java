package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.DoctorCommandAction;
import com.kuzhi.findme.network.DoctorCommandPacket;
import com.kuzhi.findme.network.DoctorPagePacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Structured AUI adapter over the existing Doctor and backup transaction services. */
public final class DoctorPageService {
    private static final int BACKUP_COMPARISON_PAGE_SIZE = 2;

    private DoctorPageService() {}

    public static void handle(ServerPlayer requester, DoctorCommandPacket packet) {
        ServerPlayer target = resolveTarget(requester, packet.targetPlayer());
        if (target == null) {
            send(requester, requester, -1, 0, false, "screen.find_me.doctor.message.target_unavailable");
            return;
        }
        int previewIndex = packet.action() == DoctorCommandAction.PREVIEW_BACKUP || packet.action() == DoctorCommandAction.BACKUP_PAGE
                ? packet.backupIndex() : -1;
        boolean success = true;
        String message = "screen.find_me.doctor.message.refreshed";
        switch (packet.action()) {
            case CREATE_BACKUP -> {
                success = CompanionSafetyService.createBackup(target, "aui_manual") > 0;
                message = success ? "screen.find_me.doctor.message.backup_created" : "screen.find_me.doctor.message.backup_empty";
            }
            case RESTORE_BACKUP -> {
                PlayerCompanionData data = CompanionDataService.data(target);
                boolean identityMatches = data.backupAt(packet.backupIndex())
                        .filter(data::backupChecksumMatches)
                        .filter(backup -> backup.savedAt() == packet.expectedSavedAt())
                        .isPresent();
                success = identityMatches && CompanionSafetyService.restoreBackup(target, packet.backupIndex()) > 0;
                message = success ? "screen.find_me.doctor.message.backup_restored" : "screen.find_me.doctor.message.restore_refused";
            }
            case PREVIEW_BACKUP, BACKUP_PAGE -> {
                PlayerCompanionData data = CompanionDataService.data(target);
                success = data.backupAt(packet.backupIndex()).filter(data::backupChecksumMatches)
                        .filter(backup -> packet.expectedSavedAt() <= 0L || backup.savedAt() == packet.expectedSavedAt()).isPresent();
                message = success ? "screen.find_me.doctor.message.review_backup" : "screen.find_me.doctor.message.backup_unavailable";
                if (!success) previewIndex = -1;
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
            case SYNC -> { }
        }
        send(requester, target, previewIndex, packet.detailPage(), success, message);
    }

    private static ServerPlayer resolveTarget(ServerPlayer requester, java.util.UUID targetUuid) {
        if (targetUuid == null || targetUuid.equals(requester.getUUID())) return requester;
        if (!requester.hasPermissions(2)) return null;
        return requester.getServer().getPlayerList().getPlayer(targetUuid);
    }

    private static void send(ServerPlayer viewer, ServerPlayer target, int previewIndex, int detailPage, boolean success, String message) {
        PlayerCompanionData data = CompanionDataService.data(target);
        CompanionIntegrityService.Report report = CompanionIntegrityService.diagnose(target, true);
        List<DoctorPagePacket.Issue> issues = report.issues().stream()
                .filter(issue -> issue.severity() != CompanionIntegrityService.Severity.OK)
                .map(issue -> new DoctorPagePacket.Issue(issue.severity().name(), issue.area(), issue.code(), issue.uuid(),
                        targetName(target, data, issue.uuid()), entityType(target, data, issue.uuid()), issue.arguments()))
                .toList();
        List<DoctorPagePacket.AreaStatus> areas = report.areaStatuses().stream()
                .map(area -> new DoctorPagePacket.AreaStatus(area.area(), area.severity().name(), area.issueCount()))
                .toList();
        ArrayList<DoctorPagePacket.Backup> backups = new ArrayList<>();
        List<PlayerCompanionData.BackupEntry> source = data.backupList();
        for (int i = 0; i < source.size(); i++) {
            PlayerCompanionData.BackupEntry backup = source.get(i);
            backups.add(new DoctorPagePacket.Backup(i, backup.savedAt(), backup.reason(),
                    data.backupChecksumMatches(backup), backup.formatVersion(), backup.manual()));
        }
        DoctorPagePacket.Counts preview = DoctorPagePacket.Counts.EMPTY;
        DoctorPagePacket.BackupDetail backupDetail = null;
        if (previewIndex >= 0 && previewIndex < source.size()) {
            PlayerCompanionData previewData = backupData(source.get(previewIndex));
            preview = counts(previewData);
            DoctorBackupDiffService.Result diff = DoctorBackupDiffService.compare(data, previewData, detailPage, BACKUP_COMPARISON_PAGE_SIZE);
            List<DoctorPagePacket.BackupComparisonRow> rows = diff.comparisons().stream()
                    .map(row -> new DoctorPagePacket.BackupComparisonRow(row.kind(), record(row.backup()),
                            record(target, data, row.current()), row.snapshotChanged())).toList();
            backupDetail = new DoctorPagePacket.BackupDetail(previewIndex, diff.page(), diff.pageCount(),
                    diff.totalChanges(), rows);
        }
        ModNetwork.sendToPlayer(viewer, new DoctorPagePacket(target.getUUID(), target.getGameProfile().getName(), report.worst().name(),
                (int) report.issueCount(), areas, issues, backups, previewIndex, counts(data), preview, backupDetail, success, message));
    }

    private static DoctorPagePacket.BackupRecord record(DoctorBackupDiffService.RecordState state) {
        if (state == null) return null;
        return new DoctorPagePacket.BackupRecord(state.uuid(), state.name(), state.entityType(), state.category(),
                state.lifecycle(), state.team(), state.home(), state.stored(), state.previewTag());
    }

    private static DoctorPagePacket.BackupRecord record(ServerPlayer player, PlayerCompanionData data,
                                                        DoctorBackupDiffService.RecordState state) {
        if (state == null) return null;
        Entity loaded = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, state.uuid()).orElse(null);
        String entityType = state.entityType();
        CompoundTag previewTag = state.previewTag();
        if (loaded != null) {
            if (entityType.isBlank()) {
                entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(loaded.getType()).toString();
            }
            previewTag = CompanionEntitySnapshots.previewEntityTag(loaded, entityType);
        }
        return new DoctorPagePacket.BackupRecord(state.uuid(), state.name(), entityType, state.category(),
                state.lifecycle(), state.team(), state.home(), state.stored(), previewTag);
    }

    private static String targetName(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (uuid == null) return "";
        String stored = data.displayName(uuid).orElse("");
        if (!stored.isBlank()) return stored;
        String dead = data.deadRecord(uuid).map(record -> record.customName()).orElse("");
        if (!dead.isBlank()) return dead;
        return CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid)
                .map(Entity::getDisplayName).map(component -> component.getString()).orElse("");
    }

    private static String entityType(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (uuid == null) return "";
        String stored = data.rawStoredEntityForDiagnostics(uuid).map(tag -> tag.getString("id")).orElse("");
        if (!stored.isBlank()) return stored;
        String dead = data.deadRecord(uuid).map(record -> record.entityType()).orElse("");
        if (!dead.isBlank()) return dead;
        return CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid)
                .map(entity -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
                .orElse("");
    }

    private static PlayerCompanionData backupData(PlayerCompanionData.BackupEntry backup) {
        CompoundTag container = new CompoundTag();
        container.put("find_me", backup.state());
        return PlayerCompanionData.load(container);
    }

    private static DoctorPagePacket.Counts counts(PlayerCompanionData data) {
        int teams = 0;
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) teams += data.teamCount(target);
        return new DoctorPagePacket.Counts(data.list(CompanionKind.MOUNT).size(), data.list(CompanionKind.COMPANION).size(),
                data.vehicleList().size(), data.deadList().size(), data.storedEntityIds().size(), teams);
    }
}
