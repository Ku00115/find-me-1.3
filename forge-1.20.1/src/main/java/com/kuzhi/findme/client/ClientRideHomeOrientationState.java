package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Keeps rider presentation and outgoing look packets aligned with the cinematic route. */
public final class ClientRideHomeOrientationState {
    private static final float YAW_SMOOTHING = 0.24f;
    private static final float PITCH_SMOOTHING = 0.18f;
    private static boolean active;
    private static float currentYaw;
    private static float currentPitch;
    private static float targetYaw;
    private static float targetPitch;

    private ClientRideHomeOrientationState() {
    }

    public static void update(float yaw, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        targetYaw = Mth.wrapDegrees(yaw);
        targetPitch = Mth.clamp(pitch, -25.0f, 25.0f);
        if (!active) {
            currentYaw = player == null ? targetYaw : player.getYRot();
            currentPitch = player == null ? targetPitch : player.getXRot();
            active = true;
        }
    }

    public static void clear() {
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static void tickBeforePlayer() {
        if (!active) {
            return;
        }
        currentYaw = Mth.rotLerp(YAW_SMOOTHING, currentYaw, targetYaw);
        currentPitch = Mth.lerp(PITCH_SMOOTHING, currentPitch, targetPitch);
        applyCurrent();
    }

    public static void applyForRender() {
        if (active) {
            applyCurrent();
        }
    }

    private static void applyCurrent() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.setYRot(currentYaw);
        player.yRotO = currentYaw;
        player.setXRot(currentPitch);
        player.xRotO = currentPitch;
        player.setYHeadRot(currentYaw);
        player.yHeadRotO = currentYaw;
        player.setYBodyRot(currentYaw);
        player.yBodyRotO = currentYaw;
    }
}
