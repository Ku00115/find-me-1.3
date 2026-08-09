package com.kuzhi.findme.common;

import java.util.Locale;

/** Persisted behavior selected for a resident while its house is active. */
public enum HouseResidentMode {
    REST,
    WANDER,
    GUARD;

    public static HouseResidentMode parse(String value) {
        if (value == null || value.isBlank()) {
            return WANDER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return WANDER;
        }
    }
}
