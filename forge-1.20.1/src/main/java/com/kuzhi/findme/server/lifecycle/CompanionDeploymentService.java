package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.Config;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionDeploymentService {
    private CompanionDeploymentService() {
    }

    public static boolean collectOtherDeployed(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid) {
        return collectOtherDeployed(player, data, kind, keepUuid, null, true);
    }

    public static boolean collectOtherDeployed(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid, UUID excludedUuid, boolean collectPendingMounts) {
        if (kind == CompanionKind.COMPANION) {
            return false;
        }
        boolean changed = false;
        for (UUID uuid : List.copyOf(data.deployedList(kind))) {
            if (uuid.equals(keepUuid) || uuid.equals(excludedUuid)) {
                continue;
            }
            changed |= collectOne(player, data, kind, uuid);
        }
        if (collectPendingMounts && kind == CompanionKind.MOUNT) {
            changed |= CompanionMountCinematicFlowService.collectOtherVisibleMountCinematics(player, data, keepUuid, excludedUuid);
        }
        return changed;
    }

    public static void rememberDeployedWithinLimit(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid) {
        if (kind == CompanionKind.COMPANION) {
            data.setDeployed(kind, keepUuid);
            enforceCompanionDeploymentLimit(player, data, keepUuid);
            return;
        }
        collectOtherDeployed(player, data, kind, keepUuid);
        data.setDeployed(kind, keepUuid);
    }

    private static void enforceCompanionDeploymentLimit(ServerPlayer player, PlayerCompanionData data, UUID keepUuid) {
        ArrayList<UUID> deployed = new ArrayList<>(data.deployedList(CompanionKind.COMPANION));
        int limit = Math.max(1, Config.companionDeploymentLimit);
        while (deployed.size() > limit) {
            // setDeployed appends a newly deployed UUID, so this list is oldest
            // first. Capacity enforcement must evict that oldest entry instead of
            // reordering around escort state; the newly bound creature remains live.
            UUID oldest = deployed.stream()
                    .filter(candidate -> !candidate.equals(keepUuid))
                    .findFirst()
                    .orElse(null);
            if (oldest == null) {
                break;
            }
            deployed.remove(oldest);
            LivingEntity living;
            Entity entity;
            boolean collected = false;
            if ((entity = CompanionEntityLookup.locateEntity(player.getServer(), data, oldest).orElse(null)) instanceof LivingEntity && (living = (LivingEntity)entity).isAlive()) {
                collected = collectLiving(player, data, CompanionKind.COMPANION, living);
            } else {
                java.util.Optional<CompoundTag> shoulderTag = CompanionShoulderService.shoulderEntityTag(player, oldest);
                if (shoulderTag.isPresent()) {
                    data.storeEntity(oldest, CompanionStorageService.storedShoulderTag(player, oldest, shoulderTag.get()));
                    CompanionShoulderService.clearPlayerShoulderEntity(player, oldest);
                    data.setLifecycleState(oldest, CompanionLifecycleState.SHOULDER);
                    collected = true;
                }
            }
            if (!collected) break;
            data.clearDeployed(CompanionKind.COMPANION, oldest);
        }
    }

    private static boolean collectOne(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        boolean changed = data.isDeployed(kind, uuid);
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            collectLiving(player, data, kind, living);
            changed = true;
        } else if (kind == CompanionKind.COMPANION) {
            java.util.Optional<CompoundTag> shoulderTag = CompanionShoulderService.shoulderEntityTag(player, uuid);
            if (shoulderTag.isPresent()) {
                data.storeEntity(uuid, CompanionStorageService.storedShoulderTag(player, uuid, shoulderTag.get()));
                CompanionShoulderService.clearPlayerShoulderEntity(player, uuid);
                data.setLifecycleState(uuid, CompanionLifecycleState.SHOULDER);
                changed = true;
            }
        }
        data.clearDeployed(kind, uuid);
        return changed;
    }

    public static boolean collectLiving(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
        if (player == null || data == null || kind == null || living == null || !living.isAlive()) {
            return false;
        }
        if (CompanionStorageService.isStoragePending(living)) {
            return false;
        }
        UUID uuid = living.getUUID();
        CompanionEscortService.cancelIfEscorting(player, data, uuid);
        if (player.getVehicle() == living) {
            player.stopRiding();
        }
        if (data.homeNestBlock(uuid).isPresent() && CompanionHomeResidentService.sendHomeIfPossible(player, data, kind, living)) {
            data.clearDeployed(kind, uuid);
            return true;
        }
        if (!CompanionLifecycleFacade.storeLiving(player, data, living, CompanionTransientStateService.Reason.AUTO_STORE, "deployment:collect_living")) {
            return false;
        }
        data.clearDeployed(kind, uuid);
        return true;
    }

    public static boolean retireMountAfterSharedRideSwitch(ServerPlayer player, PlayerCompanionData data, Entity previousRide) {
        if (!(previousRide instanceof LivingEntity oldMount) || data == null || !oldMount.isAlive()) {
            return false;
        }
        UUID uuid = oldMount.getUUID();
        if (!data.contains(CompanionKind.MOUNT, uuid)) {
            return false;
        }
        data.rememberPrevious(CompanionKind.MOUNT, uuid);
        CompanionRetreatService.sendAwayForSwitch(oldMount, player, data);
        data.clearDeployed(CompanionKind.MOUNT, uuid);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        return true;
    }
}

