package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.server.lifecycle.CompanionMountSettleProtectionService;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicSpeedService;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicMovementService;

import com.kuzhi.findme.server.lifecycle.CompanionMountSwitchService;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicOrientationHelper;

import com.kuzhi.findme.server.lifecycle.MountCinematicStage;

import com.kuzhi.findme.server.lifecycle.PendingMountCinematic;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicPositionService;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;

import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;

import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;

import com.kuzhi.findme.server.lifecycle.MountCinematicMode;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;

import com.kuzhi.findme.server.lifecycle.CompanionArrivalMagicService;

import com.kuzhi.findme.server.lifecycle.CompanionRetreatService;


import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import com.kuzhi.findme.server.compat.IceAndFireRescueCompatibility;
import com.kuzhi.findme.server.compat.CompanionFixedPostService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.integration.SalvationCompatibilityService;
import com.kuzhi.findme.server.profile.CompanionMountContactService;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.command.MountRosterTransactionService;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.network.ContractCameraPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionMountCinematicFlowService {
    private static final List<PendingMountCinematic> PENDING_MOUNT_CINEMATICS = new ArrayList<>();

    private CompanionMountCinematicFlowService() {
    }

    public static void tickMountCinematics(MinecraftServer server) {
        for (PendingMountCinematic cinematic : List.copyOf(PENDING_MOUNT_CINEMATICS)) {
            if (!PENDING_MOUNT_CINEMATICS.contains(cinematic)) {
                continue;
            }
            boolean flyingEmergencyCatch;
            LivingEntity living;
            ServerPlayer player;
            player = server.getPlayerList().getPlayer(cinematic.playerUuid());
            Entity entity = CompanionEntityLookup.findEntity(server, cinematic.mountUuid()).orElse(null);
            if (!(entity instanceof LivingEntity) || player == null || !(living = (LivingEntity)entity).isAlive()) {
                CompanionCinematicSpeedService.forgetPlayer(cinematic.playerUuid());
                removePending(cinematic);
                continue;
            }
            CompanionCinematicSpeedService.samplePlayerHorizontalSpeed(player);
            CompanionAnimationHelper.forceStandingPose(living);
            if (living.level() != player.level()) {
                restoreCinematicPhysics(cinematic, living);
                removePending(cinematic);
                continue;
            }
            if (CompanionMountSwitchService.terminateFailedRescueIfNeeded(cinematic, player, living)) {
                continue;
            }
            if (CompanionArrivalSequenceService.blocksCinematicMovement(living)) {
                if (living instanceof Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                }
                living.setDeltaMovement(Vec3.ZERO);
                living.fallDistance = 0.0f;
                living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
                cinematic.rememberPosition(living.position());
                cinematic.incrementAge();
                continue;
            }
            living.noPhysics = shouldUseCinematicNoPhysics(cinematic, living);
            living.setNoGravity(cinematic.mode().isGroundOrWaterRescue()
                    && cinematic.presentationMoveType() != CompanionMoveType.WALK);
            if (cinematic.moveType() == CompanionMoveType.FLY) {
                CompanionAnimationHelper.forceFlyingAnimationPose(living);
                BookOfDragonsRescueCompatibility.forceAirborne(living);
            }
            if (correctExternalCinematicPull(cinematic, living, player)) {
                cinematic.incrementAge();
                continue;
            }
            if (cinematic.warmupTicks() > 0) {
                Vec3 warmupTarget = CompanionCinematicLandingService.cinematicTarget(cinematic, living.level(), player);
                double warmupDistance = living.position().distanceTo(warmupTarget);
                if (warmupDistance > 0.05) {
                    CompanionCinematicMovementService.moveTowardCinematicTarget(
                            cinematic, living, player, warmupTarget, warmupDistance);
                }
                if (living instanceof Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                }
                living.fallDistance = 0.0f;
                living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
                cinematic.rememberPosition(living.position());
                cinematic.decrementWarmup();
                cinematic.incrementAge();
                continue;
            }
            if (cinematic.stage() == MountCinematicStage.SWITCH) {
                CompanionMountSwitchService.tickMountSwitch(server, cinematic, player, living);
                continue;
            }
            if (cinematic.mode().isFlyingRescue()
                    && cinematic.rescueFlightMode() == RescueFlightMode.HOVER) {
                Vec3 hoverTarget = null;
                if (living.level() instanceof ServerLevel serverLevel) {
                    hoverTarget = CompanionCinematicLandingService.flyingHoverTarget(
                            serverLevel, player, cinematic.rescueHoverPosition());
                    cinematic.setRescueHoverPosition(hoverTarget);
                }
                boolean catchContact = isPlayerInFlyingCatchZone(player, living)
                        || isTouchingMountCollision(player, living, null);
                if (catchContact) {
                    cinematic.latchContact();
                    cinematic.beginSwitch();
                    CompanionMountSwitchService.tickMountSwitch(server, cinematic, player, living);
                    continue;
                }
                if (hoverTarget != null && living.position().distanceTo(hoverTarget) > 1.75) {
                    CompanionCinematicMovementService.moveTowardCinematicTarget(
                            cinematic, living, player, hoverTarget,
                            living.position().distanceTo(hoverTarget));
                    cinematic.incrementAge();
                    continue;
                }
                if (hoverTarget != null) {
                    CompanionCinematicPositionService.holdFlyingRescueHover(cinematic, living, hoverTarget);
                    cinematic.incrementAge();
                    continue;
                }
            }
            logRescueTick(cinematic, player, living, null, Double.NaN, "pre-target");
            Vec3 target = CompanionCinematicLandingService.cinematicTarget(cinematic, living.level(), player, living);
            double distance = living.position().distanceTo(target);
            if (cinematic.mode().isFlyingRescue() && !cinematic.flyingRescueStaged()
                    && !Double.isNaN(cinematic.catchY()) && distance <= 6.0) {
                cinematic.setFlyingRescueStaged();
                cinematic.incrementAge();
                continue;
            }
            double threshold = cinematic.mode().isRescue() ? 1.5 : 2.5;
            boolean rescueFallback = cinematic.mode().isRescue() && !cinematic.mode().isFlyingRescue() && distance < 8.0 && CompanionCinematicLandingService.distanceToGround(player) <= 12.0;
            boolean flyingCatchWindow = cinematic.mode().isFlyingRescue()
                    && cinematic.flyingRescueStaged() && !Double.isNaN(cinematic.catchY())
                    && !cinematic.physicalCatchOnly() && isPlayerInFlyingCatchZone(player, living);
            flyingEmergencyCatch = cinematic.mode().isFlyingRescue() && CompanionCinematicLandingService.distanceToGround(player) <= 2.0 && player.distanceTo(living) <= 6.0f;
            logRescueTick(cinematic, player, living, target, distance,
                    "target catchWindow=" + flyingCatchWindow + " emergency=" + flyingEmergencyCatch
                            + " rescueFallback=" + rescueFallback);
            boolean presentationGatedSwitch = cinematic.mode() == MountCinematicMode.MOUNT_SWITCH
                    || cinematic.mode().isAirToAirSwitch();
            boolean switchApproachReady = !presentationGatedSwitch
                    || MountSwitchContactPolicy.canEvaluateContact(cinematic.age(), player.distanceTo(living),
                    Math.max(living.getBbWidth(), living.getBbHeight()));
            if (flyingCatchWindow) {
                cinematic.latchContact();
                cinematic.beginSwitch();
                CompanionMountSwitchService.tickMountSwitch(server, cinematic, player, living);
                continue;
            }
            if ((cinematic.mode() == MountCinematicMode.MOUNT_SWITCH || cinematic.mode().isAirToAirSwitch())
                    && player.getVehicle() != null && player.getVehicle() != living
                    && switchApproachReady
                    && isMountApproachContact(player, living, player.getVehicle())) {
                cinematic.latchContact();
                cinematic.beginSwitch();
                CompanionMountSwitchService.tickMountSwitch(server, cinematic, player, living);
                continue;
            }
            boolean flyingApproachReady = !cinematic.mode().isFlyingRescue()
                    || flyingCatchWindow || flyingEmergencyCatch;
            if (((distance <= threshold && switchApproachReady && flyingApproachReady)
                    || rescueFallback || flyingCatchWindow || flyingEmergencyCatch)) {
                CompanionCinematicMovementService.snapWalkMountToGround(cinematic, living);
                cinematic.beginSwitch();
                continue;
            }
            CompanionCinematicMovementService.moveTowardCinematicTarget(cinematic, living, player, target, distance);
            living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
            if (cinematic.mode().isFlyingRescue() && CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                holdFallingPlayerForCatch(player);
            }
            if (living instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            cinematic.incrementAge();
        }
    }

    public static void scheduleMountCinematic(ServerPlayer player, LivingEntity mount, CompanionMoveType moveType, MountCinematicMode mode, boolean restoredFromStorage, boolean sendArrivalMagic) {
        scheduleMountCinematic(player, mount, moveType, mode, restoredFromStorage,
                sendArrivalMagic, true);
    }

    public static void scheduleMountCinematic(ServerPlayer player, LivingEntity mount,
                                               CompanionMoveType moveType, MountCinematicMode mode,
                                               boolean restoredFromStorage, boolean sendArrivalMagic,
                                               boolean presentationEnabled) {
        double catchY;
        Level level = player.level();
        boolean originalNoGravity = mount.isNoGravity();
        boolean originalNoAi = originalNoAiForCinematic(mount);
        collectConflictingMountCinematics(player, mount);
        CompanionRetreatService.cancelRetreat(mount);
        CompanionAnimationHelper.forceStandingPose(mount);
        CompanionFixedPostService.acquire(mount, CompanionFixedPostService.Reason.ARRIVAL);
        if (!CompanionArrivalSequenceService.isPending(mount)) {
            CompanionAnimationHelper.restoreAnimationControl(mount);
        }
        if (mode.isRescue() && CompanionAnimationHelper.shouldSuppressOwnerFollowDuringCinematic(mount) && mount instanceof Mob mob) {
            if (shouldFreezeWholeAiDuringCinematic(moveType, mount)) {
                mob.setNoAi(true);
            }
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        CompanionRescuePlanner.Plan rescuePlan = mode.isFlyingRescue()
                ? CompanionRescuePlanner.plan(player) : null;
        RescueFlightMode rescueFlightMode = rescuePlan == null
                ? RescueFlightMode.HOVER : rescuePlan.flightMode();
        Vec3 initialHoverPosition = null;
        if (mode.isFlyingRescue() && level instanceof ServerLevel serverLevel) {
            BlockPos landing = CompanionCinematicLandingService.rescueAnchor(serverLevel, player);
            if (rescueFlightMode == RescueFlightMode.HOVER) {
                initialHoverPosition = CompanionCinematicLandingService.flyingHoverTarget(serverLevel, player);
                catchY = initialHoverPosition.y;
            } else {
                catchY = landing.getY();
            }
        } else {
            catchY = Double.NaN;
        }
        boolean startStaged = mode.isFlyingRescue();
        int warmupTicks = 0;
        RideHandoffService.Source rideSource = capturedRideSource(player, mount, mode);
        PendingMountCinematic pending = new PendingMountCinematic(player.getUUID(), mount.getUUID(), moveType,
                mode, catchY, startStaged, false, warmupTicks, mount.position(), originalNoGravity,
                originalNoAi, rideSource);
        if (moveType == CompanionMoveType.SWIM
                && !mount.level().getFluidState(mount.blockPosition()).is(net.minecraft.tags.FluidTags.WATER)) {
            pending.useGroundedSwimFallback();
        } else if (mode.isRescue() && moveType == CompanionMoveType.SWIM) {
            pending.setRescueLandingPosition(mount.position());
        }
        pending.setIntroAnchor(moveType == CompanionMoveType.FLY ? mount.position() : null);
        pending.setRescueFlightMode(rescueFlightMode);
        if (mode.isFlyingRescue() && rescueFlightMode == RescueFlightMode.LANDING_SUMMON
                && level instanceof ServerLevel serverLevel) {
            pending.setRescueLandingPosition(Vec3.atBottomCenterOf(
                    CompanionCinematicLandingService.rescueAnchor(serverLevel, player)));
        }
        if (mode.isFlyingRescue() && rescueFlightMode == RescueFlightMode.HOVER
                && initialHoverPosition != null) {
            pending.setRescueHoverPosition(initialHoverPosition);
        }
        if (mode.isRescue()) {
            BookOfDragonsRescueCompatibility.suspendConflictingGoals(mount);
        }
        PENDING_MOUNT_CINEMATICS.add(pending);
        if (mode.isAirToGroundSwitch() && player.getVehicle() != null
                && player.getVehicle() != mount) {
            player.stopRiding();
            stabilizeFallingPlayer(player);
        }
        if (!presentationEnabled) {
            if (!mode.isRescue()) {
                pending.latchContact();
            }
            pending.beginSwitch();
        }
        FindMeDebugLogger.info("mount-cinematic", "scheduled player={} mount={} moveType={} mode={} rescueFlightMode={} restoredFromStorage={} sendArrivalMagic={} warmup={} sourceType={} source={} originalNoGravity={} originalNoAi={} pos={}",
                FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), moveType, mode,
                rescueFlightMode, restoredFromStorage, sendArrivalMagic, warmupTicks, rideSource.type(),
                rideSource.uuid(), originalNoGravity, originalNoAi, mount.position());
        if (presentationEnabled && (mode.isMountSwitch() || mode.isAirToAirSwitch())) {
            sendMountApproachMask(player, mount);
        } else if (presentationEnabled && mode == MountCinematicMode.NORMAL_SUMMON) {
            sendMountApproachMask(player, mount);
        } else if (presentationEnabled && mode.isRescue() && restoredFromStorage
                && rescueFlightMode != RescueFlightMode.LANDING_SUMMON) {
            // Stored entities can spend several ticks rebuilding optional-mod state.
            // Keep those loading frames hidden; the first visible frame must move.
            sendMountApproachMask(player, mount);
        }
        // The pending list is normally consumed on the next server pass. Prime an
        // ordinary summon once now so the entity does not spend that first visible
        // frame parked at the magic-circle anchor.
        if (presentationEnabled && !CompanionArrivalSequenceService.isPending(mount)
                && ((mode == MountCinematicMode.NORMAL_SUMMON && warmupTicks == 0)
                || (mode.isRescue() && restoredFromStorage))) {
            mount.noPhysics = shouldUseCinematicNoPhysics(pending, mount);
            mount.setNoGravity(mode.isGroundOrWaterRescue() && moveType != CompanionMoveType.WALK);
            Vec3 target = CompanionCinematicLandingService.cinematicTarget(pending, mount.level(), player, mount);
            double distance = mount.position().distanceTo(target);
            if (distance > 0.05) {
                CompanionCinematicMovementService.moveTowardCinematicTarget(pending, mount, player, target, distance);
            }
        }
        if (sendArrivalMagic) {
            sendMountArrivalMagic(player, mount, mode);
        }
    }

    private static void sendMountArrivalMagic(ServerPlayer player, LivingEntity mount, MountCinematicMode mode) {
        CompanionArrivalMagicService.send(player, mount, player.position(), mountArrivalMagicDuration(mode),
                RescueMagicPacket.Style.VERTICAL_PORTAL, arrivalPurpose(mode), animationPurpose(mode));
    }

    private static int mountArrivalMagicDuration(MountCinematicMode mode) {
        return mode.isRescue() ? 48 : 42;
    }

    private static boolean originalNoAiForCinematic(LivingEntity mount) {
        return CompanionArrivalSequenceService.originalNoAi(mount).orElseGet(() -> mount instanceof Mob mob && mob.isNoAi());
    }

    private static void sendDirectFlyingCatchMagic(ServerPlayer player, LivingEntity mount) {
        CompanionArrivalMagicService.openAt(player, mount, mount.position(), player.position(), 28, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.RESCUE);
    }

    private static RescueMagicPacket.Purpose arrivalPurpose(MountCinematicMode mode) {
        return mode.isRescue() ? RescueMagicPacket.Purpose.RESCUE : RescueMagicPacket.Purpose.SUMMON;
    }

    private static CompanionAnimationPurpose animationPurpose(MountCinematicMode mode) {
        if (mode.isMountSwitch()) return CompanionAnimationPurpose.SWITCH;
        return mode.isRescue() ? CompanionAnimationPurpose.RESCUE : CompanionAnimationPurpose.SUMMON;
    }

    public static boolean collectOtherVisibleMountCinematics(ServerPlayer player, PlayerCompanionData data, UUID keepUuid, UUID currentVehicleUuid) {
        boolean changed = false;
        for (PendingMountCinematic pending : List.copyOf(PENDING_MOUNT_CINEMATICS)) {
            UUID uuid = pending.mountUuid();
            if (!pending.playerUuid().equals(player.getUUID()) || uuid.equals(keepUuid) || uuid.equals(currentVehicleUuid) || !data.contains(CompanionKind.MOUNT, uuid)) {
                continue;
            }
            if (!removePending(pending)) {
                continue;
            }
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                restoreCinematicPhysics(pending, living);
                CompanionDeploymentService.collectLiving(player, data, CompanionKind.MOUNT, living);
            }
            changed = true;
        }
        return changed;
    }

    public static int cancelForCompanion(ServerPlayer player, UUID mountUuid, String reason) {
        if (player == null || mountUuid == null || PENDING_MOUNT_CINEMATICS.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (PendingMountCinematic pending : List.copyOf(PENDING_MOUNT_CINEMATICS)) {
            if (!mountUuid.equals(pending.mountUuid())) {
                continue;
            }
            if (!removePending(pending)) {
                continue;
            }
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), pending.mountUuid()).orElse(null);
            if (entity instanceof LivingEntity living) {
                CompanionArrivalSequenceService.cancel(living);
                restoreCinematicPhysics(pending, living);
                CompanionAnimationHelper.restoreAnimationControl(living);
                clearMountApproachMask(player, living);
            }
            removed++;
        }
        if (removed > 0) {
            FindMeDebugLogger.info("transient", "cancelled mount cinematic player={} mount={} reason={} count={}", player.getUUID(), mountUuid, reason, removed);
        }
        return removed;
    }

    public static boolean isPendingForCompanion(UUID mountUuid) {
        if (mountUuid == null) {
            return false;
        }
        for (PendingMountCinematic pending : PENDING_MOUNT_CINEMATICS) {
            if (mountUuid.equals(pending.mountUuid())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBoardingTransition(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null) {
            return false;
        }
        UUID playerUuid = player.getUUID();
        UUID mountUuid = mount.getUUID();
        for (PendingMountCinematic pending : PENDING_MOUNT_CINEMATICS) {
            if (playerUuid.equals(pending.playerUuid()) && mountUuid.equals(pending.mountUuid())) {
                return true;
            }
        }
        return false;
    }

    public static int cancelForPlayer(ServerPlayer player, String reason) {
        if (player == null || PENDING_MOUNT_CINEMATICS.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (PendingMountCinematic pending : List.copyOf(PENDING_MOUNT_CINEMATICS)) {
            if (!player.getUUID().equals(pending.playerUuid())) {
                continue;
            }
            if (!removePending(pending)) {
                continue;
            }
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), pending.mountUuid()).orElse(null);
            if (entity instanceof LivingEntity living) {
                CompanionArrivalSequenceService.cancel(living);
                restoreCinematicPhysics(pending, living);
                CompanionAnimationHelper.restoreAnimationControl(living);
                clearMountApproachMask(player, living);
            }
            removed++;
        }
        if (removed > 0) {
            FindMeDebugLogger.info("transient", "cancelled player mount cinematics player={} reason={} count={}", player.getUUID(), reason, removed);
        }
        return removed;
    }

    private static void collectConflictingMountCinematics(ServerPlayer player, LivingEntity newMount) {
        boolean changed = false;
        PlayerCompanionData data = CompanionDataService.data(player);
        for (PendingMountCinematic pending : List.copyOf(PENDING_MOUNT_CINEMATICS)) {
            boolean samePlayer = pending.playerUuid().equals(player.getUUID());
            boolean sameMount = pending.mountUuid().equals(newMount.getUUID());
            if (!samePlayer && !sameMount) {
                continue;
            }
            if (!removePending(pending)) {
                continue;
            }
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), pending.mountUuid()).orElse(null);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                CompanionArrivalSequenceService.cancel(living);
                restoreCinematicPhysics(pending, living);
                clearMountApproachMask(player, living);
                if (samePlayer && !sameMount && data.contains(CompanionKind.MOUNT, pending.mountUuid())) {
                    CompanionDeploymentService.collectLiving(player, data, CompanionKind.MOUNT, living);
                    changed = true;
                }
            }
        }
        if (changed) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        }
    }

    public static boolean correctExternalCinematicPull(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
        boolean pulledToPlayer;
        if (!cinematic.mode().isRescue() || cinematic.lastCinematicPosition() == null || cinematic.switched()) {
            return false;
        }
        Vec3 expected = cinematic.lastCinematicPosition();
        Vec3 current = mount.position();
        boolean jumpedAwayFromPath = current.distanceTo(expected) > 10.0;
        pulledToPlayer = mount.distanceTo(player) < 8.0f && expected.distanceTo(player.position()) > 12.0;
        if (!jumpedAwayFromPath || !pulledToPlayer) {
            return false;
        }
        if (mount instanceof Mob mob) {
            if (shouldFreezeWholeAiDuringCinematic(cinematic.presentationMoveType(), mount)) {
                mob.setNoAi(true);
            }
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        mount.setPos(expected.x, expected.y, expected.z);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        if (cinematic.moveType() == CompanionMoveType.FLY) {
            CompanionAnimationHelper.forceFlyingAnimationPose(mount);
            BookOfDragonsRescueCompatibility.forceAirborne(mount);
        }
        return true;
    }

    public static void completeMountSwitch(MinecraftServer server, ServerPlayer player, LivingEntity newMount) {
        CompanionMountCinematicFlowService.completeMountSwitch(server, player, newMount, player.getVehicle());
    }

    public static void completeMountSwitch(MinecraftServer server, ServerPlayer player, LivingEntity newMount, Entity oldVehicle) {
        completeMountSwitch(server, player, newMount, oldVehicle, true, false);
    }

    static void completeMountSwitch(MinecraftServer server, ServerPlayer player, LivingEntity newMount,
                                    Entity oldVehicle, boolean persist) {
        completeMountSwitch(server, player, newMount, oldVehicle, persist, false);
    }

    static void completeMountSwitch(MinecraftServer server, ServerPlayer player, LivingEntity newMount,
                                    Entity oldVehicle, boolean persist, boolean sourceAlreadyRetired) {
        UUID oldVehicleUuid;
        PlayerCompanionData data = CompanionDataService.data(player);
        Entity currentVehicle;
        oldVehicleUuid = oldVehicle == null ? null : oldVehicle.getUUID();
        if (!sourceAlreadyRetired && oldVehicle != null && oldVehicle != newMount) {
            currentVehicle = player.getVehicle();
            if (currentVehicle == oldVehicle) {
                player.stopRiding();
            }
            oldVehicle.fallDistance = 0.0f;
            if (data.contains(CompanionKind.MOUNT, oldVehicleUuid) && oldVehicle instanceof LivingEntity oldMount) {
                data.rememberPrevious(CompanionKind.MOUNT, oldVehicleUuid);
                retireSwitchedMount(player, data, oldMount);
            } else if (player.getVehicle() == newMount) {
                VehicleManager.collectIfFindMeVehicle(player, data, oldVehicle);
            }
        }
        data.deployed(CompanionKind.MOUNT).ifPresent(previousUuid -> {
            if (!previousUuid.equals(newMount.getUUID()) && !previousUuid.equals(oldVehicleUuid)) {
                CompanionEntityLookup.locateEntity(server, data, previousUuid)
                        .filter(previous -> previous instanceof LivingEntity)
                        .map(previous -> (LivingEntity)previous)
                        .ifPresent(previous -> retireSwitchedMount(player, data, previous));
            }
        });
        if (persist) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        }
    }

    static void sendMountApproachMask(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null) return;
        ModNetwork.sendToPlayer(player, new ContractCameraPacket(true, mount.getId(), 6,
                mount.getYRot(), mount.getXRot(), player.getX(), player.getY(), player.getZ(),
                mount.getX(), mount.getY(), mount.getZ(), 1.55f,
                Math.max(mount.getBbWidth(), mount.getBbHeight()), "",
                ContractCameraPacket.Mode.MOUNT_APPROACH, false));
    }

    static void clearMountApproachMask(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null) return;
        ModNetwork.sendToPlayer(player, new ContractCameraPacket(false, mount.getId(), 0,
                mount.getYRot(), mount.getXRot(), player.getX(), player.getY(), player.getZ(),
                mount.getX(), mount.getY(), mount.getZ(), 1.55f,
                Math.max(mount.getBbWidth(), mount.getBbHeight()), "",
                ContractCameraPacket.Mode.MOUNT_APPROACH, false));
    }

    private static void retireSwitchedMount(ServerPlayer player, PlayerCompanionData data, LivingEntity oldMount) {
        UUID uuid = oldMount.getUUID();
        CompanionRetreatService.sendAwayForSwitch(oldMount, player, data);
        data.clearDeployed(CompanionKind.MOUNT, uuid);
    }

    public static void stabilizeFallingPlayer(ServerPlayer player) {
        player.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
    }

    static void holdFallingPlayerForCatch(ServerPlayer player) {
        stabilizeFallingPlayer(player);
        if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0
                && player.getDeltaMovement().y < 0.0) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        }
    }

    public static void restoreCinematicPhysics(PendingMountCinematic cinematic, LivingEntity living) {
        living.noPhysics = false;
        if (living instanceof Mob mob) {
            mob.setNoAi(cinematic.originalNoAi());
        }
        living.setNoGravity(cinematic.originalNoGravity());
    }

    private static boolean shouldFreezeWholeAiDuringCinematic(CompanionMoveType moveType, LivingEntity mount) {
        return moveType != CompanionMoveType.FLY && !CompanionAnimationHelper.keepsFlyingAnimationWithActiveAi(mount);
    }

    static boolean shouldUseCinematicNoPhysics(PendingMountCinematic cinematic, LivingEntity living) {
        return shouldUseCinematicNoPhysics(cinematic.presentationMoveType(), living);
    }

    static boolean shouldUseCinematicNoPhysics(CompanionMoveType moveType, LivingEntity living) {
        if (moveType == CompanionMoveType.FLY) {
            return false;
        }
        if (moveType == CompanionMoveType.WALK) {
            return true;
        }
        double maxSize = Math.max(living.getBbWidth(), living.getBbHeight());
        return maxSize < 2.6;
    }

    public static boolean isPlayerInFlyingCatchZone(ServerPlayer player, LivingEntity mount) {
        if (player.getDeltaMovement().y > 0.25) {
            return false;
        }
        AABB contactBox = CompanionMountContactService.mountContactBox(mount);
        double dx = player.getX() - mount.getX();
        double dz = player.getZ() - mount.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double contactRadius = Math.max(contactBox.getXsize(), contactBox.getZsize()) * 0.5;
        double catchRadius = Math.max(3.0, contactRadius + 1.25);
        double minY = contactBox.minY - 0.75;
        double maxY = contactBox.maxY + 1.25;
        return horizontalDistance <= catchRadius && player.getY() >= minY && player.getY() <= maxY;
    }

    public static boolean isTouchingMountCollision(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
        return isMountRideContact(player, mount, currentVehicle);
    }

    static boolean isMountApproachContact(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
        return isMountRideContact(player, mount, currentVehicle);
    }

    private static boolean isMountVehicleContact(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
        if (currentVehicle == null || currentVehicle == mount) {
            logMountContactSample(mount, "no-vehicle", "no vehicle contact player={} mount={} vehicle={} reason=no-current-vehicle",
                    player, mount, currentVehicle);
            return false;
        }
        boolean vehicleContact = CompanionMountContactService.contactsIntersect(currentVehicle, mount, 0.45);
        logMountContactSample(mount, "vehicle:" + vehicleContact, "vehicleBox checked player={} mount={} vehicle={} result={}",
                player, mount, currentVehicle, vehicleContact);
        return vehicleContact;
    }

    private static boolean playerHasReachedGround(ServerPlayer player) {
        return player.onGround()
                || (CompanionCinematicLandingService.distanceToGround(player) <= 0.35
                && player.getDeltaMovement().y >= -0.08);
    }

    static boolean isMountRideContact(ServerPlayer player, LivingEntity mount, Entity currentVehicle) {
        AABB playerBox = player.getBoundingBox().inflate(0.28);
        boolean playerContact = CompanionMountContactService.intersectsMountContact(mount, playerBox, 0.0);
        if (playerContact) {
            logMountContactSample(mount, "player-hit", "ride playerBox hit player={} mount={} vehicle={} box={}",
                    player, mount, currentVehicle, playerBox);
            return true;
        }
        AABB sweptPlayerBox = playerBox.expandTowards(player.getDeltaMovement().scale(-1.0)).inflate(0.2);
        boolean sweptPlayerContact = CompanionMountContactService.intersectsMountContact(mount, sweptPlayerBox, 0.0);
        if (sweptPlayerContact) {
            logMountContactSample(mount, "swept-hit", "ride sweptPlayerBox hit player={} mount={} vehicle={} box={} motion={}",
                    player, mount, currentVehicle, sweptPlayerBox, player.getDeltaMovement());
            return true;
        }
        if (currentVehicle == null || currentVehicle == mount) {
            return false;
        }
        double down = Math.max(1.5, Math.min(8.0, currentVehicle.getBbHeight() + 1.5));
        AABB handoffBox = playerRideBox(player, 2.75, down, 1.75);
        boolean handoffContact = CompanionMountContactService.intersectsMountContact(mount, handoffBox, 0.0);
        boolean vehicleContact = handoffContact && isMountVehicleContact(player, mount, currentVehicle);
        logMountContactSample(mount, "handoff:" + handoffContact + ':' + vehicleContact,
                "ride handoffBox checked player={} mount={} vehicle={} handoff={} vehicle={} box={}",
                player, mount, currentVehicle, handoffContact, vehicleContact, handoffBox);
        return vehicleContact;
    }

    private static void logMountContactSample(Entity sampleEntity, String key, String message, Object... values) {
        if (!FindMeDebugLogger.enabled() || sampleEntity == null) {
            return;
        }
        long tick = sampleEntity.level() == null ? Long.MIN_VALUE : sampleEntity.level().getGameTime();
        if (!FindMeDebugLogger.shouldLogSample("mount-contact", sampleEntity.getId() + ":" + key, tick, 10)) {
            return;
        }
        Object[] rendered = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            rendered[i] = value instanceof Entity entity ? FindMeDebugLogger.entity(entity)
                    : value instanceof AABB box ? FindMeDebugLogger.box(box) : value;
        }
        FindMeDebugLogger.info("mount-contact", message, rendered);
    }

    private static void logRescueTick(PendingMountCinematic cinematic, ServerPlayer player,
                                      LivingEntity mount, Vec3 target, double targetDistance,
                                      String phase) {
        if (cinematic == null || !cinematic.mode().isRescue() || player == null || mount == null
                || !FindMeDebugLogger.enabled()) {
            return;
        }
        long tick = player.level().getGameTime();
        if (!FindMeDebugLogger.shouldLogSample("rescue-tick", mount.getUUID().toString(), tick, 2)) {
            return;
        }
        double dx = mount.getX() - player.getX();
        double dz = mount.getZ() - player.getZ();
        boolean loaded = player.level() instanceof ServerLevel level
                && level.hasChunkAt(mount.blockPosition());
        FindMeDebugLogger.info("rescue-tick",
                "phase={} player={} mount={} mode={} rescueFlightMode={} stage={} age={} switchAge={} playerPos={} mountPos={} target={} targetDistance={} horizontalDistance={} verticalDistance={} playerVelocity={} mountVelocity={} groundDistance={} catchY={} waitLocked={} contactLatched={} playerOnGround={} mountLoaded={} playerVehicle={}",
                phase, FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), cinematic.mode(),
                cinematic.rescueFlightMode(), cinematic.stage(), cinematic.age(), cinematic.switchAge(),
                player.position(), mount.position(),
                target == null ? "null" : target, target == null ? "NaN" : targetDistance,
                Math.sqrt(dx * dx + dz * dz), Math.abs(mount.getY() - player.getY()),
                player.getDeltaMovement(), mount.getDeltaMovement(),
                CompanionCinematicLandingService.distanceToGround(player), cinematic.catchY(),
                cinematic.waitLocked(), cinematic.contactLatched(), player.onGround(), loaded,
                player.getVehicle() == null ? "null" : FindMeDebugLogger.entity(player.getVehicle()));
    }

    private static AABB playerRideBox(ServerPlayer player, double horizontal, double down, double up) {
        AABB base = player.getBoundingBox();
        return new AABB(
                base.minX - horizontal,
                base.minY - down,
                base.minZ - horizontal,
                base.maxX + horizontal,
                base.maxY + up,
                base.maxZ + horizontal
        );
    }

    public static boolean startRidingAfterContact(ServerPlayer player, LivingEntity mount, MountCinematicMode mode) {
        if (player.getVehicle() == mount) {
            FindMeDebugLogger.info("mount-ride", "already riding player={} mount={} mode={}",
                    FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode);
            return true;
        }
        Entity currentVehicle = player.getVehicle();
        if (!isMountRideContact(player, mount, currentVehicle)) {
            double maxDistance = Math.max(4.0, Math.min(12.0, Math.max(mount.getBbWidth(), mount.getBbHeight()) + 2.0));
            if (player.distanceTo(mount) > maxDistance) {
                FindMeDebugLogger.info("mount-ride", "blocked distant ride player={} mount={} vehicle={} mode={} distance={} max={}",
                        FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), FindMeDebugLogger.entity(currentVehicle),
                        mode, player.distanceTo(mount), maxDistance);
                return false;
            }
        }
        SalvationCompatibilityService.RideStartResult salvationRide =
                SalvationCompatibilityService.tryStartRide(player, mount);
        if (salvationRide == SalvationCompatibilityService.RideStartResult.STARTED) {
            FindMeDebugLogger.info("mount-ride", "Salvation ride API success player={} mount={} mode={}",
                    FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode);
            return true;
        }
        if (salvationRide == SalvationCompatibilityService.RideStartResult.REJECTED) {
            FindMeDebugLogger.info("mount-ride", "Salvation ride API rejected player={} mount={} mode={}",
                    FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode);
            return false;
        }
        long interactionStartedAt = System.nanoTime();
        mount.interact(player, InteractionHand.MAIN_HAND);
        double interactionMs = (System.nanoTime() - interactionStartedAt) / 1_000_000.0;
        if (player.getVehicle() == mount) {
            FindMeDebugLogger.info("mount-ride", "normal interaction success player={} mount={} mode={} interactionMs={}",
                    FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode, interactionMs);
            return true;
        }
        boolean normalForceAllowed = CompanionMountContactService.canMountByNormalUse(player, mount);
        boolean externalForceAllowed = com.kuzhi.findme.api.FindMeApi.canForceMount(player, mount)
                || (mode.isRescue() && IceAndFireRescueCompatibility.canForceRescueMount(player, mount));
        if (mode.isMountSwitch()) {
            return CompanionTemporaryForcedRideService.forceForSwitch(player, mount);
        }
        if (!normalForceAllowed && !externalForceAllowed) {
            FindMeDebugLogger.info("mount-ride", "normal interaction failed and force ride blocked player={} mount={} mode={} interactionMs={}",
                    FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode, interactionMs);
            return false;
        }
        long forceStartedAt = System.nanoTime();
        boolean result = player.startRiding(mount, true);
        double forceMs = (System.nanoTime() - forceStartedAt) / 1_000_000.0;
        FindMeDebugLogger.info("mount-ride", "forced ride player={} mount={} mode={} source={} result={} interactionMs={} forceMs={}",
                FindMeDebugLogger.entity(player), FindMeDebugLogger.entity(mount), mode,
                externalForceAllowed ? "external-provider" : "normal-use", result, interactionMs, forceMs);
        return result;
    }

    public static void finishMountCinematic(PendingMountCinematic cinematic, ServerPlayer player,
                                            LivingEntity mount, boolean mounted) {
        long totalStartedAt = System.nanoTime();
        if (!removePending(cinematic)) {
            return;
        }
        CompanionArrivalSequenceService.cancel(mount);
        clearMountApproachMask(player, mount);
        CompanionMountCinematicFlowService.restoreCinematicPhysics(cinematic, mount);
        if (mounted && cinematic.mode().isRescue() && cinematic.moveType() == CompanionMoveType.FLY) {
            BookOfDragonsRescueCompatibility.forceAirborne(mount);
            IceAndFireRescueCompatibility.finishMountedRide(mount);
        } else if (!mounted && cinematic.mode().isRescue()) {
            IceAndFireRescueCompatibility.clearRescueState(mount);
        }
        if (mounted && cinematic.rideHandoff().compatibleWith(cinematic.moveType())) {
            CompanionMountSwitchService.applyCompatibleRideState(cinematic, mount);
            CompanionMountSettleProtectionService.rememberCompatibleHandoff(player, mount, cinematic);
        }
        long dataStartedAt = System.nanoTime();
        PlayerCompanionData data = CompanionDataService.data(player);
        double dataMs = (System.nanoTime() - dataStartedAt) / 1_000_000.0;
        if (!mounted && cinematic.mode().isRescue()) {
            player.fallDistance = 0.0f;
            player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
            if (CompanionLifecycleFacade.storeLiving(player, data, mount, CompanionTransientStateService.Reason.FAILURE_RECOVERY, "mount_cinematic:rescue_catch_failed")) {
                data.clearDeployed(CompanionKind.MOUNT, mount.getUUID());
                CompanionDataService.save(player, data);
                CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
                CompanionSummonLineService.showBlocked(player, mount);
            }
            failMountCinematicTransactions(player, mount.getUUID(), "rescue_catch_failed");
            return;
        }
        Level level2 = mount.level();
        if (mounted && cinematic.mode().isRescue() && level2 instanceof ServerLevel level && !CompanionPlacementFinder.hasOpenEntitySpace(level, mount, mount.getX(), mount.getY(), mount.getZ())) {
            if (player.getVehicle() == mount) {
                player.stopRiding();
            }
            player.fallDistance = 0.0f;
            player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
            if (CompanionLifecycleFacade.storeLiving(player, data, mount, CompanionTransientStateService.Reason.MOUNT_SWITCH, "mount_cinematic:blocked_rescue")) {
                data.clearDeployed(CompanionKind.MOUNT);
                CompanionDataService.save(player, data);
                CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            }
            failMountCinematicTransactions(player, mount.getUUID(), "blocked_rescue");
            CompanionSummonLineService.showBlocked(player, mount);
            return;
        }
        data.setDeployed(CompanionKind.MOUNT, mount.getUUID());
        data.setLastKnownPosition(mount.getUUID(), SavedPosition.of(mount.level(), mount.getX(), mount.getY(), mount.getZ(), mount.getYRot(), mount.getXRot()));
        VehicleManager.enforceSingleRideSlotForMount(player, data, mount.getUUID(), null);
        long saveStartedAt = System.nanoTime();
        CompanionDataService.save(player, data);
        double saveMs = (System.nanoTime() - saveStartedAt) / 1_000_000.0;
        long syncStartedAt = System.nanoTime();
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT, data);
        double syncMs = (System.nanoTime() - syncStartedAt) / 1_000_000.0;
        player.fallDistance = 0.0f;
        CompanionMountSettleProtectionService.rememberSettledMount(
                player, mount, cinematic.moveType() == CompanionMoveType.FLY);
        FindMeDebugLogger.info("switch-commit-perf",
                "player={} target={} mode={} totalMs={} dataMs={} saveMs={} syncMs={} riding={} passenger={}",
                player.getUUID(), mount.getUUID(), cinematic.mode(),
                (System.nanoTime() - totalStartedAt) / 1_000_000.0, dataMs, saveMs, syncMs,
                player.getVehicle() == mount, mount.hasPassenger(player));
    }

    private static void failMountCinematicTransactions(ServerPlayer player, UUID mountUuid,
                                                        String reason) {
        CompanionWheelTransactionService.failLatest(player, CompanionKind.MOUNT, mountUuid, reason);
        MountRosterTransactionService.failLatest(player, mountUuid, reason);
    }

    private static RideHandoffService.Source capturedRideSource(ServerPlayer player, LivingEntity target,
                                                                 MountCinematicMode mode) {
        if (player == null || target == null || mode == null || !mode.isMountSwitch()) {
            return RideHandoffService.Source.none();
        }
        RideHandoffService.Source source = RideHandoffService.resolveSource(player, CompanionDataService.data(player));
        return source.present() && !source.uuid().equals(target.getUUID())
                ? source
                : RideHandoffService.Source.none();
    }

    static boolean removePending(PendingMountCinematic cinematic) {
        if (cinematic == null || !PENDING_MOUNT_CINEMATICS.remove(cinematic)) {
            return false;
        }
        BookOfDragonsRescueCompatibility.restoreConflictingGoals(cinematic.mountUuid());
        CompanionFixedPostService.release(cinematic.mountUuid(), CompanionFixedPostService.Reason.ARRIVAL);
        return true;
    }

    public static void softLandFailedMountCatch(ServerPlayer player, LivingEntity mount) {
        if (player.getVehicle() == mount) {
            return;
        }
        double safeY = Math.max(player.getY(), mount.getBoundingBox().maxY + 0.2);
        player.teleportTo(player.getX(), safeY, player.getZ());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        mount.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        mount.invulnerableTime = Math.max(mount.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
    }
}


