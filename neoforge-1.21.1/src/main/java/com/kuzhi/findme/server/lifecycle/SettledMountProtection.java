package com.kuzhi.findme.server.lifecycle;

import java.util.UUID;

record SettledMountProtection(UUID mountUuid, long untilTick) {
}
