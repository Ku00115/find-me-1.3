package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionEscortMovementServiceTest {
    @Test
    void groundFollowerAcceleratesWhenItFallsBehind() {
        double nearby = CompanionEscortMovementService.groundNavigationSpeed(3.0);
        double behind = CompanionEscortMovementService.groundNavigationSpeed(12.0);
        double farBehind = CompanionEscortMovementService.groundNavigationSpeed(30.0);

        assertTrue(behind > nearby);
        assertTrue(farBehind > behind);
    }

    @Test
    void groundFollowerSpeedIsBounded() {
        assertEquals(1.05, CompanionEscortMovementService.groundNavigationSpeed(0.0), 0.0001);
        assertEquals(2.15, CompanionEscortMovementService.groundNavigationSpeed(1000.0), 0.0001);
    }
}
