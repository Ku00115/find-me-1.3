package com.kuzhi.findme.common;

public enum FindMeFontFamily {
    DEFAULT,
    SANS,
    UI,
    HUMANIST,
    SERIF,
    FANGSONG,
    KAITI,
    MONOSPACE,
    LEGACY;

    public String cssClass() {
        return "fm-font-family-" + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
