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
    private static FindMeFontFamily fontFamily = FindMeFontFamily.DEFAULT;
    private static FindMeFontSize fontSize = FindMeFontSize.MEDIUM;
    private static FindMeRidingCameraMode ridingCameraMode = FindMeRidingCameraMode.NONE;
    private static boolean rotateModels;
    private static boolean reduceBackgroundAnimation = true;
    private static boolean operationSounds = true;
    private static boolean controlHints = true;
    private static boolean showCustomNames = true;
    private static boolean showOriginalNames;
    private static boolean showHealth = true;
    private static int defaultTeamIndex;
    private static SummonedOutlineMode summonedOutlineMode = SummonedOutlineMode.OFF;

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
    public static boolean reduceBackgroundAnimation() { return reduceBackgroundAnimation; }
    public static boolean operationSounds() { return operationSounds; }
    public static boolean controlHints() { return controlHints; }
    public static boolean showCustomNames() { return showCustomNames; }
    public static boolean showOriginalNames() { return showOriginalNames; }
    public static boolean showHealth() { return showHealth; }
    public static int defaultTeamIndex() { return defaultTeamIndex; }
    public static SummonedOutlineMode summonedOutlineMode() { return summonedOutlineMode; }

    public static String typographyClasses() {
        return fontFamily.cssClass() + " " + fontSize.cssClass();
    }

    public static void update(FindMeUiSettings settings) {
        rosterLayout = settings == null || settings.wheelStyle() == null
                ? FindMeWheelStyle.CLASSIC_RADIAL
                : settings.wheelStyle();
        uiAnimations = settings == null || settings.uiAnimations();
        fontFamily = settings == null || settings.fontFamily() == null ? FindMeFontFamily.DEFAULT : settings.fontFamily();
        fontSize = settings == null || settings.fontSize() == null ? FindMeFontSize.MEDIUM : settings.fontSize();
        ridingCameraMode = settings == null || settings.ridingCameraMode() == null
                ? FindMeRidingCameraMode.NONE : settings.ridingCameraMode();
        rotateModels = settings != null && settings.rotateModels();
        reduceBackgroundAnimation = settings == null || settings.reduceBackgroundAnimation();
        operationSounds = settings == null || settings.operationSounds();
        controlHints = settings == null || settings.controlHints();
        showCustomNames = settings == null || settings.showCustomNames();
        showOriginalNames = settings != null && settings.showOriginalNames();
        showHealth = settings == null || settings.showHealth();
        defaultTeamIndex = settings == null ? 0 : Math.max(0, settings.defaultTeamIndex());
        summonedOutlineMode = settings == null || settings.summonedOutlineMode() == null
                ? SummonedOutlineMode.OFF : settings.summonedOutlineMode();
    }
}
