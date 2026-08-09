package com.kuzhi.findme.server.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.server.core.CompanionOperationLockService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExternalActionLeaseServiceTest {
    @AfterEach
    void cleanUp() {
        ExternalActionLeaseService.resetForTests();
        CompanionOperationLockService.resetServerState();
    }

    @Test
    void higherPriorityLeasePreemptsAndStaleCloseCannotReleaseReplacement() {
        UUID owner = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        var first = ExternalActionLeaseService.acquire(null, owner, companion,
                new ResourceLocation("test", "scout"), 10, 100L, 40);
        assertNotNull(first);

        var rejected = ExternalActionLeaseService.acquire(null, owner, companion,
                new ResourceLocation("test", "support"), 10, 101L, 40);
        assertNull(rejected);

        var replacement = ExternalActionLeaseService.acquire(null, owner, companion,
                new ResourceLocation("test", "vanguard"), 20, 101L, 40);
        assertNotNull(replacement);
        assertEquals(replacement.actionId().toString(), "test:vanguard");

        first.close();
        assertEquals(CompanionOperationLockService.Operation.EXTERNAL,
                CompanionOperationLockService.get(companion).operation());
        replacement.close();
        assertNull(CompanionOperationLockService.get(companion));
    }

    @Test
    void nonExternalOperationBlocksLease() {
        UUID owner = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        CompanionOperationLockService.tryBeginExternal(owner, companion, "occupied", 20L, 30);
        assertFalse(CompanionOperationLockService.renew(companion,
                CompanionOperationLockService.Operation.EXTERNAL, "wrong", 21L, 30));

        var lease = ExternalActionLeaseService.acquire(null, owner, companion,
                new ResourceLocation("test", "scout"), 100, 21L, 30);
        assertNull(lease);
    }

    @Test
    void committedTaskResidentCanAcquireExternalActionWithoutNormalDeploymentSlot() {
        assertTrue(ExternalActionLeaseService.eligibleForExternalAction(false, true, true));
        assertTrue(ExternalActionLeaseService.eligibleForExternalAction(true, false, true));
        assertFalse(ExternalActionLeaseService.eligibleForExternalAction(false, false, true));
        assertFalse(ExternalActionLeaseService.eligibleForExternalAction(true, true, false));
    }

    @Test
    void tacticalLeaseTracksExactParentTargetWithoutCreatingOperationLock() {
        UUID owner = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        AtomicReference<UUID> currentTarget = new AtomicReference<>(target);

        var lease = ExternalActionLeaseService.acquireTactical(null, owner, companion, target,
                new ResourceLocation("test", "blade_combat"), 35, 100L, 40, currentTarget::get);

        assertNotNull(lease);
        assertNull(CompanionOperationLockService.get(companion));
        currentTarget.set(UUID.randomUUID());
        assertFalse(lease.isValidAt(101L));
    }

    @Test
    void tacticalLeaseRejectsMissingParentTargetAndLifecycleLock() {
        UUID owner = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        assertNull(ExternalActionLeaseService.acquireTactical(null, owner, companion, target,
                new ResourceLocation("test", "blade_combat"), 35, 100L, 40, () -> null));

        CompanionOperationLockService.tryBeginExternal(owner, companion, "storage-like-lock", 100L, 40);
        assertNull(ExternalActionLeaseService.acquireTactical(null, owner, companion, target,
                new ResourceLocation("test", "blade_combat"), 35, 100L, 40, () -> target));
    }

    @Test
    void expiredLifecycleLockDoesNotBlockTacticalLease() {
        UUID owner = UUID.randomUUID();
        UUID companion = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CompanionOperationLockService.tryBeginExternal(owner, companion, "expired", 10L, 5);

        assertNotNull(ExternalActionLeaseService.acquireTactical(null, owner, companion, target,
                new ResourceLocation("test", "blade_combat"), 35, 100L, 40, () -> target));
    }
}
