package com.kuzhi.findme.server.home;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionHomeServiceTest {
    @Test
    void capacityAllowsExistingResidentsButRejectsNewResidentsAtTheLimit() {
        int previousCapacity = Config.houseResidentCapacity;
        try {
            Config.houseResidentCapacity = 2;
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            FindMeWorldSavedData.HouseRecord house = new FindMeWorldSavedData.HouseRecord(
                    UUID.randomUUID(), UUID.randomUUID(), position(0, 64, 0), "Home",
                    new HashSet<>(java.util.Set.of(first, second)));

            assertTrue(CompanionHomeService.hasResidentCapacity(house, first));
            assertFalse(CompanionHomeService.hasResidentCapacity(house, UUID.randomUUID()));
        } finally {
            Config.houseResidentCapacity = previousCapacity;
        }
    }

    @Test
    void capacityUsesTheIndividualHouseSetting() {
        UUID resident = UUID.randomUUID();
        FindMeWorldSavedData.HouseRecord house = new FindMeWorldSavedData.HouseRecord(
                UUID.randomUUID(), UUID.randomUUID(), position(0, 64, 0), "Home",
                new HashSet<>(java.util.Set.of(resident)), java.util.Map.of(), 1, 24, 32);

        assertTrue(CompanionHomeService.hasResidentCapacity(house, resident));
        assertFalse(CompanionHomeService.hasResidentCapacity(house, UUID.randomUUID()));
    }

    @Test
    void houseInteractionRequiresTheSameDimensionAndAnEightBlockRadius() {
        SavedPosition house = position(0, 64, 0);

        assertTrue(HousePageService.withinInteractionDistance(true, 8.5, 64.5, 0.5, house));
        assertFalse(HousePageService.withinInteractionDistance(true, 8.51, 64.5, 0.5, house));
        assertFalse(HousePageService.withinInteractionDistance(false, 0.5, 64.5, 0.5, house));
    }

    @Test
    void staleHouseMembershipIsNeverTreatedAsAnAuthoritativeAssignment() {
        UUID house = UUID.randomUUID();

        assertTrue(HousePageService.hasAuthoritativeHouseAssignment(house, house));
        assertFalse(HousePageService.hasAuthoritativeHouseAssignment(null, house));
        assertFalse(HousePageService.hasAuthoritativeHouseAssignment(UUID.randomUUID(), house));
    }

    @Test
    void removingAResidentNeverInventsAStoredStateWithoutASnapshot() {
        assertEquals(CompanionLifecycleState.DEAD,
                HousePageService.detachedLifecycleState(true, true, true));
        assertEquals(CompanionLifecycleState.DEPLOYED,
                HousePageService.detachedLifecycleState(false, true, true));
        assertEquals(CompanionLifecycleState.STORED,
                HousePageService.detachedLifecycleState(false, false, true));
        assertEquals(CompanionLifecycleState.RECOVERY,
                HousePageService.detachedLifecycleState(false, false, false));
    }

    @Test
    void brokenHouseDispositionPreservesTheOnlyRecoverableState() {
        assertEquals(CompanionHomeService.DetachedResidentDisposition.IGNORE,
                CompanionHomeService.detachedResidentDisposition(false, false, true, false, true));
        assertEquals(CompanionHomeService.DetachedResidentDisposition.DEAD,
                CompanionHomeService.detachedResidentDisposition(true, true, true, true, true));
        assertEquals(CompanionHomeService.DetachedResidentDisposition.STORED,
                CompanionHomeService.detachedResidentDisposition(true, false, true, true, false));
        assertEquals(CompanionHomeService.DetachedResidentDisposition.DEPLOYED,
                CompanionHomeService.detachedResidentDisposition(true, false, true, false, true));
        assertEquals(CompanionHomeService.DetachedResidentDisposition.STORED,
                CompanionHomeService.detachedResidentDisposition(true, false, false, false, true));
        assertEquals(CompanionHomeService.DetachedResidentDisposition.RECOVERY,
                CompanionHomeService.detachedResidentDisposition(true, false, false, false, false));
    }

    @Test
    void globalRestoreBudgetIsSharedWithinATickAndResetsOnTheNextTick() {
        long tick = 91_337L;

        assertTrue(CompanionHomeResidentService.claimGlobalRestoreAttempt(tick, 2));
        assertTrue(CompanionHomeResidentService.claimGlobalRestoreAttempt(tick, 2));
        assertFalse(CompanionHomeResidentService.claimGlobalRestoreAttempt(tick, 2));
        assertTrue(CompanionHomeResidentService.claimGlobalRestoreAttempt(tick + 1, 2));
        assertFalse(CompanionHomeResidentService.claimGlobalRestoreAttempt(tick + 1, 1));
    }

    @Test
    void invalidHouseDetachmentOnlyRunsForLoadedTargets() {
        assertTrue(CompanionHomeResidentService.shouldDetachInvalidLoadedHouse(true, true, false));
        assertFalse(CompanionHomeResidentService.shouldDetachInvalidLoadedHouse(true, false, false));
        assertFalse(CompanionHomeResidentService.shouldDetachInvalidLoadedHouse(false, true, false));
        assertFalse(CompanionHomeResidentService.shouldDetachInvalidLoadedHouse(true, true, true));
    }

    private static SavedPosition position(double x, double y, double z) {
        return new SavedPosition(null, x, y, z, 0.0f, 0.0f);
    }
}
