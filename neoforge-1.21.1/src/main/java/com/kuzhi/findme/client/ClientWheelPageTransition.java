package com.kuzhi.findme.client;

/** Short two-phase handoff used when a roster wheel changes page or team. */
final class ClientWheelPageTransition {
    private static final int OUT_TICKS = 3;
    private static final int IN_TICKS = 4;
    private static final int TOTAL_TICKS = OUT_TICKS + IN_TICKS;
    private static final float TRAVEL = 13.0f;

    private int ticks = TOTAL_TICKS;
    private int direction;
    private Runnable midpointAction;

    boolean start(double scrollY, Runnable action) {
        if (scrollY == 0.0 || action == null || active()) return false;
        if (!ClientWheelPresentationState.uiAnimations()) {
            action.run();
            return true;
        }
        this.direction = scrollY < 0.0 ? 1 : -1;
        this.ticks = 0;
        this.midpointAction = action;
        return true;
    }

    void tick() {
        if (!active()) return;
        if (!ClientWheelPresentationState.uiAnimations()) {
            finish();
            return;
        }
        this.ticks++;
        if (this.ticks >= OUT_TICKS) applyMidpoint();
        if (this.ticks >= TOTAL_TICKS) settle();
    }

    void finish() {
        if (!active()) return;
        applyMidpoint();
        settle();
    }

    boolean active() {
        return this.ticks < TOTAL_TICKS;
    }

    Motion motion(float partialTick) {
        if (!active() || !ClientWheelPresentationState.uiAnimations()) return Motion.SETTLED;
        if (this.ticks < OUT_TICKS) {
            float progress = smooth(clamp((this.ticks + partialTick) / OUT_TICKS));
            // Keep the wheel opaque while AUI swaps its canvas contents at the midpoint.
            // Fading the whole canvas exposes a transient white backing surface in AUI 1.2.
            return new Motion(-this.direction * TRAVEL * progress, 1.0f,
                    1.0f - 0.025f * progress);
        }
        float progress = smooth(clamp((this.ticks - OUT_TICKS + partialTick) / IN_TICKS));
        return new Motion(this.direction * TRAVEL * (1.0f - progress), 1.0f,
                0.975f + 0.025f * progress);
    }

    private void applyMidpoint() {
        Runnable action = this.midpointAction;
        this.midpointAction = null;
        if (action != null) action.run();
    }

    private void settle() {
        this.ticks = TOTAL_TICKS;
        this.midpointAction = null;
        this.direction = 0;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float smooth(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    record Motion(float offsetY, float alpha, float scale) {
        private static final Motion SETTLED = new Motion(0.0f, 1.0f, 1.0f);
    }
}
