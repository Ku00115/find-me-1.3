package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

final class CompanionEscortMovementService {
    private static final int NAVIGATION_REFRESH_TICKS = 10;
    private static final double NAVIGATION_TARGET_EPSILON_SQR = 2.25;
    private static final Map<Mob, NavigationRequest> NAVIGATION_REQUESTS = new WeakHashMap<>();

    private CompanionEscortMovementService() {
    }

    static BlockPos spawnPos(ServerPlayer player, CompanionMoveType moveType) {
        Vec3 look = horizontal(player.getLookAngle()).normalize();
        if (look.lengthSqr() < 0.001) {
            look = horizontal(Vec3.directionFromRotation(0.0f, player.getYRot())).normalize();
        }
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        Vec3 pos = player.position().add(side.scale(5.0)).subtract(look.scale(2.0));
        if (moveType == CompanionMoveType.FLY) {
            pos = pos.add(0.0, Math.max(3.0, player.getBbHeight() + 2.0), 0.0);
            return BlockPos.containing(pos.x, pos.y, pos.z);
        }
        return CompanionPlacementFinder.findSafe(player.serverLevel(), BlockPos.containing(pos.x, pos.y, pos.z))
                .orElseGet(() -> CompanionSpawnPlacementService.findSummonSpot(player, CompanionKind.COMPANION, moveType));
    }

    static Vec3 followPosition(ServerPlayer player, Entity anchor, LivingEntity escort, CompanionMoveType moveType, int sideSign) {
        return followPosition(player, anchor, escort, moveType, sideSign, 0, 1);
    }

    static Vec3 followPosition(ServerPlayer player, Entity anchor, LivingEntity escort, CompanionMoveType moveType,
                               int sideSign, int formationIndex, int formationSize) {
        Vec3 look = horizontal(anchor.getLookAngle()).normalize();
        if (look.lengthSqr() < 0.001) {
            look = horizontal(player.getLookAngle()).normalize();
        }
        if (look.lengthSqr() < 0.001) {
            look = horizontal(Vec3.directionFromRotation(0.0f, player.getYRot())).normalize();
        }
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        double spacing = Mth.clamp(2.8 + Math.max(escort.getBbWidth(), anchor.getBbWidth()) * 1.15, 3.2, 11.0);
        int count = Math.max(1, formationSize);
        int slot = Mth.clamp(formationIndex, 0, count - 1);
        double lateral = count == 1 ? sideSign * spacing
                : (slot - (count - 1) * 0.5) * spacing * 1.2;
        double rear = spacing * (0.75 + Math.abs(slot - (count - 1) * 0.5) * 0.18);
        Vec3 base = anchor.position().subtract(look.scale(rear)).add(side.scale(lateral));
        if (moveType == CompanionMoveType.FLY) {
            double y = anchor.getY() + Mth.clamp(anchor.getBbHeight() * 0.35 + escort.getBbHeight() * 0.18, 0.8, 5.0);
            return new Vec3(base.x, y, base.z);
        }
        Vec3 ground = findGroundFollowPosition(player, base, anchor.getY(), escort);
        if (moveType == CompanionMoveType.SWIM) {
            Vec3 water = findWaterFollowPosition(player, ground, escort);
            if (water != null) return water;
        }
        return ground;
    }

    static Vec3 followPosition(ServerPlayer player, Entity anchor, LivingEntity escort, CompanionMoveType moveType,
                               CompanionFormationPlanner.Offset offset) {
        Vec3 look = horizontal(anchor.getLookAngle()).normalize();
        if (look.lengthSqr() < 0.001) look = horizontal(player.getLookAngle()).normalize();
        if (look.lengthSqr() < 0.001) {
            look = horizontal(Vec3.directionFromRotation(0.0f, player.getYRot())).normalize();
        }
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        Vec3 base = anchor.position().subtract(look.scale(offset.rear())).add(side.scale(offset.lateral()));
        if (moveType == CompanionMoveType.FLY) {
            double y = anchor.getY() + Mth.clamp(anchor.getBbHeight() * 0.35
                    + escort.getBbHeight() * 0.18, 0.8, 5.0) + offset.vertical();
            return new Vec3(base.x, y, base.z);
        }
        Vec3 ground = findGroundFollowPosition(player, base, anchor.getY(), escort);
        if (moveType == CompanionMoveType.SWIM) {
            Vec3 water = findWaterFollowPosition(player, ground, escort);
            if (water != null) return water;
        }
        return ground;
    }

    static void control(LivingEntity living, Vec3 target, CompanionMoveType moveType) {
        Vec3 delta = target.subtract(living.position());
        double distance = delta.length();
        if (moveType != CompanionMoveType.FLY && living instanceof Mob mob) {
            boolean restoringPhysicalControl = living.noPhysics || living.isNoGravity()
                    || living.getPose() == net.minecraft.world.entity.Pose.FALL_FLYING;
            living.noPhysics = false;
            living.setNoGravity(false);
            if (restoringPhysicalControl) {
                CompanionAnimationHelper.restoreAnimationControl(living);
            }
            mob.setTarget(null);
            mob.setAggressive(false);
            if (distance < 1.15) {
                mob.getNavigation().stop();
                NAVIGATION_REQUESTS.remove(mob);
                living.setDeltaMovement(living.getDeltaMovement().scale(0.45));
                living.fallDistance = 0.0f;
                return;
            }
            NavigationRequest previous = NAVIGATION_REQUESTS.get(mob);
            boolean targetChanged = previous == null || previous.target().distanceToSqr(target) > NAVIGATION_TARGET_EPSILON_SQR;
            boolean refreshDue = previous == null || living.tickCount - previous.tick() >= NAVIGATION_REFRESH_TICKS;
            if (targetChanged || refreshDue || mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(target.x, target.y, target.z, 1.15);
                NAVIGATION_REQUESTS.put(mob, new NavigationRequest(target, living.tickCount));
            }
            living.fallDistance = 0.0f;
            return;
        }
        if (moveType != CompanionMoveType.FLY) {
            NAVIGATION_REQUESTS.remove(living);
            living.noPhysics = false;
            living.setNoGravity(false);
            living.fallDistance = 0.0f;
            return;
        }
        NAVIGATION_REQUESTS.remove(living);
        softControl(living);
        living.noPhysics = true;
        living.setNoGravity(true);
        CompanionAnimationHelper.forceFlyingAnimationPose(living);
        if (distance < 1.15) {
            living.setDeltaMovement(living.getDeltaMovement().scale(0.45));
            return;
        }
        double speed = Mth.clamp(0.16 + distance * 0.045, 0.34, 1.45);
        Vec3 motion = delta.normalize().scale(speed);
        living.move(MoverType.SELF, motion);
        living.setDeltaMovement(motion);
        living.setYRot(yaw(motion));
        living.setXRot(0.0f);
        living.hurtMarked = true;
        living.fallDistance = 0.0f;
        living.hasImpulse = true;
    }

    static void moveNearPlayer(ServerPlayer player, PlayerCompanionData data, LivingEntity living, Vec3 target,
                               CompanionMoveType moveType) {
        BlockPos pos = BlockPos.containing(target.x, target.y, target.z);
        LivingEntity moved = living.level() == player.level()
                ? living
                : CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), pos, player.getYRot(), 0.0f, false);
        moved.teleportTo(target.x, target.y, target.z);
        moved.moveTo(target.x, target.y, target.z, player.getYRot(), 0.0f);
        moved.setPos(target.x, target.y, target.z);
        moved.setDeltaMovement(Vec3.ZERO);
        moved.noPhysics = moveType == CompanionMoveType.FLY;
        moved.setNoGravity(moveType == CompanionMoveType.FLY);
        moved.fallDistance = 0.0f;
        data.setLastKnownPosition(moved.getUUID(), SavedPosition.of(moved.level(), moved.getX(), moved.getY(), moved.getZ(), moved.getYRot(), moved.getXRot()));
        CompanionDataService.save(player, data);
    }

    private static void softControl(LivingEntity living) {
        if (living instanceof Mob mob) {
            mob.setNoAi(false);
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
        }
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    private static Vec3 findGroundFollowPosition(ServerPlayer player, Vec3 base, double anchorY,
                                                 LivingEntity escort) {
        BlockPos probe = BlockPos.containing(base.x, anchorY, base.z);
        if (!player.serverLevel().hasChunkAt(probe)) return escort.position();
        double nearbyY = CompanionCinematicLandingService.walkGroundY(
                player.serverLevel(), base.x, anchorY, base.z, anchorY);
        BlockPos nearby = BlockPos.containing(base.x, nearbyY, base.z);
        if (CompanionPlacementFinder.isSafe(player.serverLevel(), nearby)
                && CompanionPlacementFinder.hasOpenEntitySpace(
                player.serverLevel(), escort, base.x, nearbyY, base.z)) {
            return new Vec3(base.x, nearbyY + 0.05, base.z);
        }

        int surfaceY = player.serverLevel().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(base.x), Mth.floor(base.z));
        BlockPos surface = new BlockPos(Mth.floor(base.x), surfaceY, Mth.floor(base.z));
        return CompanionPlacementFinder.findOpenEntitySpace(player.serverLevel(), escort, surface)
                .map(pos -> Vec3.atBottomCenterOf(pos).add(0.0, 0.05, 0.0))
                .orElseGet(escort::position);
    }

    private static Vec3 findWaterFollowPosition(ServerPlayer player, Vec3 ground,
                                                LivingEntity escort) {
        BlockPos origin = BlockPos.containing(ground.x, ground.y + 1.0, ground.z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = 5; dy >= -2; dy--) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (!player.serverLevel().hasChunkAt(candidate)
                                || !player.serverLevel().getFluidState(candidate).is(FluidTags.WATER)) continue;
                        double x = candidate.getX() + 0.5;
                        double y = candidate.getY() + 0.2;
                        double z = candidate.getZ() + 0.5;
                        AABB movedBox = escort.getBoundingBox().move(
                                x - escort.getX(), y - escort.getY(), z - escort.getZ());
                        if (!CompanionPlacementFinder.hasLoadedChunks(player.serverLevel(), movedBox)
                                || !player.serverLevel().noCollision(escort, movedBox)) {
                            continue;
                        }
                        double distance = candidate.distSqr(origin);
                        if (distance < bestDistance) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            if (best != null) break;
        }
        return best == null ? null : new Vec3(best.getX() + 0.5, best.getY() + 0.2, best.getZ() + 0.5);
    }

    private static float yaw(Vec3 direction) {
        Vec3 horizontal = horizontal(direction);
        if (horizontal.lengthSqr() < 0.001) {
            return 0.0f;
        }
        return (float)(Mth.atan2(horizontal.z, horizontal.x) * (180.0 / Math.PI)) - 90.0f;
    }

    private record NavigationRequest(Vec3 target, int tick) {
    }
}

