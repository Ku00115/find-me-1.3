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
        if (cinematic.mode().isRescue()) {
            if (cinematic.moveType() == CompanionMoveType.WALK && level instanceof ServerLevel serverLevel) {
                BlockPos landing = rescueAnchor(serverLevel, player);
                return Vec3.atBottomCenterOf((Vec3i)landing).add(0.0, rescueGroundYOffset(cinematic), 0.0);
            }
            if (cinematic.moveType() == CompanionMoveType.SWIM && level instanceof ServerLevel serverLevel) {
                BlockPos landing = rescueAnchor(serverLevel, player);
                return Vec3.atBottomCenterOf((Vec3i)landing);
            }
            if (cinematic.moveType() == CompanionMoveType.FLY && cinematic.mode().isRescue()
                    && level instanceof ServerLevel serverLevel) {
                if (cinematic.rescueFlightMode() == RescueFlightMode.LANDING_SUMMON) {
                    Vec3 landing = cinematic.rescueLandingPosition();
                    return landing != null ? landing : Vec3.atBottomCenterOf(rescueAnchor(serverLevel, player));
                }
                if (cinematic.rescueFlightMode() == RescueFlightMode.HOVER
                        && cinematic.rescueHoverPosition() != null) {
                    return cinematic.rescueHoverPosition();
                }
                return flyingInterceptTarget(serverLevel, player, null);
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
        double hoverHeight = flyingHoverHeight(groundDistance, Config.rescueHoverBaseHeight,
                Config.rescueHoverHeightRatio, Config.rescueHoverMaxHeight);
        return new Vec3((double) landing.getX() + 0.5,
                (double) landing.getY() + hoverHeight,
                (double) landing.getZ() + 0.5);
    }

    static double flyingHoverHeight(double groundDistance, double baseHeight,
                                    double heightRatio, double maxHeight) {
        double safeDistance = Double.isFinite(groundDistance) ? Math.max(0.0, groundDistance) : 0.0;
        double safeBase = Math.max(2.0, baseHeight);
        double safeRatio = Mth.clamp(heightRatio, 0.0, 1.0);
        double safeMax = Math.max(safeBase, maxHeight);
        double scaledHeight = safeBase + Math.max(0.0, safeDistance - 10.0) * safeRatio;
        double belowPlayerLimit = Math.max(2.0, safeDistance - 3.0);
        return Math.min(Mth.clamp(scaledHeight, safeBase, safeMax), belowPlayerLimit);
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
        return cinematic.mode().isGroundOrWaterRescue() && cinematic.moveType() == CompanionMoveType.WALK ? 0.18 : 0.0;
    }

    public static double landingSurfaceY(Level level, BlockPos surfacePos) {
        VoxelShape shape = level.getBlockState(surfacePos).getCollisionShape(level, surfacePos);
        return (double)surfacePos.getY() + (shape.isEmpty() ? 0.0 : shape.max(net.minecraft.core.Direction.Axis.Y));
    }
}
