package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTacticalAction;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompanionTacticalOrderServiceTest {
    private CompanionTacticalOrderService.CommandRequest tracked;
    private UUID trackedUuid;

    @AfterEach
    void cleanUpRequest() {
        CompanionTacticalOrderService.terminateRequest(tracked, null);
    }

    @Test
    void terminalRejectionRemovesPendingStateAndSyncActionRunsExactlyOnce() {
        trackedUuid = UUID.randomUUID();
        tracked = request(trackedUuid);
        AtomicInteger terminalActions = new AtomicInteger();
        assertTrue(CompanionTacticalOrderService.trackRequest(tracked));
        assertEquals(CompanionTacticalAction.MOVE_TO,
                CompanionTacticalOrderService.currentAction(trackedUuid));

        assertTrue(CompanionTacticalOrderService.terminateRequest(tracked, terminalActions::incrementAndGet));
        assertFalse(CompanionTacticalOrderService.terminateRequest(tracked, terminalActions::incrementAndGet));
        assertEquals(1, terminalActions.get());
        assertNull(CompanionTacticalOrderService.currentAction(trackedUuid));
    }

    @Test
    void staleTerminalCallbackCannotRemoveReplacementRequest() {
        UUID companion = UUID.randomUUID();
        trackedUuid = companion;
        CompanionTacticalOrderService.CommandRequest stale = request(companion);
        tracked = request(companion);
        CompanionTacticalOrderService.trackRequest(stale);
        CompanionTacticalOrderService.trackRequest(tracked);

        assertFalse(CompanionTacticalOrderService.terminateRequest(stale, null));
        assertEquals(CompanionTacticalAction.MOVE_TO,
                CompanionTacticalOrderService.currentAction(companion));
        assertTrue(CompanionTacticalOrderService.terminateRequest(tracked, null));
    }

    private static CompanionTacticalOrderService.CommandRequest request(UUID companion) {
        return new CompanionTacticalOrderService.CommandRequest(UUID.randomUUID(), companion,
                CompanionKind.MOUNT, CompanionTacticalAction.MOVE_TO, null, null);
    }
}
