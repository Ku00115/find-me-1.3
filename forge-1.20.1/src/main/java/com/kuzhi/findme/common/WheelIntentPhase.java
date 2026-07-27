package com.kuzhi.findme.common;

public enum WheelIntentPhase {
    STARTED(false),
    COMPLETED(true),
    REJECTED(true),
    FAILED(true),
    CANCELLED(true),
    TIMED_OUT(true);

    private final boolean terminal;

    WheelIntentPhase(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean terminal() {
        return terminal;
    }
}
