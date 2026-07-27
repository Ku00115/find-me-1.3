package com.kuzhi.findme.server.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoctorBackupDiffServiceTest {
    @Test
    void comparisonContainsOnlyChangedRecords() {
        UUID unchanged = UUID.randomUUID();
        UUID currentOnly = UUID.randomUUID();
        PlayerCompanionData backup = new PlayerCompanionData();
        backup.add(CompanionKind.MOUNT, unchanged);
        PlayerCompanionData current = new PlayerCompanionData();
        current.add(CompanionKind.MOUNT, unchanged);
        current.add(CompanionKind.COMPANION, currentOnly);

        DoctorBackupDiffService.Result result = DoctorBackupDiffService.compare(current, backup, 0, 2);

        assertEquals(1, result.totalChanges());
        assertEquals(1, result.comparisons().size());
        assertEquals("REMOVED", result.comparisons().getFirst().kind());
        assertNull(result.comparisons().getFirst().backup());
        assertEquals(currentOnly, result.comparisons().getFirst().current().uuid());
    }
}
