package com.kuzhi.findme.common;

import net.minecraft.util.Mth;

public final class ContractCeremonyTimeline {
    public static final int DURATION_TICKS = 76;
    public static final int ENDING_TICKS = 16;
    public static final int FOCUS_END_TICK = 12;
    public static final int NAME_END_TICK = 32;
    public static final int RESPONSE_END_TICK = 54;

    private ContractCeremonyTimeline() {
    }

    public static Stage stage(int ageTicks) {
        if (ageTicks < FOCUS_END_TICK) {
            return Stage.FOCUS;
        }
        if (ageTicks < NAME_END_TICK) {
            return Stage.NAME;
        }
        if (ageTicks < RESPONSE_END_TICK) {
            return Stage.RESPONSE;
        }
        return Stage.VOW;
    }

    public static float segmentProgress(float ageTicks, int startTick, int endTick) {
        if (endTick <= startTick) {
            return ageTicks >= endTick ? 1.0f : 0.0f;
        }
        return Mth.clamp((ageTicks - startTick) / (float)(endTick - startTick), 0.0f, 1.0f);
    }

    public enum Stage {
        FOCUS,
        NAME,
        RESPONSE,
        VOW
    }
}
