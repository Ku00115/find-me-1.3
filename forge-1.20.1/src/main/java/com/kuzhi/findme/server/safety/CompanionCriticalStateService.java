package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/** Owns the persistent near-death state of registered FindMe creatures. */
public final class CompanionCriticalStateService {
    private static final float RECOVERY_HEALTH_THRESHOLD = 1.0F;

    private CompanionCriticalStateService() {
    }

    public static void handleIncomingDamage(LivingDamageEvent event) {
        if (event == null) {
            return;
        }
        LivingEntity living = event.getEntity();
        if (living.level().isClientSide()) {
            return;
        }
        if (CompanionStorageService.protectStorageTransition(living)) {
            event.setCanceled(true);
            return;
        }
        if (event.isCanceled() || !(event.getAmount() > 0.0F)
                || living.getHealth() - event.getAmount() > 0.0F) {
            return;
        }

        MinecraftServer server = living.getServer();
        if (server == null) {
            return;
        }
        Optional<UUID> ownerUuid = CompanionRecoveryService.ownerUuid(server, living.getUUID());
        if (ownerUuid.isEmpty()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid.get());
        if (player == null) {
            // Logout cleanup normally stores active companions first. Do not create a
            // half-completed storage transaction without an online presentation owner.
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        UUID companionUuid = living.getUUID();
        if (!data.contains(companionUuid) || data.isCritical(companionUuid)) {
            return;
        }

        event.setCanceled(true);
        living.setHealth(RECOVERY_HEALTH_THRESHOLD);
        dismountAll(living);
        data.setCritical(companionUuid, true);

        boolean stored = CompanionStorageService.storeAndDiscard(player, data, living);
        if (!stored) {
            // Keep the critical marker and one-health safeguard even if another
            // transaction currently owns the storage lock.
            CompanionDataService.save(player, data);
            FindMeMod.LOGGER.warn("FindMe could not auto-store critical companion {} for {}",
                    companionUuid, player.getUUID());
        }
        data.kindOf(companionUuid).ifPresent(kind -> CompanionSyncService.syncToClient(player, kind));
    }

    /** Clears the marker only after a live entity has actually gained health. */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionDataService.data(player);
            Set<UUID> critical = data.criticalCompanions();
            if (critical.isEmpty()) {
                continue;
            }
            EnumSet<CompanionKind> changedKinds = EnumSet.noneOf(CompanionKind.class);
            for (UUID uuid : critical) {
                Entity entity = CompanionEntityLookup.findEntity(server, uuid).orElse(null);
                if (!(entity instanceof LivingEntity living) || living.isRemoved() || !living.isAlive()
                        || living.getHealth() <= RECOVERY_HEALTH_THRESHOLD) {
                    continue;
                }
                if (data.setCritical(uuid, false)) {
                    data.kindOf(uuid).ifPresent(changedKinds::add);
                }
            }
            if (!changedKinds.isEmpty()) {
                CompanionDataService.save(player, data);
                for (CompanionKind kind : changedKinds) {
                    CompanionSyncService.syncToClient(player, kind);
                }
            }
        }
    }

    private static void dismountAll(LivingEntity living) {
        for (Entity passenger : living.getPassengers().stream().toList()) {
            passenger.stopRiding();
        }
        living.stopRiding();
    }
}


