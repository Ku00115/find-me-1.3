package com.kuzhi.findme.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.api.event.CompanionLifecycleEvent.Change;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionLifecycleEventServiceTest {
    @Test
    void registrationAndDeploymentAreBothObservable() {
        UUID uuid = UUID.randomUUID();
        PlayerCompanionData before = new PlayerCompanionData();
        PlayerCompanionData after = new PlayerCompanionData();
        after.add(CompanionKind.COMPANION, uuid);
        after.setDeployed(CompanionKind.COMPANION, uuid);
        after.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);

        EnumSet<Change> changes = CompanionLifecycleEventService.changes(before, after, uuid);
        assertTrue(changes.contains(Change.REGISTERED));
        assertTrue(changes.contains(Change.DEPLOYED));
        assertTrue(changes.contains(Change.LIFECYCLE_CHANGED));
    }

    @Test
    void deathAndRecoveryDoNotMasqueradeAsReleaseOrRegistration() {
        UUID uuid = UUID.randomUUID();
        PlayerCompanionData living = new PlayerCompanionData();
        living.add(CompanionKind.COMPANION, uuid);
        living.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        PlayerCompanionData dead = roundTrip(living);
        dead.markDead(uuid, CompanionKind.COMPANION);
        dead.setLifecycleState(uuid, CompanionLifecycleState.DEAD);

        assertEquals(EnumSet.of(Change.DIED),
                CompanionLifecycleEventService.changes(living, dead, uuid));

        PlayerCompanionData recovered = roundTrip(dead);
        recovered.add(CompanionKind.COMPANION, uuid);
        recovered.setLifecycleState(uuid, CompanionLifecycleState.STORED);
        EnumSet<Change> recovery = CompanionLifecycleEventService.changes(dead, recovered, uuid);
        assertTrue(recovery.contains(Change.RECOVERED));
        assertTrue(recovery.contains(Change.STORED));
        assertTrue(!recovery.contains(Change.REGISTERED));
    }

    @Test
    void homeAssignmentAndClearAreIndependentLifecycleSignals() {
        UUID uuid = UUID.randomUUID();
        PlayerCompanionData before = new PlayerCompanionData();
        before.add(CompanionKind.COMPANION, uuid);
        before.setLifecycleState(uuid, CompanionLifecycleState.STORED);
        PlayerCompanionData assigned = roundTrip(before);
        assigned.setHomeHouseId(uuid, UUID.randomUUID());
        assertTrue(CompanionLifecycleEventService.changes(before, assigned, uuid)
                .contains(Change.HOME_ASSIGNED));

        PlayerCompanionData cleared = roundTrip(assigned);
        cleared.clearHomeHouseId(uuid);
        assertTrue(CompanionLifecycleEventService.changes(assigned, cleared, uuid)
                .contains(Change.HOME_CLEARED));
    }

    private static PlayerCompanionData roundTrip(PlayerCompanionData data) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        data.save(tag);
        return PlayerCompanionData.load(tag);
    }
}
