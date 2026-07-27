package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MountSwitchContactPolicyTest {
    @Test
    void contactCannotFinishBeforeMinimumHandoffTime() {
        assertFalse(MountSwitchContactPolicy.canEvaluateContact(1, 0.0, 5.0));
        assertTrue(MountSwitchContactPolicy.canEvaluateContact(2, 6.5, 5.0));
    }

    @Test
    void multipartContactStillRequiresTheRootBodyToApproach() {
        assertFalse(MountSwitchContactPolicy.canEvaluateContact(20, 12.0, 20.0));
        assertTrue(MountSwitchContactPolicy.canEvaluateContact(20, 7.0, 20.0));
    }

    @Test
    void ordinarySizedMountsUseABoundedContactDistance() {
        assertFalse(MountSwitchContactPolicy.canEvaluateContact(20, 4.1, 1.0));
        assertTrue(MountSwitchContactPolicy.canEvaluateContact(20, 4.0, 1.0));
    }
}
