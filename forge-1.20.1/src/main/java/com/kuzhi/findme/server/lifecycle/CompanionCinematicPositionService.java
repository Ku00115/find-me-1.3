package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicOrientationHelper;

import com.kuzhi.findme.server.lifecycle.PendingMountCinematic;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionCinematicPositionService {
    private CompanionCinematicPositionService() {
    }

    public static void stabilizeAirRescueMount(PendingMountCinematic cinematic,
                                               LivingEntity mount, ServerPlayer player) {
        AABB contactBox = com.kuzhi.findme.server.profile.CompanionMountContactService.mountContactBox(mount);
        double contactTopOffset = Math.max(0.35, contactBox.maxY - mount.getY());
        double catchY = player.getBoundingBox().minY + 0.2 - contactTopOffset;
        mount.moveTo(player.getX(), catchY, player.getZ(), player.getYRot(), 0.0f);
        mount.setPos(player.getX(), catchY, player.getZ());
        CompanionCinematicOrientationHelper.faceYaw(mount, player.getYRot());
        mount.setDeltaMovement(Vec3.ZERO);
        mount.fallDistance = 0.0f;
        player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
    }

    public static double directFlyingCatchY(ServerPlayer player) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return player.getY() - 1.2;
        }
        if (!CompanionCinematicLandingService.hasReliableLandingBelow(serverLevel, player)) {
            return player.getY() - 1.2;
        }
        BlockPos landing = CompanionCinematicLandingService.rescueAnchor(serverLevel, player);
        return (double)landing.getY() + CompanionCinematicLandingService.flyingCatchHeight(player);
    }

    public static void placeFlyingRescueAtLanding(ServerPlayer player, LivingEntity mount, double catchY) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos landing = CompanionCinematicLandingService.rescueAnchor(serverLevel, player);
        double x = (double)landing.getX() + 0.5;
        double z = (double)landing.getZ() + 0.5;
        Vec3 look = player.position().subtract(x, catchY, z);
        float yaw = look.lengthSqr() < 0.001 ? player.getYRot() : CompanionCinematicOrientationHelper.yawTowardStable(mount, look);
        mount.moveTo(x, catchY, z, yaw, 0.0f);
        mount.setPos(x, catchY, z);
        CompanionCinematicOrientationHelper.faceYaw(mount, yaw);
        mount.setXRot(0.0f);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.setNoGravity(true);
        mount.fallDistance = 0.0f;
        mount.invulnerableTime = Math.max(mount.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        mount.hurtMarked = true;
        CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        if (mount instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    public static void keepFlyingRescueAboveLanding(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
        Level level;
        if (!cinematic.mode().isFlyingRescue() || !((level = player.level()) instanceof ServerLevel serverLevel)) {
            return;
        }
        double minY = CompanionCinematicLandingService.hasReliableLandingBelow(serverLevel, player)
                ? (double)CompanionCinematicLandingService.predictedLanding(serverLevel, player).getY() + 1.2
                : player.getY() - 1.2;
        if (mount.getY() < minY) {
            mount.moveTo(mount.getX(), minY, mount.getZ(), mount.getYRot(), mount.getXRot());
            mount.setPos(mount.getX(), minY, mount.getZ());
            mount.setDeltaMovement(mount.getDeltaMovement().x, Math.max(0.0, mount.getDeltaMovement().y), mount.getDeltaMovement().z);
            mount.fallDistance = 0.0f;
            mount.hurtMarked = true;
        }
    }

    public static void lockMountAtFlyingWait(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
        Level playerLevel = player.level();
        if (!(playerLevel instanceof ServerLevel level) || Double.isNaN(cinematic.catchY())) {
            mount.setDeltaMovement(Vec3.ZERO);
            return;
        }
        BlockPos landing = CompanionCinematicLandingService.rescueAnchor(level, player);
        Vec3 waitPos = new Vec3((double) landing.getX() + 0.5, cinematic.catchY(),
                (double) landing.getZ() + 0.5);
        cinematic.lockWait(waitPos);
        mount.moveTo(waitPos.x, waitPos.y, waitPos.z, mount.getYRot(), mount.getXRot());
        mount.setPos(waitPos.x, waitPos.y, waitPos.z);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.setNoGravity(true);
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        cinematic.rememberPosition(mount.position());
        CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        if (mount instanceof Mob mob) {
            mob.setNoAi(!CompanionAnimationHelper.keepsFlyingAnimationWithActiveAi(mount));
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    public static void holdFlyingRescueHover(PendingMountCinematic cinematic,
                                              LivingEntity mount, ServerPlayer player) {
        Vec3 hoverPosition = CompanionCinematicLandingService.cinematicTarget(
                cinematic, player.level(), player, mount);
        if (hoverPosition == null) {
            return;
        }
        mount.moveTo(hoverPosition.x, hoverPosition.y, hoverPosition.z,
                mount.getYRot(), 0.0f);
        mount.setPos(hoverPosition.x, hoverPosition.y, hoverPosition.z);
        mount.setXRot(0.0f);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.setNoGravity(true);
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        cinematic.rememberPosition(hoverPosition);
        CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        if (mount instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    public static void stabilizeAirRescueMount(LivingEntity mount, ServerPlayer player) {
        stabilizeAirRescueMount(null, mount, player);
    }

    public static void holdFlyingRescueHover(PendingMountCinematic cinematic, LivingEntity mount,
                                              Vec3 hoverPosition) {
        if (hoverPosition == null) {
            return;
        }
        mount.moveTo(hoverPosition.x, hoverPosition.y, hoverPosition.z, mount.getYRot(), 0.0f);
        mount.setPos(hoverPosition.x, hoverPosition.y, hoverPosition.z);
        mount.setXRot(0.0f);
        mount.setDeltaMovement(Vec3.ZERO);
        mount.setNoGravity(true);
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        cinematic.rememberPosition(hoverPosition);
        CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        if (mount instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    public static void lockMountAtGroundWait(PendingMountCinematic cinematic, LivingEntity mount, ServerPlayer player) {
        Vec3 waitPos;
        boolean followPredictedLanding = cinematic.mode().isGroundOrWaterRescue()
                && CompanionCinematicLandingService.distanceToGround(player) > 8.0;
        if (!cinematic.waitLocked() || followPredictedLanding) {
            Level level;
            if (cinematic.presentationMoveType() == CompanionMoveType.WALK && (level = player.level()) instanceof ServerLevel serverLevel) {
                waitPos = Vec3.atBottomCenterOf((Vec3i)CompanionCinematicLandingService.rescueAnchor(serverLevel, player)).add(0.0, CompanionCinematicLandingService.rescueGroundYOffset(cinematic), 0.0);
            } else {
                waitPos = mount.position();
            }
            if (cinematic.presentationMoveType() == CompanionMoveType.WALK) {
                double groundY = CompanionCinematicLandingService.walkGroundY(mount.level(), waitPos.x, waitPos.y, waitPos.z, waitPos.y);
                waitPos = new Vec3(waitPos.x, groundY + CompanionCinematicLandingService.rescueGroundYOffset(cinematic), waitPos.z);
            }
            cinematic.updateWaitPosition(waitPos);
        }
        if ((waitPos = cinematic.waitPosition()) != null && mount.position().distanceToSqr(waitPos) > 0.0025) {
            mount.setPos(waitPos.x, waitPos.y, waitPos.z);
        } else if (cinematic.presentationMoveType() == CompanionMoveType.WALK) {
            double groundY = CompanionCinematicLandingService.walkGroundY(mount.level(), mount.getX(), mount.getY(), mount.getZ(), mount.getY());
            if (Math.abs((groundY += CompanionCinematicLandingService.rescueGroundYOffset(cinematic)) - mount.getY()) > 0.001) {
                mount.setPos(mount.getX(), groundY, mount.getZ());
            }
        }
        mount.setDeltaMovement(Vec3.ZERO);
        mount.setNoGravity(cinematic.presentationMoveType() != CompanionMoveType.WALK);
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        cinematic.rememberPosition(mount.position());
        if (mount instanceof Mob mob) {
            mob.setNoAi(!CompanionAnimationHelper.keepsFlyingAnimationWithActiveAi(mount));
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }
}
