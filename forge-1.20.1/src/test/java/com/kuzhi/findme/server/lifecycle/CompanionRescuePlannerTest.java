package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionRescuePlannerTest {
    @Test
    void highWaterLandingUsesConfiguredRescueThreshold() {
        assertFalse(CompanionRescuePlanner.shouldRescueWaterLanding(4.99, 5.0));
        assertTrue(CompanionRescuePlanner.shouldRescueWaterLanding(5.0, 5.0));
        assertTrue(CompanionRescuePlanner.shouldRescueWaterLanding(40.0, 5.0));
    }


    @Test
    void rescueAnimationRequiresEnoughRoomForConfiguredWaitHeight() {
        CompanionRescuePlanner.Plan atBoundary = new CompanionRescuePlanner.Plan(true,
                CompanionRescuePlanner.Urgency.LANDING_SUMMON, true, false,
                16.0, 8, 6, 4.0, 7.0);
        CompanionRescuePlanner.Plan aboveBoundary = new CompanionRescuePlanner.Plan(true,
                CompanionRescuePlanner.Urgency.RAPID, true, false,
                16.01, 9, 7, 4.0, 7.01);

        assertEquals(RescueFlightMode.HOVER, atBoundary.flightMode());
        assertEquals(RescueFlightMode.HOVER, aboveBoundary.flightMode());
        assertFalse(CompanionRescuePlanner.canPlayCinematic(15.99, 16.0, 8.0));
        assertTrue(CompanionRescuePlanner.canPlayCinematic(16.0, 16.0, 8.0));
        assertFalse(CompanionRescuePlanner.canPlayCinematic(10.99, 8.0, 8.0));
        assertTrue(CompanionRescuePlanner.canPlayCinematic(11.0, 8.0, 8.0));
    }

    @Test
    void flyingHoverHeightScalesWithFallDistanceAndRespectsLimits() {
        double near = CompanionCinematicLandingService.flyingHoverHeight(16.0, 16.0, 8.0, 48.0);
        double medium = CompanionCinematicLandingService.flyingHoverHeight(64.0, 16.0, 8.0, 48.0);
        double high = CompanionCinematicLandingService.flyingHoverHeight(300.0, 16.0, 8.0, 48.0);

        assertTrue(medium > near);
        assertTrue(high > medium);
        assertEquals(48.0, high, 0.0001);
        assertEquals(8.0, near, 0.0001);
    }

    @Test
    void fasterFallReachesTheSameDropSooner() {
        int slow = CompanionRescuePlanner.simulateTicksToDrop(30.0, -0.1, false);
        int fast = CompanionRescuePlanner.simulateTicksToDrop(30.0, -1.0, false);

        assertTrue(fast < slow);
    }

    @Test
    void zeroDropIsImmediate() {
        assertEquals(0, CompanionRescuePlanner.simulateTicksToDrop(0.0, -0.4, false));
    }

    @Test
    void slowFallingProvidesMoreTime() {
        int normal = CompanionRescuePlanner.simulateTicksToDrop(40.0, -0.2, false);
        int slowFalling = CompanionRescuePlanner.simulateTicksToDrop(40.0, -0.2, true);

        assertTrue(slowFalling > normal);
    }

    @Test
    void verticalDropSimulationIncludesGravity() {
        double shortDrop = CompanionRescuePlanner.simulateVerticalDrop(10, -0.2, false);
        double longDrop = CompanionRescuePlanner.simulateVerticalDrop(20, -0.2, false);
        double slowDrop = CompanionRescuePlanner.simulateVerticalDrop(20, -0.2, true);

        assertTrue(shortDrop > 2.0);
        assertTrue(longDrop > shortDrop);
        assertTrue(slowDrop < longDrop);
    }
}
