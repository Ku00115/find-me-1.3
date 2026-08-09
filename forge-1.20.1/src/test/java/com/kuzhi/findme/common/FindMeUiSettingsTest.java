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
    void bindingAnimationPolicyDefaultsOffAndExplicitChoiceRoundTrips() {
        assertEquals(BindingAnimationPolicy.NEVER,
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
    void nativeMountInteractionDefaultsOnAndRoundTrips() {
        assertTrue(FindMeUiSettings.load(new CompoundTag()).preferNativeMountInteraction());

        FindMeUiSettings disabled = FindMeUiSettings.defaults().changed(0, 4);
        assertFalse(FindMeUiSettings.load(disabled.save()).preferNativeMountInteraction());
        assertTrue(disabled.resetSection(0).preferNativeMountInteraction());
    }

    @Test
    void riddenCompanionPromotionDefaultsOnAndRoundTrips() {
        assertTrue(FindMeUiSettings.load(new CompoundTag()).autoPromoteRiddenCompanions());

        FindMeUiSettings disabled = FindMeUiSettings.defaults().changed(0, 5);
        assertFalse(FindMeUiSettings.load(disabled.save()).autoPromoteRiddenCompanions());
        assertTrue(disabled.resetSection(0).autoPromoteRiddenCompanions());
    }

    @Test
    void riddenMountHidingDefaultsOnAndRoundTrips() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.hideRiddenMountWhenLookingDown());

        FindMeUiSettings disabled = defaults.changed(0, 7);
        assertFalse(disabled.hideRiddenMountWhenLookingDown());
        assertFalse(FindMeUiSettings.load(disabled.save()).hideRiddenMountWhenLookingDown());
        assertTrue(disabled.resetSection(0).hideRiddenMountWhenLookingDown());
    }

    @Test
    void summonedOutlineDefaultsOffAndColorRoundTrips() {
        assertEquals(SummonedOutlineMode.OFF,
                FindMeUiSettings.load(new CompoundTag()).summonedOutlineMode());

        FindMeUiSettings black = FindMeUiSettings.defaults()
                .withSummonedOutlineMode(SummonedOutlineMode.BLACK);
        assertEquals(SummonedOutlineMode.BLACK,
                FindMeUiSettings.load(black.save()).summonedOutlineMode());
        assertEquals(SummonedOutlineMode.OFF, black.resetSection(0).summonedOutlineMode());
    }

    @Test
    void companionSummonAnimationSettingIsRetiredAndLegacyValuesAreIgnored() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.mountSummonAnimations());
        assertFalse(defaults.companionSummonAnimations());

        FindMeUiSettings changed = defaults.changed(0, 6);
        assertFalse(changed.mountSummonAnimations());
        assertFalse(changed.companionSummonAnimations());

        CompoundTag legacy = changed.save();
        legacy.putBoolean("companionSummonAnimations", true);
        FindMeUiSettings loaded = FindMeUiSettings.load(legacy).changed(0, 7);
        assertFalse(loaded.mountSummonAnimations());
        assertFalse(loaded.companionSummonAnimations());
        assertTrue(loaded.resetSection(0).mountSummonAnimations());
        assertFalse(loaded.resetSection(0).companionSummonAnimations());
    }
}
