package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.server.animation.CompanionEffectStyleService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionEffectStyleHandler {
    private CompanionEffectStyleHandler() {
    }

    public static void set(ServerPlayer player, UUID uuid, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        CompanionEffectStyleService.setEffectStyle(player, uuid, purpose, style);
    }
}
