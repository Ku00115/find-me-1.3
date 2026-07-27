package com.kuzhi.findme.common;

public enum CompanionEffectStyle {
    NONE,
    ENDER,
    MAGIC_CIRCLE,
    VELOCITY_BURST,
    CUSTOM_MAGIC_CIRCLE,
    /** @deprecated Kept only so old player and pack data can be migrated. */
    @Deprecated
    GROUND_EMERGE,
    /** @deprecated Kept only so old player and pack data can be migrated. */
    @Deprecated
    GROUND_SINK;

    public static final CompanionEffectStyle DEFAULT = MAGIC_CIRCLE;

    public CompanionEffectStyle next() {
        return switch (visualStyle()) {
            case NONE -> ENDER;
            case ENDER -> MAGIC_CIRCLE;
            case MAGIC_CIRCLE -> CUSTOM_MAGIC_CIRCLE;
            case CUSTOM_MAGIC_CIRCLE -> VELOCITY_BURST;
            case VELOCITY_BURST, GROUND_EMERGE, GROUND_SINK -> NONE;
        };
    }

    public boolean isLegacyAnimationValue() {
        return this == GROUND_EMERGE || this == GROUND_SINK;
    }

    public CompanionEffectStyle visualStyle() {
        return isLegacyAnimationValue() ? MAGIC_CIRCLE : this;
    }
}
