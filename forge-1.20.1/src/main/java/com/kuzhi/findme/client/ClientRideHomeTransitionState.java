package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Presentation-only blackout across the ride-home teleport and client-ready boundary. */
public final class ClientRideHomeTransitionState {
    private static Phase phase = Phase.NONE;
    private static int age;
    private static int duration = 1;

    private ClientRideHomeTransitionState() {
    }

    public static void startBlackout(int fadeTicks) {
        phase = Phase.FADING_IN;
        age = 0;
        duration = Math.max(1, fadeTicks);
    }

    public static void reveal(int fadeTicks) {
        if (phase == Phase.NONE) {
            return;
        }
        phase = Phase.FADING_OUT;
        age = 0;
        duration = Math.max(1, fadeTicks);
    }

    public static void tick() {
        if (phase == Phase.NONE || phase == Phase.HOLDING) {
            return;
        }
        age++;
        if (age < duration) {
            return;
        }
        if (phase == Phase.FADING_IN) {
            phase = Phase.HOLDING;
            age = duration;
        } else {
            clear();
        }
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        float alpha = alpha(partialTick);
        if (alpha <= 0.001f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int opacity = Mth.clamp(Math.round(alpha * 255.0f), 0, 255);
        graphics.fill(0, 0, width, height, opacity << 24);
    }

    public static void clear() {
        phase = Phase.NONE;
        age = 0;
        duration = 1;
    }

    private static float alpha(float partialTick) {
        return switch (phase) {
            case NONE -> 0.0f;
            case HOLDING -> 1.0f;
            case FADING_IN -> smooth(Mth.clamp((age + partialTick) / duration, 0.0f, 1.0f));
            case FADING_OUT -> 1.0f - smooth(Mth.clamp((age + partialTick) / duration, 0.0f, 1.0f));
        };
    }

    private static float smooth(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    private enum Phase {
        NONE,
        FADING_IN,
        HOLDING,
        FADING_OUT
    }
}
