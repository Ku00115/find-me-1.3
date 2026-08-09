package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

final class FindMeAuiSound {
    private static long lastWheelHoverAt;

    private FindMeAuiSound() {
    }

    static void click() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f));
    }

    static void wheelHover() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        long now = System.currentTimeMillis();
        if (now - lastWheelHoverAt < 180L) return;
        lastWheelHoverAt = now;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.45f, 0.22f));
    }
}
