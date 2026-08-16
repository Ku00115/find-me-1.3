package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class CompanionWaystoneJourneyServiceTest {
    @Test
    void sharedRescueKeepsControlPastTheRemovedFortyTickFallback() {
        assertFalse(CompanionWaystoneJourneyService.acquisitionTimedOut(40));
    }

    @Test
    void acquisitionStillCancelsAtItsNormalTimeout() {
        org.junit.jupiter.api.Assertions.assertTrue(
                CompanionWaystoneJourneyService.acquisitionTimedOut(240));
    }
}
