package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.server.lifecycle.PendingMountCinematic;

import com.kuzhi.findme.common.CompanionMoveType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;
import java.util.WeakHashMap;

public final class CompanionCinematicLandingService {
    private static final Map<ServerPlayer, LandingSample> LANDING_CACHE = new WeakHashMap<>();

    private CompanionCinematicLandingService() {
    }

    public static double distanceToGround(ServerPlayer player) {
        Level level = player.level();
        if (level instanceof ServerLevel serverLevel) {
            if (!hasReliableLandingBelow(serverLevel, player)) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.max(0.0, player.getY() - (double)predictedLanding(serverLevel, player).getY());
        }
        return Math.max(0.0, player.fallDistance);
    }

    public static double flyingCatchHeight(ServerPlayer player) {
        return CompanionRescuePlanner.plan(player).catchHeight();
    }

    public static void preloadChunkArea(ServerLevel level, BlockPos center, int radius) {
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int x = -radius; x <= radius; ++x) {
            for (int z = -radius; z <= radius; ++z) {
                level.getChunk(chunkX + x, chunkZ + z);
            }
        }
    }

    public static Vec3 cinematicTarget(PendingMountCinematic cinematic, Level level, ServerPlayer player) {
        return cinematicTarget(cinematic, level, player, null);
    }

    public static Vec3 cinematicTarget(PendingMountCinematic cinematic, Level level,
                                       ServerPlayer player, LivingEntity mount) {
        if (cinematic.mode().isRescue()) {
            if (cinematic.presentationMoveType() == CompanionMoveType.WALK && level instanceof ServerLevel serverLevel) {
                BlockPos landing = rescueAnchor(serverLevel, player);
                return Vec3.atBottomCenterOf((Vec3i)landing).add(0.0, rescueGroundYOffset(cinematic), 0.0);
            }
            if (cinematic.presentationMoveType() == CompanionMoveType.SWIM && level instanceof ServerLevel serverLevel) {
                BlockPos landing = rescueAnchor(serverLevel, player);
                double width = mount == null ? 1.0 : mount.getBbWidth() + 0.55;
                double height = mount == null ? 2.2 : mount.getBbHeight() + 0.25;
                java.util.Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(
                        serverLevel, landing, width, height);
                if (water.isPresent()) {
                    return Vec3.atBottomCenterOf(water.orElseThrow());
                }
                cinematic.useGroundedSwimFallback();
                return Vec3.atBottomCenterOf((Vec3i)landing)
                        .add(0.0, rescueGroundYOffset(cinematic), 0.0);
            }
            if (cinematic.moveType() == CompanionMoveType.FLY && cinematic.mode().isRescue()
                    && level instanceof ServerLevel serverLevel) {
                if (cinematic.rescueFlightMode() == RescueFlightMode.HOVER) {
                    Vec3 hover = flyingHoverTarget(serverLevel, player,
                            cinematic.rescueHoverPosition());
                    cinematic.setRescueHoverPosition(hover);
                    return hover;
                }
                if (!Double.isNaN(cinematic.catchY())) {
                    if (!cinematic.flyingRescueStaged() && cinematic.flyingStageTarget() != null) {
                        return cinematic.flyingStageTarget();
                    }
                    BlockPos landing = rescueAnchor(serverLevel, player);
                    return new Vec3((double) landing.getX() + 0.5, cinematic.catchY(),
                            (double) landing.getZ() + 0.5);
                }
            }
            if (cinematic.moveType() == CompanionMoveType.FLY && level instanceof ServerLevel serverLevel && !Double.isNaN(cinematic.catchY())) {
                return flyingCatchTarget(cinematic, serverLevel, player);
            }
            Vec3 predicted = player.position().add(player.getDeltaMovement().scale(8.0));
            double yOffset = cinematic.moveType() == CompanionMoveType.FLY && distanceToGround(player) <= 20.0 ? -1.4 : -5.0;
            if (cinematic.moveType() == CompanionMoveType.FLY && level instanceof ServerLevel serverLevel) {
                double minY = hasReliableLandingBelow(serverLevel, player) ? (double)predictedLanding(serverLevel, player).getY() + 1.2 : player.getY() - 1.2;
                return new Vec3(predicted.x, Math.max(predicted.y + yOffset, minY), predicted.z);
            }
            return predicted.add(0.0, yOffset, 0.0);
        }
        return player.position();
    }

    public static Vec3 flyingCatchTarget(PendingMountCinematic cinematic, ServerLevel level, ServerPlayer player) {
        BlockPos landing = rescueAnchor(level, player);
        return new Vec3((double)landing.getX() + 0.5, cinematic.catchY(), (double)landing.getZ() + 0.5);
    }

    static double flyingWaitY(RescueFlightMode mode, double catchY, Vec3 hoverPosition) {
        return mode == RescueFlightMode.HOVER && hoverPosition != null ? hoverPosition.y : catchY;
    }

    static double flyingInitialStageY(double landingY, double groundDistance,
                                      double minimumHeight, double maximumHeight) {
        return Math.floor(landingY
                + flyingHoverHeight(groundDistance, minimumHeight, maximumHeight));
    }

    static double bodyOriginYForBottom(double desiredBottom, double entityY,
                                       double boundingBoxMinY) {
        return desiredBottom - (boundingBoxMinY - entityY);
    }

    public static Vec3 flyingInterceptTarget(ServerLevel level, ServerPlayer player) {
        return flyingInterceptTarget(level, player, null);
    }

    public static Vec3 flyingInterceptTarget(ServerLevel level, ServerPlayer player, LivingEntity mount) {
        // The target is below the player's feet, and large mounts need their
        // whole body below the player. Using a fixed -1.8 offset made dragons
        // visually overlap the player's head during the high rescue.
        double belowFeet = mount == null ? 1.8 : Math.max(1.8, mount.getBbHeight() + 0.35);
        Vec3 target = player.position().add(player.getDeltaMovement().scale(2.0)).add(0.0, -belowFeet, 0.0);
        if (hasReliableLandingBelow(level, player)) {
            double minY = predictedLanding(level, player).getY() + 1.2;
            target = new Vec3(target.x, Math.max(target.y, minY), target.z);
        }
        return target;
    }

    public static Vec3 flyingHoverTarget(ServerLevel level, ServerPlayer player) {
        BlockPos landing = rescueAnchor(level, player);
        double groundDistance = Math.max(0.0, player.getY() - landing.getY());
        double hoverY = flyingInitialStageY(landing.getY(), groundDistance,
                Config.rescueHoverMinHeight, Config.rescueHoverMaxHeight);
        return new Vec3((double) landing.getX() + 0.5, hoverY,
                (double) landing.getZ() + 0.5);
    }

    public static Vec3 flyingHoverTarget(ServerLevel level, ServerPlayer player,
                                         Vec3 lockedHoverPosition) {
        Vec3 current = flyingHoverTarget(level, player);
        double lockedY = lockedHoverPosition == null ? current.y : lockedHoverPosition.y;
        return new Vec3(current.x, lockedY, current.z);
    }

    static double flyingHoverHeight(double groundDistance, double minimumHeight, double maximumHeight) {
        double safeDistance = Double.isFinite(groundDistance) ? Math.max(0.0, groundDistance) : 0.0;
        double safeMinimum = Math.max(2.0, minimumHeight);
        double safeMaximum = Math.max(safeMinimum, maximumHeight);
        double animationStart = safeMinimum + CompanionRescuePlanner.RESCUE_MOUNT_CLEARANCE;
        double rise = Math.max(0.0, safeDistance - animationStart) * 0.45;
        return Mth.clamp(safeMinimum + rise, safeMinimum, safeMaximum);
    }

    public static BlockPos predictedLanding(ServerLevel level, ServerPlayer player) {
        return findPredictedLanding(level, player).orElse(player.blockPosition());
    }

    public static BlockPos rescueAnchor(ServerLevel level, ServerPlayer player) {
        return findPredictedLanding(level, player).orElse(BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ()));
    }

    public static boolean hasReliableLandingBelow(ServerLevel level, ServerPlayer player) {
        return findPredictedLanding(level, player).isPresent();
    }

    private static java.util.Optional<BlockPos> findPredictedLanding(ServerLevel level, ServerPlayer player) {
        long tick = level.getGameTime();
        BlockPos playerPos = player.blockPosition();
        LandingSample cached = LANDING_CACHE.get(player);
        if (cached != null && cached.tick() == tick && cached.level() == level
                && cached.playerPos().equals(playerPos)) {
            return cached.landing();
        }
        java.util.Optional<BlockPos> landing = scanPredictedLanding(level, playerPos);
        LANDING_CACHE.put(player, new LandingSample(tick, level, playerPos.immutable(), landing));
        return landing;
    }

    private static java.util.Optional<BlockPos> scanPredictedLanding(ServerLevel level, BlockPos playerPos) {
        BlockPos.MutableBlockPos pos = playerPos.mutable();
        int minY = level.getMinBuildHeight();
        while (pos.getY() > minY) {
            if (!level.getFluidState(pos).isEmpty()) {
                return java.util.Optional.of(pos.above().immutable());
            }
            BlockPos below = pos.below();
            if (!level.getFluidState(below).isEmpty()) {
                return java.util.Optional.of(pos.immutable());
            }
            if (CompanionPlacementFinder.isLandingSurface(level, below) && CompanionPlacementFinder.isOpenForLanding(level, pos)) {
                return java.util.Optional.of(pos.immutable());
            }
            pos.move(0, -1, 0);
        }
        return java.util.Optional.empty();
    }

    private record LandingSample(long tick, ServerLevel level, BlockPos playerPos,
                                 java.util.Optional<BlockPos> landing) {
    }

    public static double walkGroundY(Level level, double x, double currentY, double z, double fallbackY) {
        int startY = Mth.floor(Math.max(currentY, fallbackY) + 6.0);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(Math.min(currentY, fallbackY) - 16.0));
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, startY, z).mutable();
        while (pos.getY() >= minY) {
            BlockPos below = pos.below();
            if (CompanionPlacementFinder.isLandingSurface(level, below) && CompanionPlacementFinder.isOpenForLanding(level, pos) && level.getBlockState(pos.above()).isAir()) {
                return landingSurfaceY(level, below);
            }
            pos.move(0, -1, 0);
        }
        return fallbackY;
    }

    public static double rescueGroundYOffset(PendingMountCinematic cinematic) {
        return cinematic.mode().isGroundOrWaterRescue()
                && cinematic.presentationMoveType() == CompanionMoveType.WALK ? 0.18 : 0.0;
    }

    public static double landingSurfaceY(Level level, BlockPos surfacePos) {
        VoxelShape shape = level.getBlockState(surfacePos).getCollisionShape(level, surfacePos);
        return (double)surfacePos.getY() + (shape.isEmpty() ? 0.0 : shape.max(net.minecraft.core.Direction.Axis.Y));
    }
}
