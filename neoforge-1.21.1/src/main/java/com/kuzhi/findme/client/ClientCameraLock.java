package com.kuzhi.findme.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public final class ClientCameraLock {
    private static final int PRE_MOUNT_TICKS = 120;
    private static final int RIDING_REFRESH_TICKS = 200;
    private static final int DISMOUNT_GRACE_TICKS = 80;
    private static CameraType lockedCameraType;
    private static int ticksRemaining;
    private static int manualChangeTicks;
    private static boolean wasRiding;

    private ClientCameraLock() {
    }

    public static void arm() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (lockedCameraType == null || ticksRemaining <= 0) {
            lockedCameraType = minecraft.options.getCameraType();
        }
        ticksRemaining = Math.max(ticksRemaining, 120);
        wasRiding = minecraft.player.getVehicle() != null;
    }

    public static void allowManualChange() {
        manualChangeTicks = 6;
    }

    public static void tick() {
        boolean riding;
        if (ticksRemaining <= 0 || lockedCameraType == null) {
            if (manualChangeTicks > 0) {
                --manualChangeTicks;
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ticksRemaining = 0;
            lockedCameraType = null;
            wasRiding = false;
            return;
        }
        boolean bl = riding = minecraft.player.getVehicle() != null;
        if (wasRiding && !riding) {
            ticksRemaining = Math.max(ticksRemaining, 80);
        }
        wasRiding = riding;
        if (riding) {
            ticksRemaining = Math.max(ticksRemaining, 200);
        }
        if (minecraft.options.getCameraType() != lockedCameraType) {
            if (manualChangeTicks > 0) {
                lockedCameraType = minecraft.options.getCameraType();
            } else {
                minecraft.options.setCameraType(lockedCameraType);
            }
        }
        if (manualChangeTicks > 0) {
            --manualChangeTicks;
        }
        if (--ticksRemaining <= 0) {
            lockedCameraType = null;
        }
    }

    static void acceptManagedCameraType(CameraType cameraType) {
        if (cameraType == null) return;
        lockedCameraType = cameraType;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType() != cameraType) {
            minecraft.options.setCameraType(cameraType);
        }
    }

    public static void clear() {
        lockedCameraType = null;
        ticksRemaining = 0;
        manualChangeTicks = 0;
        wasRiding = false;
    }
}
