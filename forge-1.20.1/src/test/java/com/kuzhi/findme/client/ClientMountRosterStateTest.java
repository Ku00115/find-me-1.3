package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.MountRosterSource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientMountRosterStateTest {
    @Test
    void composeSectionsUsesStableSourceOrder() {
        ClientMountRosterState.Entry mount = entry(MountRosterSource.FIND_ME, UUID.randomUUID());
        ClientMountRosterState.Entry vehicle = entry(MountRosterSource.VEHICLE, UUID.randomUUID());

        assertEquals(List.of(mount, vehicle), ClientMountRosterState.composeSections(
                List.of(mount), List.of(vehicle)));
    }

    @Test
    void placeholdersAreNotSelectableAndResultIsImmutable() {
        ClientMountRosterState.Entry placeholder = ClientMountRosterState.Entry.placeholder(
                MountRosterSource.FIND_ME, 3);
        List<ClientMountRosterState.Entry> result = ClientMountRosterState.composeSections(
                List.of(placeholder), List.of());

        assertTrue(result.get(0).empty());
        assertNull(result.get(0).key());
        assertThrows(UnsupportedOperationException.class, () -> result.add(placeholder));
    }

    @Test
    void sourceQualifiedIdentityDistinguishesTheSameUuid() {
        UUID uuid = UUID.randomUUID();
        ClientMountRosterState.Entry mount = entry(MountRosterSource.FIND_ME, uuid);
        ClientMountRosterState.Entry vehicle = entry(MountRosterSource.VEHICLE, uuid);

        assertNotEquals(mount.key(), vehicle.key());
    }

    private static ClientMountRosterState.Entry entry(MountRosterSource source, UUID uuid) {
        return new ClientMountRosterState.Entry(source, uuid, 0, 0, null, null);
    }
}
