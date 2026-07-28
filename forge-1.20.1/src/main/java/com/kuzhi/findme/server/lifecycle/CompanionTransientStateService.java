package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.safety.CompanionCombatRescueService;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionTransientStateService {
    private CompanionTransientStateService() {
    }

    public static void cancelTarget(ServerPlayer player, PlayerCompanionData data, UUID uuid, Reason reason) {
        cancelTarget(player, data, uuid, reason, false);
    }

    public static void cancelTargetForDeploy(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                             Reason reason) {
        boolean preserveTacticalRequest = reason == Reason.DEPLOY
                && CompanionTacticalOrderService.hasPendingDeployment(player, uuid);
        cancelTarget(player, data, uuid, reason, preserveTacticalRequest);
    }

    private static void cancelTarget(ServerPlayer player, PlayerCompanionData data, UUID uuid, Reason reason,
                                     boolean preserveTacticalRequest) {
        if (player == null || data == null || uuid == null) {
            return;
        }
        int cancelled = 0;
        boolean storageReason = reason == Reason.MANUAL_STORE || reason == Reason.AUTO_STORE
                || reason == Reason.MODULE_DISABLED;
        cancelled += FindMeApi.cancelTemporaryActionsForCompanion(player.getServer(), uuid, reason.name());
        cancelled += CompanionCombatRescueService.cancelForCompanion(player, uuid, reason.name());
        cancelled += CompanionMountCinematicFlowService.cancelForCompanion(player, uuid, reason.name());
        cancelled += CompanionSummonApproachService.cancel(uuid, reason.name());
        if (!preserveTacticalRequest) {
            cancelled += CompanionTacticalOrderService.cancelTarget(player.getServer(), uuid, reason.name());
        }
        if (storageReason) {
            CompanionEscortService.cancelIfEscortingForStorage(player, data, uuid);
        } else {
            CompanionEscortService.cancelIfEscorting(player, data, uuid);
        }
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (entity instanceof LivingEntity living) {
            CompanionArrivalSequenceService.cancel(living);
            CompanionRetreatService.cancelRetreat(living);
            if (storageReason) {
                CompanionStorageService.freezeForStorageTransition(living);
            } else {
                CompanionAnimationHelper.restoreAnimationControl(living);
            }
            living.fallDistance = 0.0f;
            living.hurtMarked = true;
        }
        FindMeDebugLogger.lifecycle("TRANSIENT_CANCELLED", player, uuid, entity, "TRANSIENT", "CLEARED", reason.name(), data.storedEntity(uuid).isPresent(), entity != null);
        if (cancelled > 0) {
            FindMeDebugLogger.info("transient", "cancelTarget player={} companion={} reason={} count={}", player.getUUID(), uuid, reason, cancelled);
        }
    }

    public static void cancelCombat(ServerPlayer player, Reason reason) {
        if (player == null) {
            return;
        }
        int cancelled = 0;
        cancelled += FindMeApi.cancelTemporaryActionsForPlayer(player.getServer(), player.getUUID(), reason.name());
        cancelled += CompanionCombatRescueService.cancelForPlayer(player, reason.name());
        FindMeDebugLogger.lifecycle("TRANSIENT_CANCELLED", player, null, null, "COMBAT_TRANSIENT", "CLEARED", reason.name(), false, false);
        if (cancelled > 0) {
            FindMeDebugLogger.info("transient", "cancelCombat player={} reason={} count={}", player.getUUID(), reason, cancelled);
        }
    }

    public static void cancelPlayerAll(ServerPlayer player, Reason reason) {
        if (player == null) {
            return;
        }
        cancelCombat(player, reason);
        CompanionMountCinematicFlowService.cancelForPlayer(player, reason.name());
        CompanionSummonApproachService.cancelPlayer(player.getUUID(), reason.name());
        CompanionTeamOrderService.cancelPlayer(player.getUUID());
        CompanionTacticalOrderService.cancelPlayer(player.getServer(), player.getUUID(), reason.name());
        CompanionEscortService.cancelForTransition(player);
        int clearedLocks = CompanionOperationLockService.clearForPlayer(player,
                "player_transient:" + reason.name(), CompanionStorageService::isStoragePending);
        FindMeDebugLogger.lifecycle("TRANSIENT_CANCELLED", player, null, null, "PLAYER_TRANSIENT", "CLEARED", reason.name(), false, false);
        if (clearedLocks > 0) {
            FindMeDebugLogger.info("transient", "cleared player operation locks player={} reason={} count={}",
                    player.getUUID(), reason, clearedLocks);
        }
    }

    public enum Reason {
        MANUAL_STORE,
        AUTO_STORE,
        DEPLOY,
        MOUNT_SWITCH,
        SUPERSEDED,
        PLAYER_DEATH,
        PLAYER_LOGOUT,
        DIMENSION_CHANGE,
        RECOVER,
        BINDING,
        RELEASE,
        FAILURE_RECOVERY,
        MODULE_DISABLED
    }
}

