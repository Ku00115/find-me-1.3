package com.kuzhi.findme.server.command;

import java.util.UUID;

/** Source-qualified server identity for one player's protocol request. */
record ProtocolRequestKey(UUID playerUuid, UUID requestId) {
    ProtocolRequestKey {
        if (playerUuid == null || requestId == null) {
            throw new IllegalArgumentException("Protocol request identity cannot contain null UUIDs");
        }
    }
}
