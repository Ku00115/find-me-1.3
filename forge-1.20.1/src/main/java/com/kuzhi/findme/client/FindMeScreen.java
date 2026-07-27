package com.kuzhi.findme.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class FindMeScreen extends Screen {
    protected FindMeScreen(Component title) {
        super(title);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }
}
