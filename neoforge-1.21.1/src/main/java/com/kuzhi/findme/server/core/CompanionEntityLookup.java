package com.kuzhi.findme.server.core;

import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class CompanionEntityLookup {
    private CompanionEntityLookup() {
    }

    public static Optional<Entity> findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || entity.isRemoved()) continue;
            return Optional.of(entity);
        }
        return Optional.empty();
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
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (uuid.equals(entity.getUUID()) && !entity.isRemoved()) {
                    return Optional.of(entity);
                }
            }
        }
        return Optional.empty();
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
        level.getChunk(position.blockPos());
        return Optional.ofNullable(level.getEntity(uuid));
    }
}

