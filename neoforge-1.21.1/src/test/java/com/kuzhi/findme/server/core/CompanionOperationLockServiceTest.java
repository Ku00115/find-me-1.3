package com.kuzhi.findme.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompanionOperationLockServiceTest {
    private UUID ownerA;
    private UUID ownerB;

    @AfterEach
    void cleanUpLocks() {
        CompanionOperationLockService.clearForOwner(ownerA, ignored -> false);
        CompanionOperationLockService.clearForOwner(ownerB, ignored -> false);
    }

    @Test
    void serverStopResetPreventsLocksCrossingWorlds() {
        UUID player = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        assertTrue(CompanionOperationLockService.begin(player, companion,
                CompanionOperationLockService.Operation.SWITCH, "test", 10L, 20).accepted());

        CompanionOperationLockService.resetServerState();

        assertFalse(CompanionOperationLockService.hasActiveForPlayer(player));
        assertNull(CompanionOperationLockService.get(companion));
    }

    @Test
    void clearForOwnerOnlyClearsThatOwnerAndHonorsPreservePredicate() {
        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();
        UUID preserve = UUID.randomUUID();
        UUID clear = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();

        assertTrue(CompanionOperationLockService.begin(ownerA, preserve,
                CompanionOperationLockService.Operation.STORE, "test", 100L, 600).accepted());
        assertTrue(CompanionOperationLockService.begin(ownerA, clear,
                CompanionOperationLockService.Operation.SWITCH, "test", 100L, 600).accepted());
        assertTrue(CompanionOperationLockService.begin(ownerB, otherOwner,
                CompanionOperationLockService.Operation.DEPLOY, "test", 100L, 600).accepted());

        Map<UUID, CompanionOperationLockService.ActiveOperation> removed =
                CompanionOperationLockService.clearForOwner(ownerA, preserve::equals);
        assertEquals(1, removed.size());
        assertTrue(removed.containsKey(clear));
        assertEquals(ownerA, CompanionOperationLockService.get(preserve).playerUuid());
        assertNull(CompanionOperationLockService.get(clear));
        assertFalse(removed.containsKey(otherOwner));
        assertEquals(ownerB, CompanionOperationLockService.get(otherOwner).playerUuid());
    }

    @Test
    void activeLockRejectsButExpiredLockCanBeReplaced() {
        ownerA = UUID.randomUUID();
        ownerB = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        assertTrue(CompanionOperationLockService.begin(ownerA, companion,
                CompanionOperationLockService.Operation.DEPLOY, "first", 10L, 5).accepted());

        CompanionOperationLockService.BeginResult rejected = CompanionOperationLockService.begin(ownerB, companion,
                CompanionOperationLockService.Operation.SWITCH, "second", 15L, 5);
        assertFalse(rejected.accepted());
        assertEquals(ownerA, CompanionOperationLockService.get(companion).playerUuid());

        CompanionOperationLockService.BeginResult replaced = CompanionOperationLockService.begin(ownerB, companion,
                CompanionOperationLockService.Operation.SWITCH, "second", 16L, 5);
        assertTrue(replaced.accepted());
        assertEquals(ownerA, replaced.previous().playerUuid());
        assertEquals(ownerB, CompanionOperationLockService.get(companion).playerUuid());
    }

    @Test
    void renewalAndSourceCheckedEndCannotAffectAnotherLease() {
        ownerA = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        assertTrue(CompanionOperationLockService.tryBeginExternal(ownerA, companion,
                "external:first", 100L, 20));
        assertFalse(CompanionOperationLockService.renew(companion,
                CompanionOperationLockService.Operation.EXTERNAL, "external:other", 110L, 40));
        assertTrue(CompanionOperationLockService.renew(companion,
                CompanionOperationLockService.Operation.EXTERNAL, "external:first", 110L, 40));
        assertEquals(150L, CompanionOperationLockService.get(companion).expiresAt());
        assertFalse(CompanionOperationLockService.endIfSource(companion,
                CompanionOperationLockService.Operation.EXTERNAL, "external:other"));
        assertTrue(CompanionOperationLockService.endIfSource(companion,
                CompanionOperationLockService.Operation.EXTERNAL, "external:first"));
        assertNull(CompanionOperationLockService.get(companion));
    }
}
