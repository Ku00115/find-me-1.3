package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionMoveType;
import org.junit.jupiter.api.Test;

class CompanionWaystoneJourneyServiceTest {
    @Test
    void flyingTeleportCatchesAtVisibleContactBeforeFallback() {
        assertTrue(CompanionWaystoneJourneyService.shouldCompleteAirborneCatchAtContact(
                CompanionMoveType.FLY, true, false, true));
        assertFalse(CompanionWaystoneJourneyService.shouldCompleteAirborneCatchAtContact(
                CompanionMoveType.FLY, true, true, true));
    }

    @Test
    void flyingTeleportUsesBoundedFallbackAfterMissedCatch() {
        assertFalse(CompanionWaystoneJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 39, false, false, -0.8));
        assertTrue(CompanionWaystoneJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 40, false, false, -0.8));
        assertFalse(CompanionWaystoneJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.WALK, 80, false, false, -0.8));
    }
}
