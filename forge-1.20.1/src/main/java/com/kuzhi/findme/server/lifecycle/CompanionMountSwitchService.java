package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicMovementService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CompanionMountSwitchService {
    private static final int MOUNT_SWITCH_FAIL_TICKS = 160;
    private static final int RESCUE_TERMINAL_GRACE_TICKS = 4;

    private CompanionMountSwitchService() {
    }

    public static void tickMountSwitch(MinecraftServer server, PendingMountCinematic cinematic,
                                       ServerPlayer player, LivingEntity mount) {
        boolean rescueAtWaitPoint;
        boolean emergencyAirRescue;
        boolean inFlyingCatchZone;
        MountCinematicMode mode = cinematic.mode();
        boolean groundRescue = mode.isGroundOrWaterRescue();
        boolean airRescue = mode.isFlyingRescue();
        Entity currentVehicle = player.getVehicle();
        player.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        mount.invulnerableTime = Math.max(mount.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        boolean contactLatched = cinematic.contactLatched();
        if (mode.isMountSwitch() && !mode.isRescue() && currentVehicle != mount) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        }
        if (currentVehicle == mount) {
            CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, true);
            return;
        }
        if (contactLatched && tryCompleteMountSwitch(server, cinematic, player, mount)) {
            return;
        }
        if (contactLatched) {
            cinematic.incrementSwitchAge();
            if (cinematic.switchAge() > 10) {
                CompanionMountCinematicFlowService.softLandFailedMountCatch(player, mount);
                CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
            }
            return;
        }
        if (terminateFailedRescueIfNeeded(cinematic, player, mount)) {
            return;
        }
        if (CompanionMountCinematicFlowService.correctExternalCinematicPull(cinematic, mount, player)) {
            cinematic.incrementSwitchAge();
            return;
        }
        if (mode.isAirToGroundSwitch() && !cinematic.sourceRetirementStarted()) {
            if (!retireSourceBeforeBoarding(cinematic, player, mount)) {
                cinematic.incrementSwitchAge();
                return;
            }
            currentVehicle = player.getVehicle();
        }
        if (groundRescue && mode.isRescue()) {
            boolean groundContact;
            boolean atGroundWaitPoint = cinematic.waitLocked() || mount.position().distanceTo(CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player, mount)) <= 2.25;
            if (atGroundWaitPoint && !(groundContact = CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle))) {
                CompanionCinematicPositionService.lockMountAtGroundWait(cinematic, mount, player);
                if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                    CompanionMountCinematicFlowService.holdFallingPlayerForCatch(player);
                }
                cinematic.incrementSwitchAge();
                return;
            }
        }
        if (groundRescue && !cinematic.waitLocked() && CompanionCinematicLandingService.distanceToGround(player) >= 14.0 && (mode.isAirToGroundSwitch() || (double)player.distanceTo(mount) > 4.0)) {
            Vec3 target = CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player, mount);
            CompanionCinematicMovementService.moveTowardCinematicTarget(cinematic, mount, player,
                    target, mount.position().distanceTo(target));
            cinematic.incrementSwitchAge();
            return;
        }
        if (airRescue && cinematic.rescueFlightMode() == RescueFlightMode.HOVER
                && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            cinematic.setRescueHoverPosition(CompanionCinematicLandingService.flyingHoverTarget(
                    level, player, cinematic.rescueHoverPosition()));
        }
        double flyingWaitY = CompanionCinematicLandingService.flyingWaitY(
                cinematic.rescueFlightMode(), cinematic.catchY(), cinematic.rescueHoverPosition());
        boolean urgentAirRescue = mode.isFlyingRescue() && (!Double.isNaN(flyingWaitY)
                ? player.getY() <= flyingWaitY + 2.5
                : CompanionCinematicLandingService.distanceToGround(player) <= 12.0);
        boolean quickFlyingMount = mode.isQuickFlyingMount();
        inFlyingCatchZone = airRescue && !cinematic.physicalCatchOnly() && CompanionMountCinematicFlowService.isPlayerInFlyingCatchZone(player, mount);
        boolean fixedWaitContact = airRescue && cinematic.waitLocked()
                && (inFlyingCatchZone || CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, null));
        if (airRescue && cinematic.rescueFlightMode() == RescueFlightMode.HOVER
                && !fixedWaitContact && cinematic.rescueHoverPosition() != null
                && player.getY() > cinematic.rescueHoverPosition().y + 2.5) {
            CompanionCinematicPositionService.holdFlyingRescueHover(
                    cinematic, mount, cinematic.rescueHoverPosition());
            cinematic.incrementSwitchAge();
            return;
        }
        if (airRescue && cinematic.waitLocked() && !fixedWaitContact
                && CompanionCinematicLandingService.distanceToGround(player) > 4.0) {
            CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            cinematic.incrementSwitchAge();
            return;
        }
        boolean immediateMountContact = !mode.isRescue()
                && CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle);
        if (!(groundRescue || urgentAirRescue || quickFlyingMount || inFlyingCatchZone || fixedWaitContact
                || immediateMountContact || cinematic.switchAge() >= 4)) {
            CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
            cinematic.incrementSwitchAge();
            return;
        }
        Entity immediateContactVehicle = !mode.isRescue() ? currentVehicle : null;
        boolean physicalCatchContact = CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, immediateContactVehicle);
        if (airRescue && cinematic.physicalCatchOnly() && cinematic.switchAge() < 6 && !physicalCatchContact && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
            CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            CompanionMountCinematicFlowService.holdFallingPlayerForCatch(player);
            cinematic.incrementSwitchAge();
            return;
        }
        if (airRescue && cinematic.physicalCatchOnly() && !physicalCatchContact && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
            CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                CompanionMountCinematicFlowService.holdFallingPlayerForCatch(player);
            }
            cinematic.incrementSwitchAge();
            return;
        }
        if (airRescue && cinematic.switchAge() < 30 && CompanionCinematicLandingService.distanceToGround(player) > 6.0 && !inFlyingCatchZone && !physicalCatchContact) {
            CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            cinematic.incrementSwitchAge();
            return;
        }
        if (airRescue && !Double.isNaN(flyingWaitY) && player.getY() > flyingWaitY + 2.5 && !inFlyingCatchZone && !physicalCatchContact && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
            CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            cinematic.incrementSwitchAge();
            return;
        }
        emergencyAirRescue = airRescue && CompanionCinematicLandingService.distanceToGround(player) <= 2.0 && player.distanceTo(mount) <= 6.0f;
        if (airRescue && !emergencyAirRescue && !inFlyingCatchZone && (double)player.distanceTo(mount) > 4.0) {
            CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
            if (CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                CompanionMountCinematicFlowService.holdFallingPlayerForCatch(player);
            }
            cinematic.incrementSwitchAge();
            return;
        }
        if (emergencyAirRescue && !cinematic.physicalCatchOnly()) {
            CompanionCinematicPositionService.stabilizeAirRescueMount(cinematic, mount, player);
        }
        Entity contactVehicle = !mode.isRescue() ? currentVehicle : (cinematic.age() >= 8 ? currentVehicle : null);
        rescueAtWaitPoint = !mode.isRescue()
                || groundRescue && cinematic.waitLocked()
                || airRescue && (cinematic.waitLocked()
                || cinematic.rescueHoverPosition() != null
                && mount.position().distanceTo(cinematic.rescueHoverPosition()) <= 2.25)
                || mount.position().distanceTo(CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player, mount)) <= 2.25;
        if (mode.isRescue() && !rescueAtWaitPoint) {
            if (airRescue) {
                CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            } else {
                Vec3 target = CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player, mount);
                CompanionCinematicMovementService.moveTowardCinematicTarget(cinematic, mount, player,
                        target, mount.position().distanceTo(target));
            }
            cinematic.incrementSwitchAge();
            return;
        }
        if (!physicalCatchContact && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, contactVehicle)) {
            if (groundRescue && mode.isRescue()) {
                CompanionCinematicPositionService.lockMountAtGroundWait(cinematic, mount, player);
            } else {
                CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
            }
            if (mode.isRescue() && CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                CompanionMountCinematicFlowService.holdFallingPlayerForCatch(player);
            }
            if (cinematic.switchAge() > MOUNT_SWITCH_FAIL_TICKS && !mode.isRescue()) {
                CompanionMountCinematicFlowService.restoreCinematicPhysics(cinematic, mount);
                CompanionMountCinematicFlowService.removePending(cinematic);
            } else {
                cinematic.incrementSwitchAge();
            }
            return;
        }
        if (tryCompleteMountSwitch(server, cinematic, player, mount)) {
            return;
        } else if (mode.isMountSwitch()) {
            if (cinematic.switchAge() > MOUNT_SWITCH_FAIL_TICKS) {
                CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
            } else {
                cinematic.incrementSwitchAge();
            }
        } else if (mode.isRescue() && !cinematic.contactLatched()) {
            CompanionMountCinematicFlowService.softLandFailedMountCatch(player, mount);
            CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
        } else if (mode.isRescue()) {
            cinematic.incrementSwitchAge();
            if (cinematic.switchAge() > 10) {
                CompanionMountCinematicFlowService.softLandFailedMountCatch(player, mount);
                CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
            }
        } else if (cinematic.switchAge() > 10) {
            CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
        } else {
            cinematic.incrementSwitchAge();
        }
    }

    static boolean terminateFailedRescueIfNeeded(PendingMountCinematic cinematic,
                                                  ServerPlayer player, LivingEntity mount) {
        if (cinematic == null || player == null || mount == null || !cinematic.mode().isRescue()
                || player.getVehicle() == mount
                || CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, null)) {
            return false;
        }
        boolean terminal = player.onGround() || player.isInWater();
        int phaseAge = cinematic.stage() == MountCinematicStage.SWITCH
                ? cinematic.switchAge() : cinematic.age();
        if (!shouldFailRescue(phaseAge, terminal)) {
            return false;
        }
        CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
        return true;
    }

    static boolean shouldFailRescue(int phaseAge, boolean terminalPlayerState) {
        return terminalPlayerState && phaseAge >= RESCUE_TERMINAL_GRACE_TICKS;
    }

    private static boolean tryCompleteMountSwitch(MinecraftServer server, PendingMountCinematic cinematic,
                                                  ServerPlayer player, LivingEntity mount) {
        long totalStartedAt = System.nanoTime();
        long phaseStartedAt = totalStartedAt;
        CompanionCinematicMovementService.snapWalkMountToGround(cinematic, mount);
        double snapMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        Entity previousVehicle = player.getVehicle();
        cinematic.captureRideHandoff(previousVehicle);
        double handoffMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        if (!CompanionMountCinematicFlowService.startRidingAfterContact(player, mount, cinematic.mode())) {
            double rideMs = elapsedMs(phaseStartedAt);
            logSwitchPerformance(cinematic, player, mount, "ride_failed", totalStartedAt,
                    snapMs, handoffMs, 0.0, 0.0, rideMs, 0.0, 0.0);
            return false;
        }
        double rideMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        if (cinematic.rideHandoff().compatibleWith(CompanionMoveType.FLY)
                && cinematic.moveType() == CompanionMoveType.FLY) {
            applyCompatibleRideState(cinematic, mount);
        }
        if (cinematic.moveType() == CompanionMoveType.FLY) {
            BookOfDragonsRescueCompatibility.forceAirborne(mount);
        }
        retireSourceAfterBoarding(cinematic, player, mount);
        double retireAfterMs = elapsedMs(phaseStartedAt);
        if (!cinematic.switched()) {
            cinematic.setSwitched();
        }
        phaseStartedAt = System.nanoTime();
        CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, true);
        double finishMs = elapsedMs(phaseStartedAt);
        logSwitchPerformance(cinematic, player, mount, "completed", totalStartedAt,
                snapMs, handoffMs, 0.0, 0.0, rideMs,
                retireAfterMs, finishMs);
        return true;
    }

    private static double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private static void logSwitchPerformance(PendingMountCinematic cinematic, ServerPlayer player,
                                             LivingEntity mount, String result, long totalStartedAt,
                                             double snapMs, double handoffMs, double retireBeforeMs,
                                             double controllerPrepareMs, double rideMs,
                                             double retireAfterMs, double finishMs) {
        if (!FindMeDebugLogger.enabled()) {
            return;
        }
        FindMeDebugLogger.info("switch-perf",
                "result={} player={} sourceType={} source={} target={} mode={} totalMs={} snapMs={} handoffMs={} retireBeforeMs={} controllerPrepareMs={} rideMs={} retireAfterMs={} finishMs={} riding={} passenger={}",
                result, player.getUUID(), cinematic.rideSource().type(), cinematic.rideSource().uuid(),
                mount.getUUID(), cinematic.mode(), elapsedMs(totalStartedAt), snapMs, handoffMs,
                retireBeforeMs, controllerPrepareMs, rideMs, retireAfterMs, finishMs,
                player.getVehicle() == mount, mount.hasPassenger(player));
    }

    private static boolean retireSourceBeforeBoarding(PendingMountCinematic cinematic, ServerPlayer player,
                                                       LivingEntity target) {
        if (cinematic.sourceRetirementStarted()) {
            return true;
        }
        RideHandoffService.Source source = cinematic.rideSource();
        if (!source.present() || source.uuid().equals(target.getUUID())) {
            cinematic.markSourceRetirementStarted();
            return true;
        }
        boolean requiresDetachFirst = source.airborne();
        if (cinematic.mode().isAirToAirSwitch()) {
            requiresDetachFirst = false;
        }
        return !requiresDetachFirst || startSourceRetirement(cinematic, player, target, true);
    }

    static void applyAirToAirFlightState(PendingMountCinematic cinematic, LivingEntity mount) {
        if (!cinematic.rideHandoff().compatibleWith(CompanionMoveType.FLY)
                || cinematic.moveType() != CompanionMoveType.FLY) {
            return;
        }
        applyCompatibleRideState(cinematic, mount);
    }

    static boolean applyCompatibleRideState(PendingMountCinematic cinematic, LivingEntity mount) {
        if (cinematic == null || mount == null
                || !cinematic.rideHandoff().compatibleWith(cinematic.moveType())) {
            return false;
        }
        if (cinematic.moveType() == CompanionMoveType.FLY) {
            CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        }
        return RideHandoffService.applyCompatibleMotion(cinematic.rideHandoff(), mount, cinematic.moveType());
    }

    static void applyAirToAirFlightState(LivingEntity mount, net.minecraft.world.phys.Vec3 velocity,
                                          float yRot, float xRot, boolean captured) {
        CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        if (!captured) {
            return;
        }
        mount.setYRot(yRot);
        mount.setXRot(xRot);
        mount.yBodyRot = yRot;
        mount.yHeadRot = yRot;
        if (velocity.lengthSqr() > 1.0E-4) {
            mount.setDeltaMovement(velocity);
        }
        mount.hurtMarked = true;
    }

    private static void retireSourceAfterBoarding(PendingMountCinematic cinematic, ServerPlayer player,
                                                   LivingEntity target) {
        if (cinematic.sourceRetirementStarted()) {
            return;
        }
        if (!startSourceRetirement(cinematic, player, target, false)) {
            FindMeDebugLogger.info("ride-handoff",
                    "phase=SOURCE_RETIREMENT_DEFERRED_FAILED player={} sourceType={} source={} target={}",
                    player.getUUID(), cinematic.rideSource().type(), cinematic.rideSource().uuid(), target.getUUID());
        }
    }

    private static boolean startSourceRetirement(PendingMountCinematic cinematic, ServerPlayer player,
                                                  LivingEntity target, boolean beforeBoarding) {
        RideHandoffService.Source source = cinematic.rideSource();
        if (!source.present() || source.uuid().equals(target.getUUID())) {
            cinematic.markSourceRetirementStarted();
            return true;
        }
        if (source.type() == RideHandoffService.SourceType.OTHER) {
            if (beforeBoarding && player.getVehicle() == source.entity()) {
                player.stopRiding();
            }
            cinematic.markSourceRetirementStarted();
            return true;
        }
        com.kuzhi.findme.server.data.PlayerCompanionData data =
                com.kuzhi.findme.server.data.CompanionDataService.data(player);
        boolean started = RideHandoffService.beginRetirement(player, data, source,
                target.getUUID(), "mount-cinematic:contact_handoff");
        if (!started) {
            return false;
        }
        if (beforeBoarding && player.getVehicle() == source.entity()) {
            player.stopRiding();
        }
        cinematic.markSourceRetirementStarted();
        return true;
    }

}
