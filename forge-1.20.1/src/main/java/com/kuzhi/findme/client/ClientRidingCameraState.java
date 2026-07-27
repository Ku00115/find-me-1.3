package com.kuzhi.findme.client;

import com.kuzhi.findme.common.FindMeRidingCameraMode;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/** Applies the configured riding perspective without competing with cinematic camera ownership. */
final class ClientRidingCameraState {
    private static final int DISMOUNT_GRACE_TICKS = 8;
    private static CameraType beforeRide;
    private static boolean ridingSession;
    private static boolean cinematicWasActive;
    private static FindMeRidingCameraMode lastMode = FindMeRidingCameraMode.NONE;
    private static int dismountTicks;

    private ClientRidingCameraState() {
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            clear();
            return;
        }
        FindMeRidingCameraMode mode = ClientWheelPresentationState.ridingCameraMode();
        boolean riding = minecraft.player.getVehicle() != null;
        boolean cinematic = ClientContractCamera.active();
        if (mode == FindMeRidingCameraMode.NONE) {
            if (ridingSession && !cinematic) restore(minecraft);
            else if (!riding) clear();
            lastMode = mode;
            return;
        }

        if (riding) {
            dismountTicks = 0;
            boolean started = !ridingSession;
            if (started) {
                beforeRide = minecraft.options.getCameraType();
                ridingSession = true;
            }
            if (!cinematic && (started || cinematicWasActive || mode != lastMode)) {
                ClientCameraLock.acceptManagedCameraType(cameraType(mode));
            }
        } else if (ridingSession && ++dismountTicks > DISMOUNT_GRACE_TICKS && !cinematic) {
            restore(minecraft);
        }
        cinematicWasActive = cinematic;
        lastMode = mode;
    }

    private static CameraType cameraType(FindMeRidingCameraMode mode) {
        return switch (mode) {
            case FIRST_PERSON -> CameraType.FIRST_PERSON;
            case THIRD_PERSON_FRONT -> CameraType.THIRD_PERSON_FRONT;
            case THIRD_PERSON_BACK, NONE -> CameraType.THIRD_PERSON_BACK;
        };
    }

    private static void restore(Minecraft minecraft) {
        if (beforeRide != null) ClientCameraLock.acceptManagedCameraType(beforeRide);
        clear();
    }

    static void clear() {
        beforeRide = null;
        ridingSession = false;
        cinematicWasActive = false;
        lastMode = FindMeRidingCameraMode.NONE;
        dismountTicks = 0;
    }
}
