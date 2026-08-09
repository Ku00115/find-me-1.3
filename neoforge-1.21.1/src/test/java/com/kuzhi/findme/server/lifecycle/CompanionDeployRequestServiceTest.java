package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompanionDeployRequestServiceTest {
    @Test
    void waitingOnExistingWorkDoesNotClaimOperationOwnership() {
        var result = new CompanionDeployRequestService.Result(
                CompanionDeployRequestService.State.WAITING, null);

        assertFalse(result.operationStarted());
    }

    @Test
    void newlyStartedAsyncWorkIsExplicitlyOwned() {
        var started = new CompanionDeployRequestService.Result(
                CompanionDeployRequestService.State.STARTED, null);
        var animated = new CompanionDeployRequestService.Result(
                CompanionDeployRequestService.State.WAITING, null, true);

        assertTrue(started.operationStarted());
        assertTrue(animated.operationStarted());
    }
}
