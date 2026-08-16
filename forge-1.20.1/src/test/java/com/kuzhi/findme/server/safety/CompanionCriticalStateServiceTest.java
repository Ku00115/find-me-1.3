package com.kuzhi.findme.server.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
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

    @Test
    void ordinaryCriticalStorageClearsDeployedStateAndUsesStoredLifecycle() {
        for (CompanionKind kind : CompanionKind.values()) {
            PlayerCompanionData data = new PlayerCompanionData();
            UUID companion = UUID.randomUUID();
            data.add(kind, companion);
            data.setDeployed(kind, companion);
            data.setLifecycleState(companion, CompanionLifecycleState.DEPLOYED);
            data.setCritical(companion, true);

            CompanionCriticalStateService.commitStoredState(data, kind, companion, false);

            assertFalse(data.isDeployed(kind, companion));
            assertEquals(CompanionLifecycleState.STORED, data.lifecycleState(companion));
            assertEquals(companion, data.active(kind).orElseThrow());
            assertTrue(data.isCritical(companion));
        }
    }

    @Test
    void homeCriticalStoragePreservesHomeStoredLifecycle() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setDeployed(CompanionKind.COMPANION, companion);

        CompanionCriticalStateService.commitStoredState(data, CompanionKind.COMPANION, companion, true);

        assertFalse(data.isDeployed(CompanionKind.COMPANION, companion));
        assertEquals(CompanionLifecycleState.HOME_STORED, data.lifecycleState(companion));
    }

    @Test
    void homeRecoveryWaitsForAWholeSecond() {
        CompoundTag source = storedHealth(1.0F, 20.0F, 100L);

        CompanionCriticalStateService.HomeRecovery recovery =
                CompanionCriticalStateService.recoverHomeStoredSnapshot(source, 119L);

        assertFalse(recovery.changed());
        assertFalse(recovery.full());
        assertEquals(1.0F, recovery.snapshot().getFloat("CompanionRescueHealth"));
        assertTrue(recovery.snapshot().getBoolean("CompanionRescueCritical"));
    }

    @Test
    void homeRecoveryHealsOnePointPerElapsedSecondAndPreservesRemainder() {
        CompoundTag source = storedHealth(1.0F, 20.0F, 100L);

        CompanionCriticalStateService.HomeRecovery recovery =
                CompanionCriticalStateService.recoverHomeStoredSnapshot(source, 145L);

        assertTrue(recovery.changed());
        assertFalse(recovery.full());
        assertEquals(3.0F, recovery.snapshot().getFloat("CompanionRescueHealth"));
        assertEquals(3.0F, recovery.snapshot().getFloat("Health"));
        assertEquals(140L, recovery.snapshot().getLong("CompanionRescueStoredAt"));
        assertTrue(recovery.snapshot().getBoolean("CompanionRescueCritical"));
    }

    @Test
    void homeRecoveryCatchesUpOfflineAndClearsSnapshotCriticalAtFullHealth() {
        CompoundTag source = storedHealth(1.0F, 6.0F, 100L);

        CompanionCriticalStateService.HomeRecovery recovery =
                CompanionCriticalStateService.recoverHomeStoredSnapshot(source, 10_000L);

        assertTrue(recovery.changed());
        assertTrue(recovery.full());
        assertEquals(6.0F, recovery.snapshot().getFloat("CompanionRescueHealth"));
        assertEquals(6.0F, recovery.snapshot().getFloat("Health"));
        assertFalse(recovery.snapshot().getBoolean("CompanionRescueCritical"));
    }

    @Test
    void alreadyHealedSnapshotStillReportsFullSoPersistentCriticalCanClear() {
        CompoundTag source = storedHealth(20.0F, 20.0F, 100L);
        source.putBoolean("CompanionRescueCritical", false);

        CompanionCriticalStateService.HomeRecovery recovery =
                CompanionCriticalStateService.recoverHomeStoredSnapshot(source, 120L);

        assertFalse(recovery.changed());
        assertTrue(recovery.full());
    }

    private static CompoundTag storedHealth(float health, float maxHealth, long storedAt) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("CompanionRescueHealth", health);
        tag.putFloat("CompanionRescueMaxHealth", maxHealth);
        tag.putFloat("Health", health);
        tag.putLong("CompanionRescueStoredAt", storedAt);
        tag.putBoolean("CompanionRescueCritical", true);
        return tag;
    }
}
