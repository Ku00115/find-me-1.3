package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import com.kuzhi.findme.common.PackEntityMovementOverride;
import com.kuzhi.findme.common.PackEntityPresetField;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionEffectStylePacket;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.PackAnimationPresetStylePacket;
import com.kuzhi.findme.network.PackEntityPresetUpdatePacket;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class CompanionAnimationSettingsScreen extends FindMeScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int FADE_TICKS = 6;
    private static final int PURPOSE_MENU_WIDTH = 92;
    private static final int STYLE_MENU_WIDTH = 172;
    private static final int MENU_GAP = 4;
    private static final int MENU_ROW_HEIGHT = 20;
    private static final int INSPECTOR_BUTTON_HEIGHT = 16;
    private static final int INSPECTOR_BUTTON_STEP = 20;
    private static final int PURPOSE_BUTTON_HEIGHT = 18;
    private static final int PURPOSE_BUTTON_STEP = 22;
    private static final int STYLE_BUTTON_HEIGHT = 17;
    private static final int STYLE_BUTTON_STEP = 20;
    private static final int SCALE_BUTTON_WIDTH = 20;
    private static final int SCALE_BUTTON_HEIGHT = 14;
    private static final int PREVIEW_BUTTON_HEIGHT = 16;
    private final Screen parent;
    private final boolean packEditor;
    private Category category = Category.MOUNT;
    private int scroll;
    private int openTicks;
    private int selected = -1;
    private int menuX;
    private int menuY;
    private CompanionEffectPurpose purpose;
    private EditPage editPage = EditPage.ANIMATION;
    private EditBox searchBox;
    private EditBox arrivalSoundBox;
    private String soundEditorKey = "";
    private final Set<Integer> selectedRows = new LinkedHashSet<>();
    private boolean draggingSelection;
    private int dragAnchor = -1;
    private String pressedButton = "";
    private int pressedTicks;
    private boolean previewOpen;
    private Entity previewEntity;
    private String previewEntityKey = "";
    private float previewYaw;

    CompanionAnimationSettingsScreen(Screen parent) {
        this(parent, false);
    }

    CompanionAnimationSettingsScreen(Screen parent, boolean packEditor) {
        super(Component.translatable("screen.find_me.animation_settings"));
        this.parent = parent;
        this.packEditor = packEditor;
    }

    @Override
    protected void init() {
        ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.MOUNT, CompanionAction.SYNC, -1));
        ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.COMPANION, CompanionAction.SYNC, -1));
        ModNetwork.sendToServer(new VehicleCommandPacket(VehicleCommandAction.SYNC, null, -1));
        this.searchBox = new EditBox(this.font, 0, 0, 120, 18, Component.translatable("screen.find_me.search"));
        this.searchBox.setMaxLength(96);
        this.searchBox.setBordered(false);
        this.addRenderableWidget(this.searchBox);
        this.arrivalSoundBox = new EditBox(this.font, 0, 0, 120, 18, Component.translatable("screen.find_me.pack_arrival_sound"));
        this.arrivalSoundBox.setMaxLength(128);
        this.arrivalSoundBox.setBordered(false);
        this.arrivalSoundBox.visible = false;
        this.addRenderableWidget(this.arrivalSoundBox);
    }

    @Override
    public void tick() {
        if (openTicks < FADE_TICKS) openTicks++;
        if (pressedTicks > 0 && --pressedTicks <= 0) {
            pressedButton = "";
        }
        if (previewOpen) {
            previewYaw = (previewYaw + 0.8f) % 360.0f;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float fade = Math.min(1.0f, (openTicks + partialTick) / FADE_TICKS);
        ClientScreenBackground.renderDim(graphics, width, height);
        int x = CompanionScreenLayout.panelX(width);
        int y = CompanionScreenLayout.panelY(height);
        int w = CompanionScreenLayout.panelWidth(width);
        int h = CompanionScreenLayout.panelHeight(height);
        graphics.pose().pushPose();
        float scale = 0.96f + 0.04f * fade;
        graphics.pose().translate(x + w / 2.0, y + h / 2.0, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-(x + w / 2.0), -(y + h / 2.0), 0);
        graphics.fill(x, y, x + w, y + h, fadeColor(0xDD1B2026, fade));
        graphics.drawString(font, title, x + 8, y + 7, fadeColor(0xFFFFFF, fade));
        if (packMode()) {
            graphics.drawString(font, Component.translatable("screen.find_me.pack_edit_mode"), x + w - 156, y + 7, fadeColor(0xFFD166, fade));
            renderPackToolbar(graphics, x + 8, y + 25, w - 16, mouseX, mouseY, fade);
            int listW = packListWidth(w);
            int inspectorX = x + 8 + listW + 8;
            int inspectorW = w - 16 - listW - 8;
            renderRows(graphics, x + 8, y + 73, listW, h - 81, mouseX, mouseY, fade);
            renderInspector(graphics, inspectorX, y + 73, inspectorW, h - 81, mouseX, mouseY, fade);
        } else {
            if (searchBox != null) searchBox.visible = false;
            if (arrivalSoundBox != null) arrivalSoundBox.visible = false;
            renderTabs(graphics, x + 8, y + 25, mouseX, mouseY, fade);
            renderRows(graphics, x + 8, y + 51, w - 16, h - 59, mouseX, mouseY, fade);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (animationMenuOpen()) renderPurposeMenu(graphics, fade);
        if (purpose != null && animationMenuOpen()) renderStyleMenu(graphics, fade);
        graphics.pose().popPose();
        if (previewOpen) renderPreviewOverlay(graphics, mouseX, mouseY, fade);
    }

    private void renderPackToolbar(GuiGraphics g, int x, int y, int w, int mx, int my, float fade) {
        renderTabs(g, x, y, mx, my, fade);
        int editRowY = y + 26;
        if (searchBox != null) {
            searchBox.visible = true;
            searchBox.setX(x + 2);
            searchBox.setY(editRowY + 4);
            searchBox.setWidth(Math.min(150, Math.max(96, packListWidth(w + 16) / 2)));
            g.fill(searchBox.getX() - 5, editRowY, searchBox.getX() + searchBox.getWidth() + 5, editRowY + 19, fadeColor(0x66364148, fade));
        }
        int tabsX = x + 168;
        renderEditTabs(g, tabsX, editRowY, w - (tabsX - x), mx, my, fade, true);
    }

    private void renderEditTabs(GuiGraphics g, int x, int y, int w, int mx, int my, float fade, boolean showSelected) {
        int tabW = Math.max(48, Math.min(68, (w - 12) / EditPage.values().length));
        for (int i = 0; i < EditPage.values().length; i++) {
            EditPage value = EditPage.values()[i];
            int tx = x + i * (tabW + 6);
            boolean hovered = inside(tx, y, tabW, 19, mx, my);
            g.fill(tx, y, tx + tabW, y + 19, fadeColor(value == editPage ? 0x996EA06F : hovered ? 0x66475A48 : 0x44364148, fade));
            g.drawCenteredString(font, Component.translatable(value.key), tx + tabW / 2, y + 5, fadeColor(0xFFFFFF, fade));
        }
        if (showSelected) {
            int count = selectedRows.isEmpty() ? 0 : selectedRows.size();
            Component label = Component.translatable("screen.find_me.pack_selected", count);
            int labelX = x + EditPage.values().length * (tabW + 6) + 8;
            if (labelX + font.width(label) < x + w) {
                g.drawString(font, label, labelX, y + 5, fadeColor(0xA9D8F2, fade));
            }
        }
    }

    private void renderTabs(GuiGraphics g, int x, int y, int mx, int my, float fade) {
        int tabW = packMode() ? packCategoryTabWidth(CompanionScreenLayout.panelWidth(width) - 16) : 92;
        int drawn = 0;
        for (int i = 0; i < Category.values().length; i++) {
            Category value = Category.values()[i];
            if (!packMode() && (value == Category.ALL || value == Category.DISABLED)) continue;
            int tx = x + drawn++ * (tabW + (packMode() ? 5 : 8));
            boolean hovered = inside(tx, y, tabW, 20, mx, my);
            g.fill(tx, y, tx + tabW, y + 20, fadeColor(value == category ? 0x995D91B2 : hovered ? 0x663F505C : 0x44364148, fade));
            g.drawCenteredString(font, Component.translatable(value.key), tx + tabW / 2, y + 6, fadeColor(0xFFFFFF, fade));
        }
    }

    private void renderRows(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float fade) {
        List<Row> rows = rows();
        int visible = Math.max(1, h / ROW_HEIGHT);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.find_me.no_entries"), x + w / 2, y + h / 2, fadeColor(0xFFD166, fade));
            return;
        }
        for (int slot = 0; slot < visible && slot + scroll < rows.size(); slot++) {
            Row row = rows.get(slot + scroll);
            int absolute = slot + scroll;
            int ry = y + slot * ROW_HEIGHT;
            boolean hovered = inside(x, ry, w, ROW_HEIGHT - 2, mx, my);
            boolean selectedRow = selectedRows.contains(absolute);
            g.fill(x, ry, x + w, ry + ROW_HEIGHT - 2, fadeColor(selectedRow ? 0x995D7FA2 : hovered ? 0x884C6574 : row.active ? 0x88335645 : 0x55303B43, fade));
            g.fill(x, ry, x + 4, ry + ROW_HEIGHT - 2, fadeColor(selectedRow ? 0xFFFFD166 : 0xFF5AC88A, fade));
            int contentX = x + 11;
            int contentW = Math.max(1, w - 19);
            int nameW = packMode() ? Math.max(58, contentW / 4) : Math.max(60, x + Math.max(210, w / 2) - contentX - 11);
            int summaryX = packMode() ? contentX + nameW + 8 : x + Math.max(210, w / 2);
            int summaryW = Math.max(1, x + w - 8 - summaryX);
            Component nameText = Component.literal(row.name);
            Component summaryText = Component.literal(summary(row));
            boolean nameCovered = textCoveredByMenu(nameText, contentX, ry + 6, nameW);
            boolean summaryCovered = textCoveredByMenu(summaryText, summaryX, ry + 6, summaryW);
            boolean dividerCovered = selected >= 0 && menuCovers(summaryX - 5, ry + 4, 1, ROW_HEIGHT - 10);
            if (!nameCovered) {
                drawClippedString(g, nameText, contentX, ry + 6, nameW, fadeColor(0xFFFFFF, fade));
            }
            if (packMode() && summaryW > 8 && !dividerCovered) {
                g.fill(summaryX - 5, ry + 4, summaryX - 4, ry + ROW_HEIGHT - 6, fadeColor(0x335A6F7E, fade));
            }
            if (!summaryCovered) {
                drawClippedString(g, summaryText, summaryX, ry + 6, summaryW, fadeColor(0xA9D8F2, fade));
            }
        }
    }

    private void renderInspector(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float fade) {
        g.fill(x, y, x + w, y + h, fadeColor(0x44242B32, fade));
        if (arrivalSoundBox != null && editPage != EditPage.SOUND) {
            arrivalSoundBox.visible = false;
        }
        if (selectedRows.isEmpty()) {
            drawWrapped(g, Component.translatable("screen.find_me.pack_select_hint"), x + 9, y + 10, w - 18, fadeColor(0x9DB7C5, fade));
            return;
        }
        if (editPage == EditPage.ANIMATION && animationMenuOpen()) {
            return;
        }
        switch (editPage) {
            case TYPE -> renderTypeInspector(g, x + 9, y + 10, w - 18, mx, my, fade);
            case ANIMATION -> renderAnimationInspector(g, x + 9, y + 10, w - 18, mx, my, fade);
            case SIZE -> renderSizeInspector(g, x + 9, y + 10, w - 18, mx, my, fade);
            case SOUND -> renderSoundInspector(g, x + 9, y + 10, w - 18, mx, my, fade);
        }
    }

    private void renderTypeInspector(GuiGraphics g, int x, int y, int w, int mx, int my, float fade) {
        Row row = firstSelectedRow();
        drawSection(g, Component.translatable("screen.find_me.pack_category"), x, y, fade);
        int cy = y + 16;
        PackEntityCategoryOverride[] categories = PackEntityCategoryOverride.values();
        for (int i = 0; i < categories.length; i++) {
            PackEntityCategoryOverride value = categories[i];
            boolean active = row != null && row.categoryOverride == value;
            drawOptionButton(g, x + (i % 2) * ((w - 6) / 2 + 6), cy + (i / 2) * INSPECTOR_BUTTON_STEP, (w - 6) / 2, INSPECTOR_BUTTON_HEIGHT, Component.translatable("screen.find_me.pack_category." + value.name().toLowerCase(Locale.ROOT)), mx, my, fade, active || flashing(fieldKey(PackEntityPresetField.CATEGORY, value.name())));
        }
        int movementY = cy + ((categories.length + 1) / 2) * INSPECTOR_BUTTON_STEP + 7;
        drawSection(g, Component.translatable("screen.find_me.pack_movement"), x, movementY, fade);
        PackEntityMovementOverride[] moves = PackEntityMovementOverride.values();
        for (int i = 0; i < moves.length; i++) {
            PackEntityMovementOverride value = moves[i];
            boolean active = row != null && row.movementOverride == value;
            drawOptionButton(g, x + (i % 2) * ((w - 6) / 2 + 6), movementY + 16 + (i / 2) * INSPECTOR_BUTTON_STEP, (w - 6) / 2, INSPECTOR_BUTTON_HEIGHT, Component.translatable("screen.find_me.pack_movement." + value.name().toLowerCase(Locale.ROOT)), mx, my, fade, active || flashing(fieldKey(PackEntityPresetField.MOVEMENT, value.name())));
        }
    }

    private void renderAnimationInspector(GuiGraphics g, int x, int y, int w, int mx, int my, float fade) {
        drawWrapped(g, Component.translatable("screen.find_me.pack_animation_menu_hint"), x, y, w, fadeColor(0xA9D8F2, fade));
        Row row = firstSelectedRow();
        if (row == null) {
            return;
        }
        int yy = y + 54;
        drawSection(g, Component.translatable("screen.find_me.pack_current_animation"), x, yy, fade);
        drawClippedString(g, Component.translatable("screen.find_me.effect_purpose_short.summon"), x, yy + 17, 62, fadeColor(0xFFFFFF, fade));
        drawClippedString(g, styleName(row.summon), x + 70, yy + 17, w - 70, fadeColor(0xA9D8F2, fade));
        drawClippedString(g, Component.translatable("screen.find_me.effect_purpose_short.rescue"), x, yy + 34, 62, fadeColor(0xFFFFFF, fade));
        drawClippedString(g, styleName(row.rescue), x + 70, yy + 34, w - 70, fadeColor(0xA9D8F2, fade));
        drawClippedString(g, Component.translatable("screen.find_me.effect_purpose_short.storage"), x, yy + 51, 62, fadeColor(0xFFFFFF, fade));
        drawClippedString(g, styleName(row.storage), x + 70, yy + 51, w - 70, fadeColor(0xA9D8F2, fade));
    }

    private void renderSizeInspector(GuiGraphics g, int x, int y, int w, int mx, int my, float fade) {
        Row row = firstSelectedRow();
        float bounds = row == null ? 1.0f : row.boundsScale;
        float circle = row == null ? 1.0f : row.circleScale;
        renderScaleControl(g, x, y, w, Component.translatable("screen.find_me.pack_bounds_scale"), bounds, PackEntityPresetField.BOUNDS_SCALE, mx, my, fade);
        renderScaleControl(g, x, y + 40, w, Component.translatable("screen.find_me.pack_circle_scale"), circle, PackEntityPresetField.CIRCLE_SCALE, mx, my, fade);
        drawOptionButton(g, x, y + 82, Math.min(82, w), PREVIEW_BUTTON_HEIGHT, Component.translatable("screen.find_me.preview"), mx, my, fade, flashing("PREVIEW"));
        drawWrapped(g, Component.translatable("screen.find_me.pack_size_hint"), x, y + 107, w, fadeColor(0x9DB7C5, fade));
    }

    private void renderSoundInspector(GuiGraphics g, int x, int y, int w, int mx, int my, float fade) {
        Row row = firstSelectedRow();
        if (row == null) {
            if (arrivalSoundBox != null) arrivalSoundBox.visible = false;
            return;
        }
        if (arrivalSoundBox != null) {
            arrivalSoundBox.visible = true;
            arrivalSoundBox.setX(x);
            arrivalSoundBox.setY(y + 16);
            arrivalSoundBox.setWidth(Math.max(48, w - 34));
            String key = row.entityType + ":" + selectedRows.size();
            if (!arrivalSoundBox.isFocused() && !key.equals(soundEditorKey)) {
                arrivalSoundBox.setValue(row.arrivalSound);
                soundEditorKey = key;
            }
            g.fill(x - 3, y + 14, x + arrivalSoundBox.getWidth() + 3, y + 35, fadeColor(0x66364148, fade));
        }
        boolean valid = row.arrivalSound.isBlank() || validSoundId(row.arrivalSound);
        drawSection(g, Component.translatable("screen.find_me.pack_arrival_sound"), x, y, fade);
        if (!valid) {
            drawClippedString(g, Component.translatable("screen.find_me.pack_arrival_sound_invalid"), x + Math.max(0, w - 102), y, 102, fadeColor(0xFFFF7777, fade));
        }
        drawOptionButton(g, x + w - 28, y + 16, 28, 18, Component.translatable("screen.find_me.clear"), mx, my, fade, flashing("SOUND_CLEAR"));
        renderAudioControl(g, x, y + 43, w, Component.translatable("screen.find_me.pack_arrival_volume"), row.arrivalVolume, PackEntityPresetField.ARRIVAL_VOLUME, mx, my, fade);
        renderAudioControl(g, x, y + 78, w, Component.translatable("screen.find_me.pack_arrival_pitch"), row.arrivalPitch, PackEntityPresetField.ARRIVAL_PITCH, mx, my, fade);
    }

    private void renderAudioControl(GuiGraphics g, int x, int y, int w, Component label, float value, PackEntityPresetField field, int mx, int my, float fade) {
        drawSection(g, label, x, y, fade);
        int buttonY = y + 14;
        drawOptionButton(g, x, buttonY, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, Component.literal("-"), mx, my, fade, flashing(scaleKey(field, "-")));
        g.fill(x + SCALE_BUTTON_WIDTH + 6, buttonY, x + w - SCALE_BUTTON_WIDTH - 6, buttonY + SCALE_BUTTON_HEIGHT, fadeColor(0x55364148, fade));
        g.drawCenteredString(font, String.format(Locale.ROOT, "%.2f", value), x + w / 2, buttonY + 2, fadeColor(0xFFFFFF, fade));
        drawOptionButton(g, x + w - SCALE_BUTTON_WIDTH, buttonY, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, Component.literal("+"), mx, my, fade, flashing(scaleKey(field, "+")));
    }

    private void renderScaleControl(GuiGraphics g, int x, int y, int w, Component label, float value, PackEntityPresetField field, int mx, int my, float fade) {
        drawSection(g, label, x, y, fade);
        int buttonY = y + 16;
        drawOptionButton(g, x, buttonY, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, Component.literal("-"), mx, my, fade, flashing(scaleKey(field, "-")));
        boolean resetFlash = flashing(scaleKey(field, "1.0"));
        g.fill(x + SCALE_BUTTON_WIDTH + 6, buttonY, x + w - SCALE_BUTTON_WIDTH - 6, buttonY + SCALE_BUTTON_HEIGHT, fadeColor(resetFlash ? 0x885D91B2 : 0x55364148, fade));
        String valueText = String.format(Locale.ROOT, "%.2fx", value);
        g.drawCenteredString(font, valueText, x + w / 2, buttonY + Math.max(2, (SCALE_BUTTON_HEIGHT - font.lineHeight) / 2), fadeColor(0xFFFFFF, fade));
        drawOptionButton(g, x + w - SCALE_BUTTON_WIDTH, buttonY, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, Component.literal("+"), mx, my, fade, flashing(scaleKey(field, "+")));
    }

    private void renderPreviewOverlay(GuiGraphics g, int mx, int my, float fade) {
        Row row = firstSelectedRow();
        int boxW = previewBoxW();
        int boxH = previewBoxH();
        int x = previewBoxX(boxW);
        int y = previewBoxY(boxH);
        g.fill(0, 0, width, height, fadeColor(0x99000000, fade));
        g.fill(x - 4, y - 4, x + boxW + 4, y + boxH + 4, fadeColor(0xE6000000, fade));
        g.fill(x, y, x + boxW, y + boxH, fadeColor(0xEE1B2026, fade));
        g.drawString(font, Component.translatable("screen.find_me.preview_title"), x + 10, y + 9, fadeColor(0xFFD166, fade));
        int closeW = 52;
        drawOptionButton(g, x + boxW - closeW - 8, y + 7, closeW, 16, Component.translatable("screen.find_me.close"), mx, my, fade, false);
        if (row == null) {
            drawWrapped(g, Component.translatable("screen.find_me.preview_no_entity"), x + 10, y + 34, boxW - 20, fadeColor(0xA9D8F2, fade));
            return;
        }
        Entity entity = previewEntity(row);
        int contentY = y + 32;
        int contentH = boxH - 42;
        int infoW = Math.min(190, Math.max(150, boxW / 3));
        int previewX = x + 10;
        int previewY = contentY;
        int previewW = boxW - infoW - 30;
        int previewH = contentH;
        int infoX = previewX + previewW + 10;
        int infoY = contentY;
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, fadeColor(0x55242B32, fade));
        g.fill(infoX, infoY, infoX + infoW, infoY + contentH, fadeColor(0x44242B32, fade));
        if (entity != null) {
            AABB box = entity.getBoundingBox();
            double entityWidth = Math.max(box.getXsize(), box.getZsize());
            double entityHeight = box.getYsize();
            float scale = (float)Math.min(62.0, Math.max(8.0, Math.min(previewW * 0.30 / Math.max(0.5, entityWidth), previewH * 0.62 / Math.max(0.5, entityHeight))));
            CompanionDetailPreviewRenderer.stabilizeStoredPreviewPose(entity, previewYaw, 0.0f);
            g.enableScissor(previewX + 2, previewY + 2, previewX + previewW - 2, previewY + previewH - 2);
            CompanionDetailPreviewRenderer.renderPreviewEntity(g, entity, previewX + previewW / 2, previewY + previewH - 12, scale, previewYaw, 0.0f);
            g.disableScissor();
            int frameW = Math.max(16, (int)(entityWidth * scale));
            int frameH = Math.max(16, (int)(entityHeight * scale));
            int frameX = previewX + previewW / 2 - frameW / 2;
            int frameY = previewY + previewH - 12 - frameH;
            drawRectOutline(g, frameX, frameY, frameW, frameH, fadeColor(0xAA66D9FF, fade));
        } else {
            g.drawCenteredString(font, Component.translatable("screen.find_me.preview_no_entity"), previewX + previewW / 2, previewY + previewH / 2 - 4, fadeColor(0xA9D8F2, fade));
        }
        AABB bounds = entity == null ? null : entity.getBoundingBox();
        double rawX = bounds == null ? 0.0 : bounds.getXsize();
        double rawY = bounds == null ? 0.0 : bounds.getYsize();
        double rawZ = bounds == null ? 0.0 : bounds.getZsize();
        int textY = infoY + 10;
        drawClippedString(g, Component.literal(row.name), infoX + 8, textY, infoW - 16, fadeColor(0xFFFFFF, fade));
        drawWrapped(g, Component.translatable("screen.find_me.collision_box", format(rawX), format(rawY), format(rawZ)), infoX + 8, textY + 18, infoW - 16, fadeColor(0xA9D8F2, fade));
        drawWrapped(g, Component.translatable("screen.find_me.visual_box", format(rawX * row.boundsScale), format(rawY * row.boundsScale), format(rawZ * row.boundsScale)), infoX + 8, textY + 58, infoW - 16, fadeColor(0xA9D8F2, fade));
        drawWrapped(g, Component.translatable("screen.find_me.circle_multiplier", String.format(Locale.ROOT, "%.2f", row.circleScale)), infoX + 8, textY + 98, infoW - 16, fadeColor(0xA9D8F2, fade));
    }

    private int previewBoxW() {
        return Math.max(340, Math.min(560, width - 64));
    }

    private int previewBoxH() {
        return Math.max(250, Math.min(320, height - 56));
    }

    private int previewBoxX(int boxW) {
        return (width - boxW) / 2;
    }

    private int previewBoxY(int boxH) {
        return (height - boxH) / 2;
    }

    private void drawSection(GuiGraphics g, Component text, int x, int y, float fade) {
        g.drawString(font, text, x, y, fadeColor(0xFFD166, fade));
    }

    private void drawOptionButton(GuiGraphics g, int x, int y, int w, int h, Component text, float fade) {
        drawOptionButton(g, x, y, w, h, text, fade, false);
    }

    private void drawOptionButton(GuiGraphics g, int x, int y, int w, int h, Component text, float fade, boolean active) {
        drawOptionButton(g, x, y, w, h, text, Integer.MIN_VALUE, Integer.MIN_VALUE, fade, active);
    }

    private void drawOptionButton(GuiGraphics g, int x, int y, int w, int h, Component text, int mx, int my, float fade, boolean active) {
        boolean hovered = mx != Integer.MIN_VALUE && inside(x, y, w, h, mx, my);
        int base = active ? 0xAA5D91B2 : hovered ? 0x884C6574 : 0x66364148;
        g.fill(x, y, x + w, y + h, fadeColor(base, fade));
        drawClippedString(g, text, x + 5, y + Math.max(2, (h - font.lineHeight) / 2), w - 10, fadeColor(0xFFFFFF, fade));
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (var line : font.split(text, w)) {
            if (y > height - 18) return;
            g.drawString(font, line, x, y, color);
            y += font.lineHeight + 2;
        }
    }

    private void renderPurposeMenu(GuiGraphics g, float fade) {
        g.fill(menuX, menuY, menuX + PURPOSE_MENU_WIDTH, menuY + purposeMenuHeight(), 0xFF20252B);
        for (int i = 0; i < CompanionEffectPurpose.values().length; i++) {
            CompanionEffectPurpose value = CompanionEffectPurpose.values()[i];
            int ry = menuY + 4 + i * MENU_ROW_HEIGHT;
            if (value == purpose) {
                g.fill(menuX + 3, ry - 1, menuX + PURPOSE_MENU_WIDTH - 3, ry + MENU_ROW_HEIGHT - 2, 0x884D86A8);
            }
            drawScrollingString(g, Component.translatable("screen.find_me.effect_purpose." + value.name().toLowerCase()), menuX + 7, ry + 3, PURPOSE_MENU_WIDTH - 14, 0xFFFFFF);
        }
    }

    private void renderStyleMenu(GuiGraphics g, float fade) {
        int x = styleMenuX();
        int y = styleMenuY();
        CompanionEffectStyle[] styles = stylesForPurpose();
        CompanionEffectStyle current = currentStyle();
        g.fill(x, y, x + STYLE_MENU_WIDTH, y + styleMenuHeight(), 0xFF20252B);
        for (int i = 0; i < styles.length; i++) {
            int ry = y + 4 + i * MENU_ROW_HEIGHT;
            if (styles[i] == current) {
                g.fill(x + 3, ry - 1, x + STYLE_MENU_WIDTH - 3, ry + MENU_ROW_HEIGHT - 2, 0x884D86A8);
            }
            drawScrollingString(g, Component.translatable("screen.find_me.effect_style." + styles[i].name().toLowerCase()), x + 7, ry + 3, STYLE_MENU_WIDTH - 14, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (previewOpen) {
            int boxW = previewBoxW();
            int boxH = previewBoxH();
            int x = previewBoxX(boxW);
            int y = previewBoxY(boxH);
            int closeW = 52;
            if (FindMeUiKeys.isPrimaryMouse(button) && inside(x + boxW - closeW - 8, y + 7, closeW, 16, mx, my)) {
                previewOpen = false;
                flash("PREVIEW_CLOSE");
                return true;
            }
            if (!inside(x, y, boxW, boxH, mx, my)) {
                previewOpen = false;
            }
            return true;
        }
        if (packMode() && FindMeUiKeys.isPrimaryMouse(button) && handlePackToolbarClick(mx, my)) {
            return true;
        }
        if (purpose != null && FindMeUiKeys.isPrimaryMouse(button) && inside(styleMenuX(), styleMenuY(), STYLE_MENU_WIDTH, styleMenuHeight(), mx, my)) {
            int i = ((int) my - styleMenuY() - 2) / 20;
            CompanionEffectStyle[] styles = stylesForPurpose();
            if (i >= 0 && i < styles.length) apply(styles[i]);
            return true;
        }
        if (selected >= 0 && FindMeUiKeys.isPrimaryMouse(button) && inside(menuX, menuY, PURPOSE_MENU_WIDTH, purposeMenuHeight(), mx, my)) {
            int i = ((int) my - menuY - 2) / 20;
            if (i >= 0 && i < CompanionEffectPurpose.values().length) purpose = CompanionEffectPurpose.values()[i];
            return true;
        }
        if (packMode() && editPage == EditPage.ANIMATION && FindMeUiKeys.isPrimaryMouse(button) && (purpose != null || selected >= 0)) {
            closeMenu();
        }
        if (packMode() && editPage == EditPage.SOUND && arrivalSoundBox != null && arrivalSoundBox.visible
                && inside(arrivalSoundBox.getX(), arrivalSoundBox.getY(), arrivalSoundBox.getWidth(), arrivalSoundBox.getHeight(), mx, my)) {
            arrivalSoundBox.setFocused(true);
            return arrivalSoundBox.mouseClicked(mx, my, button);
        }
        if (packMode() && FindMeUiKeys.isPrimaryMouse(button) && handleInspectorClick(mx, my)) {
            return true;
        }
        int panelX = CompanionScreenLayout.panelX(width) + 8;
        int panelY = CompanionScreenLayout.panelY(height);
        if (!packMode()) {
            int drawn = 0;
            for (Category value : Category.values()) {
                if (value == Category.ALL || value == Category.DISABLED) continue;
                if (FindMeUiKeys.isPrimaryMouse(button) && inside(panelX + drawn * 100, panelY + 25, 92, 20, mx, my)) {
                    category = value; scroll = 0; closeMenu(); selectedRows.clear(); return true;
                }
                drawn++;
            }
        }
        int row = rowAt(mx, my);
        if (FindMeUiKeys.isSecondaryMouse(button) && row >= 0) {
            if (!packMode() || !selectedRows.contains(row)) {
                selectedRows.clear();
                selectedRows.add(row);
            }
            if (packMode()) {
                selected = row;
                if (editPage == EditPage.ANIMATION) {
                    purpose = null;
                    menuX = clampToPanelX((int) mx, PURPOSE_MENU_WIDTH);
                    menuY = clampToPanelY((int) my, 64);
                } else {
                    closeMenu();
                }
                return true;
            }
            selected = row;
            purpose = null;
            menuX = clampToPanelX((int) mx, PURPOSE_MENU_WIDTH);
            menuY = clampToPanelY((int) my, 64);
            return true;
        }
        if (FindMeUiKeys.isPrimaryMouse(button) && row >= 0 && packMode()) {
            closeMenu();
            if (!Screen.hasControlDown()) {
                selectedRows.clear();
            }
            selectedRows.add(row);
            selected = row;
            dragAnchor = row;
            draggingSelection = true;
            return true;
        }
        closeMenu();
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double dy) {
        int max = Math.max(0, rows().size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll + (dy < 0 ? 1 : -1)));
        if (!packMode()) {
            closeMenu();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (packMode() && FindMeUiKeys.isPrimaryMouse(button) && draggingSelection) {
            autoScrollDuringDrag(my);
            int row = rowAt(mx, my);
            if (row >= 0) {
                selectRange(dragAnchor, row);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (FindMeUiKeys.isPrimaryMouse(button)) {
            draggingSelection = false;
            dragAnchor = -1;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (previewOpen) {
            if (FindMeUiKeys.isCancel(keyCode)) {
                previewOpen = false;
            }
            return true;
        }
        if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            selectedRows.clear();
            scroll = 0;
            closeMenu();
            return true;
        }
        if (arrivalSoundBox != null && arrivalSoundBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                sendPresetField(PackEntityPresetField.ARRIVAL_SOUND, arrivalSoundBox.getValue().trim());
                soundEditorKey = "";
                return true;
            }
            if (arrivalSoundBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (packMode() && FindMeUiKeys.isSelectAll(keyCode) && Screen.hasControlDown()) {
            selectedRows.clear();
            for (int i = 0; i < rows().size(); i++) {
                selectedRows.add(i);
            }
            closeMenu();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (previewOpen) {
            return true;
        }
        if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) {
            selectedRows.clear();
            scroll = 0;
            closeMenu();
            return true;
        }
        if (arrivalSoundBox != null && arrivalSoundBox.isFocused() && arrivalSoundBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean handlePackToolbarClick(double mx, double my) {
        int x = CompanionScreenLayout.panelX(width) + 8;
        int y = CompanionScreenLayout.panelY(height) + 25;
        int w = CompanionScreenLayout.panelWidth(width) - 16;
        int tabX = x;
        int tabW = packCategoryTabWidth(w);
        for (int i = 0; i < Category.values().length; i++) {
            int tx = tabX + i * (tabW + 5);
            if (inside(tx, y, tabW, 20, mx, my)) {
                category = Category.values()[i];
                scroll = 0;
                selectedRows.clear();
                closeMenu();
                return true;
            }
        }
        int tabsX = x + 168;
        if (handleInspectorPageClick(mx, my, tabsX, y + 26, w - (tabsX - x))) {
            return true;
        }
        return false;
    }

    private boolean handleInspectorClick(double mx, double my) {
        int panelX = CompanionScreenLayout.panelX(width);
        int panelY = CompanionScreenLayout.panelY(height);
        int panelW = CompanionScreenLayout.panelWidth(width);
        int listW = packListWidth(panelW);
        int x = panelX + 8 + listW + 8;
        int y = panelY + 73;
        int w = panelW - 16 - listW - 8;
        int h = CompanionScreenLayout.panelHeight(height) - 81;
        if (!inside(x, y, w, h, mx, my)) {
            return false;
        }
        if (selectedRows.isEmpty()) {
            return true;
        }
        int innerX = x + 9;
        int innerY = y + 10;
        int innerW = w - 18;
        if (editPage == EditPage.TYPE) {
            if (handleEnumGridClick(mx, my, innerX, innerY + 16, innerW, PackEntityCategoryOverride.values(), PackEntityPresetField.CATEGORY)) return true;
            int moveY = innerY + 16 + ((PackEntityCategoryOverride.values().length + 1) / 2) * INSPECTOR_BUTTON_STEP + 23;
            return handleEnumGridClick(mx, my, innerX, moveY, innerW, PackEntityMovementOverride.values(), PackEntityPresetField.MOVEMENT);
        }
        if (editPage == EditPage.ANIMATION) {
            return true;
        }
        if (editPage == EditPage.SOUND) {
            Row row = firstSelectedRow();
            if (row == null) return true;
            if (inside(innerX + innerW - 28, innerY + 16, 28, 18, mx, my)) {
                flash("SOUND_CLEAR");
                soundEditorKey = "";
                if (arrivalSoundBox != null) arrivalSoundBox.setValue("");
                sendPresetField(PackEntityPresetField.ARRIVAL_SOUND, "");
                return true;
            }
            if (handleAudioClick(mx, my, innerX, innerY + 53, innerW, PackEntityPresetField.ARRIVAL_VOLUME, row.arrivalVolume)) return true;
            if (handleAudioClick(mx, my, innerX, innerY + 88, innerW, PackEntityPresetField.ARRIVAL_PITCH, row.arrivalPitch)) return true;
            return true;
        }
        if (editPage == EditPage.SIZE) {
            if (handleScaleClick(mx, my, innerX, innerY + 16, innerW, PackEntityPresetField.BOUNDS_SCALE, firstSelectedRow() == null ? 1.0f : firstSelectedRow().boundsScale)) return true;
            if (handleScaleClick(mx, my, innerX, innerY + 56, innerW, PackEntityPresetField.CIRCLE_SCALE, firstSelectedRow() == null ? 1.0f : firstSelectedRow().circleScale)) return true;
            if (inside(innerX, innerY + 82, Math.min(82, innerW), PREVIEW_BUTTON_HEIGHT, mx, my)) {
                previewOpen = true;
                flash("PREVIEW");
                return true;
            }
            return true;
        }
        return true;
    }

    private boolean handleInspectorPageClick(double mx, double my, int x, int y, int w) {
        int tabW = Math.max(48, Math.min(68, (w - 12) / EditPage.values().length));
        for (int i = 0; i < EditPage.values().length; i++) {
            int tx = x + i * (tabW + 6);
            if (inside(tx, y, tabW, 19, mx, my)) {
                editPage = EditPage.values()[i];
                closeMenu();
                return true;
            }
        }
        return false;
    }

    private <T extends Enum<T>> boolean handleEnumGridClick(double mx, double my, int x, int y, int w, T[] values, PackEntityPresetField field) {
        int buttonW = (w - 6) / 2;
        for (int i = 0; i < values.length; i++) {
            int bx = x + (i % 2) * (buttonW + 6);
            int by = y + (i / 2) * INSPECTOR_BUTTON_STEP;
            if (inside(bx, by, buttonW, INSPECTOR_BUTTON_HEIGHT, mx, my)) {
                flash(fieldKey(field, values[i].name()));
                sendPresetField(field, values[i].name());
                return true;
            }
        }
        return false;
    }

    private boolean handleScaleClick(double mx, double my, int x, int y, int w, PackEntityPresetField field, float current) {
        if (inside(x, y, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, mx, my)) {
            flash(scaleKey(field, "-"));
            sendPresetField(field, String.format(Locale.ROOT, "%.2f", Math.max(0.25f, current - 0.1f)));
            return true;
        }
        if (inside(x + w - SCALE_BUTTON_WIDTH, y, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, mx, my)) {
            flash(scaleKey(field, "+"));
            sendPresetField(field, String.format(Locale.ROOT, "%.2f", Math.min(4.0f, current + 0.1f)));
            return true;
        }
        if (inside(x + SCALE_BUTTON_WIDTH + 6, y, w - SCALE_BUTTON_WIDTH * 2 - 12, SCALE_BUTTON_HEIGHT, mx, my)) {
            flash(scaleKey(field, "1.0"));
            sendPresetField(field, "1.0");
            return true;
        }
        return false;
    }

    private boolean handleAudioClick(double mx, double my, int x, int y, int w, PackEntityPresetField field, float current) {
        if (inside(x, y, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, mx, my)) {
            flash(scaleKey(field, "-"));
            sendPresetField(field, String.format(Locale.ROOT, "%.2f", Math.max(0.0f, current - 0.1f)));
            return true;
        }
        if (inside(x + w - SCALE_BUTTON_WIDTH, y, SCALE_BUTTON_WIDTH, SCALE_BUTTON_HEIGHT, mx, my)) {
            flash(scaleKey(field, "+"));
            sendPresetField(field, String.format(Locale.ROOT, "%.2f", Math.min(2.0f, current + 0.1f)));
            return true;
        }
        return false;
    }

    private void sendPresetField(PackEntityPresetField field, String value) {
        List<Row> currentRows = rows();
        List<String> entityTypes = selectedRows.stream().filter(i -> i >= 0 && i < currentRows.size()).map(i -> currentRows.get(i).entityType).distinct().toList();
        if (!entityTypes.isEmpty()) {
            ModNetwork.sendToServer(new PackEntityPresetUpdatePacket(entityTypes, field, value));
        }
    }

    private void flash(String key) {
        pressedButton = key;
        pressedTicks = 8;
    }

    private boolean flashing(String key) {
        return pressedTicks > 0 && pressedButton.equals(key);
    }

    private static String fieldKey(PackEntityPresetField field, String value) {
        return "FIELD:" + field.name() + ":" + value;
    }

    private static String purposeKey(CompanionEffectPurpose value) {
        return "PURPOSE:" + value.name();
    }

    private static String styleKey(CompanionEffectStyle value) {
        return "STYLE:" + value.name();
    }

    private static String scaleKey(PackEntityPresetField field, String action) {
        return "SCALE:" + field.name() + ":" + action;
    }

    private int rowAt(double mx, double my) {
        int panelW = CompanionScreenLayout.panelWidth(width);
        int x = CompanionScreenLayout.panelX(width) + 8;
        int y = CompanionScreenLayout.panelY(height) + (packMode() ? 73 : 51);
        int w = packMode() ? packListWidth(panelW) : panelW - 16;
        int h = CompanionScreenLayout.panelHeight(height) - (packMode() ? 81 : 59);
        if (!inside(x, y, w, h, mx, my)) return -1;
        int index = ((int) my - y) / ROW_HEIGHT + scroll;
        return index < rows().size() ? index : -1;
    }

    private int visibleRows() { return Math.max(1, (CompanionScreenLayout.panelHeight(height) - (packMode() ? 81 : 59)) / ROW_HEIGHT); }
    private String summary(Row row) {
        if (packMode() && editPage == EditPage.TYPE) {
            return row.categoryOverride.name().toLowerCase(Locale.ROOT) + " / " + row.movementOverride.name().toLowerCase(Locale.ROOT);
        }
        if (packMode() && editPage == EditPage.SIZE) {
            return String.format(Locale.ROOT, "bounds %.2fx / circle %.2fx", row.boundsScale, row.circleScale);
        }
        return styleName(row.summon).getString() + " / " + styleName(row.rescue).getString() + " / " + styleName(row.storage).getString();
    }
    private Component styleName(CompanionEffectStyle style) { return Component.translatable("screen.find_me.effect_style." + style.name().toLowerCase()); }
    private void apply(CompanionEffectStyle style) {
        if (purpose == null) return;
        flash(styleKey(style));
        List<Row> currentRows = rows();
        if (packMode()) {
            List<String> entityTypes = selectedRows.stream().filter(i -> i >= 0 && i < currentRows.size()).map(i -> currentRows.get(i).entityType).distinct().toList();
            if (!entityTypes.isEmpty()) {
                ModNetwork.sendToServer(new PackAnimationPresetStylePacket(entityTypes, purpose, style));
            }
            return;
        }
        if (selected >= 0 && selected < currentRows.size()) {
            Row row = currentRows.get(selected);
            ModNetwork.sendToServer(new CompanionEffectStylePacket(row.uuid, purpose, style));
        }
    }
    private CompanionEffectStyle[] stylesForPurpose() {
        if (purpose == CompanionEffectPurpose.STORAGE) {
            return new CompanionEffectStyle[]{CompanionEffectStyle.NONE, CompanionEffectStyle.ENDER,
                    CompanionEffectStyle.MAGIC_CIRCLE, CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE};
        }
        return new CompanionEffectStyle[]{CompanionEffectStyle.NONE, CompanionEffectStyle.ENDER,
                CompanionEffectStyle.MAGIC_CIRCLE, CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE,
                CompanionEffectStyle.VELOCITY_BURST};
    }
    private int purposeMenuHeight() { return 8 + CompanionEffectPurpose.values().length * MENU_ROW_HEIGHT; }
    private int styleMenuHeight() { return 8 + stylesForPurpose().length * MENU_ROW_HEIGHT; }
    private int styleMenuX() {
        int panelLeft = CompanionScreenLayout.panelX(width) + 8;
        int panelRight = CompanionScreenLayout.panelX(width) + CompanionScreenLayout.panelWidth(width) - 8;
        int right = menuX + PURPOSE_MENU_WIDTH + MENU_GAP;
        if (right + STYLE_MENU_WIDTH <= panelRight) return right;
        int left = menuX - MENU_GAP - STYLE_MENU_WIDTH;
        if (left >= panelLeft) return left;
        return clampToPanelX(right, STYLE_MENU_WIDTH);
    }
    private int styleMenuY() { return clampToPanelY(menuY, styleMenuHeight()); }
    private int clampToPanelX(int x, int menuWidth) {
        int panelLeft = CompanionScreenLayout.panelX(width) + 8;
        int panelRight = CompanionScreenLayout.panelX(width) + CompanionScreenLayout.panelWidth(width) - 8;
        return Math.max(panelLeft, Math.min(x, panelRight - menuWidth));
    }
    private int clampToPanelY(int y, int menuHeight) {
        int panelTop = CompanionScreenLayout.panelY(height) + 6;
        int panelBottom = CompanionScreenLayout.panelY(height) + CompanionScreenLayout.panelHeight(height) - 8;
        return Math.max(panelTop, Math.min(y, panelBottom - menuHeight));
    }
    private boolean menuCovers(int x, int y, int w, int h) {
        if (intersects(x, y, w, h, menuX, menuY, PURPOSE_MENU_WIDTH, purposeMenuHeight())) return true;
        return purpose != null && intersects(x, y, w, h, styleMenuX(), styleMenuY(), STYLE_MENU_WIDTH, styleMenuHeight());
    }
    private boolean textCoveredByMenu(Component text, int x, int y, int maxWidth) {
        if (selected < 0 || maxWidth <= 0) return false;
        int textWidth = Math.min(maxWidth, font.width(text));
        if (textWidth <= 0) return false;
        return menuCovers(x, y, textWidth + 1, font.lineHeight + 1);
    }
    private CompanionEffectStyle currentStyle() {
        if (purpose == null || selected < 0) return null;
        List<Row> currentRows = rows();
        if (selected >= currentRows.size()) return null;
        Row row = currentRows.get(selected);
        return switch (purpose) {
            case SUMMON -> row.summon;
            case RESCUE -> row.rescue;
            case STORAGE -> row.storage;
        };
    }
    private void drawScrollingString(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        int textWidth = font.width(text);
        g.enableScissor(x, y, x + maxWidth, y + font.lineHeight + 1);
        int drawX = x;
        if (textWidth > maxWidth) {
            drawX -= scrollingOffset(textWidth - maxWidth);
        }
        g.drawString(font, text, drawX, y, color);
        g.disableScissor();
    }
    private void drawClippedString(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        g.enableScissor(x, y, x + maxWidth, y + font.lineHeight + 1);
        g.drawString(font, text, x, y, color);
        g.disableScissor();
    }

    private Entity previewEntity(Row row) {
        Minecraft minecraft = Minecraft.getInstance();
        if (row == null || minecraft.level == null || row.entityType.isBlank()) {
            return null;
        }
        if (previewEntity != null && row.entityType.equals(previewEntityKey)) {
            return previewEntity;
        }
        previewEntity = EntityType.byString(row.entityType).map(type -> type.create((Level)minecraft.level)).orElse(null);
        previewEntityKey = row.entityType;
        if (previewEntity != null) {
            previewEntity.moveTo(0.0, 0.0, 0.0, 0.0f, 0.0f);
        }
        return previewEntity;
    }

    private void drawRectOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int scrollingOffset(int overflow) {
        if (overflow <= 0) return 0;
        int pause = 55;
        int cycle = overflow * 2 + pause * 2;
        int tick = (int)((System.currentTimeMillis() / 70L) % cycle);
        if (tick < pause) return 0;
        tick -= pause;
        if (tick < overflow) return tick;
        tick -= overflow;
        if (tick < pause) return overflow;
        tick -= pause;
        return Math.max(0, overflow - tick);
    }
    private void closeMenu() { selected = -1; purpose = null; }
    private boolean animationMenuOpen() { return selected >= 0 && (!packMode() || editPage == EditPage.ANIMATION); }
    private void autoScrollDuringDrag(double my) {
        int y = CompanionScreenLayout.panelY(height) + (packMode() ? 73 : 51);
        int h = CompanionScreenLayout.panelHeight(height) - (packMode() ? 81 : 59);
        int max = Math.max(0, rows().size() - visibleRows());
        if (my < y + 12) {
            scroll = Math.max(0, scroll - 1);
        } else if (my > y + h - 12) {
            scroll = Math.min(max, scroll + 1);
        }
    }
    private void selectRange(int from, int to) {
        if (from < 0 || to < 0) return;
        selectedRows.clear();
        int start = Math.min(from, to);
        int end = Math.max(from, to);
        for (int i = start; i <= end && i < rows().size(); i++) {
            selectedRows.add(i);
        }
        selected = to;
    }
    private static boolean inside(int x, int y, int w, int h, double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static boolean intersects(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) { return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by; }
    private static int fadeColor(int color, float fade) { return ((int) (((color >>> 24) & 255) * fade) << 24) | (color & 0xFFFFFF); }

    private List<Row> rows() {
        if (packMode()) {
            String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
            return ClientPackAnimationPresetState.entries().stream()
                    .filter(entry -> switch (category) {
                        case ALL -> true;
                        case MOUNT -> entry.category() == com.kuzhi.findme.common.PackAnimationPresetCategory.MOUNT;
                        case COMPANION -> entry.category() == com.kuzhi.findme.common.PackAnimationPresetCategory.COMPANION;
                        case VEHICLE -> entry.category() == com.kuzhi.findme.common.PackAnimationPresetCategory.VEHICLE;
                        case DISABLED -> entry.category() == com.kuzhi.findme.common.PackAnimationPresetCategory.DISABLED;
                    })
                    .filter(entry -> query.isBlank() || entry.entityType().toLowerCase(Locale.ROOT).contains(query) || entry.name().toLowerCase(Locale.ROOT).contains(query))
                    .map(entry -> new Row(null, entry.entityType(), entry.name().isBlank() ? entry.entityType() : entry.name(), false, entry.categoryOverride(), entry.movementOverride(), entry.boundsScale(), entry.circleScale(), entry.arrivalSound(), entry.arrivalVolume(), entry.arrivalPitch(), entry.summonStyle(), entry.rescueStyle(), entry.storageStyle()))
                    .toList();
        }
        if (category == Category.VEHICLE) {
            LinkedHashMap<UUID, VehicleListPacket.Entry> unique = new LinkedHashMap<>();
            ClientVehicleState.wheelEntries().forEach(e -> unique.put(e.uuid(), e));
            ClientVehicleState.allEntries().forEach(e -> unique.putIfAbsent(e.uuid(), e));
            return unique.values().stream().map(e -> new Row(e.uuid(), e.entityType(), e.name(), e.deployed() || e.ridden(), PackEntityCategoryOverride.AUTO, PackEntityMovementOverride.AUTO, 1.0f, 1.0f, "", 1.0f, 1.0f, e.summonStyle(), e.rescueStyle(), e.storageStyle())).toList();
        }
        CompanionKind kind = category == Category.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION;
        return ClientCompanionState.entries(kind).stream().map(e -> new Row(e.uuid(), e.entityType(), e.name(), e.deployed() || e.ridden(), PackEntityCategoryOverride.AUTO, PackEntityMovementOverride.AUTO, 1.0f, 1.0f, "", 1.0f, 1.0f, e.summonStyle(), e.rescueStyle(), e.storageStyle())).toList();
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    private boolean packMode() { return packEditor && ClientPackAnimationPresetState.editMode(); }
    private Row firstSelectedRow() {
        List<Row> currentRows = rows();
        return selectedRows.stream().filter(i -> i >= 0 && i < currentRows.size()).findFirst().map(currentRows::get).orElse(null);
    }
    private static int packListWidth(int panelWidth) {
        int content = panelWidth - 24;
        int preferred = (int)(content * 0.54f);
        int inspectorMin = 210;
        return Math.max(260, Math.min(preferred, content - inspectorMin));
    }
    private static int packCategoryTabWidth(int contentWidth) {
        int count = Category.values().length;
        return Math.max(48, Math.min(72, (contentWidth - (count - 1) * 5) / count));
    }
    private enum Category { ALL("screen.find_me.all"), MOUNT("screen.find_me.mounts"), COMPANION("screen.find_me.companions"), VEHICLE("screen.find_me.vehicles"), DISABLED("screen.find_me.disabled"); final String key; Category(String key) { this.key = key; } }
    private boolean validSoundId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
    }
    private enum EditPage { TYPE("screen.find_me.pack_page_type"), ANIMATION("screen.find_me.pack_page_animation"), SIZE("screen.find_me.pack_page_size"), SOUND("screen.find_me.pack_page_sound"); final String key; EditPage(String key) { this.key = key; } }
    private record Row(UUID uuid, String entityType, String name, boolean active, PackEntityCategoryOverride categoryOverride, PackEntityMovementOverride movementOverride, float boundsScale, float circleScale, String arrivalSound, float arrivalVolume, float arrivalPitch, CompanionEffectStyle summon, CompanionEffectStyle rescue, CompanionEffectStyle storage) {}
}
