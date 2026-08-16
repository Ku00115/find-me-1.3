package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.Config;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class VehiclePlacementService {
    private VehiclePlacementService() {
    }

    public static BlockPos findVehicleSpot(ServerPlayer player, String entityType) {
        return findVehicleSpotNear(player, player.blockPosition(), entityType);
    }

    public static BlockPos findVehicleSpot(ServerPlayer player, String entityType, double width, double height, double depth) {
        return findVehicleSpotNear(player, player.blockPosition(), entityType, width, height, depth);
    }

    public static BlockPos findVehicleSpotNear(ServerPlayer player, BlockPos origin, String entityType) {
        return findVehicleSpotNear(player, origin, entityType, 1.0, 2.0, 1.0);
    }

    public static BlockPos findVehicleSpotNear(ServerPlayer player, BlockPos origin, String entityType, double width, double height, double depth) {
        if (entityType != null && entityType.contains("boat")) {
            Optional<BlockPos> water = findWater(player.serverLevel(), origin);
            if (water.isPresent()) {
                return water.get();
            }
        }
        ServerLevel level = player.serverLevel();
        boolean requiresGround = requiresGround(entityType);
        if (isSafe(level, origin, width, height, depth)
                || !requiresGround && hasBodySpace(level, origin, width, height, depth)) {
            return origin;
        }
        Optional<BlockPos> safe = findSafe(level, origin, width, height, depth);
        if (requiresGround) {
            return safe.or(() -> findGroundSurface(level, origin, width, height, depth)).orElse(origin);
        }
        return safe.or(() -> findCollisionFree(player.serverLevel(), origin, width, height, depth))
                .orElse(origin);
    }

    /** Vehicle handoffs preserve altitude and only need enough block space for the replacement. */
    public static BlockPos findSwitchSpotNear(ServerPlayer player, BlockPos origin,
                                              double width, double height, double depth) {
        return findCollisionFree(player.serverLevel(), origin, width, height, depth).orElse(origin);
    }

    private static boolean requiresGround(String entityType) {
        return entityType != null && entityType.startsWith("automobility:");
    }

    private static Optional<BlockPos> findGroundSurface(ServerLevel level, BlockPos origin,
                                                         double width, double height, double depth) {
        for (int r = 0; r <= Config.DEFAULT_SAFE_SEARCH_RADIUS; ++r) {
            for (int x = -r; x <= r; ++x) {
                for (int z = -r; z <= r; ++z) {
                    if (r > 0 && Math.abs(x) != r && Math.abs(z) != r) continue;
                    int worldX = origin.getX() + x;
                    int worldZ = origin.getZ() + z;
                    int worldY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
                    BlockPos candidate = new BlockPos(worldX, worldY, worldZ);
                    if (isSafe(level, candidate, width, height, depth)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findWater(ServerLevel level, BlockPos origin) {
        for (int y = -4; y <= 4; ++y) {
            for (int r = 0; r <= Config.DEFAULT_SAFE_SEARCH_RADIUS; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if ((r == 0 || Math.abs(x) == r || Math.abs(z) == r) && level.getFluidState(candidate).is(FluidTags.WATER)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findSafe(ServerLevel level, BlockPos origin, double width, double height, double depth) {
        if (isSafe(level, origin, width, height, depth)) {
            return Optional.of(origin);
        }
        for (int y = -4; y <= 8; ++y) {
            for (int r = 1; r <= Config.DEFAULT_SAFE_SEARCH_RADIUS; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if ((Math.abs(x) == r || Math.abs(z) == r) && isSafe(level, candidate, width, height, depth)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findCollisionFree(ServerLevel level, BlockPos origin, double width, double height, double depth) {
        for (int y = -4; y <= 12; ++y) {
            for (int r = 0; r <= Config.DEFAULT_SAFE_SEARCH_RADIUS; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if ((r == 0 || Math.abs(x) == r || Math.abs(z) == r) && hasBodySpace(level, candidate, width, height, depth)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos, double width, double height, double depth) {
        return hasBodySpace(level, pos, width, height, depth) && hasFooting(level, pos, width, depth);
    }

    private static boolean hasBodySpace(ServerLevel level, BlockPos pos, double width, double height, double depth) {
        double safeWidth = Mth.clamp(width, 0.8, 48.0);
        double safeDepth = Mth.clamp(depth, 0.8, 48.0);
        double safeHeight = Mth.clamp(height, 1.0, 48.0);
        double inset = Math.min(0.12, Math.min(safeWidth, safeDepth) * 0.08);
        double halfW = safeWidth * 0.5 - inset;
        double halfD = safeDepth * 0.5 - inset;
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        AABB body = new AABB(centerX - halfW, pos.getY() + 0.04, centerZ - halfD, centerX + halfW, pos.getY() + safeHeight, centerZ + halfD);
        return level.noCollision(body);
    }

    private static boolean hasFooting(ServerLevel level, BlockPos pos, double width, double depth) {
        double safeWidth = Mth.clamp(width, 0.8, 48.0);
        double safeDepth = Mth.clamp(depth, 0.8, 48.0);
        int xSteps = Mth.clamp((int)Math.ceil(safeWidth / 4.0), 1, 5);
        int zSteps = Mth.clamp((int)Math.ceil(safeDepth / 4.0), 1, 5);
        int supported = 0;
        int total = 0;
        for (int ix = -xSteps; ix <= xSteps; ++ix) {
            for (int iz = -zSteps; iz <= zSteps; ++iz) {
                double x = pos.getX() + 0.5 + ix * (safeWidth * 0.5 / (double)xSteps);
                double z = pos.getZ() + 0.5 + iz * (safeDepth * 0.5 / (double)zSteps);
                BlockPos below = BlockPos.containing(x, pos.getY() - 0.08, z);
                total++;
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                        && !level.getBlockState(below).is(Blocks.LAVA)) {
                    supported++;
                }
            }
        }
        return supported >= Math.max(1, total / 5);
    }
}
