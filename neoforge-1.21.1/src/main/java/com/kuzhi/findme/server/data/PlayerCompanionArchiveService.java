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
        CompoundTag state = PlayerCompanionDataCodec.createBackupState(data);
        data.backups.add(0, new PlayerCompanionData.BackupEntry(savedAt, reason == null ? "backup" : reason,
                state, BACKUP_FORMAT_VERSION, checksum(state), manual));
        PlayerCompanionArchiveService.trimBackups(data, limit, manual);
    }

    static List<PlayerCompanionData.BackupEntry> backupList(PlayerCompanionData data) {
        return List.copyOf(data.backups);
    }

    static Optional<PlayerCompanionData.BackupEntry> backupAt(PlayerCompanionData data, int index) {
        if (index < 0 || index >= data.backups.size()) {
            return Optional.empty();
        }
        return Optional.of(data.backups.get(index));
    }

    static boolean restoreBackupAt(PlayerCompanionData data, int index) {
        Optional<PlayerCompanionData.BackupEntry> backup = PlayerCompanionArchiveService.backupAt(data, index);
        if (backup.isEmpty()) {
            return false;
        }
        PlayerCompanionArchiveService.restoreBackup(data, backup.get());
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
        data.backups.set(index, new PlayerCompanionData.BackupEntry(backup.savedAt(), normalized, backup.state(),
                backup.formatVersion(), backup.checksum(), true));
        return true;
    }

    static boolean hasChecksum(PlayerCompanionData.BackupEntry backup) {
        return backup != null && backup.checksum() != null && !backup.checksum().isBlank();
    }

    static boolean checksumMatches(PlayerCompanionData.BackupEntry backup) {
        return backup != null && (!hasChecksum(backup)
                || backup.checksum().equals(checksum(backup.state()))
                || backup.checksum().equals(legacyChecksum(backup.state())));
    }

    static long lastBackupAt(PlayerCompanionData data) {
        return data.backups.stream().filter(backup -> !backup.manual()).mapToLong(PlayerCompanionData.BackupEntry::savedAt)
                .max().orElse(0L);
    }

    static void trimVault(PlayerCompanionData data, int limit) {
        int max = Math.max(1, limit);
        while (data.vault.size() > max) {
            data.vault.remove(data.vault.size() - 1);
        }
    }

    static void trimBackups(PlayerCompanionData data, int limit) {
        trimBackups(data, limit, false);
    }

    static void trimBackups(PlayerCompanionData data, int limit, boolean manual) {
        int max = Math.max(1, limit);
        int retained = 0;
        for (int index = 0; index < data.backups.size();) {
            PlayerCompanionData.BackupEntry backup = data.backups.get(index);
            if (backup.manual() != manual) {
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
}

