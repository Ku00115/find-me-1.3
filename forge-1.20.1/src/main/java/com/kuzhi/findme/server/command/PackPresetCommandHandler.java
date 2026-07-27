package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.PackEntityPresetField;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

public final class PackPresetCommandHandler {
    private PackPresetCommandHandler() {
    }

    public static void setStyles(ServerPlayer player, List<String> entityTypes, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        if (!managementEnabled(player)) return;
        PackAnimationPresetService.setStyles(player, entityTypes, purpose, style);
    }

    public static void setAnimationStyles(ServerPlayer player, List<String> entityTypes,
                                          CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
        if (!managementEnabled(player)) return;
        PackAnimationPresetService.setAnimationStyles(player, entityTypes, purpose, style);
    }

    public static void setField(ServerPlayer player, List<String> entityTypes, PackEntityPresetField field, String value) {
        if (!managementEnabled(player)) return;
        PackAnimationPresetService.setField(player, entityTypes, field, value);
    }

    public static void openEditor(ServerPlayer player) {
        if (!managementEnabled(player)) return;
        PackAnimationPresetService.openEditor(player);
    }

    public static void closeEditor(ServerPlayer player) {
        PackAnimationPresetService.closeEditor(player);
    }

    public static void reload(ServerPlayer player) {
        if (!managementEnabled(player)) return;
        PackAnimationPresetService.reload(player);
    }

    public static void exportPath(ServerPlayer player) {
        if (!managementEnabled(player)) return;
        PackAnimationPresetService.exportPath(player);
    }

    private static boolean managementEnabled(ServerPlayer player) {
        return player != null && FindMeModuleService.require(player, FindMeModule.MANAGEMENT);
    }
}
