package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.CobblemonPartyPacket;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientCobblemonRevisionTest {
    @AfterEach
    void reset() {
        ClientCobblemonState.reset();
    }

    @Test
    void olderPartySnapshotCannotReplaceNewerPokemonState() {
        UUID newest = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        ClientCobblemonState.update(8L, 0, List.of(entry(newest)));
        long localRevision = ClientCobblemonState.revision();

        ClientCobblemonState.update(7L, 0, List.of(entry(stale)));

        assertEquals(8L, ClientCobblemonState.serverRevision());
        assertEquals(newest, ClientCobblemonState.entries().getFirst().uuid());
        assertEquals(localRevision, ClientCobblemonState.revision());
    }

    private static CobblemonPartyPacket.Entry entry(UUID uuid) {
        return new CobblemonPartyPacket.Entry(0, uuid, -1, "test", false, false, false,
                true, CompanionMoveType.FLY, 20.0f, 20.0f, null);
    }
}
