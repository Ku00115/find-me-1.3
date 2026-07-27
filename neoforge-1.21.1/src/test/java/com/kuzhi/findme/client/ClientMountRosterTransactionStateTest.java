package com.kuzhi.findme.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.common.WheelIntentPhase;
import com.kuzhi.findme.network.MountRosterIntentResultPacket;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientMountRosterTransactionStateTest {
    @Test
    void resultMustMatchEverySourceQualifiedRequestField() {
        UUID request = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        MountRosterIntentResultPacket matching = result(request, MountRosterAction.ACTIVATE,
                MountRosterSource.FIND_ME, target);

        assertTrue(ClientMountRosterTransactionState.matches(request, MountRosterAction.ACTIVATE,
                MountRosterSource.FIND_ME, target, matching));
        assertFalse(ClientMountRosterTransactionState.matches(UUID.randomUUID(), MountRosterAction.ACTIVATE,
                MountRosterSource.FIND_ME, target, matching));
        assertFalse(ClientMountRosterTransactionState.matches(request, MountRosterAction.RECALL,
                MountRosterSource.FIND_ME, target, matching));
        assertFalse(ClientMountRosterTransactionState.matches(request, MountRosterAction.ACTIVATE,
                MountRosterSource.VEHICLE, target, matching));
        assertFalse(ClientMountRosterTransactionState.matches(request, MountRosterAction.ACTIVATE,
                MountRosterSource.FIND_ME, UUID.randomUUID(), matching));
    }

    private static MountRosterIntentResultPacket result(UUID request, MountRosterAction action,
                                                         MountRosterSource source, UUID target) {
        return new MountRosterIntentResultPacket(request, action, source, target, 3L,
                WheelIntentPhase.COMPLETED, "done");
    }
}
