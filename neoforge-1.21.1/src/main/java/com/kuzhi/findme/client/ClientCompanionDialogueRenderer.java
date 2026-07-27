package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

final class ClientCompanionDialogueRenderer {
    private ClientCompanionDialogueRenderer() {
    }

    static void renderGui(RenderGuiEvent.Post event) {
        if (!ClientCompanionDialogueState.active()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int y = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() * 0.68f);
        int maxWidth = Math.max(80, width - 64);
        int callAlpha = Math.round(ClientCompanionDialogueState.alpha(event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 255.0f);
        if (callAlpha <= 0) {
            return;
        }
        String call = fit(font, ClientCompanionDialogueState.call(), maxWidth);
        String reply = fit(font, ClientCompanionDialogueState.reply(), maxWidth);
        int replyAlpha = Math.round(ClientCompanionDialogueState.replyAlpha(event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 255.0f);
        int callColor = colorWithAlpha(ClientCompanionDialogueState.urgent() ? 0x92F6FF : 0xDAB7FF, callAlpha);
        int replyColor = colorWithAlpha(ClientCompanionDialogueState.urgent() ? 0xF4FEFF : 0xB8F4FF, replyAlpha);
        if (ClientCompanionDialogueState.showingReply()) {
            drawCentered(graphics, font, call, width, y, callColor, callAlpha);
            drawCentered(graphics, font, reply, width, y + 12, replyColor, replyAlpha);
        } else {
            drawCentered(graphics, font, call, width, y, callColor, callAlpha);
        }
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int screenWidth, int y, int color, int alpha) {
        if (text.isBlank()) {
            return;
        }
        int x = (screenWidth - font.width(text)) / 2;
        graphics.drawString(font, text, x + 1, y + 1, colorWithAlpha(0x000000, Math.round(alpha * 0.58f)), false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static String fit(Font font, String text, int width) {
        if (text == null || font.width(text) <= width) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(1, width - font.width(suffix))) + suffix;
    }

    private static int colorWithAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}
