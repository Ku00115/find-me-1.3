package com.kuzhi.findme.server.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionAction;
import org.junit.jupiter.api.Test;

class CompanionWheelTransactionServiceTest {
    @Test
    void supersessionRollsBackOnlyHalfDeployedSummonTargets() {
        assertTrue(CompanionWheelTransactionService.shouldRollbackDeployment(
                CompanionAction.SELECT_SUMMON, false));
        assertFalse(CompanionWheelTransactionService.shouldRollbackDeployment(
                CompanionAction.SELECT_SUMMON, true));
        assertFalse(CompanionWheelTransactionService.shouldRollbackDeployment(
                CompanionAction.RECALL, false));
        assertFalse(CompanionWheelTransactionService.shouldRollbackDeployment(
                CompanionAction.RIDE_HOME, false));
    }
}
