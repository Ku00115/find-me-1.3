package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;

public final class CompanionPlacementFinder {
    private CompanionPlacementFinder() {
    }

    static Optional<BlockPos> findWater(ServerLevel level, BlockPos origin) {
        int radius = Config.DEFAULT_SAFE_SEARCH_RADIUS;
        for (int y = -4; y <= 4; ++y) {
            for (int r = 0; r <= radius; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (r > 0 && Math.abs(x) != r && Math.abs(z) != r || !level.getFluidState(candidate).is(FluidTags.WATER) || !level.getFluidState(candidate.above()).is(FluidTags.WATER)) continue;
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<BlockPos> findWaterWithOpenSurface(ServerLevel level, BlockPos origin) {
        Optional<BlockPos> direct = findWater(level, origin);
        if (direct.isPresent() && isWaterArrivalOpen(level, direct.get())) {
            return direct;
        }
        int radius = Config.DEFAULT_SAFE_SEARCH_RADIUS;
        for (int y = -6; y <= 6; ++y) {
            for (int r = 0; r <= radius; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (r > 0 && Math.abs(x) != r && Math.abs(z) != r) {
                            continue;
                        }
                        if (level.getFluidState(candidate).is(FluidTags.WATER) && isWaterArrivalOpen(level, candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isWaterArrivalOpen(ServerLevel level, BlockPos waterPos) {
        return !level.getFluidState(waterPos).isEmpty() && hasOpenBox(level, new AABB(waterPos.getX(), waterPos.getY(), waterPos.getZ(), waterPos.getX() + 1.0, waterPos.getY() + 2.2, waterPos.getZ() + 1.0));
    }

    public static Optional<BlockPos> findSafe(ServerLevel level, BlockPos origin) {
        if (isSafe(level, origin)) {
            return Optional.of(origin);
        }
        int radius = Config.DEFAULT_SAFE_SEARCH_RADIUS;
        for (int y = -3; y <= 3; ++y) {
            for (int r = 1; r <= radius; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (Math.abs(x) != r && Math.abs(z) != r || !isSafe(level, candidate)) continue;
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        return isLandingSurface(level, pos.below())
                && isOpenForLanding(level, pos)
                && level.getBlockState(pos.above()).isAir()
                && !isDangerous(level, pos)
                && !isDangerous(level, pos.above())
                && !isDangerous(level, pos.below());
    }

    public static boolean hasOpenEntitySpace(ServerLevel level, LivingEntity entity, double x, double y, double z) {
        double width = Math.max(0.9, (double)entity.getBbWidth() + 0.55);
        double height = Math.max(1.8, (double)entity.getBbHeight() + 0.25);
        AABB box = new AABB(x - width * 0.5, y, z - width * 0.5, x + width * 0.5, y + height, z + width * 0.5);
        return hasOpenBox(level, box);
    }

    public static boolean hasOpenBox(ServerLevel level, BlockPos pos, double width, double height) {
        double boxWidth = Math.max(0.9, width + 0.75);
        double boxHeight = Math.max(1.8, height + 0.35);
        AABB box = new AABB((double)pos.getX() + 0.5 - boxWidth * 0.5, pos.getY(), (double)pos.getZ() + 0.5 - boxWidth * 0.5, (double)pos.getX() + 0.5 + boxWidth * 0.5, (double)pos.getY() + boxHeight, (double)pos.getZ() + 0.5 + boxWidth * 0.5);
        return hasOpenBox(level, box);
    }

    public static boolean hasOpenBox(ServerLevel level, AABB box) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            for (AABB blockBox : shape.toAabbs()) {
                if (!blockBox.move(pos).intersects(box)) continue;
                return false;
            }
        }
        return true;
    }

    public static Optional<BlockPos> findOpenEntitySpace(ServerLevel level, LivingEntity entity, BlockPos origin) {
        return findOpenGroundEntitySpace(level, entity, origin,
                Math.max(2, Config.DEFAULT_SAFE_SEARCH_RADIUS), 3);
    }

    public static Optional<BlockPos> findTacticalEntitySpace(ServerLevel level, LivingEntity entity,
                                                               BlockPos origin, CompanionMoveType moveType) {
        int radius = Math.max(12, (int)Math.ceil(entity.getBbWidth() * 1.5) + 6);
        if (moveType == CompanionMoveType.FLY) {
            return findOpenAirEntitySpace(level, entity, origin, radius);
        }
        if (moveType == CompanionMoveType.SWIM) {
            Optional<BlockPos> water = findOpenWaterEntitySpace(level, entity, origin, radius);
            if (water.isPresent()) {
                return water;
            }
        }
        return findOpenGroundEntitySpace(level, entity, origin, radius, 6);
    }

    private static Optional<BlockPos> findOpenGroundEntitySpace(ServerLevel level, LivingEntity entity,
                                                                  BlockPos origin, int radius,
                                                                  int verticalRadius) {
        if (isSafe(level, origin) && hasOpenEntitySpace(level, entity, (double)origin.getX() + 0.5, origin.getY(), (double)origin.getZ() + 0.5)) {
            return Optional.of(origin);
        }
        for (int y = -verticalRadius; y <= verticalRadius; ++y) {
            for (int r = 1; r <= radius; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        BlockPos candidate = origin.offset(x, y, z);
                        if (Math.abs(x) != r && Math.abs(z) != r || !isSafe(level, candidate) || !hasOpenEntitySpace(level, entity, (double)candidate.getX() + 0.5, candidate.getY(), (double)candidate.getZ() + 0.5)) continue;
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findOpenAirEntitySpace(ServerLevel level, LivingEntity entity,
                                                               BlockPos origin, int radius) {
        int baseLift = Math.max(3, (int)Math.ceil(entity.getBbHeight() * 0.5) + 2);
        for (int lift = baseLift; lift <= baseLift + 12; lift++) {
            for (int ring = 0; ring <= radius; ring++) {
                for (int x = -ring; x <= ring; x++) {
                    for (int z = -ring; z <= ring; z++) {
                        if (ring > 0 && Math.abs(x) != ring && Math.abs(z) != ring) continue;
                        BlockPos candidate = origin.offset(x, lift, z);
                        if (!level.hasChunkAt(candidate) || !level.getFluidState(candidate).isEmpty()) continue;
                        AABB box = entity.getBoundingBox().move(candidate.getX() + 0.5 - entity.getX(),
                                candidate.getY() - entity.getY(), candidate.getZ() + 0.5 - entity.getZ());
                        if (hasOpenBox(level, box) && level.noCollision(entity, box)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findOpenWaterEntitySpace(ServerLevel level, LivingEntity entity,
                                                                 BlockPos origin, int radius) {
        for (int y = -8; y <= 8; y++) {
            for (int ring = 0; ring <= radius; ring++) {
                for (int x = -ring; x <= ring; x++) {
                    for (int z = -ring; z <= ring; z++) {
                        if (ring > 0 && Math.abs(x) != ring && Math.abs(z) != ring) continue;
                        BlockPos candidate = origin.offset(x, y, z);
                        if (!level.hasChunkAt(candidate)
                                || !level.getFluidState(candidate).is(FluidTags.WATER)) continue;
                        AABB box = entity.getBoundingBox().move(candidate.getX() + 0.5 - entity.getX(),
                                candidate.getY() + 0.1 - entity.getY(), candidate.getZ() + 0.5 - entity.getZ());
                        if (hasOpenBox(level, box) && level.noCollision(entity, box)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<BlockPos> findOpenDimensionsSpace(ServerLevel level, double width, double height, double depth, BlockPos origin) {
        int radius = Math.max(2, Config.DEFAULT_SAFE_SEARCH_RADIUS);
        for (int y = -3; y <= 3; y++) {
            for (int r = 0; r <= radius; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (r > 0 && Math.abs(x) != r && Math.abs(z) != r) continue;
                        BlockPos candidate = origin.offset(x, y, z);
                        if (!isSafe(level, candidate)) continue;
                        AABB box = new AABB(candidate.getX() + 0.5 - width * 0.5, candidate.getY(), candidate.getZ() + 0.5 - depth * 0.5,
                                candidate.getX() + 0.5 + width * 0.5, candidate.getY() + height, candidate.getZ() + 0.5 + depth * 0.5);
                        if (hasOpenBox(level, box)) return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isLandingSurface(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape((BlockGetter)level, pos).isEmpty();
    }

    public static boolean isOpenForLanding(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape((BlockGetter)level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.25;
    }

    private static boolean isDangerous(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LAVA)
                || level.getBlockState(pos).is(Blocks.FIRE)
                || level.getBlockState(pos).is(Blocks.SOUL_FIRE)
                || level.getFluidState(pos).is(FluidTags.LAVA);
    }
}
