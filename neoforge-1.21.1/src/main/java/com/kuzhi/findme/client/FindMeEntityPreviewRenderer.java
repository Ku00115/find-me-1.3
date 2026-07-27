package com.kuzhi.findme.client;

import com.kuzhi.findme.network.CompanionListPacket;
import net.minecraft.client.gui.GuiGraphics;

final class FindMeEntityPreviewRenderer {
    private final CompanionDetailPreviewRenderer delegate = new CompanionDetailPreviewRenderer();

    void renderWheelPreview(GuiGraphics graphics, CompanionListPacket.Entry entry, int centerX, int centerY, String fallbackType, int radius) {
        this.delegate.renderWheel(graphics, entry, centerX, centerY, fallbackType, radius);
    }

    void renderCardPreview(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h, String fallbackType) {
        this.delegate.renderCard(graphics, entry, x, y, w, h, fallbackType);
    }

    void renderDetailPreview(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h, String fallbackType) {
        this.delegate.render(graphics, entry, x, y, w, h, CompanionDetailPreviewRenderer.DEFAULT_PREVIEW_YAW, 0.0f, 1.0f, 0.0f, 0.0f, fallbackType, Math.min(w, h), 14.0f);
    }

    void renderFittedPreview(GuiGraphics graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h,
                             String fallbackType) {
        this.delegate.renderFitted(graphics, entry, x, y, w, h, fallbackType);
    }
}
