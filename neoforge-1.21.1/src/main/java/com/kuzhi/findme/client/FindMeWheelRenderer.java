package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.FindMeWheelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

final class FindMeWheelRenderer {
    private static final int CYAN = 0xFF20C6E8;

    private FindMeWheelRenderer() {
    }

    static void drawBackdrop(GuiGraphics graphics, int centerX, int centerY, float fade) {
        int outer = CompanionWheelLayout.OUTER_RADIUS;
        drawCircleRing(graphics, centerX, centerY, outer + 4, 2, guiColor(0xE6FFFFFF, fade));
        drawCircleRing(graphics, centerX, centerY, outer - 4, 2, guiColor(0x88FFFFFF, fade));
    }

    static void drawCenterCancel(GuiGraphics graphics, int centerX, int centerY, float fade) {
        int color = guiColor(0x9A4A4F57, fade);
        int ring = CompanionWheelLayout.CENTER_CANCEL_RADIUS - 4;
        drawFilledCircle(graphics, centerX, centerY, ring, color);
        drawCircleRing(graphics, centerX, centerY, ring, 2, guiColor(0xDCE6EEF8, fade));
        graphics.fill(centerX - 5, centerY, centerX + 6, centerY + 1, guiColor(0xDCE6EEF8, fade));
    }

    static void drawSlotSegment(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize, boolean hovered, boolean deployed, boolean selected, float fade) {
        drawSlotSegment(graphics, centerX, centerY, index, visibleSize, hovered,
                selected ? CompanionWheelVisualState.PENDING
                        : deployed ? CompanionWheelVisualState.DEPLOYED : CompanionWheelVisualState.AVAILABLE,
                fade);
    }

    static void drawSlotSegment(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize,
                                boolean hovered, CompanionWheelVisualState state, float fade) {
        int accent = stateColor(state);
        int fill = hovered
                ? 0x66F2F5F4
                : state == CompanionWheelVisualState.AVAILABLE ? 0x52141E22
                : accent & 0xFFFFFF | 0x66000000;
        int outline = state == CompanionWheelVisualState.AVAILABLE
                ? (hovered ? 0xE6F2F5F4 : 0x8CCCD6D6) : accent;
        drawSector(graphics, centerX, centerY, index, visibleSize, guiColor(fill, fade));
        drawSectorOutline(graphics, centerX, centerY, index, visibleSize, guiColor(outline, fade));
    }

    static void drawSlotNumber(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize, float fade) {
        CompanionWheelLayout.Slot slot = CompanionWheelLayout.slot(centerX, centerY, index, visibleSize);
        graphics.drawCenteredString(Minecraft.getInstance().font, Component.literal(Integer.toString(index + 1)), slot.x(), slot.y() - 8, guiColor(0xFFFFFFFF, fade));
    }

    static void drawHomeMark(GuiGraphics graphics, int x, int y, int radius, float fade) {
        int color = guiColor(0xDCE9EEF7, fade);
        graphics.fill(x - 2, y - radius + 2, x + 2, y - radius + 6, color);
        graphics.fill(x - 4, y - radius + 4, x + 4, y - radius + 8, color);
    }

    static void drawHomeBadge(GuiGraphics graphics, CompanionWheelLayout.RosterSlot slot, float fade) {
        int left = slot.right() - 10;
        int top = slot.top() + 2;
        graphics.fill(left, top, left + 8, top + 9, guiColor(0xCC11191D, fade));
        graphics.fill(left, top, left + 8, top + 1, guiColor(0xFF20C6E8, fade));
        graphics.drawString(Minecraft.getInstance().font, "H", left + 1, top + 1,
                guiColor(0xFFF2F5F4, fade), false);
    }

    static void drawHealthBar(GuiGraphics graphics, int x, int y, int width, float pct, float fade) {
        int bg = guiColor(0x66484F57, fade);
        int fg = guiColor(0xFF6CA7FF, fade);
        graphics.fill(x - width / 2, y, x + width / 2, y + 3, bg);
        graphics.fill(x - width / 2, y, x - width / 2 + Mth.clamp(Math.round(width * pct), 0, width), y + 3, fg);
    }

    static void drawManaBar(GuiGraphics graphics, String label, float pct, float fade) {
        int left = 20;
        int top = 49;
        int width = 132;
        graphics.drawString(Minecraft.getInstance().font, label, left, top,
                guiColor(0xFFB8EFFF, fade), false);
        graphics.fill(left, top + 10, left + width, top + 13, guiColor(0xB50A151B, fade));
        graphics.fill(left, top + 10, left + Mth.clamp(Math.round(width * pct), 0, width), top + 13,
                guiColor(0xFF35A9D1, fade));
    }

    static void drawFieldScrim(GuiGraphics graphics, int width, int height, float fade) {
        graphics.fill(0, 0, width, height, guiColor(0x52061013, fade));
        for (int y = 1; y < height; y += 4) {
            graphics.fill(0, y, width, y + 1, guiColor(0x0CFFFFFF, fade));
        }
    }

    static void drawRosterHeader(GuiGraphics graphics, Component title, int page, int pageCount,
                                 FindMeWheelStyle layout, float fade) {
        int originX = 0;
        int originY = 0;
        drawHeaderPlate(graphics, originX, originY, fade);
        graphics.drawString(Minecraft.getInstance().font, title, originX + 20, originY + 16,
                guiColor(0xFFF2F5F4, fade), false);
        Component pageLabel = Component.literal(String.format("%02d / %02d", page + 1, Math.max(1, pageCount)));
        graphics.drawString(Minecraft.getInstance().font, pageLabel, originX + 20, originY + 38,
                guiColor(0xFF9DAAAC, fade), false);
    }

    static void drawRosterRail(GuiGraphics graphics, int width, int height, float fade) {
        int top = height - CompanionWheelLayout.STRIP_CARD_HEIGHT - 15;
        graphics.fill(0, top, width, height, guiColor(0xD90A1216, fade));
        graphics.fill(0, top, width * 3 / 5, top + 2, guiColor(0xDDE6EAE8, fade));
        graphics.fill(0, top, 4, height, guiColor(CYAN, fade));
    }

    static void drawRosterCardBase(GuiGraphics graphics, CompanionWheelLayout.RosterSlot slot,
                                   boolean hovered, boolean deployed, boolean ridden, boolean selected, float fade) {
        drawRosterCardBase(graphics, FindMeWheelStyle.SIX_WING, slot, hovered,
                selected ? CompanionWheelVisualState.PENDING
                        : deployed || ridden ? CompanionWheelVisualState.DEPLOYED
                        : CompanionWheelVisualState.AVAILABLE, fade);
    }

    static void drawRosterCardBase(GuiGraphics graphics, CompanionWheelLayout.RosterSlot slot,
                                   boolean hovered, CompanionWheelVisualState state, float fade) {
        drawRosterCardBase(graphics, FindMeWheelStyle.SIX_WING, slot, hovered, state, fade);
    }

    static void drawRosterCardBase(GuiGraphics graphics, FindMeWheelStyle layout,
                                   CompanionWheelLayout.RosterSlot slot,
                                   boolean hovered, CompanionWheelVisualState state, float fade) {
        int lift = hovered ? -2 : 0;
        int left = slot.left();
        int top = slot.top() + lift;
        int right = slot.right();
        int bottom = slot.bottom() + lift;
        int base = hovered ? 0xF0EDF1EF : 0xE81A2428;
        graphics.fill(left, top, right, bottom, guiColor(base, fade));
        int bandTop = top + slot.height() * 57 / 100;
        drawSlantedBand(graphics, left, bandTop, right, bottom, hovered ? 0xE52E5964 : 0xD63A484B, fade);
        if (state != CompanionWheelVisualState.AVAILABLE) {
            int accent = guiColor(stateColor(state), fade);
            if (layout == FindMeWheelStyle.SIX_WING) {
                drawSixWingOutline(graphics, left, top, right, bottom, accent);
            } else if (layout == FindMeWheelStyle.FOLDED_SHARDS) {
                drawPixelLine(graphics, left + 7, top, right - 8, top, accent);
                drawPixelLine(graphics, left + 7, top, left, bottom - 7, accent);
            } else {
                graphics.fill(left, top, left + 2, bottom, accent);
                graphics.fill(left, top, right, top + 2, accent);
            }
        }
    }

    static void drawRosterCardBase(GuiGraphics graphics, FindMeWheelStyle layout,
                                   CompanionWheelLayout.RosterSlot slot, boolean hovered,
                                   boolean deployed, boolean ridden, boolean selected, float fade) {
        drawRosterCardBase(graphics, layout, slot, hovered,
                selected ? CompanionWheelVisualState.PENDING
                        : deployed || ridden ? CompanionWheelVisualState.DEPLOYED
                        : CompanionWheelVisualState.AVAILABLE, fade);
    }

    static void drawRosterCardText(GuiGraphics graphics, CompanionWheelLayout.RosterSlot slot, int number,
                                   String name, boolean hovered, boolean deployed, boolean ridden,
                                   boolean selected, boolean alive, float healthPct, boolean hasHealth, float fade) {
        int lift = hovered ? -2 : 0;
        int left = slot.left();
        int top = slot.top() + lift;
        int bottom = slot.bottom() + lift;
        int topColor = hovered ? 0xFF11191D : 0xFFF2F5F4;
        graphics.drawString(Minecraft.getInstance().font, String.format("%02d", number), left + 3, top + 3,
                guiColor(topColor, fade), false);
        int nameColor = alive ? 0xFFF2F5F4 : 0xFF8B9294;
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(name, Math.max(8, slot.width() - 6));
        graphics.drawString(Minecraft.getInstance().font, clipped, left + 3, bottom - 21,
                guiColor(nameColor, fade), false);
        if (ridden) {
            graphics.drawString(Minecraft.getInstance().font, "R", slot.right() - 9, bottom - 16,
                    guiColor(0xFF081014, fade), false);
        }
        if (hasHealth) {
            int barLeft = left + 3;
            int barRight = slot.right() - 3;
            int width = Math.max(1, barRight - barLeft);
            graphics.fill(barLeft, bottom - 9, barRight, bottom - 7, guiColor(0x663B4548, fade));
            graphics.fill(barLeft, bottom - 9, barLeft + Mth.clamp(Math.round(width * healthPct), 0, width),
                    bottom - 7, guiColor(CYAN, fade));
        }
    }

    static void drawCenterCancelMark(GuiGraphics graphics, int centerX, int centerY, float fade) {
        drawFilledCircle(graphics, centerX, centerY, 12, guiColor(0xB30B1417, fade));
        drawCircleRing(graphics, centerX, centerY, 12, 1, guiColor(0xBDE3E8E7, fade));
        graphics.fill(centerX - 4, centerY, centerX + 5, centerY + 1, guiColor(0xFFE3E8E7, fade));
    }

    static void drawFocusedDossier(GuiGraphics graphics, FindMeWheelStyle layout,
                                   int screenWidth, int screenHeight, int centerX, int centerY,
                                   String name, int number, int total, Component state, float fade) {
        if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
            Component label = Component.literal(name).append(" | ").append(state);
            graphics.drawCenteredString(Minecraft.getInstance().font, label, centerX,
                    centerY + CompanionWheelLayout.BACKDROP_RADIUS + 12, guiColor(0xFFF2F5F4, fade));
            return;
        }
        boolean tactical = layout == FindMeWheelStyle.TACTICAL_STRIP;
        int viewportLeft = CompanionWheelLayout.viewportLeft(screenWidth);
        int viewportTop = CompanionWheelLayout.viewportTop(screenHeight);
        int width = tactical ? 122 : 94;
        int left = !tactical
                ? centerX - width / 2
                : viewportLeft + CompanionWheelLayout.VIEWPORT_WIDTH - width - 18;
        int top = !tactical
                ? centerY + 25
                : viewportTop + CompanionWheelLayout.VIEWPORT_HEIGHT - CompanionWheelLayout.STRIP_CARD_HEIGHT - 58;
        graphics.fill(left, top, left + width, top + 30, guiColor(0xE910181C, fade));
        graphics.fill(left, top, left + 3, top + 30, guiColor(CYAN, fade));
        String count = String.format("%02d / %02d", number, Math.max(1, total));
        int countWidth = Minecraft.getInstance().font.width(count);
        int countLeft = left + width - countWidth - 7;
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(name,
                Math.max(8, countLeft - left - 12));
        graphics.drawString(Minecraft.getInstance().font, clipped, left + 8, top + 5,
                guiColor(0xFFF2F5F4, fade), false);
        graphics.drawString(Minecraft.getInstance().font, state, left + 8, top + 17,
                guiColor(0xFF20C6E8, fade), false);
        graphics.drawString(Minecraft.getInstance().font, count, countLeft, top + 11,
                guiColor(0xFFF2F5F4, fade), false);
    }

    static void drawClassicSlotText(GuiGraphics graphics, CompanionWheelLayout.RosterSlot slot, int number,
                                    String name, boolean alive, float healthPct, boolean hasHealth, float fade) {
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(name, 48);
        graphics.drawCenteredString(Minecraft.getInstance().font, clipped, slot.x(), slot.y() - 28,
                guiColor(alive ? 0xFFF2F5F4 : 0xFF8B9294, fade));
        graphics.drawCenteredString(Minecraft.getInstance().font, Integer.toString(number), slot.x(), slot.y() + 12,
                guiColor(0xFFE8EEEC, fade));
        if (hasHealth) {
            drawHealthBar(graphics, slot.x(), slot.y() + 22, 24, healthPct, fade);
        }
    }

    static void drawCommandHeader(GuiGraphics graphics, Component title, String targetName, float fade) {
        int originX = 0;
        int originY = 0;
        drawHeaderPlate(graphics, originX, originY, fade);
        int titleWidth = Math.min(170, 38 + Minecraft.getInstance().font.width(title));
        graphics.drawString(Minecraft.getInstance().font, title, originX + 20, originY + 16,
                guiColor(0xFFF2F5F4, fade), false);
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(targetName, titleWidth - 16);
        graphics.drawString(Minecraft.getInstance().font, clipped, originX + 20, originY + 38,
                guiColor(0xFF9DAAAC, fade), false);
    }

    private static void drawHeaderPlate(GuiGraphics graphics, int originX, int originY, float fade) {
        int left = originX + 10;
        int top = originY + 10;
        int height = 25;
        for (int y = 0; y < height; ++y) {
            int right = originX + 176 - Math.round(10.0f * y / Math.max(1, height - 1));
            graphics.fill(left, top + y, right, top + y + 1, guiColor(0xE810181C, fade));
        }
        graphics.fill(left, top, left + 3, top + height, guiColor(CYAN, fade));
    }

    static void drawCommandShardBase(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize,
                                     boolean hovered, boolean available, float fade) {
        CompanionWheelLayout.Slot slot = CompanionWheelLayout.slot(centerX, centerY, index, visibleSize);
        int width = hovered ? 88 : 76;
        int height = hovered ? 43 : 39;
        int left = slot.x() - width / 2;
        int top = slot.y() - height / 2 - (hovered ? 2 : 0);
        int base = available ? (hovered ? 0xF0EDF1EF : 0xEC182226) : 0xD0192022;
        drawAngularPanel(graphics, left, top, width, height, base, fade);
        int bandColor = available ? (hovered ? 0xE72B5964 : 0xD83B484B) : 0xB52C3132;
        drawSlantedBand(graphics, left + 2, top + height * 55 / 100, left + width - 2, top + height - 2,
                bandColor, fade);
        if (hovered && available) {
            graphics.fill(left + width - 3, top + 3, left + width, top + height - 3, guiColor(CYAN, fade));
            graphics.fill(left + 8, top, left + width - 7, top + 2, guiColor(CYAN, fade));
        }
    }

    static void drawCommandShardText(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize,
                                     Component label, boolean hovered, boolean available, float fade) {
        CompanionWheelLayout.Slot slot = CompanionWheelLayout.slot(centerX, centerY, index, visibleSize);
        int width = hovered ? 88 : 76;
        int height = hovered ? 43 : 39;
        int left = slot.x() - width / 2;
        int top = slot.y() - height / 2 - (hovered ? 2 : 0);
        int numberColor = hovered ? 0xFF10181C : 0xFFF2F5F4;
        graphics.drawString(Minecraft.getInstance().font, String.format("%02d", index + 1), left + 7, top + 5,
                guiColor(available ? numberColor : 0xFF707779, fade), false);
        String text = Minecraft.getInstance().font.plainSubstrByWidth(label.getString(), width - 14);
        graphics.drawString(Minecraft.getInstance().font, text, left + 7, top + height - 14,
                guiColor(available ? 0xFFF2F5F4 : 0xFF737A7C, fade), false);
        if (!available) {
            graphics.fill(left + 7, top + height - 4, left + width - 8, top + height - 3,
                    guiColor(0xFF737A7C, fade));
        }
    }

    static void drawContextActionText(GuiGraphics graphics, FindMeWheelStyle layout,
                                      CompanionWheelLayout.RosterSlot slot, int number, Component label,
                                      boolean hovered, boolean available, float fade) {
        int lift = hovered ? -2 : 0;
        int top = slot.top() + lift;
        int color = available ? 0xFFF2F5F4 : 0xFF737A7C;
        if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
            graphics.drawCenteredString(Minecraft.getInstance().font, String.format("%02d", number),
                    slot.x(), slot.y() - 17, guiColor(available ? 0xFFE8EEEC : 0xFF737A7C, fade));
            List<FormattedCharSequence> lines = Minecraft.getInstance().font.split(label, 54);
            int shown = Math.min(2, lines.size());
            int textTop = slot.y() + (shown > 1 ? -2 : 2);
            for (int line = 0; line < shown; ++line) {
                graphics.drawCenteredString(Minecraft.getInstance().font, lines.get(line), slot.x(),
                        textTop + line * 9, guiColor(color, fade));
            }
            return;
        }

        int numberColor = hovered ? 0xFF10181C : color;
        graphics.drawString(Minecraft.getInstance().font, String.format("%02d", number),
                slot.left() + 3, top + 3, guiColor(numberColor, fade), false);
        int maxWidth = Math.max(12, slot.width() - 6);
        List<FormattedCharSequence> lines = Minecraft.getInstance().font.split(label, maxWidth);
        int shown = Math.min(2, lines.size());
        int textTop = slot.bottom() + lift - 4 - shown * 9;
        for (int line = 0; line < shown; ++line) {
            graphics.drawString(Minecraft.getInstance().font, lines.get(line), slot.left() + 3,
                    textTop + line * 9, guiColor(color, fade), false);
        }
        if (!available) {
            graphics.fill(slot.left() + 3, slot.bottom() + lift - 3, slot.right() - 3,
                    slot.bottom() + lift - 2, guiColor(0xFF737A7C, fade));
        }
    }

    private static void drawSlantedBand(GuiGraphics graphics, int left, int top, int right, int bottom,
                                        int color, float fade) {
        int height = Math.max(1, bottom - top);
        for (int y = 0; y < height; ++y) {
            int inset = Math.max(0, (height - y) / 4);
            graphics.fill(left, top + y, Math.max(left + 1, right - inset), top + y + 1, guiColor(color, fade));
        }
    }

    private static void drawAngularPanel(GuiGraphics graphics, int left, int top, int width, int height,
                                         int color, float fade) {
        for (int y = 0; y < height; ++y) {
            int leftInset = Math.max(0, (height - y - 1) / 5);
            int rightInset = Math.max(0, y / 5);
            graphics.fill(left + leftInset, top + y, left + width - rightInset, top + y + 1,
                    guiColor(color, fade));
        }
    }

    private static void drawSixWingOutline(GuiGraphics graphics, int left, int top, int right, int bottom,
                                           int color) {
        graphics.fill(left, top, right, top + 2, color);
        graphics.fill(left, bottom - 2, right, bottom, color);
        graphics.fill(left, top + 2, left + 2, bottom - 2, color);
        graphics.fill(right - 2, top + 2, right, bottom - 2, color);
    }

    private static void drawPixelLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = error * 2;
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    static void drawCircleRing(GuiGraphics graphics, int centerX, int centerY, int radius, int thickness, int color) {
        for (int t = 0; t < Math.max(1, thickness); ++t) {
            int r = Math.max(1, radius - t);
            drawCircleDots(graphics, centerX, centerY, r, color);
        }
    }

    static int guiColor(int color, float fade) {
        int alpha = color >>> 24 & 0xFF;
        int scaledAlpha = Math.max(0, Math.min(255, (int)Math.round(alpha * Config.guiOpacity * fade)));
        return color & 0xFFFFFF | scaledAlpha << 24;
    }

    static int stateColor(CompanionWheelVisualState state) {
        return switch (state) {
            case PENDING -> 0xFF8B9294;
            case DEPLOYED -> 0xFF20C6E8;
            case SWITCHING -> 0xFFFFC247;
            case CRITICAL -> 0xFFFF7052;
            case DEAD -> 0xFFE34850;
            case AVAILABLE -> 0xFFCCD6D6;
        };
    }

    private static void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; ++y) {
            int span = (int)Math.floor(Math.sqrt(Math.max(0, radius * radius - y * y)));
            graphics.fill(centerX - span, centerY + y, centerX + span + 1, centerY + y + 1, color);
        }
    }

    private static void drawCircleDots(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int steps = Math.max(48, (int)(radius * 10.0));
        for (int i = 0; i < steps; ++i) {
            double angle = Math.PI * 2.0 * (double)i / (double)steps;
            int x = centerX + (int)Math.round(Math.cos(angle) * (double)radius);
            int y = centerY + (int)Math.round(Math.sin(angle) * (double)radius);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    private static void drawSector(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize, int color) {
        double step = Math.PI * 2.0 / (double)Math.max(1, visibleSize);
        double start = -Math.PI / 2.0 - step / 2.0 + step * index;
        double end = start + step;
        double gap = 0.085;
        int outer = CompanionWheelLayout.OUTER_RADIUS;
        int inner = CompanionWheelLayout.CENTER_CANCEL_RADIUS + 1;
        for (int radius = inner; radius <= outer; radius += 2) {
            double arcStep = Math.max(0.03, 7.0 / Math.max(1.0, radius));
            for (double angle = start + gap; angle <= end - gap; angle += arcStep) {
                int x = centerX + (int)Math.round(Math.cos(angle) * (double)radius);
                int y = centerY + (int)Math.round(Math.sin(angle) * (double)radius);
                graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
            }
        }
    }

    private static void drawSectorOutline(GuiGraphics graphics, int centerX, int centerY, int index, int visibleSize, int color) {
        double step = Math.PI * 2.0 / (double)Math.max(1, visibleSize);
        double start = -Math.PI / 2.0 - step / 2.0 + step * index;
        double end = start + step;
        int outer = CompanionWheelLayout.OUTER_RADIUS;
        int inner = CompanionWheelLayout.CENTER_CANCEL_RADIUS + 1;
        drawRadialLine(graphics, centerX, centerY, start, inner, outer, color);
        drawRadialLine(graphics, centerX, centerY, end, inner, outer, color);
    }

    private static void drawRadialLine(GuiGraphics graphics, int centerX, int centerY, double angle, int inner, int outer, int color) {
        for (int radius = inner; radius <= outer; radius += 2) {
            int x = centerX + (int)Math.round(Math.cos(angle) * (double)radius);
            int y = centerY + (int)Math.round(Math.sin(angle) * (double)radius);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }
}
