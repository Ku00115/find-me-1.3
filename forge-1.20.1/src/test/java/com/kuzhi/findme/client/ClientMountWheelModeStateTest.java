package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClientMountWheelModeStateTest {
    @Test
    void riddenControllerOverridesRememberedPage() {
        assertEquals(ClientMountWheelModeState.Mode.VEHICLE,
                ClientMountWheelModeState.resolve(true, false, ClientMountWheelModeState.Mode.MOUNT));
        assertEquals(ClientMountWheelModeState.Mode.MOUNT,
                ClientMountWheelModeState.resolve(false, true, ClientMountWheelModeState.Mode.VEHICLE));
    }

    @Test
    void lastActivatedPageWinsWhenNothingIsRidden() {
        assertEquals(ClientMountWheelModeState.Mode.VEHICLE,
                ClientMountWheelModeState.resolve(false, false, ClientMountWheelModeState.Mode.VEHICLE));
    }
}
