package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CompanionSpawnPlacementService {
    private CompanionSpawnPlacementService() {
    }

    public static BlockPos findSummonSpot(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
        if (moveType == CompanionMoveType.SWIM) {
            Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(player.serverLevel(), player.blockPosition());
            if (water.isPresent()) {
                return water.get();
            }
        }
        Vec3 base = kind == CompanionKind.MOUNT ? player.position().add(player.getLookAngle().normalize().scale(2.0)) : player.position().add(0.0, 0.0, 1.0);
        return CompanionPlacementFinder.findSafe(player.serverLevel(), BlockPos.containing((Position)base)).orElse(player.blockPosition());
    }

    public static BlockPos findBurrowSummonSpot(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
        if (moveType == CompanionMoveType.SWIM) {
            return findSummonSpot(player, kind, moveType);
        }
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 0.001) {
            forward = new Vec3(0.0, 0.0, 1.0);
        } else {
            forward = forward.normalize();
        }
        double[] distances = kind == CompanionKind.MOUNT ? new double[]{2.2, 2.9, 3.6, 4.4} : new double[]{1.6, 2.2, 2.8, 3.5};
        double[] angles = new double[]{0.0, 28.0, -28.0, 58.0, -58.0, 96.0, -96.0, 180.0};
        for (double distance : distances) {
            for (double angle : angles) {
                Vec3 direction = rotateY(forward, Math.toRadians(angle));
                BlockPos base = BlockPos.containing((Position)player.position().add(direction.scale(distance)));
                Optional<BlockPos> safe = CompanionPlacementFinder.findSafe(player.serverLevel(), base);
                if (safe.isPresent()) {
                    return safe.get();
                }
            }
        }
        return findSummonSpot(player, kind, moveType);
    }

    public static BlockPos findBurrowMountRideSpot(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return CompanionPlacementFinder.findSafe(player.serverLevel(), feet).orElse(feet);
    }

    public static BlockPos findArrivalSpawn(ServerPlayer player, CompanionMoveType moveType, boolean rescue) {
        return findArrivalSpawn(player, moveType, rescue, "", null);
    }

    public static BlockPos findArrivalSpawn(ServerPlayer player, CompanionMoveType moveType, boolean rescue, String entityType) {
        return findArrivalSpawn(player, moveType, rescue, entityType, null);
    }

    public static BlockPos findArrivalSpawn(ServerPlayer player, CompanionMoveType moveType, boolean rescue,
                                            String entityType, RescueFlightMode rescueFlightMode) {
        Vec3 ahead;
        Vec3 look = player.getLookAngle().normalize();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        horizontal = horizontal.normalize();
        if (moveType == CompanionMoveType.FLY) {
            if (rescue) {
                BlockPos landing = CompanionCinematicLandingService.rescueAnchor(player.serverLevel(), player);
                if (rescueFlightMode == RescueFlightMode.LANDING_SUMMON) {
                    return landing;
                }
                double catchY = CompanionCinematicLandingService.flyingHoverTarget(
                        player.serverLevel(), player).y;
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
                double horizontalDistance = CompanionRescuePlanner.plan(player).approachDistance(true);
                Vec3 start = new Vec3((double)landing.getX() + 0.5, catchY, (double)landing.getZ() + 0.5)
                        .add(direction.scale(horizontalDistance));
                return BlockPos.containing((Position)start);
            }
            // Normal flying summons begin behind the player and approach into view.
            Vec3 side = new Vec3(-horizontal.z, 0.0, horizontal.x).scale(player.getRandom().nextBoolean() ? 1.0 : -1.0);
            Vec3 arrival = player.position().subtract(horizontal.scale(22.0)).add(side.scale(6.0)).add(0.0, 5.0, 0.0);
            return BlockPos.containing((Position)arrival);
        }
        if (moveType == CompanionMoveType.SWIM) {
            BlockPos origin = rescue ? CompanionCinematicLandingService.rescueAnchor(player.serverLevel(), player) : player.blockPosition();
            Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(player.serverLevel(), origin);
            if (water.isPresent()) {
                return water.get();
            }
            if (rescue && !CompanionCinematicLandingService.hasReliableLandingBelow(player.serverLevel(), player)) {
                return origin;
            }
            return findSummonSpot(player, CompanionKind.MOUNT, moveType);
        }
        if (rescue) {
            BlockPos landing = CompanionCinematicLandingService.rescueAnchor(player.serverLevel(), player);
            if (CompanionRescuePlanner.plan(player).urgency()
                    == CompanionRescuePlanner.Urgency.LANDING_SUMMON) {
                return walkSurfacePos(player.serverLevel(), landing, landing.getY());
            }
            double runDistance = CompanionRescuePlanner.plan(player).approachDistance(false);
            ahead = Vec3.atBottomCenterOf((Vec3i)landing).subtract(horizontal.scale(runDistance));
        } else {
            ahead = player.position().subtract(horizontal.scale(22.0));
        }
        BlockPos base = BlockPos.containing((Position)ahead);
        return CompanionPlacementFinder.findSafe(player.serverLevel(), base).orElseGet(() -> walkSurfacePos(player.serverLevel(), base, base.getY()));
    }

    public static BlockPos walkSurfacePos(ServerLevel level, BlockPos base, double fallbackY) {
        double y = CompanionCinematicLandingService.walkGroundY((Level)level, (double)base.getX() + 0.5, fallbackY, (double)base.getZ() + 0.5, fallbackY);
        return BlockPos.containing((double)base.getX(), y, (double)base.getZ());
    }

    public static boolean hasOpenBox(ServerLevel level, BlockPos pos, double width, double height) {
        return CompanionPlacementFinder.hasOpenBox(level, pos, width, height);
    }

    public static boolean hasOpenEntitySpace(ServerLevel level, LivingEntity entity, double x, double y, double z) {
        return CompanionPlacementFinder.hasOpenEntitySpace(level, entity, x, y, z);
    }

    public static Optional<BlockPos> findOpenEntitySpace(ServerLevel level, LivingEntity entity, BlockPos origin) {
        return CompanionPlacementFinder.findOpenEntitySpace(level, entity, origin);
    }

    private static Vec3 rotateY(Vec3 vec, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos - vec.z * sin, 0.0, vec.x * sin + vec.z * cos).normalize();
    }
}

