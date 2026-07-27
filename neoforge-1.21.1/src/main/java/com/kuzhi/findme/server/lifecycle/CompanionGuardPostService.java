package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
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
import net.minecraft.world.phys.Vec3;

/** Injects a bounded patrol goal without replacing the entity's combat or movement controller. */
public final class CompanionGuardPostService {
    static final double PATROL_RADIUS = 12.0;
    static final double HARD_RADIUS = 36.0;
    public static final double DEFAULT_HOME_PATROL_RADIUS = 64.0;
    public static final double DEFAULT_HOME_HARD_RADIUS = 128.0;
    private static final int GOAL_PRIORITY = 1;
    private static final Map<UUID, GuardLease> ACTIVE = new HashMap<>();
    private static final Map<UUID, HomeRequest> HOMES = new HashMap<>();

    private CompanionGuardPostService() {
    }

    static void acquire(Mob mob, BlockPos center, CompanionMoveType moveType) {
        removeActive(mob.getUUID(), "guard_replaced");
        install(mob, center, moveType, false, LeaseType.GUARD, PATROL_RADIUS, HARD_RADIUS);
    }

    public static void acquireHome(Mob mob, BlockPos center, CompanionMoveType moveType,
                                   boolean forceAirborne) {
        if (mob == null || center == null || moveType == null) {
            return;
        }
        boolean airborne = forceAirborne
                || moveType == CompanionMoveType.FLY && (!mob.onGround() || mob.isNoGravity());
        double patrolRadius = Math.max(DEFAULT_HOME_PATROL_RADIUS, Config.housePatrolRadius);
        double hardRadius = Math.max(Math.max(DEFAULT_HOME_HARD_RADIUS, Config.houseHardRadius), patrolRadius);
        HomeRequest request = new HomeRequest(mob, center.immutable(), moveType, airborne,
                patrolRadius, hardRadius);
        HOMES.put(mob.getUUID(), request);
        GuardLease active = ACTIVE.get(mob.getUUID());
        if (active != null && active.type == LeaseType.GUARD) {
            return;
        }
        if (active != null && active.matches(request)) {
            return;
        }
        removeActive(mob.getUUID(), "home_updated");
        install(mob, center, moveType, airborne, LeaseType.HOME, patrolRadius, hardRadius);
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

    private static void install(Mob mob, BlockPos center, CompanionMoveType moveType,
                                boolean forceAirborne, LeaseType type,
                                double patrolRadius, double hardRadius) {
        boolean airborne = forceAirborne
                || moveType == CompanionMoveType.FLY && (!mob.onGround() || mob.isNoGravity());
        GuardLease lease = new GuardLease(mob, Vec3.atBottomCenterOf(center), moveType, airborne,
                type, patrolRadius, hardRadius);
        ACTIVE.put(mob.getUUID(), lease);
        mob.goalSelector.addGoal(GOAL_PRIORITY, lease.goal);
        FindMeDebugLogger.info("guard-ai",
                "installed companion={} type={} lease={} center={} moveType={} airborne={} patrolRadius={} hardRadius={}",
                mob.getUUID(), mob.getType(), type, lease.center, moveType, airborne,
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
                    home.patrolRadius, home.hardRadius);
        }
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
    }

    private static final class GuardLease {
        private final Mob mob;
        private final Vec3 center;
        private final CompanionMoveType moveType;
        private final boolean airborne;
        private final LeaseType type;
        private final double patrolRadius;
        private final double hardRadius;
        private final GuardPostGoal goal;

        private GuardLease(Mob mob, Vec3 center, CompanionMoveType moveType, boolean airborne,
                           LeaseType type, double patrolRadius, double hardRadius) {
            this.mob = mob;
            this.center = center;
            this.moveType = moveType;
            this.airborne = airborne;
            this.type = type;
            this.patrolRadius = patrolRadius;
            this.hardRadius = hardRadius;
            this.goal = new GuardPostGoal(this);
        }

        private boolean matches(HomeRequest request) {
            return type == LeaseType.HOME && mob == request.mob && moveType == request.moveType
                    && airborne == request.airborne
                    && patrolRadius == request.patrolRadius && hardRadius == request.hardRadius
                    && center.equals(Vec3.atBottomCenterOf(request.center));
        }
    }

    private static final class GuardPostGoal extends Goal {
        private final GuardLease lease;
        private Vec3 destination;
        private int nextPatrolTick;
        private int lastMoveTick = Integer.MIN_VALUE / 2;
        private boolean returning;
        private int patrols;
        private int returns;

        private GuardPostGoal(GuardLease lease) {
            this.lease = lease;
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
            double centerDistance = Math.sqrt(horizontalDistanceSqr(mob.position(), lease.center));
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
            if (mob.getNavigation().isDone() || mob.tickCount - lastMoveTick >= 20) {
                boolean pathAccepted = mob.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
                if (!pathAccepted && lease.airborne) {
                    mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.0);
                }
                lastMoveTick = mob.tickCount;
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
                return new Vec3(lease.center.x, Math.max(lease.center.y + 2.0, lease.mob.getY()), lease.center.z);
            }
            return lease.center;
        }

        private Vec3 findPatrolPoint() {
            Mob mob = lease.mob;
            if (!(mob.level() instanceof ServerLevel level)) {
                return lease.center;
            }
            for (int attempt = 0; attempt < 12; attempt++) {
                double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
                double radius = 3.0 + mob.getRandom().nextDouble() * (lease.patrolRadius - 3.0);
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

        private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
            double dx = first.x - second.x;
            double dz = first.z - second.z;
            return dx * dx + dz * dz;
        }
    }

    private enum LeaseType {
        HOME,
        GUARD
    }

    private record HomeRequest(Mob mob, BlockPos center, CompanionMoveType moveType,
                               boolean airborne, double patrolRadius, double hardRadius) {
    }
}
