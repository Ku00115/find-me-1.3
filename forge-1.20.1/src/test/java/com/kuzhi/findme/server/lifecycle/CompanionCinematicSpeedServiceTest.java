package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionMoveType;
import org.junit.jupiter.api.Test;

class CompanionCinematicSpeedServiceTest {
    @Test
    void flyingNormalSummonUsesOneCruiseSpeedAcrossRandomizedOrigins() {
        double expected = CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 5.0);
        assertEquals(expected, CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 18.0));
        assertEquals(expected, CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 64.0));
    }

    @Test
    void flyingNormalSummonOnlySlowsDuringFinalApproach() {
        double far = CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 5.0);
        double near = CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 2.0);
        double contact = CompanionCinematicSpeedService.normalSummonSpeed(CompanionMoveType.FLY, 0.0);
        assertTrue(far > near);
        assertTrue(near > contact);
    }

    @Test
    void groundedSwitchKeepsUpWithRunningPlayer() {
        double walking = CompanionCinematicSpeedService.movingSwitchSpeed(
                CompanionMoveType.WALK, 0.42, 0.12);
        double running = CompanionCinematicSpeedService.movingSwitchSpeed(
                CompanionMoveType.WALK, 0.42, 0.72);
        assertEquals(0.42, walking);
        assertTrue(running > 1.0);
    }

    @Test
    void flyingSwitchRetainsExistingSpeedPolicy() {
        assertEquals(1.2, CompanionCinematicSpeedService.movingSwitchSpeed(
                CompanionMoveType.FLY, 1.2, 2.0));
    }
}
