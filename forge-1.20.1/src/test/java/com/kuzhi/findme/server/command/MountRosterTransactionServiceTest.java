package com.kuzhi.findme.server.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import org.junit.jupiter.api.Test;

class MountRosterTransactionServiceTest {
    @Test
    void onlyDataBackedCompletionChecksDecodePlayerData() {
        assertFalse(MountRosterTransactionService.requiresPlayerData(
                MountRosterSource.FIND_ME, MountRosterAction.ACTIVATE));

        assertTrue(MountRosterTransactionService.requiresPlayerData(
                MountRosterSource.FIND_ME, MountRosterAction.RECALL));
        assertTrue(MountRosterTransactionService.requiresPlayerData(
                MountRosterSource.VEHICLE, MountRosterAction.ACTIVATE));
        assertTrue(MountRosterTransactionService.requiresPlayerData(
                MountRosterSource.VEHICLE, MountRosterAction.RECALL));
    }

    @Test
    void terminalResultsUseTheDestinationSourcesRevisionDomain() {
        assertEquals(17L, MountRosterTransactionService.sourceRevision(
                MountRosterSource.FIND_ME, 17L));
        assertEquals(17L, MountRosterTransactionService.sourceRevision(
                MountRosterSource.VEHICLE, 17L));
    }
}
