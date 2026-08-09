package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.api.CompanionSpellRole;
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

    @Test
    void splitMagicOrdersRequireOnlyTheirMatchingSpellRole() {
        assertNull(CompanionTacticalOrderService.requiredSpellRole(CompanionTacticalAction.ATTACK_TARGET));
        assertNull(CompanionTacticalOrderService.requiredSpellRole(CompanionTacticalAction.PROTECT_OWNER));
        assertEquals(CompanionSpellRole.ATTACK,
                CompanionTacticalOrderService.requiredSpellRole(CompanionTacticalAction.MAGIC_ATTACK));
        assertEquals(CompanionSpellRole.DEFENSE,
                CompanionTacticalOrderService.requiredSpellRole(CompanionTacticalAction.MAGIC_PROTECT));
        assertEquals(CompanionSpellRole.HEAL,
                CompanionTacticalOrderService.requiredSpellRole(CompanionTacticalAction.MAGIC_SUPPORT));
    }

    @Test
    void onlyPhysicalAndMagicProtectionReactToOwnerThreats() {
        assertTrue(CompanionTacticalOrderService.reactsToOwnerThreat(CompanionTacticalAction.PROTECT_OWNER));
        assertTrue(CompanionTacticalOrderService.reactsToOwnerThreat(CompanionTacticalAction.MAGIC_PROTECT));
        assertFalse(CompanionTacticalOrderService.reactsToOwnerThreat(CompanionTacticalAction.FOLLOW));
        assertFalse(CompanionTacticalOrderService.reactsToOwnerThreat(CompanionTacticalAction.MAGIC_SUPPORT));
    }

    @Test
    void magicAttackRetreatsWhenPressedAndApproachesOnlyFromLongRange() {
        assertEquals(CompanionTacticalOrderService.MagicAttackMovement.RETREAT,
                CompanionTacticalOrderService.magicAttackMovement(6.9 * 6.9));
        assertEquals(CompanionTacticalOrderService.MagicAttackMovement.CAST,
                CompanionTacticalOrderService.magicAttackMovement(12.0 * 12.0));
        assertEquals(CompanionTacticalOrderService.MagicAttackMovement.APPROACH,
                CompanionTacticalOrderService.magicAttackMovement(24.1 * 24.1));
    }

    private static CompanionTacticalOrderService.CommandRequest request(UUID companion) {
        return new CompanionTacticalOrderService.CommandRequest(UUID.randomUUID(), companion,
                CompanionKind.MOUNT, CompanionTacticalAction.MOVE_TO, null, null);
    }
}
