package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionSpawnPlacementService {
    private static final int MAX_TEAM_PLACEMENT_CANDIDATES = 384;
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

    static Map<java.util.UUID, BlockPos> planTeamSummonSpots(ServerPlayer player,
                                                             List<CompanionFormationPlanner.Member> members) {
        if (player == null || members == null || members.isEmpty()) return Map.of();
        return planTeamSummonSpotsAt(player, player.blockPosition(), members);
    }

    static Map<java.util.UUID, BlockPos> planTeamSummonSpotsAt(ServerPlayer player, BlockPos center,
                                                               List<CompanionFormationPlanner.Member> members) {
        if (player == null || center == null || members == null || members.isEmpty()) return Map.of();
        return planReservedSpots(player, center, members,
                CompanionFormationPlanner.plan(members, player.getBbWidth()));
    }

    static Map<java.util.UUID, BlockPos> planGuardPosts(ServerPlayer player, BlockPos center,
                                                        List<CompanionFormationPlanner.Member> members) {
        if (player == null || center == null || members == null || members.isEmpty()) return Map.of();
        return planReservedSpots(player, center, members, CompanionFormationPlanner.planGuardPosts(members));
    }

    private static Map<java.util.UUID, BlockPos> planReservedSpots(
            ServerPlayer player, BlockPos anchor, List<CompanionFormationPlanner.Member> input,
            Map<java.util.UUID, CompanionFormationPlanner.Offset> offsets) {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        forward = forward.lengthSqr() < 0.001 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 anchorCenter = Vec3.atBottomCenterOf(anchor);
        ArrayList<CompanionFormationPlanner.Member> members = new ArrayList<>(input);
        members.sort(Comparator.comparing(CompanionFormationPlanner.Member::uuid));
        ArrayList<AABB> reserved = new ArrayList<>();
        LinkedHashMap<java.util.UUID, BlockPos> result = new LinkedHashMap<>();
        for (CompanionFormationPlanner.Member member : members) {
            CompanionFormationPlanner.Offset offset = offsets.get(member.uuid());
            if (offset == null) continue;
            double airborneHeight = member.moveType() == CompanionMoveType.FLY
                    ? Mth.clamp(member.height() * 0.5 + 3.0, 4.0, 14.0) + offset.vertical()
                    : 0.0;
            Vec3 desired = anchorCenter.add(right.scale(offset.lateral())).subtract(forward.scale(offset.rear()))
                    .add(0.0, airborneHeight, 0.0);
            findReservedSpot(player.serverLevel(), BlockPos.containing(desired), member, reserved)
                    .ifPresent(pos -> {
                        AABB box = reservationBox(pos, member);
                        reserved.add(box);
                        result.put(member.uuid(), pos);
                    });
        }
        return Map.copyOf(result);
    }

    private static Optional<BlockPos> findReservedSpot(ServerLevel level, BlockPos desired,
                                                        CompanionFormationPlanner.Member member,
                                                        List<AABB> reserved) {
        int radius = Math.max(6, (int)Math.ceil(Math.max(member.width(), member.depth())) + 4);
        int candidates = 0;
        for (int ring = 0; ring <= radius && candidates < MAX_TEAM_PLACEMENT_CANDIDATES; ring++) {
            for (int x = -ring; x <= ring; x++) {
                for (int z = -ring; z <= ring; z++) {
                    if (ring > 0 && Math.abs(x) != ring && Math.abs(z) != ring) continue;
                    int verticalRadius = member.moveType() == CompanionMoveType.SWIM ? 6
                            : member.moveType() == CompanionMoveType.FLY ? 4 : 3;
                    for (int y = -verticalRadius; y <= verticalRadius; y++) {
                        if (++candidates > MAX_TEAM_PLACEMENT_CANDIDATES) return Optional.empty();
                        BlockPos candidate = desired.offset(x, y, z);
                        boolean validBase = switch (member.moveType()) {
                            case SWIM -> level.getFluidState(candidate).is(FluidTags.WATER);
                            case FLY -> level.hasChunkAt(candidate)
                                    && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                                    && level.getFluidState(candidate).isEmpty();
                            default -> CompanionPlacementFinder.isSafe(level, candidate);
                        };
                        if (!validBase) continue;
                        AABB box = reservationBox(candidate, member);
                        if (!CompanionPlacementFinder.hasOpenBox(level, candidate,
                                Math.max(member.width(), member.depth()), member.height())) continue;
                        if (!level.noCollision(null, box)) continue;
                        if (reserved.stream().anyMatch(box::intersects)) continue;
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    static Vec3 findCompanionArrivalTarget(LivingEntity companion, java.util.UUID ownerUuid, int side,
                                            CompanionMoveType moveType) {
        if (!(companion.level() instanceof ServerLevel level)) return companion.position();
        Entity ownerEntity = level.getEntity(ownerUuid);
        if (!(ownerEntity instanceof ServerPlayer owner)) return companion.position();
        Vec3 look = owner.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        forward = forward.lengthSqr() < 0.001 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double clearance = Math.max(3.5, companion.getBbWidth() + owner.getBbWidth() + 1.5);
        Vec3 desired = owner.position().add(right.scale(side * clearance)).subtract(forward.scale(2.5));
        if (moveType == CompanionMoveType.FLY) {
            return desired.add(0.0, Math.max(3.0, companion.getBbHeight() * 0.5 + 1.5), 0.0);
        }
        if (moveType == CompanionMoveType.SWIM) {
            return CompanionPlacementFinder.findWaterWithOpenSurface(level, BlockPos.containing(desired))
                    .map(Vec3::atBottomCenterOf).orElse(desired);
        }
        BlockPos base = BlockPos.containing(desired);
        BlockPos safe = CompanionPlacementFinder.findOpenEntitySpace(level, companion, base)
                .orElseGet(() -> CompanionPlacementFinder.findSafe(level, base).orElse(base));
        return Vec3.atBottomCenterOf(safe);
    }

    private static AABB reservationBox(BlockPos pos, CompanionFormationPlanner.Member member) {
        double width = Math.max(0.9, Math.max(member.width(), member.depth()) + 0.75);
        double height = Math.max(1.8, member.height() + 0.25);
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        return new AABB(x - width * 0.5, pos.getY(), z - width * 0.5,
                x + width * 0.5, pos.getY() + height, z + width * 0.5);
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
        return findArrivalSpawn(player, moveType, rescue, entityType, rescueFlightMode, null);
    }

    public static BlockPos findArrivalSpawn(ServerPlayer player, CompanionMoveType moveType, boolean rescue,
                                            String entityType, RescueFlightMode rescueFlightMode,
                                            CompanionEntityVisualBoundsService.VisualDimensions dimensions) {
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
                double catchY = (double) landing.getY()
                        + CompanionCinematicLandingService.flyingCatchHeight(player);
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
                Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
                double horizontalDistance = CompanionRescuePlanner.plan(player).approachDistance(true);
                Vec3 start = new Vec3((double)landing.getX() + 0.5, catchY, (double)landing.getZ() + 0.5)
                        .add(direction.scale(horizontalDistance));
                return BlockPos.containing((Position)start);
            }
            return randomizedArrivalCandidate(player, horizontal, moveType, dimensions);
        }
        if (moveType == CompanionMoveType.SWIM) {
            BlockPos origin = rescue ? CompanionCinematicLandingService.rescueAnchor(player.serverLevel(), player) : player.blockPosition();
            if (!rescue) {
                BlockPos randomized = randomizedArrivalCandidate(player, horizontal, moveType, dimensions);
                Optional<BlockPos> randomWater = CompanionPlacementFinder.findWaterWithOpenSurface(
                        player.serverLevel(), randomized);
                if (randomWater.isPresent()) return randomWater.get();
            }
            Optional<BlockPos> water = rescue
                    ? findRescueWaterApproach(player, origin, horizontal)
                    : CompanionPlacementFinder.findWaterWithOpenSurface(player.serverLevel(), origin);
            if (water.isPresent()) {
                return water.get();
            }
            return rescue ? findWalkRescueSpawn(player, origin, horizontal)
                    : randomizedArrivalCandidate(player, horizontal, CompanionMoveType.WALK, dimensions);
        }
        if (rescue) {
            BlockPos landing = CompanionCinematicLandingService.rescueAnchor(player.serverLevel(), player);
            if (CompanionRescuePlanner.plan(player).urgency()
                    == CompanionRescuePlanner.Urgency.LANDING_SUMMON) {
                return walkSurfacePos(player.serverLevel(), landing, landing.getY());
            }
            return findWalkRescueSpawn(player, landing, horizontal);
        }
        return randomizedArrivalCandidate(player, horizontal, moveType, dimensions);
    }

    private static Optional<BlockPos> findRescueWaterApproach(ServerPlayer player, BlockPos landing,
                                                               Vec3 horizontal) {
        double runDistance = Math.max(10.0, CompanionRescuePlanner.plan(player).approachDistance(false));
        double[] angles = {0.0, 28.0, -28.0, 55.0, -55.0, 90.0, -90.0, 180.0};
        for (double angle : angles) {
            Vec3 direction = rotateY(horizontal, Math.toRadians(angle));
            BlockPos probe = BlockPos.containing(Vec3.atBottomCenterOf(landing)
                    .subtract(direction.scale(runDistance)));
            Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(
                    player.serverLevel(), probe);
            if (water.isPresent()) return water;
        }
        return CompanionPlacementFinder.findWaterWithOpenSurface(player.serverLevel(), landing);
    }

    private static BlockPos findWalkRescueSpawn(ServerPlayer player, BlockPos landing, Vec3 horizontal) {
        if (CompanionRescuePlanner.plan(player).urgency()
                == CompanionRescuePlanner.Urgency.LANDING_SUMMON) {
            return walkSurfacePos(player.serverLevel(), landing, landing.getY());
        }
        double runDistance = CompanionRescuePlanner.plan(player).approachDistance(false);
        Vec3 ahead = Vec3.atBottomCenterOf(landing).subtract(horizontal.scale(runDistance));
        BlockPos base = BlockPos.containing(ahead);
        return CompanionPlacementFinder.findSafe(player.serverLevel(), base)
                .orElseGet(() -> walkSurfacePos(player.serverLevel(), base, landing.getY()));
    }

    private static BlockPos randomizedArrivalCandidate(ServerPlayer player, Vec3 forward,
                                                        CompanionMoveType moveType,
                                                        CompanionEntityVisualBoundsService.VisualDimensions dimensions) {
        double width = dimensions == null ? 1.0 : Math.max(dimensions.width(), dimensions.depth());
        double height = dimensions == null ? 1.8 : dimensions.height();
        double baseDistance = Mth.clamp(18.0 + width * 0.9 + height * 0.25, 18.0, 52.0);
        BlockPos fallback = player.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = Math.toRadians(player.getRandom().nextDouble() * 130.0 - 65.0);
            Vec3 direction = rotateY(forward, angle);
            double distance = Mth.clamp(baseDistance + player.getRandom().nextDouble() * 10.0,
                    18.0, 64.0);
            double lift = moveType == CompanionMoveType.FLY
                    ? Mth.clamp(4.0 + height * 0.45 + player.getRandom().nextDouble() * 4.0,
                    5.0, 28.0) : 0.0;
            Vec3 candidate = player.position().subtract(direction.scale(distance)).add(0.0, lift, 0.0);
            BlockPos pos = BlockPos.containing(candidate);
            fallback = pos;
            if (moveType == CompanionMoveType.FLY) {
                double boxWidth = Math.max(0.9, width);
                if (CompanionPlacementFinder.hasOpenBox(player.serverLevel(), pos, boxWidth,
                        Math.max(1.8, height))) return pos;
            } else if (moveType == CompanionMoveType.SWIM) {
                Optional<BlockPos> water = CompanionPlacementFinder.findWaterWithOpenSurface(
                        player.serverLevel(), pos);
                if (water.isPresent()) return water.get();
            } else {
                Optional<BlockPos> safe = CompanionPlacementFinder.findSafe(player.serverLevel(), pos);
                if (safe.isPresent() && (dimensions == null
                        || CompanionPlacementFinder.hasOpenBox(player.serverLevel(), safe.get(),
                        Math.max(0.9, width), Math.max(1.8, height)))) return safe.get();
            }
        }
        if (moveType == CompanionMoveType.WALK || moveType == CompanionMoveType.COMPANION) {
            return walkSurfacePos(player.serverLevel(), fallback, fallback.getY());
        }
        return fallback;
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

