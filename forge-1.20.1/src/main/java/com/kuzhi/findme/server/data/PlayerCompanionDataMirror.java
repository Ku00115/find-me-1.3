package com.kuzhi.findme.server.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlayerCompanionDataMirror extends SavedData {
    private static final String DATA_NAME = "find_me_player_data_mirror";
    private final Map<UUID, CompoundTag> playerData = new HashMap<>();
    private final Map<UUID, List<MirrorBackup>> playerBackups = new HashMap<>();
    private final Set<UUID> clearedPlayers = new HashSet<>();

    private PlayerCompanionDataMirror() {
    }

    static PlayerCompanionDataMirror get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PlayerCompanionDataMirror::load, PlayerCompanionDataMirror::new, DATA_NAME);
    }

    public static void remember(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        if (!PlayerCompanionDataCodec.hasRoot(player.getPersistentData())) {
            return;
        }
        CompoundTag root = PlayerCompanionDataCodec.rootCopy(player.getPersistentData());
        PlayerCompanionDataMirror mirror = get(player.getServer());
        if (!PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
            mirror.rememberCleared(player.getUUID());
            return;
        }
        mirror.put(player.getUUID(), root);
    }

    public static void rememberBackup(ServerPlayer player, long savedAt, String reason, int limit) {
        if (player == null || player.getServer() == null) {
            return;
        }
        if (!PlayerCompanionDataCodec.hasRoot(player.getPersistentData())) {
            return;
        }
        CompoundTag root = PlayerCompanionDataCodec.rootCopy(player.getPersistentData());
        if (!PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
            return;
        }
        get(player.getServer()).putBackup(player.getUUID(), savedAt, reason, root, limit);
    }

    public static boolean restoreIfMissing(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return false;
        }
        if (PlayerCompanionDataCodec.hasRoot(player.getPersistentData())) {
            remember(player);
            return false;
        }
        PlayerCompanionDataMirror mirror = get(player.getServer());
        Optional<CompoundTag> mirrored = mirror.copy(player.getUUID());
        if (mirrored.isEmpty() && !mirror.isCleared(player.getUUID())) {
            mirrored = mirror.latestBackup(player.getUUID());
        }
        if (mirrored.isEmpty()) {
            return false;
        }
        PlayerCompanionDataCodec.putRoot(player.getPersistentData(), mirrored.get());
        return true;
    }

    /** Read-only legacy source for the 1.3 world-data migration. */
    public static Optional<CompoundTag> copyForMigration(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return Optional.empty();
        }
        PlayerCompanionDataMirror mirror = get(player.getServer());
        Optional<CompoundTag> current = mirror.copy(player.getUUID());
        return current.isPresent() ? current : mirror.latestBackup(player.getUUID());
    }

    public static boolean copyCloneData(Player original, ServerPlayer clone) {
        if (original == null || clone == null || clone.getServer() == null) {
            return false;
        }
        if (PlayerCompanionDataCodec.hasRoot(original.getPersistentData())) {
            CompoundTag originalRoot = PlayerCompanionDataCodec.rootCopy(original.getPersistentData());
            PlayerCompanionDataCodec.putRoot(clone.getPersistentData(), originalRoot);
            PlayerCompanionDataMirror mirror = get(clone.getServer());
            if (PlayerCompanionDataCodec.isMeaningfulRoot(originalRoot)) {
                mirror.put(clone.getUUID(), originalRoot);
                return true;
            }
            mirror.rememberCleared(clone.getUUID());
            return false;
        }
        return restoreIfMissing(clone);
    }

    private static PlayerCompanionDataMirror load(CompoundTag tag) {
        PlayerCompanionDataMirror mirror = new PlayerCompanionDataMirror();
        ListTag players = tag.getList("players", 10);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (!entry.hasUUID("uuid") || !entry.contains("data", 10)) {
                continue;
            }
            CompoundTag root = entry.getCompound("data");
            if (PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
                mirror.playerData.put(entry.getUUID("uuid"), root.copy());
            }
        }
        ListTag backups = tag.getList("backups", 10);
        for (int i = 0; i < backups.size(); i++) {
            CompoundTag playerEntry = backups.getCompound(i);
            if (!playerEntry.hasUUID("uuid")) {
                continue;
            }
            UUID uuid = playerEntry.getUUID("uuid");
            ListTag entries = playerEntry.getList("entries", 10);
            List<MirrorBackup> playerBackupList = new ArrayList<>();
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag backupTag = entries.getCompound(j);
                if (!backupTag.contains("data", 10)) {
                    continue;
                }
                CompoundTag root = backupTag.getCompound("data");
                if (!PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
                    continue;
                }
                playerBackupList.add(new MirrorBackup(backupTag.getLong("savedAt"), backupTag.getString("reason"), root));
            }
            if (!playerBackupList.isEmpty()) {
                mirror.playerBackups.put(uuid, playerBackupList);
            }
        }
        ListTag cleared = tag.getList("clearedPlayers", 10);
        for (int i = 0; i < cleared.size(); i++) {
            CompoundTag entry = cleared.getCompound(i);
            if (entry.hasUUID("uuid")) {
                mirror.clearedPlayers.add(entry.getUUID("uuid"));
            }
        }
        return mirror;
    }

    private void put(UUID uuid, CompoundTag root) {
        if (uuid == null || !PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
            return;
        }
        playerData.put(uuid, root.copy());
        clearedPlayers.remove(uuid);
        setDirty();
    }

    private void rememberCleared(UUID uuid) {
        if (uuid == null) {
            return;
        }
        boolean changed = playerData.remove(uuid) != null;
        changed |= clearedPlayers.add(uuid);
        if (changed) {
            setDirty();
        }
    }

    private Optional<CompoundTag> copy(UUID uuid) {
        CompoundTag root = playerData.get(uuid);
        if (!PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
            return Optional.empty();
        }
        return Optional.of(root.copy());
    }

    private void putBackup(UUID uuid, long savedAt, String reason, CompoundTag root, int limit) {
        if (uuid == null || !PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
            return;
        }
        List<MirrorBackup> backups = playerBackups.computeIfAbsent(uuid, ignored -> new ArrayList<>());
        backups.add(0, new MirrorBackup(savedAt, reason, root));
        int max = Math.max(1, limit);
        while (backups.size() > max) {
            backups.remove(backups.size() - 1);
        }
        clearedPlayers.remove(uuid);
        setDirty();
    }

    private Optional<CompoundTag> latestBackup(UUID uuid) {
        List<MirrorBackup> backups = playerBackups.get(uuid);
        if (backups == null || backups.isEmpty()) {
            return Optional.empty();
        }
        for (MirrorBackup backup : backups) {
            if (PlayerCompanionDataCodec.isMeaningfulRoot(backup.data())) {
                return Optional.of(backup.data());
            }
        }
        return Optional.empty();
    }

    private boolean isCleared(UUID uuid) {
        return uuid != null && clearedPlayers.contains(uuid);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, CompoundTag> mirrored : playerData.entrySet()) {
            CompoundTag root = mirrored.getValue();
            if (!PlayerCompanionDataCodec.isMeaningfulRoot(root)) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", mirrored.getKey());
            entry.put("data", (Tag)root.copy());
            players.add(entry);
        }
        tag.put("players", (Tag)players);
        ListTag backups = new ListTag();
        for (Map.Entry<UUID, List<MirrorBackup>> mirroredBackups : playerBackups.entrySet()) {
            ListTag entries = new ListTag();
            for (MirrorBackup backup : mirroredBackups.getValue()) {
                if (!PlayerCompanionDataCodec.isMeaningfulRoot(backup.data())) {
                    continue;
                }
                CompoundTag backupTag = new CompoundTag();
                backupTag.putLong("savedAt", backup.savedAt());
                backupTag.putString("reason", backup.reason());
                backupTag.put("data", (Tag)backup.data());
                entries.add(backupTag);
            }
            if (entries.isEmpty()) {
                continue;
            }
            CompoundTag playerEntry = new CompoundTag();
            playerEntry.putUUID("uuid", mirroredBackups.getKey());
            playerEntry.put("entries", (Tag)entries);
            backups.add(playerEntry);
        }
        tag.put("backups", (Tag)backups);
        ListTag cleared = new ListTag();
        for (UUID uuid : clearedPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            cleared.add(entry);
        }
        tag.put("clearedPlayers", (Tag)cleared);
        return tag;
    }

    private record MirrorBackup(long savedAt, String reason, CompoundTag data) {
        private MirrorBackup {
            reason = reason == null ? "backup" : reason;
            data = data == null ? new CompoundTag() : data.copy();
        }

        @Override
        public CompoundTag data() {
            return data.copy();
        }
    }
}

