package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.compat.CompanionSaintsDragonsCompat;
import com.kuzhi.findme.server.compat.IceAndFireRescueCompatibility;
import com.kuzhi.findme.server.safety.CompanionFriendlyFireService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Injects a bounded patrol goal without replacing the entity's combat or movement controller. */
public final class CompanionGuardPostService {
    static final double PATROL_RADIUS = 12.0;
    static final double HARD_RADIUS = 36.0;
    private static final double SAINTS_DRAGON_PATROL_RADIUS = 24.0;
    private static final double SAINTS_DRAGON_HARD_RADIUS = 64.0;
    public static final double DEFAULT_HOME_PATROL_RADIUS = 24.0;
    public static final double DEFAULT_HOME_HARD_RADIUS = 32.0;
    private static final int GOAL_PRIORITY = 1;
    private static final int NAVIGATION_SUCCESS_RETRY_TICKS = 40;
    private static final int NAVIGATION_FAILURE_MAX_RETRY_TICKS = 200;
    private static final int HOME_DEFENSE_SCAN_TICKS = 20;
    private static final int MAX_HOME_DEFENSE_CACHE = 128;
    private static final int PATROL_POINT_ATTEMPTS = 6;
    private static final Map<UUID, GuardLease> ACTIVE = new HashMap<>();
    private static final Map<UUID, HomeRequest> HOMES = new HashMap<>();
    private static final Map<HomeDefenseKey, HomeDefenseScan> HOME_DEFENSE_TARGETS = new HashMap<>();

    private CompanionGuardPostService() {
    }

    static void acquire(Mob mob, BlockPos center, CompanionMoveType moveType) {
        removeActive(mob.getUUID(), "guard_replaced");
        CompanionMoveType effectiveMoveType = effectiveMoveType(mob, moveType);
        install(mob, center, effectiveMoveType, false, LeaseType.GUARD,
                patrolRadius(mob, PATROL_RADIUS), hardRadius(mob, HARD_RADIUS),
                HouseResidentMode.GUARD);
    }

    public static void acquireHome(Mob mob, BlockPos center, CompanionMoveType moveType,
                                   boolean forceAirborne) {
        acquireHome(mob, center, moveType, forceAirborne, HouseResidentMode.WANDER);
    }

    public static void acquireHome(Mob mob, BlockPos center, CompanionMoveType moveType,
                                   boolean forceAirborne, HouseResidentMode mode) {
        acquireHome(mob, center, moveType, forceAirborne, mode, Config.housePatrolRadius,
                Config.houseHardRadius);
    }

    public static void acquireHome(Mob mob, BlockPos center, CompanionMoveType moveType,
                                   boolean forceAirborne, HouseResidentMode mode,
                                   int configuredPatrolRadius, int configuredHardRadius) {
        if (mob == null || center == null || moveType == null) {
            return;
        }
        HouseResidentMode residentMode = mode == null ? HouseResidentMode.WANDER : mode;
        CompanionMoveType effectiveMoveType = effectiveMoveType(mob, moveType);
        boolean airborne = forceAirborne || CompanionSaintsDragonsCompat.isAirborne(mob)
                || effectiveMoveType == CompanionMoveType.FLY && (!mob.onGround() || mob.isNoGravity());
        double patrolRadius = patrolRadius(mob, homePatrolRadius(residentMode, configuredPatrolRadius));
        double hardRadius = hardRadius(mob, homeHardRadius(patrolRadius, configuredHardRadius));
        HomeRequest request = new HomeRequest(mob, center.immutable(), effectiveMoveType, airborne,
                patrolRadius, hardRadius, residentMode);
        HOMES.put(mob.getUUID(), request);
        GuardLease active = ACTIVE.get(mob.getUUID());
        if (active != null && active.type == LeaseType.GUARD) {
            return;
        }
        if (active != null && active.matches(request)) {
            return;
        }
        removeActive(mob.getUUID(), "home_updated");
        install(mob, center, effectiveMoveType, airborne, LeaseType.HOME, patrolRadius, hardRadius, residentMode);
    }

    public static void releaseHome(UUID uuid, String reason) {
        if (uuid == null) {
            return;
        }
        HOMES.remove(uuid);
        GuardLease active = ACTIVE.get(uuid);
        if (active != null && active.type == LeaseType.HOME) {
            removeActive(uuid, reason);
        }
    }

    public static boolean hasMatchingHomeLease(Mob mob, BlockPos center, CompanionMoveType moveType,
                                               boolean forceAirborne, HouseResidentMode mode,
                                               int configuredPatrolRadius, int configuredHardRadius) {
        if (mob == null || center == null || moveType == null) {
            return false;
        }
        HouseResidentMode residentMode = mode == null ? HouseResidentMode.WANDER : mode;
        CompanionMoveType effectiveMoveType = effectiveMoveType(mob, moveType);
        boolean airborne = forceAirborne || CompanionSaintsDragonsCompat.isAirborne(mob)
                || effectiveMoveType == CompanionMoveType.FLY && (!mob.onGround() || mob.isNoGravity());
        double patrolRadius = patrolRadius(mob, homePatrolRadius(residentMode, configuredPatrolRadius));
        double hardRadius = hardRadius(mob, homeHardRadius(patrolRadius, configuredHardRadius));
        GuardLease active = ACTIVE.get(mob.getUUID());
        return active != null && active.type == LeaseType.HOME && active.mob == mob
                && active.moveType == effectiveMoveType && active.airborne == airborne
                && active.mode == residentMode && active.patrolRadius == patrolRadius
                && active.hardRadius == hardRadius
                && active.center.equals(Vec3.atBottomCenterOf(center));
    }

    private static void install(Mob mob, BlockPos center, CompanionMoveType moveType,
                                boolean forceAirborne, LeaseType type,
                                double patrolRadius, double hardRadius, HouseResidentMode mode) {
        boolean airborne = forceAirborne
                || moveType == CompanionMoveType.FLY && (!mob.onGround() || mob.isNoGravity());
        GuardLease lease = new GuardLease(mob, Vec3.atBottomCenterOf(center), moveType, airborne,
                type, patrolRadius, hardRadius, mode);
        ACTIVE.put(mob.getUUID(), lease);
        mob.goalSelector.addGoal(GOAL_PRIORITY, lease.goal);
        FindMeDebugLogger.info("guard-ai",
                "installed companion={} type={} lease={} center={} moveType={} airborne={} patrolRadius={} hardRadius={}",
                mob.getUUID(), mob.getType(), type + "/" + mode, lease.center, moveType, airborne,
                patrolRadius, hardRadius);
    }

    static void release(UUID uuid, String reason) {
        GuardLease lease = uuid == null ? null : ACTIVE.get(uuid);
        if (lease == null || lease.type != LeaseType.GUARD) {
            return;
        }
        removeActive(uuid, reason);
        HomeRequest home = HOMES.get(uuid);
        if (home != null && home.mob.isAlive() && !home.mob.isRemoved()) {
            install(home.mob, home.center, home.moveType, home.airborne, LeaseType.HOME,
                    home.patrolRadius, home.hardRadius, home.mode);
        }
    }

    static boolean controlCustomMovement(Mob mob) {
        GuardLease lease = mob == null ? null : ACTIVE.get(mob.getUUID());
        if (lease == null || !CompanionSaintsDragonsCompat.isSaintsDragon(mob)
                || lease.goal.destination == null) {
            return false;
        }
        CompanionEscortMovementService.control(mob, lease.goal.destination, lease.moveType);
        return true;
    }

    private static void removeActive(UUID uuid, String reason) {
        GuardLease lease = uuid == null ? null : ACTIVE.remove(uuid);
        if (lease == null) {
            return;
        }
        lease.goal.stop();
        if (!lease.mob.isRemoved()) {
            lease.mob.goalSelector.removeGoal(lease.goal);
        }
        FindMeDebugLogger.info("guard-ai", "removed companion={} type={} lease={} reason={} patrols={} returns={}",
                uuid, lease.mob.getType(), lease.type, reason, lease.goal.patrols, lease.goal.returns);
    }

    static void reset() {
        for (UUID uuid : java.util.List.copyOf(ACTIVE.keySet())) {
            removeActive(uuid, "server_reset");
        }
        HOMES.clear();
        HOME_DEFENSE_TARGETS.clear();
    }

    static double homePatrolRadius(HouseResidentMode mode, double configuredRadius) {
        return Math.max(4.0, configuredRadius);
    }

    static double homeHardRadius(double patrolRadius, double configuredRadius) {
        return Math.max(Math.max(8.0, configuredRadius), patrolRadius);
    }

    private static CompanionMoveType effectiveMoveType(Mob mob, CompanionMoveType moveType) {
        if (!CompanionSaintsDragonsCompat.isSaintsDragon(mob)) {
            return moveType;
        }
        if (moveType == CompanionMoveType.SWIM) {
            return moveType;
        }
        return CompanionSaintsDragonsCompat.isAirborne(mob) ? CompanionMoveType.FLY : CompanionMoveType.WALK;
    }

    private static double patrolRadius(Mob mob, double base) {
        return CompanionSaintsDragonsCompat.isSaintsDragon(mob)
                ? Math.max(base, SAINTS_DRAGON_PATROL_RADIUS) : base;
    }

    private static double hardRadius(Mob mob, double base) {
        return CompanionSaintsDragonsCompat.isSaintsDragon(mob)
                ? Math.max(base, SAINTS_DRAGON_HARD_RADIUS) : base;
    }

    static boolean allowsCombat(HouseResidentMode mode) {
        return mode == HouseResidentMode.WANDER || mode == HouseResidentMode.GUARD;
    }

    static boolean holdsPosition(HouseResidentMode mode) {
        return mode == HouseResidentMode.REST;
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    private static final class GuardLease {
        private final Mob mob;
        private final Vec3 center;
        private final Vec3 returnAnchor;
        private final CompanionMoveType moveType;
        private final boolean airborne;
        private final LeaseType type;
        private final double patrolRadius;
        private final double hardRadius;
        private final HouseResidentMode mode;
        private final GuardPostGoal goal;

        private GuardLease(Mob mob, Vec3 center, CompanionMoveType moveType, boolean airborne,
                           LeaseType type, double patrolRadius, double hardRadius,
                           HouseResidentMode mode) {
            this.mob = mob;
            this.center = center;
            this.returnAnchor = horizontalDistanceSqr(mob.position(), center) <= hardRadius * hardRadius
                    ? mob.position() : center;
            this.moveType = moveType;
            this.airborne = airborne;
            this.type = type;
            this.patrolRadius = patrolRadius;
            this.hardRadius = hardRadius;
            this.mode = mode == null ? HouseResidentMode.WANDER : mode;
            this.goal = new GuardPostGoal(this);
        }

        private boolean matches(HomeRequest request) {
            return type == LeaseType.HOME && mob == request.mob && moveType == request.moveType
                    && airborne == request.airborne
                    && mode == request.mode
                    && patrolRadius == request.patrolRadius && hardRadius == request.hardRadius
                    && center.equals(Vec3.atBottomCenterOf(request.center));
        }
    }

    private static final class GuardPostGoal extends Goal {
        private final GuardLease lease;
        private Vec3 destination;
        private int nextPatrolTick;
        private int nextMoveTick;
        private int failedMoveAttempts;
        private int outsideHardSince = -1;
        private boolean returning;
        private int patrols;
        private int returns;

        private GuardPostGoal(GuardLease lease) {
            this.lease = lease;
            this.nextMoveTick = lease.mob.tickCount
                    + Math.floorMod(lease.mob.getUUID().hashCode(), NAVIGATION_SUCCESS_RETRY_TICKS);
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return shouldOwnMovement();
        }

        @Override
        public boolean canContinueToUse() {
            return shouldOwnMovement();
        }

        private boolean shouldOwnMovement() {
            Mob mob = lease.mob;
            if (ACTIVE.get(mob.getUUID()) != lease || !mob.isAlive() || mob.isVehicle()) {
                return false;
            }
            if (!allowsCombat(lease.mode)) {
                if (mob.getTarget() != null) {
                    mob.setTarget(null);
                }
                return true;
            }
            double distance = horizontalDistanceSqr(mob.position(), lease.center);
            if (distance > lease.hardRadius * lease.hardRadius) {
                if (mob.getTarget() != null) {
                    FindMeDebugLogger.info("guard-ai",
                            "boundary target cleared companion={} target={} distance={}", mob.getUUID(),
                            mob.getTarget().getUUID(), String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distance)));
                    mob.setTarget(null);
                }
                return true;
            }
            return mob.getTarget() == null || !mob.getTarget().isAlive();
        }

        @Override
        public void start() {
            if (holdsPosition(lease.mode)) {
                lease.mob.getNavigation().stop();
                return;
            }
            chooseDestination(true);
        }

        @Override
        public void stop() {
            destination = null;
            returning = false;
        }

        @Override
        public void tick() {
            Mob mob = lease.mob;
            if (holdsPosition(lease.mode)) {
                if (!mob.getNavigation().isDone()) {
                    mob.getNavigation().stop();
                }
                return;
            }
            double centerDistance = Math.sqrt(horizontalDistanceSqr(mob.position(), lease.center));
            if (centerDistance > lease.hardRadius) {
                if (outsideHardSince < 0) {
                    outsideHardSince = mob.tickCount;
                } else if (mob.tickCount - outsideHardSince >= 200 && snapBackInsideBoundary()) {
                    outsideHardSince = -1;
                    returning = false;
                    destination = null;
                    nextPatrolTick = mob.tickCount + 80;
                    return;
                }
            } else {
                outsideHardSince = -1;
            }
            if (centerDistance <= lease.hardRadius && mob.getTarget() == null) {
                net.minecraft.world.entity.LivingEntity defenseTarget = homeDefenseTarget(lease);
                if (defenseTarget != null) {
                    mob.setTarget(defenseTarget);
                    mob.getNavigation().stop();
                    return;
                }
            }
            boolean outsidePatrol = centerDistance > lease.patrolRadius;
            if (outsidePatrol && !returning) {
                returning = true;
                returns++;
                destination = returnPoint();
                FindMeDebugLogger.info("guard-ai",
                        "return companion={} centerDistance={} destination={} navigation={}", mob.getUUID(),
                        String.format(java.util.Locale.ROOT, "%.2f", centerDistance), destination,
                        mob.getNavigation().getClass().getName());
            }

            double completion = Math.max(1.4, mob.getBbWidth() * 0.6);
            boolean reached = destination == null || mob.position().distanceToSqr(destination) <= completion * completion;
            if (returning && reached) {
                returning = false;
                destination = null;
                nextPatrolTick = mob.tickCount + 20 + mob.getRandom().nextInt(41);
                mob.getNavigation().stop();
            } else if (!returning && (reached || mob.tickCount >= nextPatrolTick)) {
                chooseDestination(false);
            }

            if (destination == null) {
                return;
            }
            if (CompanionSaintsDragonsCompat.isSaintsDragon(mob)) {
                CompanionEscortMovementService.control(mob, destination, lease.moveType);
                return;
            }
            if (mob.tickCount >= nextMoveTick) {
                boolean pathAccepted = mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
                if (!pathAccepted && lease.airborne) {
                    mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.0);
                }
                failedMoveAttempts = pathAccepted ? 0 : Math.min(4, failedMoveAttempts + 1);
                nextMoveTick = mob.tickCount + navigationRetryDelay(pathAccepted, failedMoveAttempts);
                FindMeDebugLogger.info("guard-ai",
                        "move companion={} mode={} returning={} destination={} pathAccepted={} centerDistance={}",
                        mob.getUUID(), lease.moveType, returning, destination, pathAccepted,
                        String.format(java.util.Locale.ROOT, "%.2f", centerDistance));
            }
        }

        private void chooseDestination(boolean initial) {
            Mob mob = lease.mob;
            if (horizontalDistanceSqr(mob.position(), lease.center) > lease.patrolRadius * lease.patrolRadius) {
                returning = true;
                returns++;
                destination = returnPoint();
                return;
            }
            destination = findPatrolPoint();
            nextPatrolTick = mob.tickCount + 60 + mob.getRandom().nextInt(81);
            if (destination != null) {
                patrols++;
                FindMeDebugLogger.info("guard-ai",
                        "patrol companion={} initial={} destination={} patrol={} moveType={} airborne={}",
                        mob.getUUID(), initial, destination, patrols, lease.moveType, lease.airborne);
            }
        }

        private Vec3 returnPoint() {
            if (lease.airborne) {
                return new Vec3(lease.returnAnchor.x,
                        Math.max(lease.center.y + 2.0, lease.returnAnchor.y), lease.returnAnchor.z);
            }
            return lease.returnAnchor;
        }

        private Vec3 findPatrolPoint() {
            Mob mob = lease.mob;
            if (!(mob.level() instanceof ServerLevel level)) {
                return lease.center;
            }
            for (int attempt = 0; attempt < PATROL_POINT_ATTEMPTS; attempt++) {
                double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
                double radius = patrolDestinationRadius(lease.patrolRadius, mob.getRandom().nextDouble());
                double x = lease.center.x + Math.cos(angle) * radius;
                double z = lease.center.z + Math.sin(angle) * radius;
                if (!level.hasChunkAt(BlockPos.containing(x, lease.center.y, z))) {
                    continue;
                }
                if (lease.airborne) {
                    double y = Math.max(lease.center.y + 2.0,
                            lease.center.y + 3.0 + mob.getRandom().nextDouble() * 5.0);
                    if (level.noCollision(mob, mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ()))) {
                        return new Vec3(x, y, z);
                    }
                    continue;
                }
                if (lease.moveType == CompanionMoveType.SWIM) {
                    int y = Mth.floor(lease.center.y) + mob.getRandom().nextInt(7) - 3;
                    BlockPos water = BlockPos.containing(x, y, z);
                    if (level.getFluidState(water).is(FluidTags.WATER)
                            && level.noCollision(mob, mob.getBoundingBox().move(
                            x - mob.getX(), water.getY() + 0.2 - mob.getY(), z - mob.getZ()))) {
                        return new Vec3(x, water.getY() + 0.2, z);
                    }
                    continue;
                }
                BlockPos origin = BlockPos.containing(x, lease.center.y, z);
                java.util.Optional<BlockPos> ground = CompanionPlacementFinder.findOpenEntitySpace(level, mob, origin);
                if (ground.isPresent()) {
                    return Vec3.atBottomCenterOf(ground.get());
                }
            }
            return returnPoint();
        }

        private boolean snapBackInsideBoundary() {
            Mob mob = lease.mob;
            if (!(mob.level() instanceof ServerLevel level)) {
                return false;
            }
            Vec3 target;
            Vec3 anchor = returnPoint();
            if (level.noCollision(mob, mob.getBoundingBox().move(anchor.subtract(mob.position())))) {
                target = anchor;
            } else if (lease.airborne || lease.moveType == CompanionMoveType.SWIM) {
                return false;
            } else {
                java.util.Optional<BlockPos> safe = CompanionPlacementFinder.findOpenEntitySpace(
                        level, mob, BlockPos.containing(lease.returnAnchor));
                if (safe.isEmpty()) {
                    return false;
                }
                target = Vec3.atBottomCenterOf(safe.get());
            }
            mob.getNavigation().stop();
            mob.teleportTo(target.x, target.y, target.z);
            mob.moveTo(target.x, target.y, target.z, mob.getYRot(), mob.getXRot());
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0.0F;
            FindMeDebugLogger.info("guard-ai",
                    "hard boundary recovery companion={} center={} target={} hardRadius={}",
                    mob.getUUID(), lease.center, target, lease.hardRadius);
            return true;
        }

    }

    static int navigationRetryDelay(boolean pathAccepted, int failedAttempts) {
        if (pathAccepted) {
            return NAVIGATION_SUCCESS_RETRY_TICKS;
        }
        int shift = Math.max(0, Math.min(3, failedAttempts - 1));
        return Math.min(NAVIGATION_FAILURE_MAX_RETRY_TICKS,
                NAVIGATION_SUCCESS_RETRY_TICKS << shift);
    }

    static double patrolDestinationRadius(double patrolRadius, double randomUnit) {
        double maximum = Math.max(3.0, patrolRadius * 0.72);
        return 3.0 + Math.max(0.0, Math.min(1.0, randomUnit)) * (maximum - 3.0);
    }

    private static net.minecraft.world.entity.LivingEntity homeDefenseTarget(GuardLease lease) {
        if (!allowsCombat(lease.mode) || !(lease.mob.level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos center = BlockPos.containing(lease.center);
        HomeDefenseKey key = new HomeDefenseKey(level, center);
        long now = level.getGameTime();
        HomeDefenseScan cached = HOME_DEFENSE_TARGETS.get(key);
        if (cached != null && now < cached.nextScanAt) {
            if (cached.target == null) {
                return null;
            }
            if (validDefenseTarget(cached.target, level, lease.center, lease.hardRadius)) {
                return cached.target;
            }
        }
        double radius = Math.min(lease.hardRadius, Math.max(12.0, lease.patrolRadius));
        AABB area = new AABB(center).inflate(radius, Math.min(12.0, radius), radius);
        net.minecraft.world.entity.LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Mob candidate : level.getEntitiesOfClass(Mob.class, area,
                candidate -> candidate instanceof Enemy && candidate.isAlive()
                        && !CompanionFriendlyFireService.isBoundCompanion(candidate))) {
            double distance = candidate.position().distanceToSqr(lease.center);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        HOME_DEFENSE_TARGETS.put(key, new HomeDefenseScan(nearest, now + HOME_DEFENSE_SCAN_TICKS));
        if (HOME_DEFENSE_TARGETS.size() > MAX_HOME_DEFENSE_CACHE) {
            HOME_DEFENSE_TARGETS.entrySet().removeIf(entry -> entry.getValue().nextScanAt <= now);
        }
        return nearest;
    }

    private static boolean validDefenseTarget(net.minecraft.world.entity.LivingEntity target,
                                              ServerLevel level, Vec3 center, double hardRadius) {
        return target != null && target.isAlive() && target.level() == level
                && !CompanionFriendlyFireService.isBoundCompanion(target)
                && horizontalDistanceSqr(target.position(), center) <= hardRadius * hardRadius;
    }

    private enum LeaseType {
        HOME,
        GUARD
    }

    private record HomeRequest(Mob mob, BlockPos center, CompanionMoveType moveType,
                               boolean airborne, double patrolRadius, double hardRadius,
                               HouseResidentMode mode) {
    }

    private record HomeDefenseKey(ServerLevel level, BlockPos center) {
    }

    private record HomeDefenseScan(net.minecraft.world.entity.LivingEntity target, long nextScanAt) {
    }
}
