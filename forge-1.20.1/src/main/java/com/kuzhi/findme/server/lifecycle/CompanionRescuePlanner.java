package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;

/** Produces one coherent risk and timing decision for falling-mount rescue. */
public final class CompanionRescuePlanner {
    private static final int MAX_SIMULATION_TICKS = 240;
    private static final int RAPID_TICKS = 30;
    private static final int SAFETY_MARGIN_TICKS = 6;
    static final double RESCUE_MOUNT_CLEARANCE = 3.0;

    private CompanionRescuePlanner() {
    }

    public static Plan plan(ServerPlayer player) {
        if (player == null || player.onGround() || player.isFallFlying() || player.onClimbable()
                || player.isInWater() || player.getAbilities().flying
                || player.getDeltaMovement().y >= -0.01) {
            return Plan.none();
        }
        ServerLevel level = player.serverLevel();
        boolean reliableLanding = CompanionCinematicLandingService.hasReliableLandingBelow(level, player);
        double groundDistance = reliableLanding
                ? CompanionCinematicLandingService.distanceToGround(player) : Double.POSITIVE_INFINITY;
        BlockPos landing = reliableLanding
                ? CompanionCinematicLandingService.predictedLanding(level, player) : player.blockPosition();
        boolean waterLanding = reliableLanding && isWaterLanding(level, landing);
        boolean slowFalling = player.hasEffect(MobEffects.SLOW_FALLING);
        double verticalVelocity = estimatedVerticalVelocity(player);
        int impactTicks = reliableLanding
                ? simulateTicksToDrop(groundDistance, verticalVelocity, slowFalling)
                : Integer.MAX_VALUE;
        double catchHeight = catchHeight(groundDistance);
        int catchTicks = reliableLanding
                ? simulateTicksToDrop(Math.max(0.0, groundDistance - catchHeight),
                verticalVelocity, slowFalling)
                : 1;
        double predictedFallDistance = reliableLanding ? player.fallDistance + groundDistance : Double.POSITIVE_INFINITY;
        double expectedDamage = waterLanding || slowFalling ? 0.0
                : Math.max(0.0, predictedFallDistance - 3.0);
        double configuredDanger = Math.max(1.0, Config.DEFAULT_RESCUE_DANGER_DISTANCE - 3.0);
        boolean waterRescue = waterLanding
                && shouldRescueWaterLanding(predictedFallDistance, Config.DEFAULT_RESCUE_DANGER_DISTANCE);
        boolean landingSummon = reliableLanding && !waterLanding && !slowFalling
                && groundDistance > 0.5 && !canPlayCinematic(groundDistance,
                Config.rescueHoverMinHeight);
        boolean shouldRescue = !reliableLanding || waterRescue || landingSummon
                || expectedDamage >= configuredDanger;
        Urgency urgency = !shouldRescue ? Urgency.NONE
                : landingSummon ? Urgency.LANDING_SUMMON
                : impactTicks <= RAPID_TICKS ? Urgency.RAPID : Urgency.CINEMATIC;
        return new Plan(shouldRescue, urgency, reliableLanding, waterLanding, groundDistance,
                impactTicks, catchTicks, catchHeight, expectedDamage);
    }

    /**
     * ServerPlayer movement packets can leave deltaMovement one or more ticks
     * behind the position the client has already reached. Use the observed
     * previous-tick displacement when it is more dangerous, so rescue does not
     * budget time from stale near-zero vertical velocity.
     */
    static double estimatedVerticalVelocity(ServerPlayer player) {
        if (player == null) {
            return -0.01;
        }
        double observed = player.getY() - player.yOld;
        double reported = player.getDeltaMovement().y;
        return Math.max(-6.0, Math.min(-0.01, Math.min(observed, reported)));
    }

    static int simulateTicksToDrop(double blocks, double initialVelocity, boolean slowFalling) {
        if (!Double.isFinite(blocks) || blocks <= 0.0) {
            return blocks <= 0.0 ? 0 : Integer.MAX_VALUE;
        }
        double remaining = blocks;
        double velocity = Math.min(-0.01, initialVelocity);
        double gravity = slowFalling ? 0.01 : 0.08;
        for (int tick = 1; tick <= MAX_SIMULATION_TICKS; tick++) {
            remaining += velocity;
            if (remaining <= 0.0) {
                return tick;
            }
            velocity = (velocity - gravity) * 0.98;
        }
        return MAX_SIMULATION_TICKS;
    }

    static double simulateVerticalDrop(int ticks, double initialVelocity, boolean slowFalling) {
        double velocity = Math.min(-0.01, initialVelocity);
        double gravity = slowFalling ? 0.01 : 0.08;
        double drop = 0.0;
        for (int tick = 0; tick < Math.max(0, ticks); tick++) {
            drop -= velocity;
            velocity = (velocity - gravity) * 0.98;
        }
        return Math.max(0.0, drop);
    }

    static boolean shouldRescueWaterLanding(double predictedFallDistance, double configuredThreshold) {
        return Double.isFinite(predictedFallDistance)
                && predictedFallDistance >= Math.max(1.0, configuredThreshold);
    }

    static boolean canPlayCinematic(double groundDistance, double minimumSpawnHeight) {
        if (!Double.isFinite(groundDistance)) {
            return true;
        }
        double requiredHeight = Math.max(2.0, minimumSpawnHeight) + RESCUE_MOUNT_CLEARANCE;
        return groundDistance >= requiredHeight;
    }

    private static boolean isWaterLanding(ServerLevel level, BlockPos landing) {
        return level.getFluidState(landing).is(FluidTags.WATER)
                || level.getFluidState(landing.below()).is(FluidTags.WATER);
    }

    private static double catchHeight(double groundDistance) {
        if (!Double.isFinite(groundDistance)) {
            return 4.0;
        }
        if (groundDistance <= 18.0) {
            return Math.max(4.0, groundDistance - 2.0);
        }
        return Math.min(Math.max(groundDistance * 0.45, 18.0),
                Math.min(42.0, groundDistance - 6.0));
    }

    public enum Urgency {
        NONE,
        CINEMATIC,
        RAPID,
        LANDING_SUMMON
    }

    public record Plan(boolean shouldRescue, Urgency urgency, boolean reliableLanding,
                       boolean waterLanding, double groundDistance, int ticksToImpact,
                       int ticksToCatch, double catchHeight, double expectedDamage) {
        static Plan none() {
            return new Plan(false, Urgency.NONE, true, false, 0.0, Integer.MAX_VALUE,
                    Integer.MAX_VALUE, 0.0, 0.0);
        }

        public RescueFlightMode flightMode() {
            return this.shouldRescue && this.reliableLanding && !this.playsCinematic()
                    ? RescueFlightMode.LANDING_SUMMON : RescueFlightMode.HOVER;
        }

        public boolean playsCinematic() {
            return !this.shouldRescue || canPlayCinematic(this.groundDistance,
                    Config.rescueHoverMinHeight);
        }

        public double approachDistance(boolean flying) {
            if (!this.shouldRescue) {
                return 0.0;
            }
            double height = Math.max(0.0, this.groundDistance);
            double desired;
            if (flying) {
                desired = height <= 80.0 ? Mth.clamp(12.0 + height * 0.45, 16.0, 48.0)
                        : Math.min(192.0, 48.0 + Math.sqrt(height - 80.0) * 3.7);
            } else {
                desired = height <= 80.0 ? Mth.clamp(10.0 + height * 0.5, 14.0, 52.0)
                        : Math.min(144.0, 52.0 + Math.sqrt(height - 80.0) * 2.3);
            }
            int availableTicks = Math.max(1, (flying ? this.ticksToCatch : this.ticksToImpact)
                    - SAFETY_MARGIN_TICKS);
            double reachable = availableTicks * (flying ? 1.45 : 0.78);
            double urgencyCap = this.urgency == Urgency.RAPID ? (flying ? 28.0 : 18.0) : desired;
            return Math.max(flying ? 6.0 : 5.0, Math.min(Math.min(desired, urgencyCap), reachable));
        }
    }
}
