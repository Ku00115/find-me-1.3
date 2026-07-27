package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientRosterRevisionTest {
    @AfterEach
    void resetClientState() {
        ClientCompanionState.reset();
        ClientVehicleState.reset();
        ClientCompanionWheelController.reset();
    }

    @Test
    void olderCompanionSnapshotCannotReplaceNewerUuidState() {
        UUID newest = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        ClientCompanionState.update(CompanionKind.MOUNT, 9L, 0,
                List.of(companion(newest)), List.of(companion(newest)));
        long localRevision = ClientCompanionState.revision();

        ClientCompanionState.update(CompanionKind.MOUNT, 8L, 0,
                List.of(companion(stale)), List.of(companion(stale)));

        assertEquals(9L, ClientCompanionState.serverRevision(CompanionKind.MOUNT));
        assertEquals(0, ClientCompanionState.serverWheelIndex(CompanionKind.MOUNT, newest));
        assertEquals(-1, ClientCompanionState.serverWheelIndex(CompanionKind.MOUNT, stale));
        assertEquals(localRevision, ClientCompanionState.revision());
    }

    @Test
    void olderVehicleSnapshotCannotReplaceNewerUuidState() {
        UUID newest = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        ClientVehicleState.update(12L, 0, List.of(vehicle(newest)), List.of(vehicle(newest)));
        long localRevision = ClientVehicleState.revision();

        ClientVehicleState.update(11L, 0, List.of(vehicle(stale)), List.of(vehicle(stale)));

        assertEquals(12L, ClientVehicleState.serverRevision());
        assertEquals(0, ClientVehicleState.serverWheelIndex(newest));
        assertEquals(-1, ClientVehicleState.serverWheelIndex(stale));
        assertEquals(localRevision, ClientVehicleState.revision());
    }

    private static CompanionListPacket.Entry companion(UUID uuid) {
        return new CompanionListPacket.Entry(uuid, -1, "minecraft:horse", uuid.toString(), false,
                true, false, false, false, false, null, 20.0f, 20.0f, 0.0f, null,
                null, null, null, null, null, null, null, null);
    }

    private static VehicleListPacket.Entry vehicle(UUID uuid) {
        return new VehicleListPacket.Entry(uuid, -1, "test:vehicle", uuid.toString(), false,
                true, false, false, null, null, null, null, null, null, null, null);
    }
}
