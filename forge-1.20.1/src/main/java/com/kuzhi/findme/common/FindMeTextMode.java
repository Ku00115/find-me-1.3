package com.kuzhi.findme.common;

public enum FindMeTextMode {
    OFF,
    PRACTICAL,
    IMMERSIVE;

    public FindMeTextMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
