package com.kuzhi.findme.common;

public enum FindMeSettingsAction {
    SYNC(false),
    SAVE(false),
    EXPORT_DEFAULTS(false),
    RESET_DEFAULTS(false),
    RESET_BINDING_HISTORY(false),
    UPDATE(true);

    private final boolean updatesClient;

    FindMeSettingsAction(boolean updatesClient) {
        this.updatesClient = updatesClient;
    }

    public boolean updatesClient() {
        return this.updatesClient;
    }
}
