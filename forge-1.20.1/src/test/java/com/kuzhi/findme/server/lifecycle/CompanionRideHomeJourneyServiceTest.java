package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import net.minecraft.world.phys.Vec3;
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

    @Test
    void mountedFlightClimbsWhenTheForwardRouteIsBlocked() {
        Vec3 step = CompanionCinematicMovementService.selectMountedFlightStep(
                new Vec3(1.0, 0.0, 0.0), 0.4, false, true, 0.86);

        assertEquals(0.0, step.x, 1.0E-6);
        assertEquals(0.86, step.y, 1.0E-6);
        assertEquals(0.0, step.z, 1.0E-6);
    }

    @Test
    void mountedFlightKeepsItsRouteWhenForwardSpaceIsOpen() {
        Vec3 step = CompanionCinematicMovementService.selectMountedFlightStep(
                new Vec3(1.0, 0.0, 0.0), 0.4, true, true, 0.86);

        assertEquals(0.4, step.x, 1.0E-6);
        assertEquals(0.0, step.y, 1.0E-6);
    }
}
