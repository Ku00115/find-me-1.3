package com.kuzhi.findme.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FindMeUiSettingsTest {
    @Test
    void dossierFontIsDefaultAndOriginalUiFontRoundTrips() {
        assertEquals(FindMeFontFamily.SANS, FindMeUiSettings.defaults().fontFamily());
        assertEquals(FindMeFontFamily.SANS, FindMeUiSettings.load(new CompoundTag()).fontFamily());
        assertEquals("fm-font-family-legacy", FindMeFontFamily.LEGACY.cssClass());

        FindMeUiSettings originalUiFont = FindMeUiSettings.defaults().withFontFamily(FindMeFontFamily.LEGACY);
        assertEquals(FindMeFontFamily.LEGACY, FindMeUiSettings.load(originalUiFont.save()).fontFamily());
    }

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
    void presentationSettingsRoundTrip() {
        FindMeUiSettings settings = FindMeUiSettings.defaults()
                .changed(0, 0)
                .changed(0, 2)
                .changed(2, 2);

        FindMeUiSettings loaded = FindMeUiSettings.load(settings.save());
        assertTrue(loaded.rotateModels());
        assertFalse(loaded.operationSounds());
        assertFalse(loaded.showHealth());
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
    void friendlyFireProtectionDefaultsOnAndRoundTrips() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.friendlyFireProtection());

        FindMeUiSettings disabled = defaults.changed(1, 3);
        assertFalse(disabled.friendlyFireProtection());
        assertFalse(FindMeUiSettings.load(disabled.save()).friendlyFireProtection());
        assertTrue(disabled.resetSection(1).friendlyFireProtection());
    }

    @Test
    void automaticBindingStorageDefaultsOnAndRoundTrips() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.autoStoreOnBinding());

        FindMeUiSettings disabled = defaults.changed(1, 4);
        assertFalse(disabled.autoStoreOnBinding());
        assertFalse(FindMeUiSettings.load(disabled.save()).autoStoreOnBinding());
        assertTrue(disabled.resetSection(1).autoStoreOnBinding());
    }

    @Test
    void automaticTeamOrganizationDefaultsOnAndRoundTrips() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.autoOrganizeTeams());

        FindMeUiSettings disabled = defaults.changed(1, 5);
        assertFalse(disabled.autoOrganizeTeams());
        assertFalse(FindMeUiSettings.load(disabled.save()).autoOrganizeTeams());
        assertTrue(disabled.resetSection(1).autoOrganizeTeams());
    }

    @Test
    void boundCreatureBlockProtectionDefaultsOnAndRoundTrips() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.boundCreatureBlockProtection());

        FindMeUiSettings disabled = defaults.changed(1, 6);
        assertFalse(disabled.boundCreatureBlockProtection());
        assertFalse(FindMeUiSettings.load(disabled.save()).boundCreatureBlockProtection());
        assertTrue(disabled.resetSection(1).boundCreatureBlockProtection());
    }

    @Test
    void fallingAnimationDefaultsOffAndRoundTripsThroughServerSettings() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertFalse(defaults.fallingAnimation());

        FindMeUiSettings enabled = defaults.changed(0, 8);
        assertTrue(enabled.fallingAnimation());
        assertTrue(FindMeUiSettings.load(enabled.save()).fallingAnimation());
        assertFalse(enabled.resetSection(0).fallingAnimation());
    }

    @Test
    void guiOpacityDefaultsToFullAndRoundTripsThroughServerSettings() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertEquals(100, defaults.guiOpacityPercent());

        FindMeUiSettings translucent = defaults.withGuiOpacityPercent(60);
        assertEquals(60, FindMeUiSettings.load(translucent.save()).guiOpacityPercent());
        assertEquals(100, translucent.resetSection(0).guiOpacityPercent());
        assertEquals(20, defaults.withGuiOpacityPercent(0).guiOpacityPercent());
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
    void retiredSettingsAreNotSavedAndLegacyValuesAreIgnored() {
        FindMeUiSettings defaults = FindMeUiSettings.load(new CompoundTag());
        assertTrue(defaults.mountSummonAnimations());

        FindMeUiSettings changed = defaults.changed(0, 6);
        assertFalse(changed.mountSummonAnimations());

        CompoundTag legacy = changed.save();
        legacy.putBoolean("companionSummonAnimations", true);
        legacy.putBoolean("allowNameColors", true);
        legacy.putInt("nameMaxLength", 64);
        legacy.putInt("defaultTeamIndex", 3);
        FindMeUiSettings loaded = FindMeUiSettings.load(legacy).changed(0, 7);
        assertFalse(loaded.mountSummonAnimations());
        assertTrue(loaded.resetSection(0).mountSummonAnimations());
        CompoundTag saved = loaded.save();
        assertFalse(saved.contains("companionSummonAnimations"));
        assertFalse(saved.contains("allowNameColors"));
        assertFalse(saved.contains("nameMaxLength"));
        assertFalse(saved.contains("defaultTeamIndex"));
    }
}
