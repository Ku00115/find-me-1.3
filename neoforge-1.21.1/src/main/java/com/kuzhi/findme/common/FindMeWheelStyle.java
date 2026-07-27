package com.kuzhi.findme.common;

public enum FindMeWheelStyle {
    SIX_WING,
    TACTICAL_STRIP,
    FOLDED_SHARDS,
    CLASSIC_RADIAL;

    public FindMeWheelStyle next() {
        FindMeWheelStyle[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
