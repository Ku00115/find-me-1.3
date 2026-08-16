package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/** Owns the persistent near-death state of registered FindMe creatures. */
public final class CompanionCriticalStateService {
    private static final float RECOVERY_HEALTH_THRESHOLD = 1.0F;
    private static final String STORED_HEALTH_TAG = "CompanionRescueHealth";
    private static final String STORED_MAX_HEALTH_TAG = "CompanionRescueMaxHealth";
    private static final String STORED_AT_TAG = "CompanionRescueStoredAt";
    private static final String STORED_CRITICAL_TAG = "CompanionRescueCritical";
    private static final int RECOVERY_INTERVAL_TICKS = 20;

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
        Optional<CompanionKind> companionKind = data.kindOf(companionUuid);
        boolean wasHomeResident = data.lifecycleState(companionUuid) == CompanionLifecycleState.HOME_ACTIVE
                || CompanionHomeResidentService.isHomeResident(living);

        event.setCanceled(true);
        living.setHealth(RECOVERY_HEALTH_THRESHOLD);
        dismountAll(living);
        data.setCritical(companionUuid, true);

        boolean stored = CompanionStorageService.storeAndDiscard(player, data, living);
        if (stored) {
            companionKind.ifPresent(kind -> commitStoredState(data, kind, companionUuid, wasHomeResident));
            CompanionDataService.save(player, data);
        } else {
            // Keep the critical marker and one-health safeguard even if another
            // transaction currently owns the storage lock.
            CompanionDataService.save(player, data);
            FindMeMod.LOGGER.warn("FindMe could not auto-store critical companion {} for {}",
                    companionUuid, player.getUUID());
        }
        companionKind.ifPresent(kind -> CompanionSyncService.syncToClient(player, kind));
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
                if (recoverHomeResident(player, data, uuid)) {
                    data.kindOf(uuid).ifPresent(changedKinds::add);
                    continue;
                }
                Entity entity = CompanionEntityLookup.findEntity(server, uuid).orElse(null);
                if (!(entity instanceof LivingEntity living)
                        || !shouldClearCritical(data.hasStoredEntity(uuid), living.isRemoved(), living.isAlive(),
                        living.getHealth())) {
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

    static boolean shouldClearCritical(boolean storedSnapshot, boolean removed, boolean alive, float health) {
        return !storedSnapshot && !removed && alive && health > RECOVERY_HEALTH_THRESHOLD;
    }

    static void commitStoredState(PlayerCompanionData data, CompanionKind kind, UUID uuid,
                                  boolean homeResident) {
        data.clearDeployed(kind, uuid);
        data.setLifecycleState(uuid, homeResident
                ? CompanionLifecycleState.HOME_STORED
                : CompanionLifecycleState.STORED);
    }

    private static boolean recoverHomeResident(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (data.lifecycleState(uuid) != CompanionLifecycleState.HOME_STORED
                || data.homeHouseId(uuid).isEmpty()
                || data.homeNestBlock(uuid).isEmpty()
                || CompanionOperationLockService.get(uuid) != null
                || CompanionStorageService.isStoragePending(uuid)) {
            return false;
        }
        CompoundTag stored = data.storedEntity(uuid).orElse(null);
        if (stored == null) {
            return false;
        }
        HomeRecovery recovery = recoverHomeStoredSnapshot(stored, player.serverLevel().getGameTime());
        if (!recovery.changed() && !recovery.full()) {
            return false;
        }
        if (recovery.changed()) {
            data.storeEntity(uuid, recovery.snapshot());
        }
        if (recovery.full()) {
            data.setCritical(uuid, false);
        }
        return true;
    }

    static HomeRecovery recoverHomeStoredSnapshot(CompoundTag source, long now) {
        CompoundTag tag = CompanionStorageService.sanitizedStoredTag(source);
        float health = tag.getFloat(STORED_HEALTH_TAG);
        float maxHealth = tag.getFloat(STORED_MAX_HEALTH_TAG);
        if (!(maxHealth > 0.0F)) {
            return new HomeRecovery(tag, false, false);
        }
        health = Math.min(maxHealth, Math.max(RECOVERY_HEALTH_THRESHOLD, health));
        long storedAt = tag.getLong(STORED_AT_TAG);
        if (health >= maxHealth) {
            boolean changed = tag.getBoolean(STORED_CRITICAL_TAG)
                    || tag.getFloat(STORED_HEALTH_TAG) != maxHealth
                    || tag.getFloat("Health") != maxHealth;
            tag.putFloat(STORED_HEALTH_TAG, maxHealth);
            tag.putFloat("Health", maxHealth);
            tag.putBoolean(STORED_CRITICAL_TAG, false);
            return new HomeRecovery(tag, changed, true);
        }
        if (storedAt <= 0L) {
            tag.putLong(STORED_AT_TAG, now);
            tag.putFloat(STORED_HEALTH_TAG, health);
            tag.putFloat("Health", health);
            tag.putBoolean(STORED_CRITICAL_TAG, true);
            return new HomeRecovery(tag, true, false);
        }
        long elapsed = Math.max(0L, now - storedAt);
        long seconds = elapsed / RECOVERY_INTERVAL_TICKS;
        if (seconds <= 0L) {
            return new HomeRecovery(tag, false, false);
        }
        float healed = Math.min(maxHealth, health + seconds);
        tag.putFloat(STORED_HEALTH_TAG, healed);
        tag.putFloat("Health", healed);
        tag.putLong(STORED_AT_TAG, storedAt + seconds * RECOVERY_INTERVAL_TICKS);
        boolean full = healed >= maxHealth;
        tag.putBoolean(STORED_CRITICAL_TAG, !full);
        return new HomeRecovery(tag, true, full);
    }

    static record HomeRecovery(CompoundTag snapshot, boolean changed, boolean full) {
    }

    private static void dismountAll(LivingEntity living) {
        for (Entity passenger : living.getPassengers().stream().toList()) {
            passenger.stopRiding();
        }
        living.stopRiding();
    }
}
