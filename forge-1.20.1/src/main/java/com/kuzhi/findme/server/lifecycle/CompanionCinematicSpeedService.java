package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CompanionCinematicSpeedService {
    private static final Map<UUID, PlayerMotionSample> PLAYER_MOTION_SAMPLES = new HashMap<>();
    private static final int MOUNT_SWITCH_SPEED_BOOST_TICKS = 100;
    private static final double MOVING_PLAYER_SPEED_THRESHOLD = 0.25;
    private static final double AIR_SWITCH_PLAYER_SPEED_GAIN = 1.65;
    private static final double AIR_SWITCH_PLAYER_SPEED_CAP_BONUS = 4.5;
    private static final double MOUNT_SWITCH_STUCK_SPEED_MULTIPLIER = 3.0;
    private static final double MOUNT_SWITCH_SPEED_MULTIPLIER = 0.88;
    private static final double DEFAULT_SUMMON_SWITCH_SPEED_SCALE = 0.70;
    private static final double DEFAULT_FLY_CINEMATIC_SPEED = 1.75;

    private CompanionCinematicSpeedService() {
    }

    public static void forgetPlayer(UUID playerUuid) {
        PLAYER_MOTION_SAMPLES.remove(playerUuid);
    }

    public static double cinematicSpeed(PendingMountCinematic cinematic, LivingEntity living, ServerPlayer player, double distance) {
        if (cinematic.mode() == MountCinematicMode.NORMAL_SUMMON) {
            return normalSummonSpeed(cinematic.moveType(), distance) * DEFAULT_SUMMON_SWITCH_SPEED_SCALE;
        }
        if (cinematic.mode().isMountSwitch() && cinematic.moveType() == CompanionMoveType.FLY) {
            double base = baseSpeed(CompanionMoveType.FLY) * MOUNT_SWITCH_SPEED_MULTIPLIER
                    * DEFAULT_SUMMON_SWITCH_SPEED_SCALE;
            double playerSpeed = switchReferenceSpeed(cinematic, player);
            double chaseSpeed = Math.max(base, playerSpeed + 0.55);
            double cap = base + AIR_SWITCH_PLAYER_SPEED_CAP_BONUS;
            return Math.min(distance, applyStuckSpeedBoost(cinematic, Math.min(cap, chaseSpeed)));
        }
        if (cinematic.mode().isRescue()) {
            double groundDistance = CompanionCinematicLandingService.distanceToGround(player);
            double fallSpeed = Math.max(0.35, -player.getDeltaMovement().y);
            if (cinematic.mode().isFlyingRescue()) {
                double speed = flyingRescueSpeed(cinematic, player, distance, groundDistance, fallSpeed);
                return Math.min(distance, speed);
            }
            return groundRescueSpeed(cinematic, player, distance, groundDistance, fallSpeed);
        }
        if (cinematic.mode().isMountSwitch() && cinematic.moveType() != CompanionMoveType.FLY) {
            double playerSpeed = switchReferenceSpeed(cinematic, player);
            double base = baseSpeed(cinematic.moveType()) * MOUNT_SWITCH_SPEED_MULTIPLIER
                    * DEFAULT_SUMMON_SWITCH_SPEED_SCALE;
            double switchSpeed = movingSwitchSpeed(cinematic.moveType(), base, playerSpeed);
            return Math.min(distance, applyStuckSpeedBoost(cinematic, switchSpeed));
        }
        double speed = baseSpeed(cinematic.moveType());
        if (cinematic.mode().isMountSwitch()) {
            speed *= MOUNT_SWITCH_SPEED_MULTIPLIER * DEFAULT_SUMMON_SWITCH_SPEED_SCALE;
        }
        return applyStuckSpeedBoost(cinematic, speed);
    }

    public static double samplePlayerHorizontalSpeed(ServerPlayer player) {
        Vec3 current = player.position();
        int tick = player.tickCount;
        PlayerMotionSample previous = PLAYER_MOTION_SAMPLES.get(player.getUUID());
        double speed = 0.0;
        if (previous != null) {
            if (previous.tick() == tick) {
                return previous.speed();
            }
            Vec3 position = previous.position();
            speed = Math.sqrt(Mth.square(current.x - position.x) + Mth.square(current.z - position.z));
        }
        PLAYER_MOTION_SAMPLES.put(player.getUUID(), new PlayerMotionSample(current, tick, speed));
        return speed;
    }

    private static double flyingRescueSpeed(PendingMountCinematic cinematic, ServerPlayer player, double distance, double groundDistance, double fallSpeed) {
        double blocksUntilCatch = Math.max(1.0, player.getY() - cinematic.catchY());
        double ticksUntilCatch = Math.max(2.0, blocksUntilCatch / fallSpeed);
        double targetTicks = cinematic.flyingRescueStaged() ? Math.max(2.0, ticksUntilCatch - 30.0) : ticksUntilCatch;
        double needed = distance / targetTicks * (cinematic.flyingRescueStaged() ? 1.0 : 1.03);
        double progress = 1.0 - Mth.clamp(blocksUntilCatch / Math.max(1.0, groundDistance), 0.0, 1.0);
        if (!cinematic.flyingRescueStaged()) {
            double base = 2.2 + progress * 3.2;
            double cap = blocksUntilCatch > 50.0 ? 4.0 : (blocksUntilCatch > 30.0 ? 5.2 : (blocksUntilCatch > 20.0 ? 6.8 : (blocksUntilCatch > 10.0 ? 9.0 : 12.0)));
            return applyStuckSpeedBoost(cinematic, Math.min(cap, Math.max(base, needed)));
        }
        double base = 1.6 + progress * 2.4;
        double cap = groundDistance > 70.0 ? 4.5 : (blocksUntilCatch > 30.0 ? 4.8 : (blocksUntilCatch > 18.0 ? 5.5 : (blocksUntilCatch > 10.0 ? 6.8 : 9.0)));
        return applyStuckSpeedBoost(cinematic, Math.min(cap, Math.max(base, needed)));
    }

    public static double rideHomeSpeed(CompanionMoveType moveType, double distance, int age,
                                       boolean approachingHome) {
        double acceleration = Mth.clamp((age + 1) / 18.0, 0.18, 1.0);
        double cruise = switch (moveType) {
            case FLY -> approachingHome ? 0.62 : 0.76;
            case SWIM -> approachingHome ? 0.46 : 0.54;
            case WALK, COMPANION -> approachingHome ? 0.38 : 0.44;
        };
        double braking = approachingHome ? Mth.clamp(distance / 6.0, 0.32, 1.0) : 1.0;
        return Math.min(distance, cruise * acceleration * braking);
    }

    /**
     * Retreats use the same distance bands as a summon. Keeping this here avoids
     * a second hardcoded movement-speed table for switch departures.
     */
    public static double retreatSpeed(CompanionMoveType moveType, double distance) {
        return normalSummonSpeed(moveType, Math.max(0.0, distance));
    }

    static double normalSummonSpeed(CompanionMoveType moveType, double distance) {
        if (moveType == CompanionMoveType.FLY) {
            // Randomized summon origins must not select visibly different speed bands.
            // Hold one cruise speed and ease only through the final approach.
            if (distance > 4.0) return 0.86;
            return Mth.clamp(0.28 + distance * 0.145, 0.28, 0.86);
        }
        if (distance > 12.0) return 0.50;
        if (distance > 5.0) return 0.65;
        return 0.42;
    }

    static double movingSwitchSpeed(CompanionMoveType moveType, double base, double playerSpeed) {
        if (moveType == CompanionMoveType.FLY || playerSpeed <= MOVING_PLAYER_SPEED_THRESHOLD) {
            return base;
        }
        double chaseMargin = moveType == CompanionMoveType.SWIM ? 0.28 : 0.34;
        double cap = moveType == CompanionMoveType.SWIM ? 2.2 : 2.6;
        return Math.min(cap, Math.max(base, playerSpeed * 1.12 + chaseMargin));
    }

    private static double groundRescueSpeed(PendingMountCinematic cinematic, ServerPlayer player, double distance, double groundDistance, double fallSpeed) {
        CompanionRescuePlanner.Plan rescuePlan = CompanionRescuePlanner.plan(player);
        double ticksUntilTenBlocks = Math.max(2.0, rescuePlan.ticksToImpact() == Integer.MAX_VALUE
                ? Math.max(0.0, groundDistance - 10.0) / fallSpeed
                : Math.max(2.0, rescuePlan.ticksToImpact() - 6.0));
        double needed = distance / ticksUntilTenBlocks * 0.92;
        double urgency = 1.0 - Mth.clamp((groundDistance - 10.0) / 70.0, 0.0, 1.0);
        double base = cinematic.moveType() == CompanionMoveType.FLY ? 2.0 + urgency * 3.2 : 0.8 + urgency * 2.2;
        double cap = groundDistance > 80.0 ? 2.4 : (groundDistance > 50.0 ? 3.2 : (groundDistance > 30.0 ? 4.2 : (groundDistance > 20.0 ? 5.5 : (groundDistance > 12.0 ? 7.0 : 9.0))));
        if (groundDistance <= 12.0) {
            cap = Math.max(cap, 9.0);
            base = Math.max(base, 5.5);
        }
        if (cinematic.mode().isGroundOrWaterRescue()) {
            base *= 0.78;
            cap *= 0.78;
        }
        if (cinematic.mode().isAirToGroundSwitch()) {
            double playerSpeed = switchReferenceSpeed(cinematic, player);
            if (playerSpeed > MOVING_PLAYER_SPEED_THRESHOLD) {
                double bonus = Math.min(AIR_SWITCH_PLAYER_SPEED_CAP_BONUS, playerSpeed * AIR_SWITCH_PLAYER_SPEED_GAIN);
                base += bonus * 0.55;
                cap += bonus;
                base = Math.max(base, movingSwitchSpeed(cinematic.moveType(), base, playerSpeed));
            }
        }
        return applyStuckSpeedBoost(cinematic, Math.min(cap, Math.max(base, needed)));
    }

    private static double applyStuckSpeedBoost(PendingMountCinematic cinematic, double speed) {
        if (!cinematic.mode().isMountSwitch()) {
            return speed;
        }
        int age = cinematic.stage() == MountCinematicStage.SWITCH ? cinematic.switchAge() : cinematic.age();
        return age >= MOUNT_SWITCH_SPEED_BOOST_TICKS ? speed * MOUNT_SWITCH_STUCK_SPEED_MULTIPLIER : speed;
    }

    private static double playerHorizontalSpeed(ServerPlayer player) {
        double speed = samplePlayerHorizontalSpeed(player);
        Vec3 movement = player.getDeltaMovement();
        speed = Math.max(speed, movement.horizontalDistance());
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            Vec3 vehicleMovement = vehicle.getDeltaMovement();
            speed = Math.max(speed, vehicleMovement.horizontalDistance());
        }
        return speed;
    }

    private static double switchReferenceSpeed(PendingMountCinematic cinematic, ServerPlayer player) {
        double speed = playerHorizontalSpeed(player);
        Vec3 handoffVelocity = cinematic.rideHandoff().velocity();
        return handoffVelocity == null ? speed
                : Math.max(speed, handoffVelocity.horizontalDistance());
    }

    private static double baseSpeed(CompanionMoveType moveType) {
        return switch (moveType) {
            case FLY -> DEFAULT_FLY_CINEMATIC_SPEED;
            case SWIM -> 0.55;
            case WALK, COMPANION -> 0.42;
        };
    }

}
