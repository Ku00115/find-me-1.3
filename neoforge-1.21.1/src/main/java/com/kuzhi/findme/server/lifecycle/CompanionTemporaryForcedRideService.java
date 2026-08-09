package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** One-shot forced boarding lease used only by an accepted mount-switch transaction. */
public final class CompanionTemporaryForcedRideService {
    private static final int VERIFY_TIMEOUT_TICKS = 5;
    private static final Map<UUID, Lease> LEASES = new HashMap<>();

    private CompanionTemporaryForcedRideService() {
    }

    public static boolean forceForSwitch(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null || !mount.isAlive() || mount.isRemoved()
                || player.level() != mount.level()) return false;
        long now = player.getServer().overworld().getGameTime();
        LEASES.put(player.getUUID(), new Lease(mount.getUUID(), mount.level().dimension(), now,
                now + VERIFY_TIMEOUT_TICKS));
        boolean accepted = player.startRiding(mount, true) || player.getVehicle() == mount;
        if (!accepted) LEASES.remove(player.getUUID());
        FindMeDebugLogger.info("mount-ride", "temporary switch force player={} mount={} accepted={}",
                player.getUUID(), mount.getUUID(), accepted);
        return accepted;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || LEASES.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Lease>> iterator = LEASES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Lease> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Lease lease = entry.getValue();
            boolean stable = player != null && player.getVehicle() != null
                    && lease.mountUuid.equals(player.getVehicle().getUUID());
            boolean invalid = player == null || !player.level().dimension().equals(lease.dimension)
                    || now > lease.expiresAt;
            if ((stable && now > lease.startedAt) || invalid) {
                iterator.remove();
                FindMeDebugLogger.info("mount-ride", "temporary switch force cleared player={} mount={} stable={} invalid={}",
                        entry.getKey(), lease.mountUuid, stable, invalid);
            }
        }
    }

    public static void clearForPlayer(UUID playerUuid) {
        if (playerUuid != null) LEASES.remove(playerUuid);
    }

    public static void resetServerState() {
        LEASES.clear();
    }

    static boolean hasLease(UUID playerUuid, UUID mountUuid) {
        Lease lease = LEASES.get(playerUuid);
        return lease != null && lease.mountUuid.equals(mountUuid);
    }

    private record Lease(UUID mountUuid, ResourceKey<Level> dimension, long startedAt, long expiresAt) {
    }
}
