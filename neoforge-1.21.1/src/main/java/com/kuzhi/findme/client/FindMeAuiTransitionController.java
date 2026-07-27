package com.kuzhi.findme.client;

/**
 * Shared millisecond timeline for FindMe's paper-fold page transitions.
 * CSS owns the smooth per-frame motion; Java only controls the swap point and input lock.
 */
final class FindMeAuiTransitionController {
    private static final long OPEN_NANOS = 380_000_000L;
    private static final long FOLD_IN_NANOS = 145_000_000L;
    private static final long FOLD_OUT_NANOS = 380_000_000L;

    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;
    private long generation;
    private Runnable foldedAction;
    private boolean unfoldAfterAction;

    void enter(Host host) {
        if (host == null) return;
        generation++;
        foldedAction = null;
        unfoldAfterAction = false;
        phase = Phase.OPENING;
        phaseStartedAt = System.nanoTime();
        host.setInputBlocked(true);
        host.beginFoldOut();
    }

    boolean start(Host host, Runnable action, boolean unfoldAfterAction) {
        if (host == null || action == null || phase != Phase.IDLE) return false;
        generation++;
        this.foldedAction = action;
        this.unfoldAfterAction = unfoldAfterAction;
        phase = Phase.FOLDING_IN;
        phaseStartedAt = System.nanoTime();
        host.setInputBlocked(true);
        host.beginFoldIn();
        return true;
    }

    void update(Host host) {
        if (host == null || phase == Phase.IDLE) return;
        long activeGeneration = generation;
        long elapsed = Math.max(0L, System.nanoTime() - phaseStartedAt);
        if (phase == Phase.FOLDING_IN && elapsed >= FOLD_IN_NANOS) {
            Runnable action = foldedAction;
            foldedAction = null;
            try {
                if (unfoldAfterAction) {
                    phase = Phase.FOLDING_OUT;
                    phaseStartedAt = System.nanoTime();
                    action.run();
                    if (generation == activeGeneration) host.beginFoldOut();
                } else {
                    phase = Phase.IDLE;
                    action.run();
                }
            } catch (RuntimeException | Error failure) {
                forceSettle(host);
                throw failure;
            }
            return;
        }
        long duration = phase == Phase.OPENING ? OPEN_NANOS : FOLD_OUT_NANOS;
        if ((phase == Phase.OPENING || phase == Phase.FOLDING_OUT) && elapsed >= duration) {
            phase = Phase.IDLE;
            host.finishFoldOut();
            host.setInputBlocked(false);
        }
    }

    void forceSettle(Host host) {
        generation++;
        phase = Phase.IDLE;
        phaseStartedAt = 0L;
        foldedAction = null;
        unfoldAfterAction = false;
        if (host == null) return;
        host.finishFoldOut();
        host.setInputBlocked(false);
    }

    boolean isRunning() {
        return phase != Phase.IDLE;
    }

    boolean isRevealing() {
        return phase == Phase.OPENING || phase == Phase.FOLDING_OUT;
    }

    enum Phase {
        IDLE,
        OPENING,
        FOLDING_IN,
        FOLDING_OUT
    }

    interface Host {
        void beginFoldIn();

        void beginFoldOut();

        void finishFoldOut();

        void setInputBlocked(boolean blocked);
    }
}
