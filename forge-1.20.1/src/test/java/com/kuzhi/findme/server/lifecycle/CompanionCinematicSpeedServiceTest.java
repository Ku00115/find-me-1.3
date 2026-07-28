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
}
