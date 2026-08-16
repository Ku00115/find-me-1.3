package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

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
        assertFalse(CompanionRescuePlanner.canPlayCinematic(22.99, 20.0));
        assertTrue(CompanionRescuePlanner.canPlayCinematic(23.0, 20.0));
        assertFalse(CompanionRescuePlanner.canPlayCinematic(10.99, 8.0));
        assertTrue(CompanionRescuePlanner.canPlayCinematic(11.0, 8.0));
    }

    @Test
    void flyingHoverHeightScalesWithFallDistanceAndRespectsLimits() {
        double near = CompanionCinematicLandingService.flyingHoverHeight(25.0, 22.0, 30.0);
        double medium = CompanionCinematicLandingService.flyingHoverHeight(35.0, 22.0, 30.0);
        double high = CompanionCinematicLandingService.flyingHoverHeight(100.0, 22.0, 30.0);

        assertTrue(medium > near);
        assertTrue(high > medium);
        assertEquals(22.0, near, 0.0001);
        assertEquals(26.5, medium, 0.0001);
        assertEquals(30.0, high, 0.0001);
    }

    @Test
    void hoverWaitUsesConfiguredHoverHeightInsteadOfCatchHeight() {
        Vec3 configuredHover = new Vec3(10.0, 48.0, 20.0);

        assertEquals(48.0, CompanionCinematicLandingService.flyingWaitY(
                RescueFlightMode.HOVER, 120.0, configuredHover), 0.0001);
        assertEquals(120.0, CompanionCinematicLandingService.flyingWaitY(
                RescueFlightMode.LANDING_SUMMON, 120.0, configuredHover), 0.0001);
    }

    @Test
    void flyingRescueInitialStageUsesTheSameBoundedWaitHeight() {
        assertEquals(87.0, CompanionCinematicLandingService.flyingInitialStageY(
                65.0, 25.0, 22.0, 30.0), 0.0001);
        assertEquals(95.0, CompanionCinematicLandingService.flyingInitialStageY(
                65.0, 100.0, 22.0, 30.0), 0.0001);
        assertEquals(91.0, CompanionCinematicLandingService.flyingInitialStageY(
                65.0, 35.0, 22.0, 30.0), 0.0001);
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
