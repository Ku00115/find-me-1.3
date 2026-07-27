package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.CompanionKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionMessageService {
    private CompanionMessageService() {
    }

    public static void tell(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }

    public static Component label(CompanionKind kind) {
        return Component.translatable(kind == CompanionKind.MOUNT
                ? "message.find_me.kind_mount"
                : "message.find_me.kind_companion");
    }
}
