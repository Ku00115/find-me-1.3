package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Coordinates ownership handoff without taking ownership away from each ride system. */
public final class RideHandoffService {
    private static final double MAX_LAND_HANDOFF_SPEED = 1.5;
    private static final double MAX_LIQUID_HANDOFF_SPEED = 2.0;
    private static final double MAX_AIR_HANDOFF_SPEED = 4.0;
    private static final Map<UUID, TransactionMotion> TRANSACTION_MOTION = new HashMap<>();

    private RideHandoffService() {
    }

    public static Source resolveSource(ServerPlayer player, PlayerCompanionData data) {
        Entity ride = player.getVehicle();
        if (ride == null || ride.isRemoved()) {
            return Source.none();
        }
        if (data.contains(CompanionKind.MOUNT, ride.getUUID())) {
            return source(SourceType.FINDME_MOUNT, ride.getUUID(),
                    CompanionEntityClassifier.moveType(ride, CompanionKind.MOUNT), ride, player);
        }
        if (VehicleManager.isVehicleEntry(data, ride.getUUID())) {
            return vehicleSource(ride, player);
        }
        return source(SourceType.OTHER, ride.getUUID(),
                CompanionEntityClassifier.moveType(ride, CompanionKind.MOUNT), ride, player);
    }

    public static MountCinematicMode modeForMountTarget(Source source, CompanionMoveType targetMoveType,
                                                         boolean fallingRescue) {
        boolean switching = source.present();
        boolean transitionFromAir = switching && source.airborne();
        return CompanionSummonModeService.modeFor(
                CompanionKind.MOUNT, targetMoveType, fallingRescue, switching, transitionFromAir);
    }

    public static boolean beginRetirement(ServerPlayer player, PlayerCompanionData data, Source source,
                                          UUID targetUuid, String reason) {
        if (source == null || !source.present() || source.uuid().equals(targetUuid)) {
            return false;
        }
        UUID operationId = UUID.randomUUID();
        FindMeDebugLogger.info("ride-handoff",
                "operation={} phase=SOURCE_RETIREMENT_REQUESTED player={} sourceType={} source={} sourceMove={} airborne={} target={} reason={}",
                operationId, player.getUUID(), source.type(), source.uuid(), source.moveType(), source.airborne(),
                targetUuid, reason);

        long retirementStartedAt = System.nanoTime();
        boolean started = switch (source.type()) {
            case FINDME_MOUNT -> retireFindMeMount(player, data, source.entity());
            case ENTITY_VEHICLE -> retireEntityVehicle(player, data, source.entity());
            case NONE, OTHER -> false;
        };

        if (source.airborne()) {
            CompanionMountCinematicFlowService.stabilizeFallingPlayer(player);
        }
        FindMeDebugLogger.info("ride-handoff",
                "operation={} phase=SOURCE_RETIREMENT_STARTED player={} sourceType={} source={} target={} result={} retirementMs={}",
                operationId, player.getUUID(), source.type(), source.uuid(), targetUuid, started,
                (System.nanoTime() - retirementStartedAt) / 1_000_000.0);
        return started;
    }

    /**
     * Completes the retirement requirement for a shared ride switch. Rides FindMe does not own are
     * deliberately left alone; every managed source must positively begin retirement.
     */
    public static boolean retireSourceForSwitch(ServerPlayer player, PlayerCompanionData data, Source source,
                                                UUID targetUuid, String reason) {
        if (source == null || !source.present() || source.uuid().equals(targetUuid)) {
            return true;
        }
        if (source.type() == SourceType.OTHER) {
            FindMeDebugLogger.info("ride-handoff",
                    "phase=SOURCE_RETIREMENT_SKIPPED player={} sourceType={} source={} target={} reason={} unmanaged=true",
                    player.getUUID(), source.type(), source.uuid(), targetUuid, reason);
            return true;
        }
        return beginRetirement(player, data, source, targetUuid, reason);
    }

    public static MotionSnapshot captureMotion(Source source, Entity currentVehicle) {
        if (source == null || !source.present()) {
            return MotionSnapshot.none();
        }
        Entity entity = source.entity();
        if ((entity == null || entity.isRemoved()) && currentVehicle != null
                && source.uuid().equals(currentVehicle.getUUID()) && !currentVehicle.isRemoved()) {
            entity = currentVehicle;
        }
        if (entity == null || entity.isRemoved()) {
            return new MotionSnapshot(source.type(), source.uuid(), source.moveType(), Vec3.ZERO,
                    0.0f, 0.0f, false);
        }
        Vec3 velocity = entity.getDeltaMovement();
        MotionSnapshot snapshot = new MotionSnapshot(source.type(), source.uuid(), source.moveType(),
                velocity == null ? Vec3.ZERO : velocity, entity.getYRot(), entity.getXRot(), true);
        FindMeDebugLogger.info("ride-handoff",
                "phase=MOTION_CAPTURED sourceType={} source={} sourceMove={} entity={} velocity={} yaw={} pitch={}",
                source.type(), source.uuid(), source.moveType(), entity.getId(), snapshot.velocity(),
                snapshot.yRot(), snapshot.xRot());
        return snapshot;
    }

    public static void registerTransactionMotion(UUID playerUuid, UUID targetUuid, MotionSnapshot snapshot) {
        if (playerUuid == null || targetUuid == null || snapshot == null) return;
        TRANSACTION_MOTION.put(playerUuid, new TransactionMotion(targetUuid, snapshot));
    }

    public static Optional<MotionSnapshot> consumeTransactionMotion(UUID playerUuid, UUID targetUuid) {
        TransactionMotion pending = playerUuid == null ? null : TRANSACTION_MOTION.get(playerUuid);
        if (pending == null || targetUuid == null || !targetUuid.equals(pending.targetUuid)) {
            return Optional.empty();
        }
        TRANSACTION_MOTION.remove(playerUuid, pending);
        return Optional.of(pending.snapshot);
    }

    public static Optional<MotionSnapshot> transactionMotion(UUID playerUuid, UUID targetUuid) {
        TransactionMotion pending = playerUuid == null ? null : TRANSACTION_MOTION.get(playerUuid);
        return pending != null && targetUuid != null && targetUuid.equals(pending.targetUuid)
                ? Optional.of(pending.snapshot) : Optional.empty();
    }

    public static void clearTransactionMotion(UUID playerUuid, UUID targetUuid) {
        TransactionMotion pending = playerUuid == null ? null : TRANSACTION_MOTION.get(playerUuid);
        if (pending != null && (targetUuid == null || targetUuid.equals(pending.targetUuid))) {
            TRANSACTION_MOTION.remove(playerUuid, pending);
        }
    }

    public static void resetServerState() {
        TRANSACTION_MOTION.clear();
    }

    public static boolean applyCompatibleMotion(MotionSnapshot snapshot, Entity target,
                                                CompanionMoveType targetMoveType) {
        if (snapshot == null || target == null || targetMoveType == null
                || !snapshot.compatibleWith(targetMoveType)) {
            return false;
        }
        Vec3 velocity = snapshot.velocityFor(targetMoveType);
        target.setYRot(snapshot.yRot());
        target.setXRot(snapshot.xRot());
        if (target instanceof LivingEntity living) {
            living.yBodyRot = snapshot.yRot();
            living.yHeadRot = snapshot.yRot();
        }
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        FindMeDebugLogger.info("ride-handoff",
                "phase=MOTION_APPLIED sourceType={} source={} sourceMove={} target={} targetMove={} velocity={} yaw={} pitch={}",
                snapshot.sourceType(), snapshot.sourceUuid(), snapshot.sourceMoveType(), target.getUUID(),
                targetMoveType, velocity, snapshot.yRot(), snapshot.xRot());
        return true;
    }

    public static boolean applyDirectVehicleMotion(MotionSnapshot snapshot, Entity target) {
        if (snapshot == null || target == null || !snapshot.captured()) {
            return false;
        }
        applyRotation(snapshot, target);
        target.setDeltaMovement(snapshot.velocity() == null ? Vec3.ZERO : snapshot.velocity());
        target.hurtMarked = true;
        FindMeDebugLogger.info("ride-handoff",
                "phase=DIRECT_VEHICLE_MOTION_APPLIED sourceType={} source={} target={} velocity={} yaw={} pitch={}",
                snapshot.sourceType(), snapshot.sourceUuid(), target.getUUID(), target.getDeltaMovement(),
                snapshot.yRot(), snapshot.xRot());
        return true;
    }

    private static boolean retireFindMeMount(ServerPlayer player, PlayerCompanionData data, Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (player.getVehicle() == entity) {
            player.stopRiding();
        }
        return CompanionDeploymentService.retireMountAfterSharedRideSwitch(player, data, entity);
    }

    private static boolean retireEntityVehicle(ServerPlayer player, PlayerCompanionData data, Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        return VehicleManager.collectIfFindMeVehicle(player, data, entity);
    }

    private static Source source(SourceType type, UUID uuid, CompanionMoveType moveType, Entity entity,
                                 ServerPlayer player) {
        CompanionMoveType resolvedMoveType = moveType == null ? CompanionMoveType.WALK : moveType;
        boolean airborne = airborneFor(type, resolvedMoveType,
                CompanionCinematicLandingService.distanceToGround(player));
        return new Source(type, uuid, resolvedMoveType, entity, airborne);
    }

    private static Source vehicleSource(Entity ride, ServerPlayer player) {
        double groundDistance = CompanionCinematicLandingService.distanceToGround(player);
        boolean airborne = airborneFor(SourceType.ENTITY_VEHICLE, CompanionMoveType.WALK, groundDistance);
        CompanionMoveType moveType = airborne ? CompanionMoveType.FLY
                : CompanionEntityClassifier.moveType(ride, CompanionKind.MOUNT);
        return new Source(SourceType.ENTITY_VEHICLE, ride.getUUID(), moveType, ride, airborne);
    }

    static boolean airborneFor(SourceType type, CompanionMoveType moveType, double groundDistance) {
        if (groundDistance <= 6.0) {
            return false;
        }
        return type == SourceType.ENTITY_VEHICLE || moveType == CompanionMoveType.FLY;
    }

    private static void applyRotation(MotionSnapshot snapshot, Entity target) {
        target.setYRot(snapshot.yRot());
        target.setXRot(snapshot.xRot());
        if (target instanceof LivingEntity living) {
            living.yBodyRot = snapshot.yRot();
            living.yHeadRot = snapshot.yRot();
        }
    }

    public enum SourceType {
        NONE,
        FINDME_MOUNT,
        ENTITY_VEHICLE,
        OTHER
    }

    public record Source(SourceType type, UUID uuid, CompanionMoveType moveType, Entity entity, boolean airborne) {
        public static Source none() {
            return new Source(SourceType.NONE, new UUID(0L, 0L), CompanionMoveType.WALK, null, false);
        }

        public boolean present() {
            return this.type != SourceType.NONE;
        }
    }

    public record MotionSnapshot(SourceType sourceType, UUID sourceUuid, CompanionMoveType sourceMoveType,
                                 Vec3 velocity, float yRot, float xRot, boolean captured) {
        public static MotionSnapshot none() {
            return new MotionSnapshot(SourceType.NONE, new UUID(0L, 0L), CompanionMoveType.WALK,
                    Vec3.ZERO, 0.0f, 0.0f, false);
        }

        public boolean compatibleWith(CompanionMoveType targetMoveType) {
            return this.captured && targetMoveType != null && targetMoveType != CompanionMoveType.COMPANION
                    && (this.sourceType == SourceType.ENTITY_VEHICLE || this.sourceMoveType == targetMoveType);
        }

        public Vec3 velocityFor(CompanionMoveType targetMoveType) {
            if (!compatibleWith(targetMoveType) || this.velocity == null) {
                return Vec3.ZERO;
            }
            return switch (targetMoveType) {
                case WALK -> clamp(new Vec3(this.velocity.x, 0.0, this.velocity.z), MAX_LAND_HANDOFF_SPEED);
                case SWIM -> clamp(this.velocity, MAX_LIQUID_HANDOFF_SPEED);
                case FLY -> clamp(this.velocity, MAX_AIR_HANDOFF_SPEED);
                case COMPANION -> Vec3.ZERO;
            };
        }

        private static Vec3 clamp(Vec3 velocity, double maxSpeed) {
            double lengthSqr = velocity.lengthSqr();
            return lengthSqr > maxSpeed * maxSpeed
                    ? velocity.scale(maxSpeed / Math.sqrt(lengthSqr))
                    : velocity;
        }
    }

    private record TransactionMotion(UUID targetUuid, MotionSnapshot snapshot) {
    }
}
