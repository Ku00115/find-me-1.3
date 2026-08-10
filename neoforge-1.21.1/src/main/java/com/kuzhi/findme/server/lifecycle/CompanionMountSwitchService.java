package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicMovementService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionMountSwitchService {
    private static final int MOUNT_SWITCH_FAIL_TICKS = 160;

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
        // The source may already have been retired before contact. Keep the player at
        // the handoff height until the destination ride is authoritative; otherwise
        // the client renders a fall to the ground followed by a sudden mount snap.
        if (mode.isMountSwitch() && !mode.isRescue() && currentVehicle != mount) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        }
        if (currentVehicle == mount) {
            if (cinematic.rideSource().type() == RideHandoffService.SourceType.SABLE
                    && !cinematic.sourceRetirementStarted()) {
                cinematic.incrementDestinationRideStableTicks();
                if (cinematic.destinationRideStableTicks() < 3) {
                    logSableDestinationVerification(cinematic, player, mount);
                    return;
                }
                retireSourceAfterBoarding(cinematic, player, mount);
                if (!cinematic.sourceRetirementStarted()) {
                    return;
                }
            }
            CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, true);
            return;
        }
        cinematic.resetDestinationRideStableTicks();
        if (contactLatched) {
            boolean stillInContact = CompanionMountCinematicFlowService.isTouchingMountCollision(
                    player, mount, mode.isRescue() ? null : currentVehicle);
            if (airRescue) {
                stillInContact = stillInContact
                        || CompanionMountCinematicFlowService.isPlayerInFlyingCatchZone(player, mount);
            }
            if (stillInContact && tryCompleteMountSwitch(server, cinematic, player, mount)) {
                return;
            }
            if (!stillInContact) {
                CompanionCinematicMovementService.moveTowardMountContact(
                        cinematic, mount, player, currentVehicle);
                if (mode.isRescue()
                        && CompanionCinematicLandingService.distanceToGround(player) <= 5.0) {
                    CompanionMountCinematicFlowService.holdFallingPlayerForCatch(player);
                }
            }
            cinematic.incrementSwitchAge();
            if (cinematic.switchAge() > MOUNT_SWITCH_FAIL_TICKS) {
                CompanionMountCinematicFlowService.softLandFailedMountCatch(player, mount);
                CompanionMountCinematicFlowService.finishMountCinematic(cinematic, player, mount, false);
            }
            return;
        }
        if (CompanionMountCinematicFlowService.correctExternalCinematicPull(cinematic, mount, player)) {
            cinematic.incrementSwitchAge();
            return;
        }
        if (!cinematic.switched() && mode.isAirToGroundSwitch()) {
            if (!retireSourceBeforeBoarding(cinematic, player, mount)) {
                cinematic.incrementSwitchAge();
                return;
            }
            cinematic.setSwitched();
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
            CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
            cinematic.incrementSwitchAge();
            return;
        }
        boolean urgentAirRescue = mode.isFlyingRescue() && (!Double.isNaN(cinematic.catchY()) ? player.getY() <= cinematic.catchY() + 2.5 : CompanionCinematicLandingService.distanceToGround(player) <= 12.0);
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
        if (airRescue && !Double.isNaN(cinematic.catchY()) && player.getY() > cinematic.catchY() + 2.5 && !inFlyingCatchZone && !physicalCatchContact && !CompanionMountCinematicFlowService.isTouchingMountCollision(player, mount, currentVehicle)) {
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
            CompanionCinematicPositionService.stabilizeAirRescueMount(mount, player);
        }
        Entity contactVehicle = !mode.isRescue() ? currentVehicle : (cinematic.age() >= 8 ? currentVehicle : null);
        rescueAtWaitPoint = !mode.isRescue()
                || groundRescue && cinematic.waitLocked()
                || mount.position().distanceTo(CompanionCinematicLandingService.cinematicTarget(cinematic, mount.level(), player, mount)) <= 2.25;
        if (mode.isRescue() && !rescueAtWaitPoint) {
            if (airRescue) {
                CompanionCinematicPositionService.lockMountAtFlyingWait(cinematic, mount, player);
            } else {
                CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, currentVehicle);
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

    private static boolean tryCompleteMountSwitch(MinecraftServer server, PendingMountCinematic cinematic,
                                                  ServerPlayer player, LivingEntity mount) {
        long totalStartedAt = System.nanoTime();
        long phaseStartedAt = totalStartedAt;
        CompanionCinematicMovementService.snapWalkMountToGround(cinematic, mount);
        double snapMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        Entity previousVehicle = player.getVehicle();
        cinematic.captureRideHandoff(previousVehicle);
        if (cinematic.rideHandoff().compatibleWith(CompanionMoveType.FLY)
                && cinematic.moveType() == CompanionMoveType.FLY) {
            applyCompatibleRideState(cinematic, mount);
        }
        double handoffMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        if (!retireSourceBeforeBoarding(cinematic, player, mount)) {
            logSwitchPerformance(cinematic, player, mount, "retire_before_failed", totalStartedAt,
                    snapMs, handoffMs, elapsedMs(phaseStartedAt), 0.0, 0.0, 0.0, 0.0);
            return false;
        }
        double retireBeforeMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        CobblemonCompat.prepareRideHandoff(mount, cinematic.rideHandoff());
        double controllerPrepareMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        if (!CompanionMountCinematicFlowService.startRidingAfterContact(player, mount, cinematic.mode())) {
            double rideMs = elapsedMs(phaseStartedAt);
            if (cinematic.mode().requiresExactRideContact() && !cinematic.contactLatched()) {
                CompanionCinematicMovementService.moveTowardMountContact(cinematic, mount, player, previousVehicle);
            }
            logSwitchPerformance(cinematic, player, mount, "ride_failed", totalStartedAt,
                    snapMs, handoffMs, retireBeforeMs, controllerPrepareMs, rideMs, 0.0, 0.0);
            return false;
        }
        double rideMs = elapsedMs(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        if (cinematic.rideSource().type() == RideHandoffService.SourceType.SABLE) {
            cinematic.incrementDestinationRideStableTicks();
            if (!cinematic.switched()) {
                cinematic.setSwitched();
            }
            logSableDestinationVerification(cinematic, player, mount);
            return true;
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
                snapMs, handoffMs, retireBeforeMs, controllerPrepareMs, rideMs,
                retireAfterMs, finishMs);
        return true;
    }

    private static double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0;
    }

    private static void logSableDestinationVerification(PendingMountCinematic cinematic,
                                                         ServerPlayer player, LivingEntity mount) {
        int stableTicks = cinematic.destinationRideStableTicks();
        if (stableTicks != 1 && stableTicks != 2 && stableTicks != 3) {
            return;
        }
        FindMeDebugLogger.info("vehicle-handoff",
                "phase=SABLE_SOURCE_DESTINATION_VERIFY player={} source={} destination={} stableTicks={} riding={} passenger={}",
                player.getUUID(), cinematic.rideSource().uuid(), mount.getUUID(), stableTicks,
                player.getVehicle() == mount, mount.hasPassenger(player));
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
        boolean requiresDetachFirst = source.airborne()
                && source.type() != RideHandoffService.SourceType.SABLE;
        if (cinematic.mode().isAirToAirSwitch()) {
            requiresDetachFirst = false;
        }
        return !requiresDetachFirst || startSourceRetirement(cinematic, player, target, true);
    }

    static void beginSimultaneousAirToGroundRetirement(PendingMountCinematic cinematic, ServerPlayer player,
                                                        LivingEntity target) {
        if (!cinematic.mode().isAirToGroundSwitch() || cinematic.sourceRetirementStarted()) {
            return;
        }
        if (!startSourceRetirement(cinematic, player, target, true,
                "mount-cinematic:simultaneous_air_to_ground")) {
            FindMeDebugLogger.info("ride-handoff",
                    "phase=SIMULTANEOUS_RETIREMENT_START_FAILED player={} sourceType={} source={} target={} mode={}",
                    player.getUUID(), cinematic.rideSource().type(), cinematic.rideSource().uuid(),
                    target.getUUID(), cinematic.mode());
        }
    }

    static void applyAirToAirFlightState(PendingMountCinematic cinematic, LivingEntity mount) {
        if (!cinematic.rideHandoff().compatibleWith(CompanionMoveType.FLY)
                || cinematic.moveType() != CompanionMoveType.FLY || CobblemonCompat.isPokemon(mount)) {
            return;
        }
        applyCompatibleRideState(cinematic, mount);
    }

    static boolean applyCompatibleRideState(PendingMountCinematic cinematic, LivingEntity mount) {
        if (cinematic == null || mount == null || CobblemonCompat.isPokemon(mount)
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
        if (CobblemonCompat.isPokemon(mount)) {
            return;
        }
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
        return startSourceRetirement(cinematic, player, target, beforeBoarding,
                "mount-cinematic:contact_handoff");
    }

    private static boolean startSourceRetirement(PendingMountCinematic cinematic, ServerPlayer player,
                                                  LivingEntity target, boolean beforeBoarding, String reason) {
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
                source.type() == RideHandoffService.SourceType.COBBLEMON ? null
                        : com.kuzhi.findme.server.data.CompanionDataService.data(player);
        boolean started = RideHandoffService.beginRetirement(player, data, source,
                target.getUUID(), reason);
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
