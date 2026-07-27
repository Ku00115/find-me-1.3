package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;

import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;


import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.Iterator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionCollectionService {
    private static final Map<UUID, PendingUnloadSnapshot> PENDING_UNLOAD_SNAPSHOTS = new LinkedHashMap<>();

    private CompanionCollectionService() {
    }

    public static void collectActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        Optional<UUID> active = data.active(kind);
        Optional<UUID> maybeUuid = active.filter(uuid -> data.isDeployed(kind, uuid)).or(() -> data.deployed(kind)).or(() -> active);
        if (maybeUuid.isEmpty()) {
            CompanionSummonLineService.showNoRegistered(player, kind);
            return;
        }
        collectUuid(player, data, kind, maybeUuid.get());
    }

    public static boolean collectIndex(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            return false;
        }
        collectUuid(player, data, kind, maybeUuid.get());
        return true;
    }

    public static boolean collectUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        if (CompanionStorageService.isStoragePending(uuid)) {
            CompanionSummonLineService.showBusy(player, data, kind, uuid);
            return true;
        }
        boolean wasDeployed = data.isDeployed(kind, uuid);
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
        if (!(entity instanceof LivingEntity)) {
            Optional<CompoundTag> shoulderTag = CompanionShoulderService.shoulderEntityTag(player, uuid);
            if (shoulderTag.isPresent()) {
                Optional<LivingEntity> released = CompanionShoulderService.releaseShoulderCompanion(player, uuid, shoulderTag.get());
                if (released.isPresent()) {
                    if (!CompanionLifecycleFacade.storeLiving(player, data, released.get(), CompanionTransientStateService.Reason.MANUAL_STORE, "collection:shoulder_release")) {
                        return true;
                    }
                } else {
                    data.storeEntity(uuid, CompanionStorageService.storedShoulderTag(player, uuid, shoulderTag.get()));
                    CompanionShoulderService.clearPlayerShoulderEntity(player, uuid);
                    data.setLifecycleState(uuid, CompanionLifecycleState.SHOULDER);
                }
                data.clearDeployed(kind, uuid);
                CompanionDataService.save(player, data);
                CompanionSyncService.syncToClient(player, kind);
                return true;
            }
            if (wasDeployed && data.storedEntity(uuid).isPresent()) {
                data.clearDeployed(kind, uuid);
                CompanionDataService.save(player, data);
                CompanionSyncService.syncToClient(player, kind);
                return true;
            }
            CompanionSummonLineService.showUnavailable(player, data, kind);
            return true;
        }
        LivingEntity living = (LivingEntity)entity;
        CompanionEscortService.cancelIfEscorting(player, data, uuid);
        CompanionDeploymentService.collectLiving(player, data, kind, living);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        return true;
    }

    public static boolean collectActiveShoulderCompanion(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (CompanionStorageService.isStoragePending(uuid)) {
            CompanionSummonLineService.showBusy(player, data, CompanionKind.COMPANION, uuid);
            return true;
        }
        Optional<CompoundTag> shoulderTag = CompanionShoulderService.shoulderEntityTag(player, uuid);
        if (shoulderTag.isEmpty()) {
            return false;
        }
        Optional<LivingEntity> released = CompanionShoulderService.releaseShoulderCompanion(player, uuid, shoulderTag.get());
        if (released.isPresent()) {
            if (!CompanionLifecycleFacade.storeLiving(player, data, released.get(), CompanionTransientStateService.Reason.MANUAL_STORE, "collection:active_shoulder")) {
                return true;
            }
        } else {
            data.storeEntity(uuid, CompanionStorageService.storedShoulderTag(player, uuid, shoulderTag.get()));
            CompanionShoulderService.clearPlayerShoulderEntity(player, uuid);
            data.setLifecycleState(uuid, CompanionLifecycleState.SHOULDER);
        }
        data.clearDeployed(CompanionKind.COMPANION, uuid);
        data.setReadyAt(CompanionKind.COMPANION, player.serverLevel().getGameTime() + (long)Config.summonCooldownTicks);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        return true;
    }

    public static void collectDeployedOnDeath(ServerPlayer player) {
        collectDeployedForReason(player, CompanionTransientStateService.Reason.PLAYER_DEATH, "lifecycle:death_collect");
    }

    public static void collectDeployedForModule(ServerPlayer player, CompanionKind kind) {
        if (player == null || kind == null) {
            return;
        }
        collectDeployedForReason(player, CompanionTransientStateService.Reason.MODULE_DISABLED,
                "module:disable_" + kind.name().toLowerCase(), kind);
        CompanionSyncService.syncToClient(player, kind);
    }

    private static void collectDeployedForReason(ServerPlayer player, CompanionTransientStateService.Reason reason, String source) {
        collectDeployedForReason(player, reason, source, null);
    }

    private static void collectDeployedForReason(ServerPlayer player, CompanionTransientStateService.Reason reason,
                                                 String source, CompanionKind onlyKind) {
        PlayerCompanionData data = CompanionDataService.data(player);
        boolean changed = false;
        for (CompanionKind kind : CompanionKind.values()) {
            if (onlyKind != null && kind != onlyKind) {
                continue;
            }
            for (UUID uuid : data.deployedList(kind)) {
                Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    CompanionEscortService.cancelIfEscorting(player, data, uuid);
                    if (player.getVehicle() == living) {
                        player.stopRiding();
                    }
                    if (!CompanionLifecycleFacade.storeLiving(player, data, living, reason, source)) {
                        continue;
                    }
                } else {
                    CompanionShoulderService.shoulderEntityTag(player, uuid).ifPresent(tag -> {
                        data.storeEntity(uuid, CompanionStorageService.storedShoulderTag(player, uuid, tag));
                        CompanionShoulderService.clearPlayerShoulderEntity(player, uuid);
                        data.setLifecycleState(uuid, CompanionLifecycleState.SHOULDER);
                    });
                }
                data.clearDeployed(kind, uuid);
                changed = true;
            }
        }
        if (changed) {
            CompanionDataService.save(player, data);
        }
    }

    public static void collectDeployedForTravel(ServerPlayer player) {
        if (CompanionMountSettleProtectionService.isMountSettleProtected(player)) {
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            return;
        }
        collectDeployedForReason(player, CompanionTransientStateService.Reason.DIMENSION_CHANGE, "lifecycle:travel_collect");
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
    }

    public static void collectDeployedForLogout(ServerPlayer player) {
        PlayerCompanionData data = CompanionDataService.data(player);
        boolean changed = false;
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.deployedList(kind)) {
                Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    CompanionEscortService.cancelIfEscorting(player, data, uuid);
                    if (player.getVehicle() == living) {
                        player.stopRiding();
                    }
                    if (!CompanionLifecycleFacade.storeLiving(player, data, living, CompanionTransientStateService.Reason.PLAYER_LOGOUT, "lifecycle:logout_collect")) {
                        continue;
                    }
                    changed = true;
                } else if (CompanionShoulderService.shoulderEntityTag(player, uuid).isPresent()) {
                    CompoundTag tag = CompanionShoulderService.shoulderEntityTag(player, uuid).get();
                    data.storeEntity(uuid, CompanionStorageService.storedShoulderTag(player, uuid, tag));
                    CompanionShoulderService.clearPlayerShoulderEntity(player, uuid);
                    data.setLifecycleState(uuid, CompanionLifecycleState.SHOULDER);
                    changed = true;
                } else if (data.storedEntity(uuid).isPresent()) {
                    changed = true;
                }
                data.clearDeployed(kind, uuid);
            }
        }
        if (changed) {
            CompanionDataService.save(player, data);
        }
    }

    public static void snapshotRegisteredBeforeUnload(MinecraftServer server, Entity entity) {
        if (server == null || entity instanceof ServerPlayer
                || !(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        UUID uuid = living.getUUID();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionDataService.data(player);
            if (!data.contains(uuid)) continue;
            CompanionHomeResidentService.clearResident(uuid);
            PENDING_UNLOAD_SNAPSHOTS.put(uuid, new PendingUnloadSnapshot(player.getUUID(), living));
            break;
        }
    }

    public static void processPendingUnloadSnapshots(MinecraftServer server) {
        if (server == null || PENDING_UNLOAD_SNAPSHOTS.isEmpty()) {
            return;
        }
        Map<UUID, EnumSet<CompanionKind>> changedKinds = new HashMap<>();
        Iterator<Map.Entry<UUID, PendingUnloadSnapshot>> iterator = PENDING_UNLOAD_SNAPSHOTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingUnloadSnapshot> entry = iterator.next();
            iterator.remove();
            PendingUnloadSnapshot pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
            LivingEntity living = pending.living;
            if (player == null || living == null) {
                continue;
            }
            PlayerCompanionData data = CompanionDataService.data(player);
            if (!data.contains(entry.getKey())) {
                continue;
            }
            boolean saved = CompanionStorageService.snapshotEntity(player, data, living);
            if (saved) {
                data.kindOf(entry.getKey()).ifPresent(kind -> changedKinds
                        .computeIfAbsent(player.getUUID(), ignored -> EnumSet.noneOf(CompanionKind.class))
                        .add(kind));
            }
            FindMeDebugLogger.lifecycle(saved ? "UNLOAD_SNAPSHOT_CREATED" : "UNLOAD_SNAPSHOT_FAILED",
                    player, entry.getKey(), living, "ACTIVE", "ACTIVE", "entity_leave_level_deferred", saved, true);
        }
        changedKinds.forEach((playerUuid, kinds) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                kinds.forEach(kind -> CompanionSyncService.syncToClient(player, kind));
            }
        });
    }

    private record PendingUnloadSnapshot(UUID playerUuid, LivingEntity living) {
    }
}


