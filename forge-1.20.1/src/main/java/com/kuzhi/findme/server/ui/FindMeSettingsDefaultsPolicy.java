package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.FindMeUiSettings;

final class FindMeSettingsDefaultsPolicy {
    private FindMeSettingsDefaultsPolicy() {
    }

    static boolean canManageDefaults(boolean hasPermissionLevelTwo, boolean creativeMode) {
        return hasPermissionLevelTwo && creativeMode;
    }

    static FindMeUiSettings resetTarget(boolean defaultsManager, FindMeUiSettings configuredPreset) {
        if (defaultsManager) {
            return FindMeUiSettings.defaults();
        }
        return configuredPreset == null ? FindMeUiSettings.defaults() : configuredPreset.normalized();
    }
}
