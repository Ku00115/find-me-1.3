package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanionSummonServiceTest {
    @Test
    void criticalHomeStoredCompanionCannotBeSummonedBeforeRecovery() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setHomeHouseId(companion, UUID.randomUUID());
        data.setLifecycleState(companion, CompanionLifecycleState.HOME_STORED);
        data.setCritical(companion, true);

        assertTrue(CompanionSummonService.isRecoveringAtHome(data, companion));

        data.setCritical(companion, false);
        assertFalse(CompanionSummonService.isRecoveringAtHome(data, companion));
    }

    @Test
    void ordinaryCriticalStoredCompanionKeepsExistingSummonBehavior() {
        PlayerCompanionData data = new PlayerCompanionData();
        UUID companion = UUID.randomUUID();
        data.add(CompanionKind.COMPANION, companion);
        data.setLifecycleState(companion, CompanionLifecycleState.STORED);
        data.setCritical(companion, true);

        assertFalse(CompanionSummonService.isRecoveringAtHome(data, companion));
    }
}
