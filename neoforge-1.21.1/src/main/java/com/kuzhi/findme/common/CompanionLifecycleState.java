package com.kuzhi.findme.common;

/** The only persisted primary state a registered FindMe creature may have. */
public enum CompanionLifecycleState {
    STORED,
    DEPLOYED,
    SHOULDER,
    HOME_STORED,
    HOME_ACTIVE,
    DEAD,
    RECOVERY
}
