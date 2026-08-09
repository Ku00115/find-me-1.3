package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Vanilla editor for the client-side FindMe HUD placement. */
public final class FindMeHudEditorScreen extends Screen {
    private ClientFindMeHudLayout.Layout workingLayout;
    private boolean dragging;
    private double grabX;
    private double grabY;

    private FindMeHudEditorScreen() {
        super(Component.translatable("screen.find_me.hud.editor_title"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof FindMeHudEditorScreen)) {
            minecraft.setScreen(new FindMeHudEditorScreen());
        }
    }

    @Override
    protected void init() {
        workingLayout = ClientFindMeHudLayout.current();
        dragging = false;
        int buttonY = height - 32;
        addRenderableWidget(Button.builder(Component.translatable("screen.find_me.hud.reset"), button -> {
            workingLayout = ClientFindMeHudLayout.defaults();
        }).bounds(12, buttonY, 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.find_me.hud.cancel"),
                button -> onClose()).bounds(70, buttonY, 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.find_me.hud.save"),
                button -> saveAndClose()).bounds(128, buttonY, 52, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99090E11);
        graphics.fill(12, 12, 14, 42, 0xFF16B5DF);
        graphics.drawString(font, title, 21, 14, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.find_me.hud.editor_hint"),
                21, 29, 0xFFB8C5C9);
        FindMeHudRenderer.renderEditor(graphics, workingLayout, width, height, (float) workingLayout.scale());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && insideHud(mouseX, mouseY)) {
            int left = editorLeft();
            int top = editorTop();
            grabX = mouseX - left;
            grabY = mouseY - top;
            dragging = true;
            return true;
        }
        return button == 0 ? false : super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            workingLayout = ClientFindMeHudLayout.fromPixels(mouseX - grabX, mouseY - grabY,
                    width, height, workingLayout.visible(),
                    FindMeHudRenderer.editorWidth(workingLayout.scale()),
                    FindMeHudRenderer.editorHeight(workingLayout.scale()), workingLayout.scale());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (insideHud(mouseX, mouseY) && scrollDelta != 0.0) {
            double nextScale = Mth.clamp((float) workingLayout.scale()
                    + (scrollDelta > 0.0 ? 0.1f : -0.1f), 0.25f, 3.0f);
            workingLayout = workingLayout.withScale(nextScale);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean insideHud(double mouseX, double mouseY) {
        int left = editorLeft();
        int top = editorTop();
        return mouseX >= left && mouseX <= left + FindMeHudRenderer.editorWidth(workingLayout.scale())
                && mouseY >= top && mouseY <= top + FindMeHudRenderer.editorHeight(workingLayout.scale());
    }

    private int editorLeft() {
        return ClientFindMeHudLayout.left(workingLayout, width,
                FindMeHudRenderer.editorWidth(workingLayout.scale()));
    }

    private int editorTop() {
        return ClientFindMeHudLayout.top(workingLayout, height,
                FindMeHudRenderer.editorHeight(workingLayout.scale()));
    }

    private void saveAndClose() {
        ClientFindMeHudLayout.save(workingLayout);
        FindMeHudRenderer.refresh();
        onClose();
    }
}
