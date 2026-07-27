package com.kuzhi.findme.common;

public enum FindMeModule {
    RIDING("riding", true),
    COMPANIONS("companions", true),
    MANAGEMENT("management", true),
    HOUSES("houses", true),
    SLEEP_REVIVAL("sleep_revival", true);

    private final String id;
    private final boolean defaultEnabled;

    FindMeModule(String id, boolean defaultEnabled) {
        this.id = id;
        this.defaultEnabled = defaultEnabled;
    }

    public String id() {
        return this.id;
    }

    public boolean defaultEnabled() {
        return this.defaultEnabled;
    }

    public long bit() {
        return 1L << this.ordinal();
    }
}
