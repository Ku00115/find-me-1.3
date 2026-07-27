package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kuzhi.findme.common.CompanionTeamTarget;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientTeamRevisionTest {
    @AfterEach
    void reset() {
        ClientCompanionTeamState.reset();
    }

    @Test
    void olderTeamSnapshotCannotReplaceNewerMergedRosterOrder() {
        UUID newest = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        ClientCompanionTeamState.update(12L, List.of(team(newest)));
        long localRevision = ClientCompanionTeamState.revision();

        ClientCompanionTeamState.update(11L, List.of(team(stale)));

        assertEquals(12L, ClientCompanionTeamState.serverRevision());
        assertEquals(List.of(newest), ClientCompanionTeamState.members(CompanionTeamTarget.MOUNT, 0));
        assertEquals(localRevision, ClientCompanionTeamState.revision());
    }

    private static ClientCompanionTeamState.TeamEntry team(UUID uuid) {
        return new ClientCompanionTeamState.TeamEntry(CompanionTeamTarget.MOUNT, 0, 1, true, "", List.of(uuid));
    }
}
