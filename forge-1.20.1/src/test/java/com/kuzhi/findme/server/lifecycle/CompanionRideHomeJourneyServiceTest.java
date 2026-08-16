package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class CompanionRideHomeJourneyServiceTest {
    @Test
    void sharedRescueKeepsControlPastTheRemovedFortyTickFallback() {
        assertFalse(CompanionRideHomeJourneyService.acquisitionTimedOut(40));
        assertFalse(CompanionRideHomeJourneyService.acquisitionTimedOut(239));
    }

    @Test
    void acquisitionStillCancelsAtItsNormalTimeout() {
        org.junit.jupiter.api.Assertions.assertTrue(
                CompanionRideHomeJourneyService.acquisitionTimedOut(240));
    }
}
