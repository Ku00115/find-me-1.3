package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicSpeedService;

import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CompanionCinematicMovementService {
    private CompanionCinematicMovementService() {
    }

    public static void moveTowardCinematicTarget(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player, Vec3 target, double distance) {
        double speed = CompanionCinematicSpeedService.cinematicSpeed(cinematic, mount, player, distance);
        moveTowardTarget(cinematic, mount, player, target, Math.min(speed, distance), speed, false);
    }

    public static void moveTowardMountContact(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player, Entity currentVehicle) {
        Vec3 target = mountContactTarget(cinematic, player, currentVehicle);
        double distance = mount.position().distanceTo(target);
        if (distance < 0.15) {
            snapWalkMountToGround(cinematic, mount);
            mount.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double speed = Math.min(CompanionCinematicSpeedService.cinematicSpeed(cinematic, mount, player, distance), distance);
        moveTowardTarget(cinematic, mount, player, target, speed, speed, true);
    }

    public static double moveMountedRideHome(LivingEntity mount, ServerPlayer player,
                                             CompanionMoveType moveType, Vec3 target, int age,
                                             boolean approachingHome) {
        Vec3 offset = target.subtract(mount.position());
        double distance = offset.length();
        if (distance < 1.0E-4) {
            mount.setDeltaMovement(Vec3.ZERO);
            return 0.0;
        }
        Vec3 direction = offset.normalize();
        double speed = CompanionCinematicSpeedService.rideHomeSpeed(moveType, distance, age,
                approachingHome);
        Vec3 step = direction.scale(speed);
        Vec3 motionDirection = direction;
        if (moveType == CompanionMoveType.WALK) {
            Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
            if (horizontal.lengthSqr() < 0.001) {
                horizontal = new Vec3(0.0, 0.0, 1.0);
            }
            horizontal = horizontal.normalize();
            moveMountedWalkStep(mount, horizontal, speed);
            motionDirection = horizontal;
        } else if (moveType == CompanionMoveType.FLY && mount.level() instanceof ServerLevel level) {
            double climbSpeed = Math.max(speed,
                    CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 8.0));
            boolean forwardOpen = CompanionPlacementFinder.hasOpenEntitySpace(level, mount,
                    mount.getX() + step.x, mount.getY() + step.y, mount.getZ() + step.z);
            boolean upwardOpen = CompanionPlacementFinder.hasOpenEntitySpace(level, mount,
                    mount.getX(), mount.getY() + climbSpeed, mount.getZ());
            step = selectMountedFlightStep(direction, speed, forwardOpen, upwardOpen, climbSpeed);
            motionDirection = step.lengthSqr() < 1.0E-6 ? direction : step.normalize();
            mount.move(MoverType.SELF, step);
        } else {
            mount.move(MoverType.SELF, step);
        }
        Vec3 facingDirection = motionDirection.horizontalDistanceSqr() < 1.0E-4
                ? direction : motionDirection;
        float yaw = CompanionCinematicOrientationHelper.yawTowardStable(mount, facingDirection);
        CompanionCinematicOrientationHelper.faceYaw(mount, yaw);
        if (moveType == CompanionMoveType.FLY) {
            mount.setXRot(CompanionCinematicOrientationHelper.pitchToward(motionDirection));
            CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        }
        mount.setDeltaMovement(motionDirection.scale(speed * (moveType == CompanionMoveType.FLY ? 0.35 : 0.55)));
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        player.fallDistance = 0.0f;
        return Math.sqrt(step.x * step.x + step.z * step.z);
    }

    static Vec3 selectMountedFlightStep(Vec3 direction, double speed, boolean forwardOpen,
                                        boolean upwardOpen, double climbSpeed) {
        Vec3 safeDirection = direction == null || direction.lengthSqr() < 1.0E-6
                ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        if (!forwardOpen && upwardOpen) {
            return new Vec3(0.0, Math.max(speed, climbSpeed), 0.0);
        }
        return safeDirection.scale(speed);
    }

    private static void moveMountedWalkStep(LivingEntity mount, Vec3 horizontal, double speed) {
        float yaw = CompanionCinematicOrientationHelper.yawTowardStable(mount, horizontal);
        CompanionCinematicOrientationHelper.faceYaw(mount, yaw);
        mount.setNoGravity(false);
        Vec3 previousMotion = mount.getDeltaMovement();
        Vec3 motion = new Vec3(horizontal.x * speed, Math.min(0.0, previousMotion.y),
                horizontal.z * speed);
        mount.move(MoverType.SELF, motion);
        mount.setDeltaMovement(horizontal.x * speed, mount.getDeltaMovement().y,
                horizontal.z * speed);
        mount.hasImpulse = true;
    }

    private static void moveTowardTarget(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player, Vec3 target, double stepDistance, double speed, boolean forceFlyingPose) {
        if (stepDistance > 1.0E-4) {
            CompanionArrivalSequenceService.revealForCinematicMovement(mount);
        }
        double groundOffset = CompanionCinematicLandingService.rescueGroundYOffset(cinematic);
        moveArrivalStep(mount, cinematic.presentationMoveType(), target, stepDistance, speed,
                forceFlyingPose || cinematic.mode().isFlyingRescue(), groundOffset, cinematic);
        CompanionCinematicPositionService.keepFlyingRescueAboveLanding(cinematic, mount, player);
        cinematic.rememberPosition(mount.position());
    }

    private static void moveArrivalStep(LivingEntity living, CompanionMoveType moveType, Vec3 target,
                                        double stepDistance, double speed, boolean forceFlyingPose,
                                        double groundOffset, PendingMountCinematic cinematic) {
        Vec3 direction = target.subtract(living.position()).normalize();
        float lookYaw = CompanionCinematicOrientationHelper.yawTowardStable(living, direction);
        if (moveType == CompanionMoveType.FLY) {
            Vec3 next = living.position().add(direction.scale(stepDistance));
            float pitch = forceFlyingPose
                    ? CompanionCinematicOrientationHelper.pitchToward(direction) : living.getXRot();
            living.moveTo(next.x, next.y, next.z, lookYaw, pitch);
            living.setPos(next.x, next.y, next.z);
            living.setXRot(pitch);
            if (forceFlyingPose) CompanionAnimationHelper.forceFlyingAnimationPose(living);
        } else if (moveType == CompanionMoveType.WALK) {
            CompanionAnimationHelper.forceGroundMovingPose(living);
            Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
            if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0.0, 0.0, 1.0);
            if (cinematic != null) {
                walkCinematicStep(cinematic, living, direction, stepDistance, target, lookYaw);
            } else {
                moveWalkStep(living, horizontal.normalize(), stepDistance, target, groundOffset, lookYaw);
            }
        } else {
            living.move(MoverType.SELF, direction.scale(stepDistance));
        }
        CompanionCinematicOrientationHelper.faceYaw(living, lookYaw);
        living.setDeltaMovement(direction.scale(moveType == CompanionMoveType.FLY ? speed * 0.35 : speed * 0.55));
        living.fallDistance = 0.0f;
        living.hasImpulse = true;
    }

    private static void walkCinematicStep(PendingMountCinematic cinematic, LivingEntity mount,
                                          Vec3 direction, double speed, Vec3 target,
                                          float lookYaw) {
        Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
        if (horizontal.lengthSqr() < 0.001) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        horizontal = horizontal.normalize();
        double yOffset = CompanionCinematicLandingService.rescueGroundYOffset(cinematic);
        double nextX = mount.getX() + horizontal.x * speed;
        double nextZ = mount.getZ() + horizontal.z * speed;
        moveWalkStep(mount, horizontal, speed, target, yOffset, lookYaw);
    }

    private static void moveWalkStep(LivingEntity mount, Vec3 horizontal, double speed, Vec3 target,
                                     double yOffset, float lookYaw) {
        double nextX = mount.getX() + horizontal.x * speed;
        double nextZ = mount.getZ() + horizontal.z * speed;
        double nextY = CompanionCinematicLandingService.walkStepGroundY(
                mount.level(), nextX, mount.getY() - yOffset, nextZ) + yOffset;
        mount.moveTo(nextX, nextY, nextZ, lookYaw, mount.getXRot());
        mount.setPos(nextX, nextY, nextZ);
    }

    public static void snapWalkMountToGround(PendingMountCinematic cinematic, LivingEntity mount) {
        if (cinematic.presentationMoveType() != CompanionMoveType.WALK) {
            return;
        }
        double groundY = CompanionCinematicLandingService.walkGroundY(mount.level(), mount.getX(), mount.getY(), mount.getZ(), mount.getY());
        groundY += CompanionCinematicLandingService.rescueGroundYOffset(cinematic);
        if (Math.abs(groundY - mount.getY()) > 0.001) {
            mount.moveTo(mount.getX(), groundY, mount.getZ(), mount.getYRot(), mount.getXRot());
            mount.setPos(mount.getX(), groundY, mount.getZ());
        }
        mount.setDeltaMovement(Vec3.ZERO);
        mount.fallDistance = 0.0f;
    }

    private static Vec3 mountContactTarget(PendingMountCinematic cinematic, ServerPlayer player, Entity currentVehicle) {
        Level level;
        if (currentVehicle != null && currentVehicle != player && !cinematic.mode().isAirToGroundSwitch()) {
            Vec3 center = currentVehicle.getBoundingBox().getCenter();
            if (cinematic.presentationMoveType() == CompanionMoveType.WALK) {
                return new Vec3(center.x, currentVehicle.getY(), center.z);
            }
            return center;
        }
        Vec3 center = player.getBoundingBox().getCenter();
        if (cinematic.mode().isFlyingRescue()) {
            return center.add(0.0, -0.35, 0.0);
        }
        if (cinematic.presentationMoveType() == CompanionMoveType.WALK && (level = player.level()) instanceof ServerLevel level2) {
            BlockPos landing = CompanionCinematicLandingService.predictedLanding(level2, player);
            return Vec3.atBottomCenterOf((Vec3i)landing).add(0.0, CompanionCinematicLandingService.rescueGroundYOffset(cinematic), 0.0);
        }
        return center;
    }
}
