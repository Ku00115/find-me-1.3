package com.kuzhi.findme.common;

import net.minecraft.nbt.CompoundTag;

/** Player-owned presentation and interaction preferences. */
public record FindMeUiSettings(
        boolean showCustomNames,
        boolean showOriginalNames,
        boolean showHealth,
        boolean rotateModels,
        boolean operationSounds,
        boolean autoJoinTeams,
        boolean autoCreateTeams,
        FindMeWheelStyle wheelStyle,
        FindMeTextMode textMode,
        boolean uiAnimations,
        FindMeFontFamily fontFamily,
        FindMeFontSize fontSize,
        FindMeRidingCameraMode ridingCameraMode,
        BindingAnimationPolicy bindingAnimationPolicy,
        boolean preferNativeMountInteraction,
        boolean autoPromoteRiddenCompanions,
        SummonedOutlineMode summonedOutlineMode,
        boolean mountSummonAnimations,
        boolean hideRiddenMountWhenLookingDown,
        boolean friendlyFireProtection,
        boolean autoStoreOnBinding,
        boolean autoOrganizeTeams,
        boolean fallingAnimation,
        boolean boundCreatureBlockProtection,
        int guiOpacityPercent) {

    public static final int TEAM_CAPACITY = 6;

    public static FindMeUiSettings defaults() {
        return new FindMeUiSettings(true, false, true, false, true, true, true,
                FindMeWheelStyle.CLASSIC_RADIAL, FindMeTextMode.PRACTICAL, true,
                FindMeFontFamily.SANS, FindMeFontSize.MEDIUM, FindMeRidingCameraMode.NONE,
                BindingAnimationPolicy.NEVER, true, true, SummonedOutlineMode.OFF,
                true, true, true, true, true, false, true, 100);
    }

    public FindMeUiSettings normalized() {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle == null ? FindMeWheelStyle.CLASSIC_RADIAL : this.wheelStyle,
                this.textMode == null ? FindMeTextMode.PRACTICAL : this.textMode, this.uiAnimations,
                this.fontFamily == null ? FindMeFontFamily.SANS : this.fontFamily,
                this.fontSize == null ? FindMeFontSize.MEDIUM : this.fontSize,
                this.ridingCameraMode == null ? FindMeRidingCameraMode.NONE : this.ridingCameraMode,
                this.bindingAnimationPolicy == null || this.bindingAnimationPolicy == BindingAnimationPolicy.INHERIT
                        ? BindingAnimationPolicy.NEVER : this.bindingAnimationPolicy,
                this.preferNativeMountInteraction, this.autoPromoteRiddenCompanions,
                this.summonedOutlineMode == null ? SummonedOutlineMode.OFF : this.summonedOutlineMode,
                this.mountSummonAnimations, this.hideRiddenMountWhenLookingDown,
                this.friendlyFireProtection, this.autoStoreOnBinding, this.autoOrganizeTeams,
                this.fallingAnimation, this.boundCreatureBlockProtection,
                Math.max(20, Math.min(100, this.guiOpacityPercent)));
    }

    /** Compact numeric mapping used by AUI setting-card payloads. */
    public FindMeUiSettings changed(int section, int row) {
        return switch (section) {
            case 0 -> switch (row) {
                case 0 -> withRotateModels(!this.rotateModels);
                case 2 -> withOperationSounds(!this.operationSounds);
                case 4 -> withPreferNativeMountInteraction(!this.preferNativeMountInteraction);
                case 5 -> withAutoPromoteRiddenCompanions(!this.autoPromoteRiddenCompanions);
                case 6 -> withMountSummonAnimations(!this.mountSummonAnimations);
                case 7 -> withHideRiddenMountWhenLookingDown(!this.hideRiddenMountWhenLookingDown);
                case 8 -> withFallingAnimation(!this.fallingAnimation);
                default -> this;
            };
            case 1 -> switch (row) {
                case 0 -> withTeamAutomation(!this.autoJoinTeams, this.autoCreateTeams);
                case 1 -> withTeamAutomation(this.autoJoinTeams, !this.autoCreateTeams);
                case 3 -> withFriendlyFireProtection(!this.friendlyFireProtection);
                case 4 -> withAutoStoreOnBinding(!this.autoStoreOnBinding);
                case 5 -> withAutoOrganizeTeams(!this.autoOrganizeTeams);
                case 6 -> withBoundCreatureBlockProtection(!this.boundCreatureBlockProtection);
                default -> this;
            };
            case 2 -> switch (row) {
                case 0 -> withNameDisplay(!this.showCustomNames, this.showOriginalNames, this.showHealth);
                case 1 -> withNameDisplay(this.showCustomNames, !this.showOriginalNames, this.showHealth);
                case 2 -> withNameDisplay(this.showCustomNames, this.showOriginalNames, !this.showHealth);
                default -> this;
            };
            default -> this;
        };
    }

    public FindMeUiSettings resetSection(int section) {
        FindMeUiSettings d = defaults();
        return switch (section) {
            case 0 -> new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                    d.rotateModels, d.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                    d.wheelStyle, this.textMode, d.uiAnimations, this.fontFamily, this.fontSize,
                    d.ridingCameraMode, d.bindingAnimationPolicy, d.preferNativeMountInteraction,
                    d.autoPromoteRiddenCompanions, d.summonedOutlineMode, d.mountSummonAnimations,
                    d.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                    this.autoStoreOnBinding, this.autoOrganizeTeams, d.fallingAnimation,
                    this.boundCreatureBlockProtection, d.guiOpacityPercent);
            case 1 -> new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                    this.rotateModels, this.operationSounds, d.autoJoinTeams, d.autoCreateTeams,
                    this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                    this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                    this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                    this.hideRiddenMountWhenLookingDown, d.friendlyFireProtection,
                    d.autoStoreOnBinding, d.autoOrganizeTeams, this.fallingAnimation,
                    d.boundCreatureBlockProtection, this.guiOpacityPercent);
            case 2 -> new FindMeUiSettings(d.showCustomNames, d.showOriginalNames, d.showHealth,
                    this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                    this.wheelStyle, d.textMode, this.uiAnimations, d.fontFamily, d.fontSize,
                    this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                    this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                    this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                    this.autoStoreOnBinding, this.autoOrganizeTeams, this.fallingAnimation,
                    this.boundCreatureBlockProtection, this.guiOpacityPercent);
            default -> this;
        };
    }

    public FindMeUiSettings withUiAnimations(boolean value) { return copy(null, null, null, null, null, null, null, null, null, value, null, null, null, null); }
    public FindMeUiSettings withWheelStyle(FindMeWheelStyle value) { return copy(null, null, null, null, null, null, null, value, null, null, null, null, null, null); }
    public FindMeUiSettings withTextMode(FindMeTextMode value) { return copy(null, null, null, null, null, null, null, null, value, null, null, null, null, null); }
    public FindMeUiSettings withFontFamily(FindMeFontFamily value) { return copy(null, null, null, null, null, null, null, null, null, null, value, null, null, null); }
    public FindMeUiSettings withFontSize(FindMeFontSize value) { return copy(null, null, null, null, null, null, null, null, null, null, null, value, null, null); }
    public FindMeUiSettings withRidingCameraMode(FindMeRidingCameraMode value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, value, null); }
    public FindMeUiSettings withBindingAnimationPolicy(BindingAnimationPolicy value) { return copy(null, null, null, null, null, null, null, null, null, null, null, null, null, value); }

    public FindMeUiSettings withPreferNativeMountInteraction(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, value, this.autoPromoteRiddenCompanions,
                this.summonedOutlineMode, this.mountSummonAnimations, this.hideRiddenMountWhenLookingDown,
                this.friendlyFireProtection, this.autoStoreOnBinding, this.autoOrganizeTeams,
                this.fallingAnimation, this.boundCreatureBlockProtection, this.guiOpacityPercent);
    }

    public FindMeUiSettings withAutoPromoteRiddenCompanions(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction, value,
                this.summonedOutlineMode, this.mountSummonAnimations, this.hideRiddenMountWhenLookingDown,
                this.friendlyFireProtection, this.autoStoreOnBinding, this.autoOrganizeTeams,
                this.fallingAnimation, this.boundCreatureBlockProtection, this.guiOpacityPercent);
    }

    public FindMeUiSettings withSummonedOutlineMode(SummonedOutlineMode value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, value == null ? SummonedOutlineMode.OFF : value,
                this.mountSummonAnimations, this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, this.autoOrganizeTeams, this.fallingAnimation,
                this.boundCreatureBlockProtection, this.guiOpacityPercent);
    }

    public FindMeUiSettings withMountSummonAnimations(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, value,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, this.autoOrganizeTeams, this.fallingAnimation,
                this.boundCreatureBlockProtection, this.guiOpacityPercent);
    }

    public FindMeUiSettings withHideRiddenMountWhenLookingDown(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                value, this.friendlyFireProtection, this.autoStoreOnBinding,
                this.autoOrganizeTeams, this.fallingAnimation, this.boundCreatureBlockProtection,
                this.guiOpacityPercent);
    }

    public FindMeUiSettings withFriendlyFireProtection(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, value, this.autoStoreOnBinding,
                this.autoOrganizeTeams, this.fallingAnimation, this.boundCreatureBlockProtection,
                this.guiOpacityPercent);
    }

    public FindMeUiSettings withAutoStoreOnBinding(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection, value,
                this.autoOrganizeTeams, this.fallingAnimation, this.boundCreatureBlockProtection,
                this.guiOpacityPercent);
    }

    public FindMeUiSettings withAutoOrganizeTeams(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, value, this.fallingAnimation,
                this.boundCreatureBlockProtection, this.guiOpacityPercent);
    }

    public FindMeUiSettings withFallingAnimation(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, this.autoOrganizeTeams, value,
                this.boundCreatureBlockProtection, this.guiOpacityPercent);
    }

    public FindMeUiSettings withBoundCreatureBlockProtection(boolean value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, this.autoOrganizeTeams, this.fallingAnimation, value,
                this.guiOpacityPercent);
    }

    public FindMeUiSettings withGuiOpacityPercent(int value) {
        return new FindMeUiSettings(this.showCustomNames, this.showOriginalNames, this.showHealth,
                this.rotateModels, this.operationSounds, this.autoJoinTeams, this.autoCreateTeams,
                this.wheelStyle, this.textMode, this.uiAnimations, this.fontFamily, this.fontSize,
                this.ridingCameraMode, this.bindingAnimationPolicy, this.preferNativeMountInteraction,
                this.autoPromoteRiddenCompanions, this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, this.autoOrganizeTeams, this.fallingAnimation,
                this.boundCreatureBlockProtection, value).normalized();
    }

    private FindMeUiSettings withRotateModels(boolean value) { return copy(null, null, null, value, null, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withOperationSounds(boolean value) { return copy(null, null, null, null, value, null, null, null, null, null, null, null, null, null); }
    private FindMeUiSettings withTeamAutomation(boolean join, boolean create) { return copy(null, null, null, null, null, join, create, null, null, null, null, null, null, null); }
    private FindMeUiSettings withNameDisplay(boolean custom, boolean original, boolean health) { return copy(custom, original, health, null, null, null, null, null, null, null, null, null, null, null); }

    private FindMeUiSettings copy(Boolean custom, Boolean original, Boolean health, Boolean rotate,
                                  Boolean sounds, Boolean autoJoin, Boolean autoCreate,
                                  FindMeWheelStyle wheel, FindMeTextMode text, Boolean animations,
                                  FindMeFontFamily font, FindMeFontSize size,
                                  FindMeRidingCameraMode camera, BindingAnimationPolicy binding) {
        return new FindMeUiSettings(custom == null ? this.showCustomNames : custom,
                original == null ? this.showOriginalNames : original,
                health == null ? this.showHealth : health,
                rotate == null ? this.rotateModels : rotate,
                sounds == null ? this.operationSounds : sounds,
                autoJoin == null ? this.autoJoinTeams : autoJoin,
                autoCreate == null ? this.autoCreateTeams : autoCreate,
                wheel == null ? this.wheelStyle : wheel,
                text == null ? this.textMode : text,
                animations == null ? this.uiAnimations : animations,
                font == null ? this.fontFamily : font,
                size == null ? this.fontSize : size,
                camera == null ? this.ridingCameraMode : camera,
                binding == null ? this.bindingAnimationPolicy : binding,
                this.preferNativeMountInteraction, this.autoPromoteRiddenCompanions,
                this.summonedOutlineMode, this.mountSummonAnimations,
                this.hideRiddenMountWhenLookingDown, this.friendlyFireProtection,
                this.autoStoreOnBinding, this.autoOrganizeTeams, this.fallingAnimation,
                this.boundCreatureBlockProtection, this.guiOpacityPercent).normalized();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("showCustomNames", this.showCustomNames);
        tag.putBoolean("showOriginalNames", this.showOriginalNames);
        tag.putBoolean("showHealth", this.showHealth);
        tag.putBoolean("rotateModels", this.rotateModels);
        tag.putBoolean("operationSounds", this.operationSounds);
        tag.putBoolean("autoJoinTeams", this.autoJoinTeams);
        tag.putBoolean("autoCreateTeams", this.autoCreateTeams);
        tag.putString("wheelStyle", this.wheelStyle.name());
        tag.putString("textMode", this.textMode.name());
        tag.putBoolean("uiAnimations", this.uiAnimations);
        tag.putString("fontFamily", this.fontFamily.name());
        tag.putString("fontSize", this.fontSize.name());
        tag.putString("ridingCameraMode", this.ridingCameraMode.name());
        tag.putString("bindingAnimationPolicy", this.bindingAnimationPolicy.name());
        tag.putBoolean("preferNativeMountInteraction", this.preferNativeMountInteraction);
        tag.putBoolean("autoPromoteRiddenCompanions", this.autoPromoteRiddenCompanions);
        tag.putString("summonedOutlineMode", this.summonedOutlineMode.name());
        tag.putBoolean("mountSummonAnimations", this.mountSummonAnimations);
        tag.putBoolean("hideRiddenMountWhenLookingDown", this.hideRiddenMountWhenLookingDown);
        tag.putBoolean("friendlyFireProtection", this.friendlyFireProtection);
        tag.putBoolean("autoStoreOnBinding", this.autoStoreOnBinding);
        tag.putBoolean("autoOrganizeTeams", this.autoOrganizeTeams);
        tag.putBoolean("fallingAnimation", this.fallingAnimation);
        tag.putBoolean("boundCreatureBlockProtection", this.boundCreatureBlockProtection);
        tag.putInt("guiOpacityPercent", this.guiOpacityPercent);
        return tag;
    }

    public static FindMeUiSettings load(CompoundTag tag) {
        FindMeUiSettings d = defaults();
        if (tag == null || tag.isEmpty()) return d;
        return new FindMeUiSettings(value(tag, "showCustomNames", d.showCustomNames),
                value(tag, "showOriginalNames", d.showOriginalNames),
                value(tag, "showHealth", d.showHealth), value(tag, "rotateModels", d.rotateModels),
                value(tag, "operationSounds", d.operationSounds),
                value(tag, "autoJoinTeams", d.autoJoinTeams),
                value(tag, "autoCreateTeams", d.autoCreateTeams),
                enumValue(tag, "wheelStyle", FindMeWheelStyle.class, d.wheelStyle),
                enumValue(tag, "textMode", FindMeTextMode.class, d.textMode),
                value(tag, "uiAnimations", d.uiAnimations),
                enumValue(tag, "fontFamily", FindMeFontFamily.class, d.fontFamily),
                enumValue(tag, "fontSize", FindMeFontSize.class, d.fontSize),
                enumValue(tag, "ridingCameraMode", FindMeRidingCameraMode.class, d.ridingCameraMode),
                enumValue(tag, "bindingAnimationPolicy", BindingAnimationPolicy.class, d.bindingAnimationPolicy),
                value(tag, "preferNativeMountInteraction", d.preferNativeMountInteraction),
                value(tag, "autoPromoteRiddenCompanions", d.autoPromoteRiddenCompanions),
                enumValue(tag, "summonedOutlineMode", SummonedOutlineMode.class, d.summonedOutlineMode),
                value(tag, "mountSummonAnimations", d.mountSummonAnimations),
                value(tag, "hideRiddenMountWhenLookingDown", d.hideRiddenMountWhenLookingDown),
                value(tag, "friendlyFireProtection", d.friendlyFireProtection),
                value(tag, "autoStoreOnBinding", d.autoStoreOnBinding),
                value(tag, "autoOrganizeTeams", d.autoOrganizeTeams),
                value(tag, "fallingAnimation", d.fallingAnimation),
                value(tag, "boundCreatureBlockProtection", d.boundCreatureBlockProtection),
                intValue(tag, "guiOpacityPercent", d.guiOpacityPercent)).normalized();
    }

    private static <E extends Enum<E>> E enumValue(CompoundTag tag, String key, Class<E> type, E fallback) {
        if (tag.contains(key)) try { return Enum.valueOf(type, tag.getString(key)); } catch (IllegalArgumentException ignored) { }
        return fallback;
    }

    private static boolean value(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    private static int intValue(CompoundTag tag, String key, int fallback) {
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }
}
