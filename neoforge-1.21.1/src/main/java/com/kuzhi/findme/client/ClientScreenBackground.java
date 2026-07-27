package com.kuzhi.findme.client;

import net.minecraft.client.gui.GuiGraphics;

final class ClientScreenBackground {
    private static final int DIM_COLOR = 0x66000000;

    private ClientScreenBackground() {
    }

    static void renderDim(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, DIM_COLOR);
    }
}
