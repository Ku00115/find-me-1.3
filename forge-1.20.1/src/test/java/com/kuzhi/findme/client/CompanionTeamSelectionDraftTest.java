package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionTeamSelectionDraftTest {
    @Test
    void emptyTeamCanSelectOneMember() {
        CompanionTeamSelectionDraft draft = new CompanionTeamSelectionDraft(6);
        UUID member = UUID.randomUUID();
        draft.begin(List.of(), List.of(member));

        assertTrue(draft.toggle(member));

        assertEquals(List.of(member), draft.selected());
        assertTrue(draft.changed());
    }

    @Test
    void seventhMemberCannotBeSelected() {
        CompanionTeamSelectionDraft draft = new CompanionTeamSelectionDraft(6);
        List<UUID> members = new ArrayList<>();
        for (int index = 0; index < 7; index++) members.add(UUID.randomUUID());
        draft.begin(List.of(), members);
        for (int index = 0; index < 6; index++) assertTrue(draft.toggle(members.get(index)));

        assertFalse(draft.toggle(members.get(6)));

        assertEquals(members.subList(0, 6), draft.selected());
        assertTrue(draft.full());
    }

    @Test
    void existingMemberCanBeDeselectedToProduceAnEmptyTeam() {
        CompanionTeamSelectionDraft draft = new CompanionTeamSelectionDraft(6);
        UUID member = UUID.randomUUID();
        draft.begin(List.of(member), List.of(member));

        assertTrue(draft.toggle(member));

        assertTrue(draft.selected().isEmpty());
        assertTrue(draft.changed());
    }

    @Test
    void deselectingMemberDoesNotChangeDisplayOrder() {
        CompanionTeamSelectionDraft draft = new CompanionTeamSelectionDraft(6);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID available = UUID.randomUUID();
        draft.begin(List.of(first, second), List.of(available, second, first));

        assertEquals(List.of(first, second, available), draft.displayOrder());
        assertTrue(draft.toggle(first));

        assertEquals(List.of(first, second, available), draft.displayOrder());
        assertFalse(draft.contains(first));
    }
}
