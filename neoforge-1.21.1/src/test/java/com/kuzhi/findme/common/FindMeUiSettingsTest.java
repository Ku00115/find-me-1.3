package com.kuzhi.findme.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FindMeUiSettingsTest {
    @Test
    void newSettingsUseClassicRadialWheel() {
        assertEquals(FindMeWheelStyle.CLASSIC_RADIAL,
                FindMeUiSettings.load(new CompoundTag()).wheelStyle());
    }

    @Test
    void oldSettingsDefaultToNoRidingCameraOverride() {
        assertEquals(FindMeRidingCameraMode.NONE,
                FindMeUiSettings.load(new CompoundTag()).ridingCameraMode());
    }

    @Test
    void ridingCameraModeRoundTrips() {
        FindMeUiSettings settings = FindMeUiSettings.defaults()
                .withRidingCameraMode(FindMeRidingCameraMode.THIRD_PERSON_FRONT);

        assertEquals(FindMeRidingCameraMode.THIRD_PERSON_FRONT,
                FindMeUiSettings.load(settings.save()).ridingCameraMode());
    }

    @Test
    void bindingAnimationPolicyRoundTripsAndOldSettingsUseFirstType() {
        assertEquals(BindingAnimationPolicy.FIRST_TYPE,
                FindMeUiSettings.load(new CompoundTag()).bindingAnimationPolicy());

        FindMeUiSettings settings = FindMeUiSettings.defaults()
                .withBindingAnimationPolicy(BindingAnimationPolicy.FIRST_TYPE);

        assertEquals(BindingAnimationPolicy.FIRST_TYPE,
                FindMeUiSettings.load(settings.save()).bindingAnimationPolicy());
    }

    @Test
    void restoredPresentationAndDefaultTeamSettingsRoundTrip() {
        FindMeUiSettings settings = FindMeUiSettings.defaults()
                .changed(0, 0)
                .changed(0, 2)
                .changed(2, 2)
                .withDefaultTeamIndex(3);

        FindMeUiSettings loaded = FindMeUiSettings.load(settings.save());
        assertTrue(loaded.rotateModels());
        assertFalse(loaded.operationSounds());
        assertFalse(loaded.showHealth());
        assertEquals(3, loaded.defaultTeamIndex());
    }

    @Test
    void nativeMountInteractionDefaultsOffAndRoundTrips() {
        assertFalse(FindMeUiSettings.load(new CompoundTag()).preferNativeMountInteraction());

        FindMeUiSettings enabled = FindMeUiSettings.defaults().changed(0, 4);
        assertTrue(FindMeUiSettings.load(enabled.save()).preferNativeMountInteraction());
        assertFalse(enabled.resetSection(0).preferNativeMountInteraction());
    }
}
