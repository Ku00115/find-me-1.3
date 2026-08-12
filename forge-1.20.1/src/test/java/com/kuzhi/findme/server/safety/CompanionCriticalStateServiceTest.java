package com.kuzhi.findme.server.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionCriticalStateServiceTest {
    @Test
    void storedSnapshotPreventsStoragePresentationFromClearingCriticalState() {
        assertFalse(CompanionCriticalStateService.shouldClearCritical(true, false, true, 20.0F));
    }

    @Test
    void summonedLivingCompanionClearsCriticalStateOnlyAfterHealing() {
        assertFalse(CompanionCriticalStateService.shouldClearCritical(false, false, true, 1.0F));
        assertTrue(CompanionCriticalStateService.shouldClearCritical(false, false, true, 2.0F));
    }
}
