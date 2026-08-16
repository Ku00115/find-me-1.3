package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.common.CompanionMoveType;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

class CompanionGuardPostServiceTest {
    @Test
    void residentModesUseDistinctMovementAndCombatPolicies() {
        assertEquals(10.0, CompanionGuardPostService.homePatrolRadius(HouseResidentMode.REST, 10.0));
        assertEquals(10.0, CompanionGuardPostService.homePatrolRadius(HouseResidentMode.WANDER, 10.0));
        assertEquals(24.0, CompanionGuardPostService.homePatrolRadius(HouseResidentMode.WANDER, 24.0));
        assertEquals(10.0, CompanionGuardPostService.homePatrolRadius(HouseResidentMode.GUARD, 10.0));

        assertTrue(CompanionGuardPostService.holdsPosition(HouseResidentMode.REST));
        assertFalse(CompanionGuardPostService.holdsPosition(HouseResidentMode.WANDER));
        assertFalse(CompanionGuardPostService.holdsPosition(HouseResidentMode.GUARD));

        assertFalse(CompanionGuardPostService.allowsCombat(HouseResidentMode.REST));
        assertFalse(CompanionGuardPostService.allowsCombat(HouseResidentMode.WANDER));
        assertTrue(CompanionGuardPostService.allowsCombat(HouseResidentMode.GUARD));
    }

    @Test
    void hardBoundaryNeverFallsBelowThePatrolRadius() {
        assertEquals(24.0, CompanionGuardPostService.homeHardRadius(10.0, 24.0));
        assertEquals(30.0, CompanionGuardPostService.homeHardRadius(30.0, 8.0));
        assertEquals(8.0, CompanionGuardPostService.homeHardRadius(4.0, 1.0));
    }

    @Test
    void failedNavigationUsesBoundedExponentialBackoff() {
        assertEquals(40, CompanionGuardPostService.navigationRetryDelay(false, 1));
        assertEquals(80, CompanionGuardPostService.navigationRetryDelay(false, 2));
        assertEquals(160, CompanionGuardPostService.navigationRetryDelay(false, 3));
        assertEquals(200, CompanionGuardPostService.navigationRetryDelay(false, 4));
        assertEquals(40, CompanionGuardPostService.navigationRetryDelay(true, 4));
    }

    @Test
    void patrolDestinationsStayInsideAConservativeShareOfTheConfiguredRadius() {
        assertEquals(3.0, CompanionGuardPostService.patrolDestinationRadius(24.0, 0.0));
        assertEquals(17.28, CompanionGuardPostService.patrolDestinationRadius(24.0, 1.0), 0.0001);
    }

    @Test
    void flyingHomePolicyIsStableAcrossTakeoffAndLanding() {
        assertTrue(CompanionGuardPostService.homeAirbornePolicy(
                CompanionMoveType.FLY, HouseResidentMode.WANDER, false));
        assertTrue(CompanionGuardPostService.homeAirbornePolicy(
                CompanionMoveType.FLY, HouseResidentMode.GUARD, false));
        assertFalse(CompanionGuardPostService.homeAirbornePolicy(
                CompanionMoveType.FLY, HouseResidentMode.REST, true));
        assertFalse(CompanionGuardPostService.homeAirbornePolicy(
                CompanionMoveType.WALK, HouseResidentMode.WANDER, false));
    }

    @Test
    void defenseRadiusUsesACircleInsteadOfTheScanBoxCorners() {
        Vec3 center = new Vec3(0.0, 64.0, 0.0);
        assertTrue(CompanionGuardPostService.withinDefenseRadius(
                new Vec3(24.0, 64.0, 0.0), center, 24.0));
        assertFalse(CompanionGuardPostService.withinDefenseRadius(
                new Vec3(17.0, 64.0, 17.0), center, 24.0));
    }
}
