package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class PlayerCompanionArchiveService {
    private static final int BACKUP_FORMAT_VERSION = 1;
    static final String SAFETY_BACKUP_REASON = "safety";

    private PlayerCompanionArchiveService() {
    }

    static void addVaultSnapshot(PlayerCompanionData data, UUID uuid, CompanionKind kind, String name, CompoundTag entityTag, long savedAt, String reason, int limit) {
        if (uuid == null || entityTag == null) {
            return;
        }
        CompoundTag tag = entityTag.copy();
        String entityType = tag.getString("CompanionRescueType");
        if (entityType == null || entityType.isBlank()) {
            entityType = tag.getString("id");
        }
        String displayName = name == null || name.isBlank() ? tag.getString("CompanionRescueName") : name;
        if (displayName == null || displayName.isBlank()) {
            displayName = uuid.toString().substring(0, 8);
        }
        long snapshotTime = savedAt > 0L ? savedAt : tag.getLong("CompanionRescueStoredAt");
        data.vault.removeIf(entry -> entry.uuid().equals(uuid));
        data.vault.add(0, new PlayerCompanionData.VaultEntry(uuid, kind, entityType == null ? "" : entityType, displayName, tag, snapshotTime, reason == null ? "snapshot" : reason));
        PlayerCompanionArchiveService.trimVault(data, limit);
    }

    static void vaultSnapshot(PlayerCompanionData data, UUID uuid, CompanionKind kind, String reason, int limit) {
        CompoundTag tag = data.storedEntities.get(uuid);
        if (tag == null) {
            return;
        }
        PlayerCompanionArchiveService.addVaultSnapshot(data, uuid, kind, data.displayNames.get(uuid), tag, tag.getLong("CompanionRescueStoredAt"), reason, limit);
    }

    static List<PlayerCompanionData.VaultEntry> vaultList(PlayerCompanionData data) {
        return List.copyOf(data.vault);
    }

    static void createBackup(PlayerCompanionData data, long savedAt, String reason, int limit) {
        createBackup(data, savedAt, reason, limit, false);
    }

    static void createBackup(PlayerCompanionData data, long savedAt, String reason, int limit, boolean manual) {
        createBackup(data, savedAt, reason, limit, manual, null);
    }

    static void createBackup(PlayerCompanionData data, long savedAt, String reason, int limit, boolean manual,
                             CompoundTag previewContents) {
        String backupReason = reason == null ? "backup" : reason;
        if (!manual && isSafetyBackupReason(backupReason)) {
            // Safety protection is a single replaceable slot, not a second history.
            data.backups.removeIf(backup -> isSafetyBackupReason(backup.reason()));
        }
        CompoundTag state = PlayerCompanionDataCodec.createBackupState(data);
        if (previewContents != null && !previewContents.isEmpty()) {
            state.put("backupContents", previewContents.copy());
        }
        data.backups.add(0, new PlayerCompanionData.BackupEntry(savedAt, System.currentTimeMillis(),
                backupReason, state, BACKUP_FORMAT_VERSION, checksum(state), manual));
        PlayerCompanionArchiveService.trimBackups(data, limit, manual);
    }

    static List<PlayerCompanionData.BackupEntry> backupList(PlayerCompanionData data) {
        return List.copyOf(data.backups);
    }

    static void normalizeBackups(PlayerCompanionData data, int automaticLimit, int manualLimit) {
        PlayerCompanionData.BackupEntry newestSafety = null;
        for (PlayerCompanionData.BackupEntry backup : data.backups) {
            if (isSafetyBackupReason(backup.reason())
                    && (newestSafety == null || backup.savedAt() > newestSafety.savedAt())) {
                newestSafety = backup;
            }
        }
        data.backups.removeIf(backup -> !backup.manual()
                && !"auto".equalsIgnoreCase(backup.reason())
                && !isSafetyBackupReason(backup.reason()));
        PlayerCompanionData.BackupEntry safetyToKeep = newestSafety;
        data.backups.removeIf(backup -> isSafetyBackupReason(backup.reason()) && backup != safetyToKeep);
        if (safetyToKeep != null && !SAFETY_BACKUP_REASON.equals(safetyToKeep.reason())) {
            int index = data.backups.indexOf(safetyToKeep);
            if (index >= 0) {
                data.backups.set(index, new PlayerCompanionData.BackupEntry(safetyToKeep.savedAt(),
                        safetyToKeep.createdAtEpochMillis(), SAFETY_BACKUP_REASON, safetyToKeep.state(),
                        safetyToKeep.formatVersion(), safetyToKeep.checksum(), false));
            }
        }
        trimBackups(data, automaticLimit, false);
        trimBackups(data, manualLimit, true);
    }

    static List<CompoundTag> recoverySnapshots(PlayerCompanionData data, UUID uuid) {
        ArrayList<CompoundTag> snapshots = new ArrayList<>();
        for (PlayerCompanionData.VaultEntry entry : data.vault) {
            if (entry.uuid().equals(uuid)) {
                snapshots.add(entry.entityTag());
            }
        }
        for (PlayerCompanionData.BackupEntry backup : data.backups) {
            if (!checksumMatches(backup)) continue;
            PlayerCompanionData restored = new PlayerCompanionData();
            PlayerCompanionDataCodec.restoreBackupState(restored, backup.state());
            restored.rawStoredEntityForDiagnostics(uuid).ifPresent(snapshots::add);
        }
        return List.copyOf(snapshots);
    }

    static Optional<PlayerCompanionData.BackupEntry> backupAt(PlayerCompanionData data, int index) {
        if (index < 0 || index >= data.backups.size()) {
            return Optional.empty();
        }
        return Optional.of(data.backups.get(index));
    }

    static boolean deleteBackup(PlayerCompanionData data, int index, long expectedSavedAt) {
        if (index < 0 || index >= data.backups.size()) {
            return false;
        }
        PlayerCompanionData.BackupEntry backup = data.backups.get(index);
        if (backup.savedAt() != expectedSavedAt) {
            return false;
        }
        data.backups.remove(index);
        return true;
    }

    static void restoreBackup(PlayerCompanionData data, PlayerCompanionData.BackupEntry backup) {
        if (backup != null) {
            PlayerCompanionDataCodec.restoreBackupState(data, backup.state());
        }
    }

    static boolean renameManualBackup(PlayerCompanionData data, int index, long expectedSavedAt, String name) {
        if (index < 0 || index >= data.backups.size()) {
            return false;
        }
        PlayerCompanionData.BackupEntry backup = data.backups.get(index);
        if (!backup.manual() || backup.savedAt() != expectedSavedAt) {
            return false;
        }
        String normalized = name == null ? "" : name.strip();
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48);
        }
        if (normalized.isBlank()) {
            normalized = "manual";
        }
        data.backups.set(index, new PlayerCompanionData.BackupEntry(backup.savedAt(), backup.createdAtEpochMillis(),
                normalized, backup.state(), backup.formatVersion(), backup.checksum(), true));
        return true;
    }

    static boolean hasChecksum(PlayerCompanionData.BackupEntry backup) {
        return backup != null && backup.checksum() != null && !backup.checksum().isBlank();
    }

    static boolean checksumMatches(PlayerCompanionData.BackupEntry backup) {
        return backup != null && (!hasChecksum(backup)
                || backup.checksum().equals(checksum(backup.internalState()))
                || backup.checksum().equals(legacyChecksum(backup.internalState())));
    }

    static long lastBackupAt(PlayerCompanionData data) {
        return data.backups.stream().filter(backup -> !backup.manual() && isAutomaticBackup(backup))
                .mapToLong(PlayerCompanionData.BackupEntry::savedAt)
                .max().orElse(0L);
    }

    static void trimVault(PlayerCompanionData data, int limit) {
        int max = Math.max(1, limit);
        while (data.vault.size() > max) {
            data.vault.remove(data.vault.size() - 1);
        }
    }

    static void trimBackups(PlayerCompanionData data, int limit, boolean manual) {
        int max = Math.max(1, limit);
        int retained = 0;
        for (int index = 0; index < data.backups.size();) {
            PlayerCompanionData.BackupEntry backup = data.backups.get(index);
            if (backup.manual() != manual || !manual && !isAutomaticBackup(backup)) {
                index++;
                continue;
            }
            retained++;
            if (retained > max) {
                data.backups.remove(index);
            } else {
                index++;
            }
        }
    }

    private static String checksum(CompoundTag state) {
        return NbtFingerprint.sha256(state);
    }

    private static String legacyChecksum(CompoundTag state) {
        return NbtFingerprint.sha256(state == null ? "" : state.toString());
    }

    private static boolean isAutomaticBackup(PlayerCompanionData.BackupEntry backup) {
        return backup != null && "auto".equalsIgnoreCase(backup.reason());
    }

    private static boolean isSafetyBackupReason(String reason) {
        return reason != null && (SAFETY_BACKUP_REASON.equalsIgnoreCase(reason)
                || reason.toLowerCase(java.util.Locale.ROOT).startsWith("before_"));
    }
}

