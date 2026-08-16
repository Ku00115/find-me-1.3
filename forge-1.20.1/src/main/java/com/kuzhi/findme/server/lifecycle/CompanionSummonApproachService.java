package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import com.kuzhi.findme.server.compat.CompanionFixedPostService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Independent summon presentation for companions; it never enters the mount cinematic state machine. */
public final class CompanionSummonApproachService {
    private static final int MAX_AGE_TICKS = 140;
    private static final int MAGIC_DURATION_TICKS = 42;
    private static final Map<UUID, Approach> ACTIVE = new HashMap<>();

    private CompanionSummonApproachService() {
    }

    static void start(ServerPlayer owner, LivingEntity companion, CompanionMoveType moveType) {
        start(owner, companion, moveType, null, true);
    }

    static void start(ServerPlayer owner, LivingEntity companion, CompanionMoveType moveType,
                      boolean sendPresentation) {
        start(owner, companion, moveType, null, sendPresentation);
    }

    static void start(ServerPlayer owner, LivingEntity companion, CompanionMoveType moveType,
                      Vec3 fixedTarget) {
        start(owner, companion, moveType, fixedTarget, true);
    }

    static void start(ServerPlayer owner, LivingEntity companion, CompanionDeploymentPlan plan,
                      boolean sendPresentation) {
        if (plan == null || plan.moveType() == null) return;
        start(owner, companion, plan.moveType(),
                plan.destination() == null ? null : Vec3.atBottomCenterOf(plan.destination()),
                sendPresentation, plan.overheadArrival());
    }

    private static void start(ServerPlayer owner, LivingEntity companion, CompanionMoveType moveType,
                              Vec3 fixedTarget, boolean sendPresentation) {
        start(owner, companion, moveType, fixedTarget, sendPresentation, false);
    }

    private static void start(ServerPlayer owner, LivingEntity companion, CompanionMoveType moveType,
                              Vec3 fixedTarget, boolean sendPresentation, boolean overheadArrival) {
        if (owner == null || companion == null || moveType == null) {
            return;
        }
        cancel(companion.getUUID(), "replaced");
        int side = ((owner.getUUID().getLeastSignificantBits()
                ^ companion.getUUID().getLeastSignificantBits()) & 1L) == 0L ? 1 : -1;
        Approach approach = new Approach(owner.getUUID(), companion, moveType, side, fixedTarget,
                overheadArrival);
        ACTIVE.put(companion.getUUID(), approach);
        CompanionFixedPostService.acquire(companion, CompanionFixedPostService.Reason.ARRIVAL);
        if (companion instanceof Mob mob && approach.movementLease != null) {
            mob.goalSelector.addGoal(1, approach.movementLease);
        }
        if (sendPresentation && overheadArrival) {
            float radius = (float)Math.max(0.75, Math.max(companion.getBbWidth(), companion.getBbHeight() * 0.35));
            CompanionArrivalMagicService.openPulse(owner, companion.position(), radius,
                    MAGIC_DURATION_TICKS, RescueMagicPacket.Purpose.SUMMON);
        } else if (sendPresentation) {
            CompanionArrivalMagicService.send(owner, companion, approach.target, MAGIC_DURATION_TICKS,
                    RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON,
                    CompanionAnimationPurpose.SUMMON);
        }
        prepareArrivalFrame(companion, approach);
        if (!CompanionArrivalSequenceService.isPending(companion)) {
            moveArrivalStep(companion, approach);
        }
        FindMeDebugLogger.info("companion-approach",
                "started owner={} companion={} moveType={} pos={} target={} initialDistance={} cruiseSpeed={}",
                owner.getUUID(), companion.getUUID(), moveType, companion.position(), approach.target,
                approach.initialDistance, approach.cruiseSpeed);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Approach>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Approach> entry = iterator.next();
            Approach approach = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(approach.ownerUuid);
            Entity found = CompanionEntityLookup.findEntity(server, approach.companionUuid).orElse(null);
            if (owner == null || !(found instanceof LivingEntity companion) || !companion.isAlive()
                    || companion.level() != owner.level()
                    || CompanionStorageService.isStoragePending(companion)) {
                iterator.remove();
                finish(approach, found instanceof LivingEntity living ? living : approach.entity,
                        "invalid_context");
                continue;
            }
            if (CompanionArrivalSequenceService.isPending(companion)) {
                approach.age++;
                continue;
            }
            prepareArrivalFrame(companion, approach);
            double distance = companion.position().distanceTo(approach.target);
            if (distance <= approach.completionRadius || approach.age++ >= MAX_AGE_TICKS) {
                iterator.remove();
                finish(approach, companion, distance <= approach.completionRadius ? "completed" : "timeout");
                FindMeDebugLogger.info("companion-approach",
                        "finished owner={} companion={} age={} distance={} target={}", owner.getUUID(),
                        companion.getUUID(), approach.age, distance, approach.target);
                continue;
            }
            moveArrivalStep(companion, approach);
        }
    }

    public static int cancel(UUID companionUuid, String reason) {
        Approach removed = companionUuid == null ? null : ACTIVE.remove(companionUuid);
        if (removed == null) {
            return 0;
        }
        finish(removed, removed.entity, reason);
        FindMeDebugLogger.info("companion-approach", "cancelled companion={} reason={}",
                companionUuid, reason);
        return 1;
    }

    static boolean isActive(UUID companionUuid) {
        return companionUuid != null && ACTIVE.containsKey(companionUuid);
    }

    public static int cancelPlayer(UUID ownerUuid, String reason) {
        int cancelled = 0;
        for (Approach approach : java.util.List.copyOf(ACTIVE.values())) {
            if (approach.ownerUuid.equals(ownerUuid)) {
                cancelled += cancel(approach.companionUuid, reason);
            }
        }
        return cancelled;
    }

    static void reset() {
        for (UUID uuid : java.util.List.copyOf(ACTIVE.keySet())) {
            cancel(uuid, "server_reset");
        }
    }

    private static void prepareArrivalFrame(LivingEntity living, Approach approach) {
        living.noPhysics = approach.arrivalNoPhysics;
        living.setNoGravity(approach.originalNoGravity || approach.moveType == CompanionMoveType.FLY
                || approach.overheadArrival);
        if (approach.moveType == CompanionMoveType.FLY) {
            CompanionAnimationHelper.forceFlyingAnimationPose(living);
            BookOfDragonsRescueCompatibility.forceAirborne(living);
        }
        living.fallDistance = 0.0f;
    }

    private static void moveArrivalStep(LivingEntity living, Approach approach) {
        Vec3 offset = approach.target.subtract(living.position());
        double distance = offset.length();
        if (distance < 1.0E-4) {
            living.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 direction = offset.scale(1.0 / distance);
        double acceleration = Mth.clamp((approach.moveAge + 1) / 7.0, 0.42, 1.0);
        double brakeDistance = Math.max(4.0, approach.completionRadius * 2.25);
        double braking = Mth.clamp((distance - approach.completionRadius) / brakeDistance, 0.32, 1.0);
        double speed = Math.min(distance, approach.cruiseSpeed * acceleration * braking);
        float yaw = CompanionCinematicOrientationHelper.yawTowardStable(living, direction);
        if (approach.overheadArrival) {
            Vec3 next = living.position().add(direction.scale(speed));
            living.moveTo(next.x, next.y, next.z, yaw, 0.0f);
            living.setPos(next.x, next.y, next.z);
            if (approach.moveType == CompanionMoveType.FLY) {
                CompanionAnimationHelper.forceFlyingAnimationPose(living);
                BookOfDragonsRescueCompatibility.forceAirborne(living);
            }
        } else if (approach.moveType == CompanionMoveType.FLY) {
            Vec3 next = living.position().add(direction.scale(speed));
            float pitch = CompanionCinematicOrientationHelper.pitchToward(direction);
            living.moveTo(next.x, next.y, next.z, yaw, pitch);
            living.setPos(next.x, next.y, next.z);
            living.setXRot(pitch);
            CompanionAnimationHelper.forceFlyingAnimationPose(living);
            BookOfDragonsRescueCompatibility.forceAirborne(living);
        } else if (approach.moveType == CompanionMoveType.WALK
                || approach.moveType == CompanionMoveType.COMPANION) {
            Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
            if (horizontal.lengthSqr() < 1.0E-4) {
                horizontal = new Vec3(0.0, 0.0, 1.0);
            }
            horizontal = horizontal.normalize();
            double nextX = living.getX() + horizontal.x * speed;
            double nextZ = living.getZ() + horizontal.z * speed;
            double nextY = CompanionCinematicLandingService.walkStepGroundY(
                    living.level(), nextX, living.getY(), nextZ);
            living.moveTo(nextX, nextY, nextZ, yaw, living.getXRot());
            living.setPos(nextX, nextY, nextZ);
            direction = horizontal;
        } else {
            living.move(MoverType.SELF, direction.scale(speed));
        }
        CompanionCinematicOrientationHelper.faceYaw(living, yaw);
        living.setDeltaMovement(direction.scale(speed * (approach.moveType == CompanionMoveType.FLY
                || approach.overheadArrival ? 0.45 : 0.55)));
        living.fallDistance = 0.0f;
        living.hasImpulse = true;
        approach.moveAge++;
    }

    private static void finish(Approach approach, LivingEntity living, String reason) {
        CompanionFixedPostService.release(approach.companionUuid,
                CompanionFixedPostService.Reason.ARRIVAL);
        if (living == null || living.isRemoved()) {
            return;
        }
        living.noPhysics = approach.originalNoPhysics;
        living.setNoGravity(approach.originalNoGravity);
        living.setDeltaMovement(living.getDeltaMovement().scale(0.35));
        living.fallDistance = 0.0f;
        CompanionAnimationHelper.restoreAnimationControl(living);
        if (living instanceof Mob mob) {
            if (approach.movementLease != null) {
                approach.movementLease.stop();
                mob.goalSelector.removeGoal(approach.movementLease);
            }
            mob.getNavigation().stop();
            if (mob.isNoAi() != approach.originalNoAi) {
                mob.setNoAi(approach.originalNoAi);
            }
        }
        FindMeDebugLogger.info("companion-approach",
                "restored companion={} reason={} noPhysics={} noGravity={}",
                approach.companionUuid, reason, approach.originalNoPhysics, approach.originalNoGravity);
    }

    private static double cruiseSpeed(CompanionMoveType moveType,
                                      CompanionEntityVisualBoundsService.VisualDimensions dimensions) {
        double size = Math.max(dimensions.width(), Math.max(dimensions.depth(), dimensions.height() * 0.55));
        double sizeBonus = Mth.clamp((size - 1.0) * 0.018, 0.0, 0.20);
        return switch (moveType) {
            case FLY -> 0.62 + sizeBonus;
            case SWIM -> 0.46 + sizeBonus * 0.65;
            case WALK, COMPANION -> 0.38 + sizeBonus * 0.55;
        };
    }

    private static boolean useArrivalNoPhysics(CompanionMoveType moveType, LivingEntity living) {
        if (moveType == CompanionMoveType.FLY) {
            return false;
        }
        if (moveType == CompanionMoveType.WALK || moveType == CompanionMoveType.COMPANION) {
            return true;
        }
        return Math.max(living.getBbWidth(), living.getBbHeight()) < 2.6;
    }

    private static final class Approach {
        private final UUID ownerUuid;
        private final UUID companionUuid;
        private final LivingEntity entity;
        private final CompanionMoveType moveType;
        private final Vec3 target;
        private final boolean originalNoPhysics;
        private final boolean originalNoGravity;
        private final boolean originalNoAi;
        private final boolean arrivalNoPhysics;
        private final boolean overheadArrival;
        private final Goal movementLease;
        private final double initialDistance;
        private final double completionRadius;
        private final double cruiseSpeed;
        private int age;
        private int moveAge;

        private Approach(UUID ownerUuid, LivingEntity entity, CompanionMoveType moveType, int side,
                         Vec3 fixedTarget, boolean overheadArrival) {
            this.ownerUuid = ownerUuid;
            this.companionUuid = entity.getUUID();
            this.entity = entity;
            this.moveType = moveType;
            this.target = fixedTarget == null
                    ? CompanionSpawnPlacementService.findCompanionArrivalTarget(
                    entity, ownerUuid, side, moveType) : fixedTarget;
            this.originalNoPhysics = entity.noPhysics;
            this.originalNoGravity = entity.isNoGravity();
            this.originalNoAi = entity instanceof Mob mob && mob.isNoAi();
            this.overheadArrival = overheadArrival;
            this.arrivalNoPhysics = overheadArrival || useArrivalNoPhysics(moveType, entity);
            CompanionEntityVisualBoundsService.VisualDimensions dimensions =
                    CompanionEntityVisualBoundsService.effectDimensions(entity);
            double visualWidth = Math.max(dimensions.width(), dimensions.depth());
            this.completionRadius = Math.max(1.75, visualWidth * 0.34 + 0.85);
            this.initialDistance = Math.max(this.completionRadius, entity.position().distanceTo(this.target));
            this.cruiseSpeed = cruiseSpeed(moveType, dimensions);
            this.movementLease = entity instanceof Mob ? new Goal() {
                {
                    setFlags(EnumSet.of(Flag.MOVE));
                }

                @Override
                public boolean canUse() {
                    return ACTIVE.get(companionUuid) == Approach.this;
                }

                @Override
                public boolean canContinueToUse() {
                    return ACTIVE.get(companionUuid) == Approach.this;
                }
            } : null;
        }
    }
}
