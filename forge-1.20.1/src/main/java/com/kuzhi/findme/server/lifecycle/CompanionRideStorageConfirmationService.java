package com.kuzhi.findme.server.lifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Keeps the deliberate second request required before storing a ridden vehicle with passengers. */
public final class CompanionRideStorageConfirmationService {
    private static final long CONFIRMATION_WINDOW_TICKS = 5L * 20L;
    private static final Map<UUID, PendingConfirmation> PENDING = new HashMap<>();

    private CompanionRideStorageConfirmationService() {
    }

    public static boolean consumeOrRequest(ServerPlayer player, UUID rideUuid, boolean hasExternalPassengers) {
        if (!hasExternalPassengers) {
            return true;
        }
        long now = player.serverLevel().getGameTime();
        PendingConfirmation pending = PENDING.get(player.getUUID());
        if (pending != null && pending.rideUuid().equals(rideUuid) && pending.expiresAt() >= now) {
            PENDING.remove(player.getUUID());
            return true;
        }
        PENDING.put(player.getUUID(), new PendingConfirmation(rideUuid, now + CONFIRMATION_WINDOW_TICKS));
        return false;
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            PENDING.remove(player.getUUID());
        }
    }

    private record PendingConfirmation(UUID rideUuid, long expiresAt) {
    }
}
