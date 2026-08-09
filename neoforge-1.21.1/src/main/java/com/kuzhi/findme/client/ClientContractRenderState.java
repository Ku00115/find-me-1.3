package com.kuzhi.findme.client;

import com.kuzhi.findme.network.ContractCameraPacket;
import com.kuzhi.findme.common.ContractCeremonyTimeline;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class ClientContractRenderState {
    private static int targetEntityId = -1;
    private static int durationTicks;
    private static int ticksRemaining;
    private static int endingDurationTicks;
    private static int endingTicksRemaining;
    private static Vec3 playerCircle = Vec3.ZERO;
    private static Vec3 targetCircle = Vec3.ZERO;
    private static float playerRadius = 1.5f;
    private static float targetRadius = 2.2f;
    private static String targetName = "";
    private static Outcome outcome = Outcome.NONE;

    private ClientContractRenderState() {
    }

    public static void start(ContractCameraPacket packet) {
        targetEntityId = packet.targetEntityId();
        durationTicks = Math.max(packet.durationTicks(), 20);
        ticksRemaining = durationTicks;
        playerCircle = new Vec3(packet.playerX(), packet.playerY(), packet.playerZ());
        targetCircle = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
        playerRadius = packet.playerRadius();
        targetRadius = packet.targetRadius();
        targetName = packet.targetName();
        endingDurationTicks = 0;
        endingTicksRemaining = 0;
        outcome = Outcome.NONE;
    }

    public static void finish(ContractCameraPacket packet, Outcome finishOutcome) {
        targetEntityId = packet.targetEntityId();
        if (durationTicks <= 0) {
            durationTicks = ContractCeremonyTimeline.DURATION_TICKS;
        }
        ticksRemaining = 0;
        endingDurationTicks = Math.max(packet.durationTicks(), 12);
        endingTicksRemaining = endingDurationTicks;
        if (!packet.targetName().isBlank()) {
            targetName = packet.targetName();
        }
        playerCircle = new Vec3(packet.playerX(), packet.playerY(), packet.playerZ());
        targetCircle = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
        playerRadius = packet.playerRadius();
        targetRadius = packet.targetRadius();
        outcome = finishOutcome == null ? Outcome.NONE : finishOutcome;
    }

    public static void stop() {
        targetEntityId = -1;
        durationTicks = 0;
        ticksRemaining = 0;
        endingDurationTicks = 0;
        endingTicksRemaining = 0;
        targetName = "";
        outcome = Outcome.NONE;
    }

    public static void tick() {
        if (endingTicksRemaining > 0) {
            endingTicksRemaining--;
        } else if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public static boolean active() {
        return ticksRemaining > 0 || endingTicksRemaining > 0;
    }

    public static boolean locksInput() {
        return ticksRemaining > 0 && outcome == Outcome.NONE;
    }

    public static int targetEntityId() {
        return targetEntityId;
    }

    public static float progress(float partialTick) {
        if (outcome != Outcome.NONE) {
            return 1.0f;
        }
        if (durationTicks <= 0) {
            return 1.0f;
        }
        return Math.min(1.0f, Math.max(0.0f, (durationTicks - ticksRemaining + partialTick) / (float)durationTicks));
    }

    public static int age(float partialTick) {
        if (outcome != Outcome.NONE) {
            return durationTicks + Math.max(0, Math.round(endingDurationTicks - endingTicksRemaining + partialTick));
        }
        return Math.max(0, Math.round(durationTicks - ticksRemaining + partialTick));
    }

    public static float endingProgress(float partialTick) {
        if (outcome == Outcome.NONE || endingDurationTicks <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, Math.max(0.0f, (endingDurationTicks - endingTicksRemaining + partialTick) / (float)endingDurationTicks));
    }

    public static Vec3 playerCircle() {
        return playerCircle;
    }

    public static Vec3 targetCircle() {
        return targetCircle;
    }

    public static float playerRadius() {
        return playerRadius;
    }

    public static float targetRadius() {
        return targetRadius;
    }

    public static String targetName() {
        return targetName;
    }

    public static Outcome outcome() {
        return outcome;
    }

    public static Component subtitle() {
        int age = age(0.0f);
        if (outcome == Outcome.COMPLETE) {
            return Component.translatable("message.find_me.contract_sealed", targetName);
        }
        if (outcome == Outcome.CANCEL) {
            return Component.translatable("message.find_me.contract_broken");
        }
        if (age >= ContractCeremonyTimeline.RESPONSE_END_TICK) {
            return Component.translatable("message.find_me.contract_vow", targetName);
        }
        if (age >= ContractCeremonyTimeline.NAME_END_TICK) {
            return Component.translatable("message.find_me.contract_answer", targetName);
        }
        if (age >= ContractCeremonyTimeline.FOCUS_END_TICK) {
            return Component.translatable("message.find_me.contract_name", targetName);
        }
        return Component.empty();
    }

    public static ContractCeremonyTimeline.Stage phase() {
        return ContractCeremonyTimeline.stage(age(0.0f));
    }

    public enum Outcome {
        NONE,
        COMPLETE,
        CANCEL
    }
}
