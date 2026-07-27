package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FindMeDialogueConfigScreen extends FindMeScreen {
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private int scroll;

    public FindMeDialogueConfigScreen(Screen parent) {
        super(Component.translatable("screen.find_me.config_dialogue"));
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ClientScreenBackground.renderDim(graphics, this.width, this.height);
        int x = this.panelX();
        int y = this.panelY();
        int panelW = this.panelWidth();
        int panelH = this.panelHeight();
        graphics.fill(x, y, x + panelW, y + panelH, -300936424);
        graphics.fill(x + 1, y + 1, x + panelW - 1, y + 28, -14669774);
        graphics.drawString(this.font, this.title, x + 10, y + 10, -1);
        graphics.drawString(this.font, this.trimToWidth(Component.translatable("screen.find_me.config_dialogue_hint").getString(), panelW - 20), x + 10, y + panelH - 18, -4734772);

        List<Config.DialogueListOption> options = Config.dialogueOptions();
        int listX = x + 10;
        int listY = y + 36;
        int listW = panelW - 20;
        int visibleRows = this.visibleRows();
        for (int visible = 0; visible < visibleRows && visible + this.scroll < options.size(); ++visible) {
            int index = visible + this.scroll;
            int rowY = listY + visible * ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            graphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT - 2, hovered ? -2009315755 : 857744427);
            Config.DialogueListOption option = options.get(index);
            int count = Config.dialogueValue(option).size();
            graphics.drawString(this.font, this.trimToWidth(Component.translatable(option.labelKey()).getString(), listW - 70), listX + 8, rowY + 7, -1);
            graphics.drawString(this.font, Component.translatable("screen.find_me.config_dialogue_count", count), listX + listW - 58, rowY + 7, -6642510);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = this.rowAt(mouseX, mouseY);
        List<Config.DialogueListOption> options = Config.dialogueOptions();
        if (FindMeUiKeys.isPrimaryMouse(button) && row >= 0 && row < options.size()) {
            Minecraft.getInstance().setScreen(new FindMeDialogueListEditScreen(this, options.get(row)));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        int max = Math.max(0, Config.dialogueOptions().size() - this.visibleRows());
        this.scroll = Math.max(0, Math.min(max, this.scroll + (delta < 0.0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (FindMeUiKeys.isCancel(keyCode)) {
            Minecraft.getInstance().setScreen(this.parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowAt(double mouseX, double mouseY) {
        int x = this.panelX() + 10;
        int y = this.panelY() + 36;
        int w = this.panelWidth() - 20;
        if (mouseX < (double)x || mouseX > (double)(x + w) || mouseY < (double)y || mouseY > (double)(y + this.visibleRows() * ROW_HEIGHT)) {
            return -1;
        }
        int visible = (int)((mouseY - (double)y) / (double)ROW_HEIGHT);
        return visible + this.scroll;
    }

    private int visibleRows() {
        return Math.max(3, (this.panelHeight() - 62) / ROW_HEIGHT);
    }

    private int panelX() {
        return CompanionScreenLayout.panelX(this.width);
    }

    private int panelY() {
        return CompanionScreenLayout.panelY(this.height);
    }

    private int panelWidth() {
        return CompanionScreenLayout.panelWidth(this.width);
    }

    private int panelHeight() {
        return CompanionScreenLayout.panelHeight(this.height);
    }

    private String trimToWidth(String text, int pixels) {
        return this.font.width(text) <= pixels ? text : this.font.plainSubstrByWidth(text, Math.max(8, pixels - this.font.width("..."))) + "...";
    }
}
