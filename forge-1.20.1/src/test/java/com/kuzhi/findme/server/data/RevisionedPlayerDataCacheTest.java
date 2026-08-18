package com.kuzhi.findme.server.data;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionedPlayerDataCacheTest {
    @Test
    void retainsDecodedDataUntilTheSavedRevisionChanges() {
        RevisionedPlayerDataCache cache = new RevisionedPlayerDataCache();
        UUID playerUuid = UUID.randomUUID();
        PlayerCompanionData decoded = new PlayerCompanionData();

        cache.put(playerUuid, 4L, decoded);

        assertSame(decoded, cache.get(playerUuid, 4L));
        assertNull(cache.get(playerUuid, 5L));
    }

    @Test
    void removingAPlayerDropsItsDecodedData() {
        RevisionedPlayerDataCache cache = new RevisionedPlayerDataCache();
        UUID playerUuid = UUID.randomUUID();
        cache.put(playerUuid, 1L, new PlayerCompanionData());

        cache.remove(playerUuid);

        assertNull(cache.get(playerUuid, 1L));
    }
}
