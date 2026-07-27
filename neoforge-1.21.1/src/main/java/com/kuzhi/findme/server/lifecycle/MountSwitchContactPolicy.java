package com.kuzhi.findme.server.lifecycle;

final class MountSwitchContactPolicy {
    static final int MIN_APPROACH_TICKS = 2;

    private MountSwitchContactPolicy() {
    }

    static boolean canEvaluateContact(int approachAge, double centerDistance, double bodySize) {
        if (approachAge < MIN_APPROACH_TICKS || !Double.isFinite(centerDistance)) {
            return false;
        }
        double finiteBodySize = Double.isFinite(bodySize) ? Math.max(0.0, bodySize) : 0.0;
        double centerLimit = Math.max(4.0, Math.min(7.0, finiteBodySize + 2.0));
        return centerDistance <= centerLimit;
    }
}
