package com.kuzhi.findme.common;

import net.minecraft.nbt.CompoundTag;

/** Player-owned presentation and interaction preferences. */
public record FindMeUiSettings(
        boolean showCustomNames,
        boolean showOriginalNames,
        boolean showHealth,
        boolean rotateModels,
        boolean reduceBackgroundAnimation,
        int dragHoldMillis,
        boolean operationSounds,
        boolean controlHints,
        boolean autoJoinTeams,
        boolean autoCreateTeams,
        int defaultTeamIndex,
        boolean allowNameColors,
        int nameMaxLength,
        FindMeWheelStyle wheelStyle,
        FindMeTextMode textMode,
        boolean uiAnimations,
        FindMeFontFamily fontFamily,
        FindMeFontSize fontSize,
        FindMeRidingCameraMode ridingCameraMode,
        BindingAnimationPolicy bindingAnimationPolicy,
        boolean preferNativeMountInteraction) {

    public static final int TEAM_CAPACITY = 6;

    public static FindMeUiSettings defaults() {
        return new FindMeUiSettings(true, false, true, false, true, 300, true, true,
                true, true, 0, false, 32, FindMeWheelStyle.CLASSIC_RADIAL,
                FindMeTextMode.PRACTICAL, true, FindMeFontFamily.DEFAULT, FindMeFontSize.MEDIUM,
                FindMeRidingCameraMode.NONE, BindingAnimationPolicy.FIRST_TYPE, false);
    }

    public FindMeUiSettings normalized() {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.reduceBackgroundAnimation, clamp(this.dragHoldMillis, 150, 600),
                this.operationSounds, this.controlHints, this.autoJoinTeams, this.autoCreateTeams,
                Math.max(0, this.defaultTeamIndex), this.allowNameColors, clamp(this.nameMaxLength, 8, 64),
                this.wheelStyle == null ? FindMeWheelStyle.CLASSIC_RADIAL : this.wheelStyle,
                this.textMode == null ? FindMeTextMode.PRACTICAL : this.textMode, this.uiAnimations,
                this.fontFamily == null ? FindMeFontFamily.DEFAULT : this.fontFamily,
                this.fontSize == null ? FindMeFontSize.MEDIUM : this.fontSize,
                this.ridingCameraMode == null ? FindMeRidingCameraMode.NONE : this.ridingCameraMode,
                this.bindingAnimationPolicy == null || this.bindingAnimationPolicy == BindingAnimationPolicy.INHERIT
                        ? BindingAnimationPolicy.FIRST_TYPE : this.bindingAnimationPolicy,
                this.preferNativeMountInteraction);
    }

    /** Compact numeric mapping used by AUI setting-card payloads. */
    public FindMeUiSettings changed(int section, int row) {
        return switch (section) {
            case 0 -> switch (row) {
                case 0 -> withRotateModels(!this.rotateModels);
                case 1 -> withReducedBackgroundAnimation(!this.reduceBackgroundAnimation);
                case 2 -> withOperationSounds(!this.operationSounds);
                case 3 -> withControlHints(!this.controlHints);
                case 4 -> withPreferNativeMountInteraction(!this.preferNativeMountInteraction);
                default -> this;
            };
            case 1 -> switch (row) {
                case 0 -> withTeamAutomation(!this.autoJoinTeams, this.autoCreateTeams);
                case 1 -> withTeamAutomation(this.autoJoinTeams, !this.autoCreateTeams);
                default -> this;
            };
            case 2 -> switch (row) {
                case 0 -> withNameDisplay(!this.showCustomNames, this.showOriginalNames, this.showHealth);
                case 1 -> withNameDisplay(this.showCustomNames, !this.showOriginalNames, this.showHealth);
                case 2 -> withNameDisplay(this.showCustomNames, this.showOriginalNames, !this.showHealth);
                case 3 -> withNaming(!this.allowNameColors, this.nameMaxLength);
                default -> this;
            };
            default -> this;
        };
    }

    public FindMeUiSettings resetSection(int section) {
        FindMeUiSettings d = defaults();
        return switch (section) {
            case 0 -> new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                    d.rotateModels, d.reduceBackgroundAnimation, d.dragHoldMillis, d.operationSounds,
                    d.controlHints, this.autoJoinTeams, this.autoCreateTeams, this.defaultTeamIndex,
                    this.allowNameColors, this.nameMaxLength, d.wheelStyle, this.textMode, d.uiAnimations,
                    this.fontFamily, this.fontSize, d.ridingCameraMode, d.bindingAnimationPolicy,
                    d.preferNativeMountInteraction);
            case 1 -> new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                    this.rotateModels, this.reduceBackgroundAnimation, this.dragHoldMillis, this.operationSounds,
                    this.controlHints, d.autoJoinTeams, d.autoCreateTeams, d.defaultTeamIndex,
                    this.allowNameColors, this.nameMaxLength, this.wheelStyle, this.textMode, this.uiAnimations,
                    this.fontFamily, this.fontSize, this.ridingCameraMode, this.bindingAnimationPolicy,
                    this.preferNativeMountInteraction);
            case 2 -> new FindMeUiSettings(d.showCustomNames, d.showOriginalNames, d.showHealth,
                    this.rotateModels, this.reduceBackgroundAnimation, this.dragHoldMillis, this.operationSounds,
                    this.controlHints, this.autoJoinTeams, this.autoCreateTeams, this.defaultTeamIndex,
                    d.allowNameColors, d.nameMaxLength, this.wheelStyle, d.textMode, this.uiAnimations,
                    d.fontFamily, d.fontSize, this.ridingCameraMode, this.bindingAnimationPolicy,
                    this.preferNativeMountInteraction);
            default -> this;
        };
    }

    public FindMeUiSettings withUiAnimations(boolean value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, value, null, null, null, null); }
    public FindMeUiSettings withWheelStyle(FindMeWheelStyle value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, value, null, null, null, null, null, null); }
    public FindMeUiSettings withDragHoldMillis(int value) { return copy(null, null, null, null, null, value, null, null, null, null, null, null, null, null, null, null, null, null, null, null); }
    public FindMeUiSettings withNameMaxLength(int value) { return withNaming(this.allowNameColors, value); }
    public FindMeUiSettings withTextMode(FindMeTextMode value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, null, value, null, null, null, null, null); }
    public FindMeUiSettings withFontFamily(FindMeFontFamily value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, value, null, null, null); }
    public FindMeUiSettings withFontSize(FindMeFontSize value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, value, null, null); }
    public FindMeUiSettings withRidingCameraMode(FindMeRidingCameraMode value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, value, null); }
    public FindMeUiSettings withBindingAnimationPolicy(BindingAnimationPolicy value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, value); }
    public FindMeUiSettings withDefaultTeamIndex(int value) { return copy(null, null, null, null, null, null, null, null, null, null, value, null, null, null, null, null, null, null, null, null); }
    public FindMeUiSettings withPreferNativeMountInteraction(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.reduceBackgroundAnimation, this.dragHoldMillis, this.operationSounds,
                this.controlHints, this.autoJoinTeams, this.autoCreateTeams, this.defaultTeamIndex,
                this.allowNameColors, this.nameMaxLength, this.wheelStyle, this.textMode, this.uiAnimations,
                this.fontFamily, this.fontSize, this.ridingCameraMode, this.bindingAnimationPolicy, value);
    }
    private FindMeUiSettings withRotateModels(boolean value) { return copy(null, null, null, value, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withReducedBackgroundAnimation(boolean value) { return copy(null, null, null, null, value, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withOperationSounds(boolean value) { return copy(null, null, null, null, null, null, value, null, null, null, null, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withControlHints(boolean value) { return copy(null, null, null, null, null, null, null, value, null, null, null, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withTeamAutomation(boolean join, boolean create) { return copy(null, null, null, null, null, null, null, null, join, create, null, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withNaming(boolean colors, int length) { return copy(null, null, null, null, null, null, null, null, null, null, null, colors, length, null, null, null, null, null, null, null); }
    private FindMeUiSettings withNameDisplay(boolean custom, boolean original, boolean health) { return copy(custom, original, health, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null); }

    private FindMeUiSettings copy(Boolean custom, Boolean original, Boolean health, Boolean rotate,
                                  Boolean reduced, Integer hold, Boolean sounds, Boolean hints,
                                  Boolean autoJoin, Boolean autoCreate, Integer defaultTeam, Boolean colors,
                                  Integer maxName, FindMeWheelStyle wheel, FindMeTextMode text,
                                  Boolean animations, FindMeFontFamily font, FindMeFontSize size,
                                  FindMeRidingCameraMode camera, BindingAnimationPolicy binding) {
        return new FindMeUiSettings(custom == null ? this.showCustomNames : custom,
                original == null ? this.showOriginalNames : original, health == null ? this.showHealth : health,
                rotate == null ? this.rotateModels : rotate, reduced == null ? this.reduceBackgroundAnimation : reduced,
                hold == null ? this.dragHoldMillis : hold, sounds == null ? this.operationSounds : sounds,
                hints == null ? this.controlHints : hints, autoJoin == null ? this.autoJoinTeams : autoJoin,
                autoCreate == null ? this.autoCreateTeams : autoCreate,
                defaultTeam == null ? this.defaultTeamIndex : defaultTeam,
                colors == null ? this.allowNameColors : colors, maxName == null ? this.nameMaxLength : maxName,
                wheel == null ? this.wheelStyle : wheel, text == null ? this.textMode : text,
                animations == null ? this.uiAnimations : animations, font == null ? this.fontFamily : font,
                size == null ? this.fontSize : size, camera == null ? this.ridingCameraMode : camera,
                binding == null ? this.bindingAnimationPolicy : binding,
                this.preferNativeMountInteraction).normalized();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("showCustomNames", this.showCustomNames);
        tag.putBoolean("showOriginalNames", this.showOriginalNames);
        tag.putBoolean("showHealth", this.showHealth);
        tag.putBoolean("rotateModels", this.rotateModels);
        tag.putBoolean("reduceBackgroundAnimation", this.reduceBackgroundAnimation);
        tag.putInt("dragHoldMillis", this.dragHoldMillis);
        tag.putBoolean("operationSounds", this.operationSounds);
        tag.putBoolean("controlHints", this.controlHints);
        tag.putBoolean("autoJoinTeams", this.autoJoinTeams);
        tag.putBoolean("autoCreateTeams", this.autoCreateTeams);
        tag.putInt("defaultTeamIndex", this.defaultTeamIndex);
        tag.putBoolean("allowNameColors", this.allowNameColors);
        tag.putInt("nameMaxLength", this.nameMaxLength);
        tag.putString("wheelStyle", this.wheelStyle.name());
        tag.putString("textMode", this.textMode.name());
        tag.putBoolean("uiAnimations", this.uiAnimations);
        tag.putString("fontFamily", this.fontFamily.name());
        tag.putString("fontSize", this.fontSize.name());
        tag.putString("ridingCameraMode", this.ridingCameraMode.name());
        tag.putString("bindingAnimationPolicy", this.bindingAnimationPolicy.name());
        tag.putBoolean("preferNativeMountInteraction", this.preferNativeMountInteraction);
        return tag;
    }

    public static FindMeUiSettings load(CompoundTag tag) {
        FindMeUiSettings d = defaults();
        if (tag == null || tag.isEmpty()) return d;
        return new FindMeUiSettings(value(tag, "showCustomNames", d.showCustomNames),
                value(tag, "showOriginalNames", d.showOriginalNames), value(tag, "showHealth", d.showHealth),
                value(tag, "rotateModels", d.rotateModels), value(tag, "reduceBackgroundAnimation", d.reduceBackgroundAnimation),
                number(tag, "dragHoldMillis", d.dragHoldMillis), value(tag, "operationSounds", d.operationSounds),
                value(tag, "controlHints", d.controlHints), value(tag, "autoJoinTeams", d.autoJoinTeams),
                value(tag, "autoCreateTeams", d.autoCreateTeams), number(tag, "defaultTeamIndex", d.defaultTeamIndex),
                value(tag, "allowNameColors", d.allowNameColors), number(tag, "nameMaxLength", d.nameMaxLength),
                enumValue(tag, "wheelStyle", FindMeWheelStyle.class, d.wheelStyle),
                enumValue(tag, "textMode", FindMeTextMode.class, d.textMode), value(tag, "uiAnimations", d.uiAnimations),
                enumValue(tag, "fontFamily", FindMeFontFamily.class, d.fontFamily),
                enumValue(tag, "fontSize", FindMeFontSize.class, d.fontSize),
                enumValue(tag, "ridingCameraMode", FindMeRidingCameraMode.class, d.ridingCameraMode),
                enumValue(tag, "bindingAnimationPolicy", BindingAnimationPolicy.class, d.bindingAnimationPolicy),
                value(tag, "preferNativeMountInteraction", d.preferNativeMountInteraction)).normalized();
    }

    private static <E extends Enum<E>> E enumValue(CompoundTag tag, String key, Class<E> type, E fallback) {
        if (tag.contains(key)) try { return Enum.valueOf(type, tag.getString(key)); } catch (IllegalArgumentException ignored) { }
        return fallback;
    }
    private static boolean value(CompoundTag tag, String key, boolean fallback) { return tag.contains(key) ? tag.getBoolean(key) : fallback; }
    private static int number(CompoundTag tag, String key, int fallback) { return tag.contains(key) ? tag.getInt(key) : fallback; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
