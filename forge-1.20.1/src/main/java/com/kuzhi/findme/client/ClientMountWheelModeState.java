package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import net.minecraft.client.gui.screens.Screen;

public final class ClientMountWheelModeState {
    private static Mode remembered = Mode.MOUNT;

    private ClientMountWheelModeState() {
    }

    public static void rememberMount() {
        remembered = Mode.MOUNT;
    }

    public static void rememberVehicle() {
        remembered = Mode.VEHICLE;
    }

    public static Screen createScreen() {
        boolean vehicleRidden = ClientVehicleState.allEntries().stream().anyMatch(entry -> entry.ridden());
        boolean mountRidden = ClientCompanionState.allEntries(CompanionKind.MOUNT).stream()
                .anyMatch(entry -> entry.ridden());
        return resolve(vehicleRidden, mountRidden, remembered) == Mode.VEHICLE
                ? new VehicleWheelScreen() : new CompanionWheelScreen(CompanionKind.MOUNT);
    }

    static Mode resolve(boolean vehicleRidden, boolean mountRidden, Mode remembered) {
        if (vehicleRidden != mountRidden) {
            return vehicleRidden ? Mode.VEHICLE : Mode.MOUNT;
        }
        return remembered == null ? Mode.MOUNT : remembered;
    }

    public static void reset() {
        remembered = Mode.MOUNT;
    }

    enum Mode {
        MOUNT,
        VEHICLE
    }
}
