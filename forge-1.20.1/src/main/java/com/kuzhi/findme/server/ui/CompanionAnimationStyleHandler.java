package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.server.animation.CompanionAnimationStyleService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionAnimationStyleHandler {
    private CompanionAnimationStyleHandler() {
    }

    public static void set(ServerPlayer player, UUID uuid,
                           CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
        CompanionAnimationStyleService.setAnimationStyle(player, uuid, purpose, style);
    }
}
