package com.kuzhi.findme.client;

import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.common.FindMeFontFamily;
import com.kuzhi.findme.common.FindMeFontSize;
import com.kuzhi.findme.common.FindMeWheelStyle;
import com.kuzhi.findme.common.FindMeRidingCameraMode;
import com.kuzhi.findme.common.SummonedOutlineMode;

public final class ClientWheelPresentationState {
    private static FindMeWheelStyle rosterLayout = FindMeWheelStyle.CLASSIC_RADIAL;
    private static boolean uiAnimations = true;
    private static FindMeFontFamily fontFamily = FindMeFontFamily.SANS;
    private static FindMeFontSize fontSize = FindMeFontSize.MEDIUM;
    private static FindMeRidingCameraMode ridingCameraMode = FindMeRidingCameraMode.NONE;
    private static boolean rotateModels;
    private static boolean operationSounds = true;
    private static boolean showCustomNames = true;
    private static boolean showOriginalNames;
    private static boolean showHealth = true;
    private static SummonedOutlineMode summonedOutlineMode = SummonedOutlineMode.OFF;
    private static boolean hideRiddenMountWhenLookingDown = true;
    private static boolean fallingAnimation;
    private static float guiOpacity = 1.0f;

    private ClientWheelPresentationState() {
    }

    static FindMeWheelStyle rosterLayout() {
        return rosterLayout;
    }

    public static boolean uiAnimations() {
        return uiAnimations;
    }

    static FindMeFontFamily fontFamily() {
        return fontFamily;
    }

    static FindMeFontSize fontSize() {
        return fontSize;
    }

    static FindMeRidingCameraMode ridingCameraMode() {
        return ridingCameraMode;
    }
    public static boolean rotateModels() { return rotateModels; }
    public static boolean operationSounds() { return operationSounds; }
    public static boolean showCustomNames() { return showCustomNames; }
    public static boolean showOriginalNames() { return showOriginalNames; }
    public static boolean showHealth() { return showHealth; }
    public static SummonedOutlineMode summonedOutlineMode() { return summonedOutlineMode; }
    public static boolean hideRiddenMountWhenLookingDown() { return hideRiddenMountWhenLookingDown; }
    public static boolean fallingAnimation() { return fallingAnimation; }
    public static float guiOpacity() { return guiOpacity; }

    public static String typographyClasses() {
        return fontFamily.cssClass() + " " + fontSize.cssClass();
    }

    public static void update(FindMeUiSettings settings) {
        rosterLayout = settings == null || settings.wheelStyle() == null
                ? FindMeWheelStyle.CLASSIC_RADIAL
                : settings.wheelStyle();
        uiAnimations = settings == null || settings.uiAnimations();
        fontFamily = settings == null || settings.fontFamily() == null ? FindMeFontFamily.SANS : settings.fontFamily();
        fontSize = settings == null || settings.fontSize() == null ? FindMeFontSize.MEDIUM : settings.fontSize();
        ridingCameraMode = settings == null || settings.ridingCameraMode() == null
                ? FindMeRidingCameraMode.NONE : settings.ridingCameraMode();
        rotateModels = settings != null && settings.rotateModels();
        operationSounds = settings == null || settings.operationSounds();
        showCustomNames = settings == null || settings.showCustomNames();
        showOriginalNames = settings != null && settings.showOriginalNames();
        showHealth = settings == null || settings.showHealth();
        summonedOutlineMode = settings == null || settings.summonedOutlineMode() == null
                ? SummonedOutlineMode.OFF : settings.summonedOutlineMode();
        hideRiddenMountWhenLookingDown = settings == null || settings.hideRiddenMountWhenLookingDown();
        fallingAnimation = settings != null && settings.fallingAnimation();
        guiOpacity = settings == null ? 1.0f : settings.guiOpacityPercent() / 100.0f;
    }
}
