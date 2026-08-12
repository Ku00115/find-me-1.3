package com.kuzhi.findme.server.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.FindMeUiSettings;
import org.junit.jupiter.api.Test;

class FindMeSettingsDefaultsPolicyTest {
    @Test
    void managingDefaultsRequiresCreativeModeAndPermissionLevelTwo() {
        assertTrue(FindMeSettingsDefaultsPolicy.canManageDefaults(true, true));
        assertFalse(FindMeSettingsDefaultsPolicy.canManageDefaults(true, false));
        assertFalse(FindMeSettingsDefaultsPolicy.canManageDefaults(false, true));
        assertFalse(FindMeSettingsDefaultsPolicy.canManageDefaults(false, false));
    }

    @Test
    void regularResetUsesConfiguredPreset() {
        FindMeUiSettings preset = FindMeUiSettings.defaults().changed(1, 4);

        assertEquals(preset, FindMeSettingsDefaultsPolicy.resetTarget(false, preset));
    }

    @Test
    void authorResetUsesBuiltInDefaults() {
        FindMeUiSettings preset = FindMeUiSettings.defaults().changed(1, 4);

        assertEquals(FindMeUiSettings.defaults(),
                FindMeSettingsDefaultsPolicy.resetTarget(true, preset));
    }
}
