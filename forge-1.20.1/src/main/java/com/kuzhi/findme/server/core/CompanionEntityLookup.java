package com.kuzhi.findme.server.core;

import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class CompanionEntityLookup {
    private static final long RESTORE_SCAN_CACHE_TICKS = 5L;
    private static final Map<MinecraftServer, Map<UUID, Entity>> ENTITY_INDEX = new WeakHashMap<>();
    private static final Map<MinecraftServer, RestoreScan> RESTORE_SCANS = new WeakHashMap<>();

    private CompanionEntityLookup() {
    }

    public static Optional<Entity> findEntity(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return Optional.empty();
        }
        long startedAt = FindMePerformanceMonitor.start();
        try {
        Map<UUID, Entity> index = ENTITY_INDEX.get(server);
        Entity indexed = index == null ? null : index.get(uuid);
        if (isUsable(indexed, server)) {
            FindMePerformanceMonitor.recordEntityIndexHit();
            return Optional.of(indexed);
        }
        if (indexed != null && index != null) {
            index.remove(uuid);
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || entity.isRemoved()) continue;
            FindMePerformanceMonitor.recordEntityLevelFallback();
            trackIfOwned(server, entity);
            return Optional.of(entity);
        }
        return Optional.empty();
        } finally {
            FindMePerformanceMonitor.recordEntityLookup(startedAt);
        }
    }

    /** Called from the server entity lifecycle so registered companions resolve without a level scan. */
    public static void trackEntity(MinecraftServer server, Entity entity) {
        if (server == null || entity == null || entity.isRemoved()) {
            return;
        }
        trackIfOwned(server, entity);
    }

    public static void untrackEntity(MinecraftServer server, Entity entity) {
        if (server == null || entity == null) {
            return;
        }
        Map<UUID, Entity> index = ENTITY_INDEX.get(server);
        if (index == null) {
            return;
        }
        index.remove(entity.getUUID(), entity);
        if (index.isEmpty()) {
            ENTITY_INDEX.remove(server);
        }
    }

    /**
     * Restore paths need a stronger lookup than the UUID index alone. During
     * section transitions an entity can already be registered for duplicate
     * protection before {@link ServerLevel#getEntity(UUID)} exposes it.
     */
    public static Optional<Entity> findEntityForRestore(MinecraftServer server, UUID uuid) {
        Optional<Entity> indexed = findEntity(server, uuid);
        if (indexed.isPresent()) {
            return indexed;
        }
        long gameTime = server.overworld().getGameTime();
        RestoreScan scan = RESTORE_SCANS.get(server);
        if (scan == null || gameTime - scan.gameTime() >= RESTORE_SCAN_CACHE_TICKS) {
            long startedAt = FindMePerformanceMonitor.start();
            FindMePerformanceMonitor.recordEntityRestoreGlobalScan();
            Map<UUID, Entity> entities = new HashMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!entity.isRemoved()) entities.putIfAbsent(entity.getUUID(), entity);
                }
            }
            scan = new RestoreScan(gameTime, entities);
            RESTORE_SCANS.put(server, scan);
            FindMePerformanceMonitor.recordRestoreScan(startedAt);
        }
        return Optional.ofNullable(scan.entities().get(uuid))
                .filter(entity -> isUsable(entity, server));
    }

    public static Optional<Entity> findLoadedEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
        Optional<Entity> loaded = findEntity(server, uuid);
        if (loaded.isPresent()) {
            return loaded;
        }
        Optional<SavedPosition> maybePosition = data.lastKnownPosition(uuid);
        if (maybePosition.isEmpty()) {
            return Optional.empty();
        }
        SavedPosition position = maybePosition.get();
        ServerLevel level = server.getLevel(position.dimension());
        if (level == null || !level.hasChunkAt(position.blockPos()) || !level.isLoaded(position.blockPos())) {
            return Optional.empty();
        }
        return Optional.ofNullable(level.getEntity(uuid));
    }

    public static Optional<Entity> locateEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
        Optional<Entity> loaded = findEntity(server, uuid);
        if (loaded.isPresent()) {
            return loaded;
        }
        Optional<SavedPosition> maybePosition = data.lastKnownPosition(uuid);
        if (maybePosition.isEmpty()) {
            return Optional.empty();
        }
        SavedPosition position = maybePosition.get();
        ServerLevel level = server.getLevel(position.dimension());
        if (level == null) {
            return Optional.empty();
        }
        long startedAt = FindMePerformanceMonitor.start();
        level.getChunk(position.blockPos());
        FindMePerformanceMonitor.recordChunkLoad(startedAt);
        // A chunk load can make an entity visible to the world entity collection
        // during the same game tick. Do not reuse a scan made before that load.
        RESTORE_SCANS.remove(server);
        return Optional.ofNullable(level.getEntity(uuid));
    }

    static void finishServerTick(MinecraftServer server) {
        // Keep the short-lived compatibility snapshot for a few ticks so a single
        // missed index entry cannot trigger a full server entity walk every tick.
    }

    static void resetServerState(MinecraftServer server) {
        if (server == null) {
            ENTITY_INDEX.clear();
            RESTORE_SCANS.clear();
        } else {
            ENTITY_INDEX.remove(server);
            RESTORE_SCANS.remove(server);
        }
    }

    private static void trackIfOwned(MinecraftServer server, Entity entity) {
        if (entity == null || !FindMeWorldSavedData.get(server).companionOwner(entity.getUUID()).isPresent()) {
            return;
        }
        ENTITY_INDEX.computeIfAbsent(server, ignored -> new HashMap<>()).put(entity.getUUID(), entity);
    }

    private static boolean isUsable(Entity entity, MinecraftServer server) {
        return entity != null && !entity.isRemoved()
                && entity.level() instanceof ServerLevel level
                && level.getServer() == server;
    }

    private record RestoreScan(long gameTime, Map<UUID, Entity> entities) {
    }
}

