package com.kuzhi.findme.common;

public enum FindMeFontSize {
    EXTRA_SMALL,
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE;

    public String cssClass() {
        return "fm-font-size-" + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
