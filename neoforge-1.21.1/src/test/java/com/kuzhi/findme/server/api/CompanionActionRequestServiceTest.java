package com.kuzhi.findme.server.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.api.CompanionActionRequest.Action;
import com.kuzhi.findme.api.CompanionDescriptor;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionActionRequestServiceTest {
    @Test
    void deploymentRequiresCommittedLiveDeployedState() {
        CompanionDescriptor pending = descriptor(CompanionLifecycleState.DEPLOYED, true, false, false);
        CompanionDescriptor ready = descriptor(CompanionLifecycleState.DEPLOYED, true, true, false);
        assertFalse(CompanionActionRequestService.satisfied(pending, Action.DEPLOY));
        assertTrue(CompanionActionRequestService.satisfied(ready, Action.DEPLOY));
    }

    @Test
    void storageRequiresSnapshotAndNoAuthoritativeLiveEntity() {
        CompanionDescriptor pending = descriptor(CompanionLifecycleState.STORED, false, true, true);
        CompanionDescriptor stored = descriptor(CompanionLifecycleState.STORED, false, false, true);
        assertFalse(CompanionActionRequestService.satisfied(pending, Action.STORE));
        assertTrue(CompanionActionRequestService.satisfied(stored, Action.STORE));
    }

    private static CompanionDescriptor descriptor(CompanionLifecycleState lifecycle, boolean deployed,
                                                  boolean live, boolean stored) {
        return new CompanionDescriptor(UUID.randomUUID(), UUID.randomUUID(), CompanionKind.COMPANION,
                lifecycle, CompanionMoveType.WALK, Optional.empty(), deployed, live, stored,
                Optional.empty(), 1L);
    }
}
