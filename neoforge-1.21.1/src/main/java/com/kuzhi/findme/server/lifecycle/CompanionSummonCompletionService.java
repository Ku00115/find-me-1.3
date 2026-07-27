package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;

import com.kuzhi.findme.server.lifecycle.CompanionSummonModeService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.safety.CompanionCombatRescueService;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionSummonCompletionService {
    private CompanionSummonCompletionService() {
    }

    public static void complete(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living,
                                CompanionMoveType moveType, MountCinematicMode mode, boolean restoredFromStorage,
                                boolean inCombat, boolean companionRescue, LivingEntity companionRescueTarget,
                                boolean arrivalStarted, boolean presentationAlreadyPlayed, long now,
                                boolean tacticalDeploy) {
        CompanionHomeResidentService.clearResident(living);
        collectOtherVisibleEntitiesBeforeSummon(player, data, kind, living.getUUID());
        data.setLastKnownPosition(living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
        living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        if (tacticalDeploy) {
            CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
        } else if (kind == CompanionKind.MOUNT) {
            completeMountSummon(player, living, moveType, mode, restoredFromStorage, inCombat,
                    !arrivalStarted && !presentationAlreadyPlayed);
        } else {
            completeCompanionSummon(player, data, kind, living, companionRescue, companionRescueTarget,
                    !arrivalStarted && !presentationAlreadyPlayed);
        }
        data.setLifecycleState(living.getUUID(), CompanionLifecycleState.DEPLOYED);
        int cooldown = Config.summonCooldownTicks;
        data.setReadyAt(kind, now + (long)cooldown);
        FindMeDebugLogger.info("summon-timing",
                "stage=BEFORE_SAVE_SYNC player={} entity={} kind={} arrivalStarted={} presentationAlreadyPlayed={} gameTime={}",
                player.getUUID(), FindMeDebugLogger.entity(living), kind, arrivalStarted, presentationAlreadyPlayed,
                player.level().getGameTime());
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        FindMeDebugLogger.info("summon-timing",
                "stage=AFTER_SAVE_SYNC player={} entity={} kind={} gameTime={}",
                player.getUUID(), FindMeDebugLogger.entity(living), kind, player.level().getGameTime());
        CompanionSummonLineService.showOrSchedule(player, living, kind, mode.isRescue() || companionRescue, companionRescue, arrivalStarted);
    }

    public static void completeVoidFlyingMount(ServerPlayer player, PlayerCompanionData data, LivingEntity living,
                                               boolean inCombat, long now,
                                               RideHandoffService.MotionSnapshot handoff) {
        CompanionHomeResidentService.clearResident(living);
        collectOtherVisibleEntitiesBeforeSummon(player, data, CompanionKind.MOUNT, living.getUUID());
        CompanionArrivalSequenceService.cancel(living);
        CompanionAnimationHelper.restoreAnimationControl(living);
        CompanionAnimationHelper.forceFlyingAnimationPose(living);
        Entity previousRide = player.getVehicle();
        boolean riding = player.startRiding(living, true) || player.getVehicle() == living;
        if (riding) {
            RideHandoffService.applyCompatibleMotion(handoff, living, CompanionMoveType.FLY);
            CompanionMountCinematicFlowService.completeMountSwitch(player.getServer(), player, living, previousRide);
        }
        player.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        living.fallDistance = 0.0f;
        living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        data.setDeployed(CompanionKind.MOUNT, living.getUUID());
        data.setLifecycleState(living.getUUID(), CompanionLifecycleState.DEPLOYED);
        data.setLastKnownPosition(living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
        VehicleManager.enforceSingleRideSlotForMount(player, data, living.getUUID(), previousRide);
        int cooldown = Config.summonCooldownTicks;
        data.setReadyAt(CompanionKind.MOUNT, now + (long)cooldown);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSummonLineService.showVoidFlyingMount(player, living);
    }

    private static void completeMountSummon(ServerPlayer player, LivingEntity living, CompanionMoveType moveType, MountCinematicMode mode, boolean restoredFromStorage, boolean inCombat, boolean sendArrivalMagic) {
        CompanionMoveType cinematicMoveType = CompanionSummonModeService.landPresentationMoveType(moveType);
        player.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        // All flying rescues use the same approach/wait cinematic. The former
        // direct catch shortcut skipped the visible hover phase and mounted too
        // early for a player still in free fall.
        CompanionMountCinematicFlowService.scheduleMountCinematic(player, living, cinematicMoveType, mode, restoredFromStorage, sendArrivalMagic);
    }

    private static void completeCompanionSummon(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living, boolean companionRescue, LivingEntity companionRescueTarget, boolean sendArrivalMagic) {
        CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
        if (companionRescue) {
            CompanionCombatRescueService.applyArrival(player, living, companionRescueTarget, sendArrivalMagic);
        } else if (sendArrivalMagic) {
            CompanionArrivalMagicService.sendGround(player, living, player.position(), 42);
        }
    }

    private static void collectOtherVisibleEntitiesBeforeSummon(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID keepUuid) {
        Entity currentVehicle = player.getVehicle();
        UUID currentVehicleUuid = currentVehicle == null ? null : currentVehicle.getUUID();
        boolean changed = CompanionDeploymentService.collectOtherDeployed(player, data, kind, keepUuid, currentVehicleUuid, true);
        if (changed) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, kind);
        }
    }
}


