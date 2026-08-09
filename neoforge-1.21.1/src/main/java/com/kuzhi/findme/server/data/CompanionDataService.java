package com.kuzhi.findme.server.data;

import com.kuzhi.findme.server.ui.CompanionSpellItemReturnService;
import com.kuzhi.findme.server.api.CompanionLifecycleEventService;
import com.kuzhi.findme.server.core.FindMePerformanceMonitor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class CompanionDataService {
    private static final Map<MinecraftServer, Map<UUID, CachedData>> TICK_CACHE = new WeakHashMap<>();

    private CompanionDataService() {
    }

    public static PlayerCompanionData data(ServerPlayer player) {
        FindMeWorldSavedData world = FindMeWorldMigrationService.ensureMigrated(player);
        long revision = world.playerRevision(player.getUUID());
        long gameTime = player.getServer().overworld().getGameTime();
        PlayerCompanionData cached = cached(player.getServer(), player.getUUID(), revision, gameTime);
        if (cached != null) return cached;
        long startedAt = FindMePerformanceMonitor.start();
        boolean hasRoot = world.hasPlayerRoot(player.getUUID());
        PlayerCompanionData data = world.decodePlayerData(player.getUUID());
        if (!hasRoot) {
            data.setUiSettings(com.kuzhi.findme.server.ui.FindMeDefaultSettingsService.loadUiDefaults());
        }
        FindMePerformanceMonitor.recordDataDecode(startedAt);
        cache(player.getServer(), player.getUUID(), revision, gameTime, data);
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
        long startedAt = FindMePerformanceMonitor.start();
        FindMeWorldSavedData world = FindMeWorldMigrationService.ensureMigrated(player);
        Set<UUID> changed = data.lifecycleChanges();
        long beforeRevision = world.playerRevision(player.getUUID());
        PlayerCompanionData before = changed.isEmpty() ? null : world.decodePlayerData(player.getUUID());
        CompanionSpellItemReturnService.deliverPending(player, data);
        CompoundTag container = new CompoundTag();
        data.save(container);
        long afterRevision = world.putPlayerRoot(player.getUUID(),
                PlayerCompanionDataCodec.rootForImmediateStore(container));
        data.clearLifecycleChanges();
        cache(player.getServer(), player.getUUID(), afterRevision,
                player.getServer().overworld().getGameTime(), data);
        if (!changed.isEmpty()) CompanionLifecycleEventService.publishChanges(player.getServer(), player.getUUID(),
                before, data, changed, beforeRevision, afterRevision);
        FindMePerformanceMonitor.recordDataSave(startedAt);
    }

    public static PlayerCompanionData data(MinecraftServer server, UUID playerUuid) {
        if (server == null || playerUuid == null) return new PlayerCompanionData();
        FindMeWorldSavedData world = FindMeWorldSavedData.get(server);
        long revision = world.playerRevision(playerUuid);
        long gameTime = server.overworld().getGameTime();
        PlayerCompanionData cached = cached(server, playerUuid, revision, gameTime);
        if (cached != null) return cached;
        long startedAt = FindMePerformanceMonitor.start();
        PlayerCompanionData data = world.decodePlayerData(playerUuid);
        FindMePerformanceMonitor.recordDataDecode(startedAt);
        cache(server, playerUuid, revision, gameTime, data);
        return data;
    }

    public static void save(MinecraftServer server, UUID playerUuid, PlayerCompanionData data) {
        if (server == null || playerUuid == null || data == null) return;
        long startedAt = FindMePerformanceMonitor.start();
        FindMeWorldSavedData world = FindMeWorldSavedData.get(server);
        Set<UUID> changed = data.lifecycleChanges();
        long beforeRevision = world.playerRevision(playerUuid);
        PlayerCompanionData before = changed.isEmpty() ? null : world.decodePlayerData(playerUuid);
        CompoundTag container = new CompoundTag();
        data.save(container);
        long afterRevision = world.putPlayerRoot(playerUuid,
                PlayerCompanionDataCodec.rootForImmediateStore(container));
        data.clearLifecycleChanges();
        cache(server, playerUuid, afterRevision, server.overworld().getGameTime(), data);
        if (!changed.isEmpty()) CompanionLifecycleEventService.publishChanges(server, playerUuid, before, data,
                changed, beforeRevision, afterRevision);
        FindMePerformanceMonitor.recordDataSave(startedAt);
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (player == null) return;
        synchronized (TICK_CACHE) {
            Map<UUID, CachedData> cache = TICK_CACHE.get(player.getServer());
            if (cache != null) cache.remove(player.getUUID());
        }
    }

    public static void resetServerState(MinecraftServer server) {
        synchronized (TICK_CACHE) {
            if (server == null) TICK_CACHE.clear();
            else TICK_CACHE.remove(server);
        }
    }

    private static PlayerCompanionData cached(MinecraftServer server, UUID playerUuid,
                                               long revision, long gameTime) {
        synchronized (TICK_CACHE) {
            CachedData cached = TICK_CACHE.computeIfAbsent(server, ignored -> new HashMap<>()).get(playerUuid);
            return cached != null && cached.revision() == revision && cached.gameTime() == gameTime
                    ? cached.data() : null;
        }
    }

    private static void cache(MinecraftServer server, UUID playerUuid, long revision,
                              long gameTime, PlayerCompanionData data) {
        synchronized (TICK_CACHE) {
            TICK_CACHE.computeIfAbsent(server, ignored -> new HashMap<>())
                    .put(playerUuid, new CachedData(revision, gameTime, data));
        }
    }

    private record CachedData(long revision, long gameTime, PlayerCompanionData data) {
    }
}

