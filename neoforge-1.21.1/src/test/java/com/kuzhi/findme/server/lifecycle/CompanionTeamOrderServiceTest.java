package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionTeamCommandAction;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionTeamOrderServiceTest {
    @Test
    void cancellationActionsNeverDeployStoredMembers() {
        assertTrue(CompanionTeamCommandAction.CANCEL_PROTECT.cancelsTacticalAction());
        assertTrue(CompanionTeamCommandAction.CANCEL_GUARD.cancelsTacticalAction());
        assertFalse(CompanionTeamCommandAction.CANCEL_PROTECT.deploysTeam());
        assertFalse(CompanionTeamCommandAction.CANCEL_GUARD.deploysTeam());
        assertFalse(CompanionTeamCommandAction.PROTECT_OWNER.cancelsTacticalAction());
    }

    @Test
    void protectKeepsDeployedMembersAndFillsOnlyAvailableSlots() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        assertEquals(List.of(first, second), CompanionTeamOrderService.selectRecipients(
                List.of(first, second, third), List.of(first), 2, true));
        assertEquals(List.of(first), CompanionTeamOrderService.selectRecipients(
                List.of(first, second, third), List.of(outside, first), 2, true));
    }

    @Test
    void nonDeployingCommandsOnlyUseAlreadyDeployedTeamMembers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        assertEquals(List.of(second), CompanionTeamOrderService.selectRecipients(
                List.of(first, second), List.of(outside, second), 8, false));
    }

    @Test
    void guardFormationIsStableAndSeparatesMixedEntityFootprints() {
        UUID small = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID large = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID flying = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<CompanionFormationPlanner.Member> members = List.of(
                new CompanionFormationPlanner.Member(large, 8.0, 10.0, 6.0, false),
                new CompanionFormationPlanner.Member(flying, 4.0, 5.0, 5.0, true),
                new CompanionFormationPlanner.Member(small, 1.0, 1.0, 1.8, false));

        Map<UUID, CompanionFormationPlanner.Offset> first =
                CompanionTeamOrderService.formationOffsets(members);
        Map<UUID, CompanionFormationPlanner.Offset> reordered =
                CompanionTeamOrderService.formationOffsets(List.of(members.get(2), members.get(0), members.get(1)));

        assertEquals(first, reordered);
        assertTrue(horizontalDistance(first.get(small), first.get(large)) >= 11.0);
        assertTrue(horizontalDistance(first.get(large), first.get(flying)) >= 11.0);
        assertTrue(first.get(flying).vertical() >= 0.0);
    }

    private static double horizontalDistance(CompanionFormationPlanner.Offset first,
                                             CompanionFormationPlanner.Offset second) {
        return Math.hypot(first.lateral() - second.lateral(), first.rear() - second.rear());
    }
}
