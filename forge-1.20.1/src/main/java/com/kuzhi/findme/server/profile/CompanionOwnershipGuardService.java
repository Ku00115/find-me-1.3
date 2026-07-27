package com.kuzhi.findme.server.profile;

import com.kuzhi.findme.server.data.CompanionDataService;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class CompanionOwnershipGuardService {
    private CompanionOwnershipGuardService() {
    }

    public static Optional<ServerPlayer> registeredOwner(MinecraftServer server, UUID entityUuid) {
        if (server == null || entityUuid == null) {
            return Optional.empty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CompanionDataService.data(player).contains(entityUuid)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    public static boolean isRegisteredFor(ServerPlayer player, Entity entity) {
        return player != null && entity != null && CompanionDataService.data(player).contains(entity.getUUID());
    }
}
