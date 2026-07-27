package com.kuzhi.findme.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class CompanionDataService {
    private CompanionDataService() {
    }

    public static PlayerCompanionData data(ServerPlayer player) {
        FindMeWorldSavedData world = FindMeWorldMigrationService.ensureMigrated(player);
        CompoundTag container = new CompoundTag();
        var root = world.playerRoot(player.getUUID());
        root.ifPresent(value -> PlayerCompanionDataCodec.putRoot(container, value));
        PlayerCompanionData data = PlayerCompanionData.load(container);
        if (root.isEmpty()) {
            data.setUiSettings(com.kuzhi.findme.server.ui.FindMeDefaultSettingsService.loadUiDefaults());
        }
        return data;
    }

    public static long revision(ServerPlayer player) {
        return player == null ? 0L : FindMeWorldMigrationService.ensureMigrated(player)
                .playerRevision(player.getUUID());
    }

    public static HomeResidentIndex homeResidentIndex(ServerPlayer player) {
        return player == null ? HomeResidentIndex.empty()
                : FindMeWorldMigrationService.ensureMigrated(player).homeResidentIndex(player.getUUID());
    }

    public static CompanionRuntimeIndex runtimeIndex(ServerPlayer player) {
        return player == null ? CompanionRuntimeIndex.empty()
                : FindMeWorldMigrationService.ensureMigrated(player).companionRuntimeIndex(player.getUUID());
    }

    public static long revision(MinecraftServer server, UUID playerUuid) {
        return server == null || playerUuid == null ? 0L
                : FindMeWorldSavedData.get(server).playerRevision(playerUuid);
    }

    public static void save(ServerPlayer player, PlayerCompanionData data) {
        FindMeWorldSavedData world = FindMeWorldMigrationService.ensureMigrated(player);
        CompoundTag container = new CompoundTag();
        data.save(container);
        world.putPlayerRoot(player.getUUID(), PlayerCompanionDataCodec.rootCopy(container));
    }

    public static PlayerCompanionData data(MinecraftServer server, UUID playerUuid) {
        CompoundTag container = new CompoundTag();
        if (server != null && playerUuid != null) {
            FindMeWorldSavedData.get(server).playerRoot(playerUuid)
                    .ifPresent(root -> PlayerCompanionDataCodec.putRoot(container, root));
        }
        return PlayerCompanionData.load(container);
    }

    public static void save(MinecraftServer server, UUID playerUuid, PlayerCompanionData data) {
        if (server == null || playerUuid == null || data == null) return;
        CompoundTag container = new CompoundTag();
        data.save(container);
        FindMeWorldSavedData.get(server).putPlayerRoot(playerUuid, PlayerCompanionDataCodec.rootCopy(container));
    }
}

