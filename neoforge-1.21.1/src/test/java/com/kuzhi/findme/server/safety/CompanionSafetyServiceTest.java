package com.kuzhi.findme.server.safety;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionSafetyServiceTest {
    @Test
    void initialAutomaticBackupsAreDeterministicallyStaggered() {
        int first = CompanionSafetyService.initialAutoBackupDelay(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        int second = CompanionSafetyService.initialAutoBackupDelay(
                UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertTrue(first >= 20 && first <= 200);
        assertTrue(second >= 20 && second <= 200);
        assertTrue(first != second);
    }
}
