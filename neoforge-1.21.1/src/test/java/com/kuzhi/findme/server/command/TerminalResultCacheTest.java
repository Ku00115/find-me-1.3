package com.kuzhi.findme.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerminalResultCacheTest {
    @Test
    void duplicateRequestReplaysTheSameTerminalValueUntilExpiry() {
        TerminalResultCache<UUID, String> cache = new TerminalResultCache<>();
        UUID request = UUID.randomUUID();
        cache.put(request, "completed", 50L);

        assertTrue(cache.contains(request));
        assertEquals("completed", cache.get(request));
        assertEquals(0, cache.expire(49L));
        assertEquals("completed", cache.get(request));
        assertEquals(1, cache.expire(50L));
        assertFalse(cache.contains(request));
        assertNull(cache.get(request));
    }

    @Test
    void replacingACachedResultKeepsOneAuthoritativeValue() {
        TerminalResultCache<UUID, String> cache = new TerminalResultCache<>();
        UUID request = UUID.randomUUID();
        cache.put(request, "first", 10L);
        cache.put(request, "authoritative", 30L);

        assertEquals("authoritative", cache.get(request));
        assertEquals(0, cache.expire(10L));
        assertEquals(1, cache.expire(30L));
    }

    @Test
    void equalRequestIdsRemainIsolatedByPlayer() {
        TerminalResultCache<ProtocolRequestKey, String> cache = new TerminalResultCache<>();
        UUID request = UUID.randomUUID();
        ProtocolRequestKey firstPlayer = new ProtocolRequestKey(UUID.randomUUID(), request);
        ProtocolRequestKey secondPlayer = new ProtocolRequestKey(UUID.randomUUID(), request);

        cache.put(firstPlayer, "first-result", 50L);
        cache.put(secondPlayer, "second-result", 50L);

        assertEquals("first-result", cache.get(firstPlayer));
        assertEquals("second-result", cache.get(secondPlayer));
    }
}
