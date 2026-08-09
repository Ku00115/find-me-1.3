package com.kuzhi.findme.api;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** Lifecycle callback implemented by addons that temporarily control registered companions. */
public interface FindMeTemporaryActionController {
    void tick(MinecraftServer server);

    int cancelForCompanion(MinecraftServer server, UUID companionUuid, String reason);

    int cancelForPlayer(MinecraftServer server, UUID playerUuid, String reason);

    int cancelAll(MinecraftServer server, String reason);

    default boolean isTemporaryPerformer(Entity entity) {
        return false;
    }

    /** True while this controller fully replaces FindMe's native tactical combat for the entity. */
    default boolean ownsTacticalCombat(Entity entity) {
        return false;
    }

    default void cleanupTemporaryPerformers(MinecraftServer server) {
    }
}
