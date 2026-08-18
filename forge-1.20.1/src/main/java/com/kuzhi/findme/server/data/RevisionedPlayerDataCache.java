package com.kuzhi.findme.server.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps decoded player data until its authoritative SavedData revision changes. */
final class RevisionedPlayerDataCache {
    private final Map<UUID, CachedData> entries = new HashMap<>();

    PlayerCompanionData get(UUID playerUuid, long revision) {
        CachedData cached = entries.get(playerUuid);
        return cached != null && cached.revision() == revision ? cached.data() : null;
    }

    void put(UUID playerUuid, long revision, PlayerCompanionData data) {
        entries.put(playerUuid, new CachedData(revision, data));
    }

    void remove(UUID playerUuid) {
        entries.remove(playerUuid);
    }

    private record CachedData(long revision, PlayerCompanionData data) {
    }
}
