package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionMoveType;
import org.junit.jupiter.api.Test;

class CompanionRideHomeJourneyServiceTest {
    @Test
    void flyingRideHomeUsesFallbackAfterApproachWindowWhileFalling() {
        assertFalse(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 39, false, false, -0.8));
        assertTrue(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 40, false, false, -0.8));
    }

    @Test
    void fallbackDoesNotPullGroundOrSwimmingMountsIntoTheAir() {
        assertFalse(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.WALK, 80, false, false, -0.8));
        assertFalse(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.SWIM, 80, false, false, -0.8));
    }

    @Test
    void fallbackStopsOncePlayerIsSafe() {
        assertFalse(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 80, true, false, -0.8));
        assertFalse(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 80, false, true, -0.8));
        assertFalse(CompanionRideHomeJourneyService.shouldUseAirborneCatchFallback(
                CompanionMoveType.FLY, 80, false, false, 0.0));
    }

    @Test
    void flyingRideHomeCompletesAsSoonAsVisibleMountReachesCatchZone() {
        assertTrue(CompanionRideHomeJourneyService.shouldCompleteAirborneCatchAtContact(
                CompanionMoveType.FLY, true, false, true));
        assertFalse(CompanionRideHomeJourneyService.shouldCompleteAirborneCatchAtContact(
                CompanionMoveType.FLY, true, true, true));
        assertFalse(CompanionRideHomeJourneyService.shouldCompleteAirborneCatchAtContact(
                CompanionMoveType.WALK, true, false, true));
    }
}
