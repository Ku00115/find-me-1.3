package com.kuzhi.findme.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.common.WheelIntentPhase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MountRosterProtocolTest {
    @Test
    void requestCarriesCorrelationIdentityAndTeamContext() {
        UUID requestId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        MountRosterIntentPacket packet = new MountRosterIntentPacket(requestId, MountRosterAction.ACTIVATE,
                MountRosterSource.VEHICLE, targetId, 4, 2, 17L);

        assertEquals(requestId, packet.requestId());
        assertEquals(targetId, packet.targetUuid());
        assertEquals(MountRosterSource.VEHICLE, packet.destinationSource());
        assertEquals(4, packet.sourceSlot());
        assertEquals(2, packet.teamIndex());
        assertEquals(17L, packet.expectedRevision());
        assertNotEquals(packet.requestId(), new MountRosterIntentPacket(UUID.randomUUID(), packet.action(),
                packet.destinationSource(), packet.targetUuid(), packet.sourceSlot(), packet.teamIndex(),
                packet.expectedRevision()).requestId());
    }

    @Test
    void resultCorrelatesToRequestAndCarriesTerminalServerRevision() {
        UUID requestId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        MountRosterIntentResultPacket result = new MountRosterIntentResultPacket(requestId,
                MountRosterAction.ACTIVATE, MountRosterSource.COBBLEMON, targetId, 23L,
                WheelIntentPhase.COMPLETED, "mounted");

        assertEquals(requestId, result.requestId());
        assertEquals(targetId, result.targetUuid());
        assertEquals(23L, result.serverRevision());
        assertTrue(result.phase().terminal());
        assertEquals("mounted", result.reason());
    }

    @Test
    void startedIsTheOnlyNonTerminalPhase() {
        for (WheelIntentPhase phase : WheelIntentPhase.values()) {
            if (phase == WheelIntentPhase.STARTED) assertFalse(phase.terminal());
            else assertTrue(phase.terminal(), phase.name());
        }
    }
}
