package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;

public final class ClientPackAnimationPresetScreenOpener {
    private ClientPackAnimationPresetScreenOpener() {
    }

    public static void setMode(boolean enabled, boolean openScreen) {
        ClientPackAnimationPresetState.setEditMode(enabled);
        if (openScreen) {
            FindMeAuiPackEditorScreen.open();
        }
    }
}
