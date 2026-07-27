package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

final class FindMeAuiSound {
    private FindMeAuiSound() {
    }

    static void click() {
        if (!ClientWheelPresentationState.operationSounds()) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.55f));
    }
}
