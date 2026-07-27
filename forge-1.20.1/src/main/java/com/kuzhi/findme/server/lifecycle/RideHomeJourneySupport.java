package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.RideHomeCameraPacket;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

final class RideHomeJourneySupport {
    private RideHomeJourneySupport() {
    }

    static CompanionMoveType resolveMoveType(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        Entity live = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (live instanceof LivingEntity living) {
            return CompanionEntityClassifier.moveType(living, CompanionKind.MOUNT);
        }
        return data.storedEntity(uuid)
                .map(CompanionEntitySnapshots::storedEntityType)
                .map(type -> CompanionEntityClassifier.moveType(type, CompanionKind.MOUNT))
                .orElse(CompanionMoveType.WALK);
    }

    static boolean validRide(ServerPlayer player, LivingEntity mount, UUID expectedUuid) {
        return mount != null && mount.isAlive() && expectedUuid.equals(mount.getUUID())
                && player.getVehicle() == mount;
    }

    static Physics capturePhysics(LivingEntity mount) {
        return new Physics(mount.isNoGravity(), mount.noPhysics, mount.getPose());
    }

    static void prepareCinematic(LivingEntity mount, CompanionMoveType moveType) {
        mount.setNoGravity(moveType == CompanionMoveType.FLY);
        if (moveType == CompanionMoveType.FLY) {
            CompanionAnimationHelper.forceFlyingAnimationPose(mount);
        }
        if (mount instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    static void restorePhysics(LivingEntity mount, Physics physics) {
        if (mount == null || physics == null) {
            return;
        }
        CompanionAnimationHelper.restoreAnimationControl(mount);
        mount.setNoGravity(physics.noGravity());
        mount.noPhysics = physics.noPhysics();
        mount.setPose(physics.pose());
        mount.setDeltaMovement(Vec3.ZERO);
        mount.hurtMarked = true;
    }

    static void stabilize(ServerPlayer player, LivingEntity mount) {
        player.fallDistance = 0.0f;
        mount.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime,
                Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        mount.invulnerableTime = Math.max(mount.invulnerableTime,
                Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
    }

    static boolean holdRide(ServerPlayer player, LivingEntity mount, UUID expectedUuid, Vec3 anchor,
                            float headingYaw, CompanionMoveType moveType) {
        if (!validRide(player, mount, expectedUuid)) {
            return false;
        }
        mount.setDeltaMovement(Vec3.ZERO);
        player.setDeltaMovement(Vec3.ZERO);
        if (anchor != null && mount.position().distanceToSqr(anchor) > 1.0E-4) {
            mount.moveTo(anchor.x, anchor.y, anchor.z, headingYaw, mount.getXRot());
            mount.setPos(anchor.x, anchor.y, anchor.z);
        }
        faceYaw(mount, headingYaw);
        prepareCinematic(mount, moveType);
        stabilize(player, mount);
        return true;
    }

    static void transferPlayer(ServerPlayer player, ServerLevel destination, Vec3 start,
                               float headingYaw, float pitch) {
        if (player.level() != destination) {
            player.teleportTo(destination, start.x, start.y, start.z, Set.<RelativeMovement>of(),
                    headingYaw, pitch);
        } else {
            player.connection.teleport(start.x, start.y, start.z, headingYaw, pitch);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        syncPlayerAfterTransfer(player);
    }

    static void syncPlayerAfterTransfer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.onUpdateAbilities();
        player.connection.resetPosition();
    }

    static boolean holdTransferredPlayer(ServerPlayer player, ResourceKey<Level> destination,
                                         Vec3 anchor, float headingYaw, float pitch) {
        if (player == null || destination == null || anchor == null
                || !player.level().dimension().equals(destination)) {
            return false;
        }
        player.setInvisible(true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        if (player.position().distanceToSqr(anchor) > 1.0E-4) {
            player.connection.teleport(anchor.x, anchor.y, anchor.z, headingYaw, pitch);
        }
        return true;
    }

    static void settleAtCurrentPosition(LivingEntity mount, float headingYaw) {
        mount.setDeltaMovement(Vec3.ZERO);
        mount.fallDistance = 0.0f;
        mount.hurtMarked = true;
        faceYaw(mount, headingYaw);
    }

    static Optional<ArrivalPlan> planArrival(ServerLevel level, LivingEntity mount, BlockPos home,
                                             Vec3 preferredDirection, CompanionMoveType moveType,
                                             RandomSource random) {
        Optional<ArrivalPlan> preferred = planArrivalExact(level, mount, home, preferredDirection,
                moveType, random);
        if (preferred.isPresent() || moveType != CompanionMoveType.SWIM) {
            return preferred;
        }
        return planArrivalExact(level, mount, home, preferredDirection, CompanionMoveType.WALK, random);
    }

    private static Optional<ArrivalPlan> planArrivalExact(ServerLevel level, LivingEntity mount, BlockPos home,
                                                          Vec3 preferredDirection, CompanionMoveType moveType,
                                                          RandomSource random) {
        Vec3 direction = horizontal(preferredDirection);
        direction = direction.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        if (moveType == CompanionMoveType.SWIM) {
            Optional<ArrivalPlan> waterPlan = findNearbyWaterPlan(level, mount, home, direction, random);
            if (waterPlan.isPresent()) {
                return waterPlan;
            }
        }
        for (BlockPos target : arrivalTargetCandidates(level, mount, home, direction, moveType)) {
            BlockPos start = findArrivalStart(level, mount, target, direction, moveType).orElse(null);
            if (start != null) {
                Vec3 startPoint = Vec3.atBottomCenterOf(start);
                Vec3 targetPoint = Vec3.atBottomCenterOf(target);
                return Optional.of(new ArrivalPlan(startPoint, targetPoint,
                        headingYaw(targetPoint.subtract(startPoint), 0.0f), moveType));
            }
        }
        return Optional.empty();
    }

    private static java.util.List<BlockPos> arrivalTargetCandidates(ServerLevel level, LivingEntity mount,
                                                                     BlockPos home, Vec3 direction,
                                                                     CompanionMoveType moveType) {
        java.util.LinkedHashSet<BlockPos> candidates = new java.util.LinkedHashSet<>();
        double[] angles = {0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0, 135.0, -135.0, 180.0};
        double[] distances = {2.0, 3.0, 4.5, 6.0, 8.0, 10.0, 12.0, 16.0, 20.0};
        for (double distance : distances) {
            for (double angle : angles) {
                Vec3 offset = rotateY(direction, Math.toRadians(angle)).scale(distance);
                BlockPos base = home.offset(Mth.floor(offset.x), 0, Mth.floor(offset.z));
                findOpen(level, mount, base, moveType).ifPresent(candidates::add);
            }
        }
        if (moveType != CompanionMoveType.SWIM) {
            CompanionPlacementFinder.findOpenEntitySpace(level, mount, home).ifPresent(candidates::add);
        }
        return java.util.List.copyOf(candidates);
    }

    private static Optional<ArrivalPlan> findNearbyWaterPlan(ServerLevel level, LivingEntity mount, BlockPos home,
                                                             Vec3 preferredDirection, RandomSource random) {
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int radius = 2; radius <= 16; radius += 2) {
            int samples = Math.max(12, radius * 3);
            for (int sample = 0; sample < samples; sample++) {
                double angle = phase + Math.PI * 2.0 * sample / samples;
                BlockPos base = home.offset(Mth.floor(Math.cos(angle) * radius), 0,
                        Mth.floor(Math.sin(angle) * radius));
                BlockPos target = findOpen(level, mount, base, CompanionMoveType.SWIM).orElse(null);
                if (target == null) {
                    continue;
                }
                Vec3 fromHome = horizontal(Vec3.atBottomCenterOf(target).subtract(Vec3.atBottomCenterOf(home)));
                Vec3 approach = fromHome.lengthSqr() < 0.01 ? preferredDirection : fromHome.normalize();
                BlockPos start = findArrivalStart(level, mount, target, approach, CompanionMoveType.SWIM)
                        .orElse(null);
                if (start != null) {
                    Vec3 startPoint = Vec3.atBottomCenterOf(start);
                    Vec3 targetPoint = Vec3.atBottomCenterOf(target);
                    return Optional.of(new ArrivalPlan(startPoint, targetPoint,
                            headingYaw(targetPoint.subtract(startPoint), 0.0f), CompanionMoveType.SWIM));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findArrivalStart(ServerLevel level, LivingEntity mount, BlockPos target,
                                                       Vec3 direction, CompanionMoveType moveType) {
        double[] angles = {0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0, 180.0};
        boolean flying = moveType == CompanionMoveType.FLY;
        double[] distances = flying ? new double[]{20.0, 16.0, 12.0, 8.0}
                : moveType == CompanionMoveType.SWIM ? new double[]{11.0, 8.0, 5.0}
                : new double[]{14.0, 11.0, 8.0, 5.0, 3.0};
        for (double distance : distances) {
            for (double angle : angles) {
                Vec3 approach = rotateY(direction, Math.toRadians(angle));
                Vec3 desired = Vec3.atBottomCenterOf(target).subtract(approach.scale(distance))
                        .add(0.0, flying ? 8.0 : 0.0, 0.0);
                Optional<BlockPos> start = flying
                        ? (CompanionPlacementFinder.hasOpenEntitySpace(level, mount, desired.x, desired.y, desired.z)
                        ? Optional.of(BlockPos.containing(desired)) : Optional.empty())
                        : findOpen(level, mount, BlockPos.containing(desired), moveType);
                if (start.isPresent() && routeHasSpace(level, mount, Vec3.atBottomCenterOf(start.get()),
                        target, moveType)) {
                    return start;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findOpen(ServerLevel level, LivingEntity mount, BlockPos base,
                                               CompanionMoveType moveType) {
        for (int y : new int[]{0, 1, 2, -1, -2}) {
            BlockPos candidate = base.above(y);
            if (moveType == CompanionMoveType.SWIM && isWaterSpot(level, mount, candidate)) {
                return Optional.of(candidate);
            }
            if (moveType != CompanionMoveType.SWIM && CompanionPlacementFinder.isSafe(level, candidate)
                    && CompanionPlacementFinder.hasOpenEntitySpace(level, mount,
                    candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean routeHasSpace(ServerLevel level, LivingEntity mount, Vec3 from,
                                         BlockPos target, CompanionMoveType moveType) {
        Vec3 to = Vec3.atBottomCenterOf(target);
        int samples = Math.max(2, Mth.ceil(from.distanceTo(to) / 1.5));
        double groundY = from.y;
        for (int i = 0; i <= samples; i++) {
            Vec3 point = from.lerp(to, (double)i / samples);
            if (moveType == CompanionMoveType.SWIM) {
                if (!isWaterSpot(level, mount, BlockPos.containing(point))) {
                    return false;
                }
            } else if (moveType == CompanionMoveType.FLY) {
                if (!CompanionPlacementFinder.hasOpenEntitySpace(level, mount, point.x, point.y, point.z)) {
                    return false;
                }
            } else {
                double nextGround = CompanionCinematicLandingService.walkGroundY(level, point.x, groundY,
                        point.z, point.y);
                if (Math.abs(nextGround - groundY) > 1.35
                        || !CompanionPlacementFinder.hasOpenEntitySpace(level, mount,
                        point.x, nextGround, point.z)) {
                    return false;
                }
                groundY = nextGround;
            }
        }
        return true;
    }

    private static boolean isWaterSpot(ServerLevel level, LivingEntity mount, BlockPos candidate) {
        for (int y = 0; y < Math.max(1, Mth.ceil(mount.getBbHeight())); y++) {
            if (!level.getFluidState(candidate.above(y)).is(FluidTags.WATER)) {
                return false;
            }
        }
        return CompanionPlacementFinder.hasOpenEntitySpace(level, mount,
                candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
    }

    private static void faceYaw(LivingEntity mount, float headingYaw) {
        float yaw = Mth.wrapDegrees(headingYaw);
        mount.setYRot(yaw);
        mount.setYHeadRot(yaw);
        mount.setYBodyRot(yaw);
    }

    static Vec3 departureDirection(ServerPlayer player, LivingEntity mount) {
        Vec3 look = horizontal(player.getLookAngle());
        if (look.lengthSqr() >= 0.01) {
            return look.normalize();
        }
        Vec3 mountLook = horizontal(mount.getLookAngle());
        return mountLook.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : mountLook.normalize();
    }

    static Vec3 departureTarget(LivingEntity mount, Vec3 direction, CompanionMoveType moveType) {
        Vec3 route = horizontal(direction);
        if (route.lengthSqr() < 0.001) {
            route = yawVector(mount.getYRot());
        }
        route = route.normalize();
        if (moveType == CompanionMoveType.FLY) {
            route = route.add(0.0, 0.34, 0.0).normalize();
        }
        return mount.position().add(route.scale(48.0));
    }

    static float headingYaw(Vec3 direction, float fallbackYaw) {
        Vec3 horizontal = horizontal(direction);
        return horizontal.lengthSqr() < 0.001 ? Mth.wrapDegrees(fallbackYaw)
                : Mth.wrapDegrees((float)(Math.atan2(horizontal.z, horizontal.x) * 57.2957763671875) - 90.0f);
    }

    static RideHomeCameraPacket camera(ServerPlayer player, Entity mount,
                                       RideHomeCameraPacket.Mode mode, int duration, float headingYaw) {
        Vec3 target = mount == null ? player.position() : mount.position();
        return camera(player, mount, target, mode, duration, headingYaw);
    }

    static RideHomeCameraPacket camera(ServerPlayer player, Entity mount, Vec3 target,
                                       RideHomeCameraPacket.Mode mode, int duration, float headingYaw) {
        float radius = mount instanceof LivingEntity living
                ? Math.max(1.0f, living.getBbWidth() * 0.8f) : 2.2f;
        return new RideHomeCameraPacket(mode == RideHomeCameraPacket.Mode.START,
                mount == null ? -1 : mount.getId(), duration,
                player.getX(), player.getY(), player.getZ(), target.x, target.y, target.z,
                radius, mode, headingYaw, 0.0f);
    }

    private static Vec3 horizontal(Vec3 value) {
        return new Vec3(value.x, 0.0, value.z);
    }

    private static Vec3 yawVector(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static Vec3 rotateY(Vec3 vector, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vector.x * cos - vector.z * sin, vector.y,
                vector.x * sin + vector.z * cos);
    }

    private static float yawToward(Vec3 from, Vec3 to) {
        return (float)(Math.atan2(to.z - from.z, to.x - from.x) * 57.2957763671875) - 90.0f;
    }

    record Physics(boolean noGravity, boolean noPhysics, Pose pose) {
    }

    record ArrivalPlan(Vec3 start, Vec3 target, float headingYaw, CompanionMoveType moveType) {
    }

    static final class ChunkTicket {
        private static final int TICKET_RADIUS = 3;
        private static final int REQUIRED_RADIUS = 2;
        private static final TicketType<UUID> TYPE = TicketType.create(
                "find_me_ride_home", UUID::compareTo, 20 * 60);

        private final ResourceKey<Level> dimension;
        private final ChunkPos chunk;
        private final UUID owner;

        private ChunkTicket(ResourceKey<Level> dimension, ChunkPos chunk, UUID owner) {
            this.dimension = dimension;
            this.chunk = chunk;
            this.owner = owner;
        }

        static ChunkTicket acquire(ServerLevel level, BlockPos home, UUID owner) {
            ChunkPos chunk = new ChunkPos(home);
            level.getChunkSource().addRegionTicket(TYPE, chunk, TICKET_RADIUS, owner);
            return new ChunkTicket(level.dimension(), chunk, owner);
        }

        boolean ready(MinecraftServer server, BlockPos center) {
            ServerLevel level = server.getLevel(this.dimension);
            if (level == null) {
                return false;
            }
            int chunkX = center.getX() >> 4;
            int chunkZ = center.getZ() >> 4;
            for (int x = -REQUIRED_RADIUS; x <= REQUIRED_RADIUS; x++) {
                for (int z = -REQUIRED_RADIUS; z <= REQUIRED_RADIUS; z++) {
                    if (!level.hasChunk(chunkX + x, chunkZ + z)) {
                        return false;
                    }
                }
            }
            return true;
        }

        void release(MinecraftServer server) {
            ServerLevel level = server.getLevel(this.dimension);
            if (level != null) {
                level.getChunkSource().removeRegionTicket(TYPE, this.chunk, TICKET_RADIUS, this.owner);
            }
        }
    }
}
