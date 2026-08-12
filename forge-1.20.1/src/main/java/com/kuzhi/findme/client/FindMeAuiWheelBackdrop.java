package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.FindMeWheelStyle;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class FindMeAuiWheelBackdrop {
    private static final String PATH = "findme/wheel/vanilla.html";
    private static final int CSS_WIDTH = CompanionWheelLayout.VIEWPORT_WIDTH;
    private static final int CSS_HEIGHT = CompanionWheelLayout.VIEWPORT_HEIGHT;
    private static final int BITMAP_SCALE = 2;
    private static Document document;
    private static String lastState = "";

    private FindMeAuiWheelBackdrop() {
    }

    static boolean drawRoster(GuiGraphics graphics, int screenWidth, int screenHeight, FindMeWheelStyle style,
                              int visibleSize, int hoveredLocal, int selectedLocal,
                              int deployedMask, int riddenMask, float fade) {
        int stateCode = 0;
        for (int i = 0; i < visibleSize; i++) {
            CompanionWheelVisualState state = i == selectedLocal ? CompanionWheelVisualState.PENDING
                    : (deployedMask & 1 << i) != 0 ? CompanionWheelVisualState.DEPLOYED
                    : CompanionWheelVisualState.AVAILABLE;
            stateCode |= state.ordinal() << (i * 3);
        }
        return drawRoster(graphics, screenWidth, screenHeight, style, visibleSize, hoveredLocal, stateCode,
                Component.empty(), 0, 1, fade);
    }

    static boolean drawRoster(GuiGraphics graphics, int screenWidth, int screenHeight, FindMeWheelStyle style,
                              int visibleSize, int hoveredLocal, int stateCode, Component title,
                              int page, int pageCount, float fade) {
        try {
            Document doc = document();
            Canvas canvas = canvas(doc);
            if (doc == null || doc.body == null || canvas == null) {
                return false;
            }
            updateHeading(doc, title, page, pageCount, fade);
            String state = "roster:" + style + ':' + visibleSize + ':' + hoveredLocal + ':' + stateCode
                    + ':' + Math.round(fade * 100.0f);
            if (!state.equals(lastState)) {
                canvas.renderOperation(g -> drawRosterCanvas(g, style, visibleSize, hoveredLocal, stateCode, fade));
                lastState = state;
            }
            return drawDocument(graphics, doc, screenWidth, screenHeight, fade, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean drawCommand(GuiGraphics graphics, int screenWidth, int screenHeight, FindMeWheelStyle style,
                               int hoveredLocal, int availableMask, int actionCount, float fade) {
        try {
            Document doc = document();
            Canvas canvas = canvas(doc);
            if (doc == null || doc.body == null || canvas == null) {
                return false;
            }
            updateHeading(doc, Component.empty(), 0, 1, fade);
            String state = "command:" + style + ':' + hoveredLocal + ':' + availableMask + ':' + actionCount
                    + ':' + Math.round(fade * 100.0f);
            if (!state.equals(lastState)) {
                canvas.renderOperation(g -> drawCommandCanvas(g, style, hoveredLocal, availableMask,
                        actionCount, fade));
                lastState = state;
            }
            return drawDocument(graphics, doc, screenWidth, screenHeight, fade, true);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void refresh() {
        document = null;
        lastState = "";
    }

    private static Document document() {
        if (document == null) {
            document = new Document(PATH, false);
            document.refresh();
            lastState = "";
        }
        return document;
    }

    private static Canvas canvas(Document doc) {
        if (doc == null) return null;
        Element element = doc.getElementById("findme-wheel-canvas");
        return element instanceof Canvas canvas ? canvas : null;
    }

    private static void updateHeading(Document doc, Component title, int page, int pageCount, float fade) {
        boolean visible = title != null && !title.getString().isBlank();
        String opacity = "opacity:" + (visible
                ? Math.max(0.0f, Math.min(1.0f, fade * (float) Config.guiOpacity)) : 0.0f);
        Element titleElement = doc.getElementById("findme-wheel-title");
        if (titleElement != null) {
            titleElement.setTextContent(title == null ? "" : title.getString());
            Element heading = titleElement.closest(".wheel-heading");
            if (heading != null) {
                heading.setClassName("wheel-heading " + ClientWheelPresentationState.typographyClasses());
                heading.setAttribute("style", opacity);
            }
        }
        Element plate = doc.getElementById("findme-wheel-heading-plate");
        if (plate != null) plate.setAttribute("style", opacity);
        Element pageElement = doc.getElementById("findme-wheel-page");
        if (pageElement != null) {
            pageElement.setTextContent(String.format("%02d / %02d", page + 1, Math.max(1, pageCount)));
        }
    }

    private static boolean drawDocument(GuiGraphics graphics, Document doc, int screenWidth, int screenHeight,
                                        float fade, boolean drawScrim) {
        doc.tickFrame();
        if (drawScrim) FindMeWheelRenderer.drawFieldScrim(graphics, screenWidth, screenHeight, fade);
        int left = (screenWidth - CSS_WIDTH) / 2;
        int top = (screenHeight - CSS_HEIGHT) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0f);
        graphics.pose().scale(1.0f / BITMAP_SCALE, 1.0f / BITMAP_SCALE, 1.0f);
        Base.drawScreenDocument(graphics.pose(), doc);
        graphics.pose().popPose();
        return true;
    }

    private static void drawRosterCanvas(Graphics2D raw, FindMeWheelStyle style, int visibleSize,
                                         int hoveredLocal, int stateCode, float fade) {
        prepare(raw, fade);
        drawSharedSurface(raw, style == FindMeWheelStyle.TACTICAL_STRIP);
        int centerX = CSS_WIDTH / 2;
        int centerY = CSS_HEIGHT / 2;
        if (style == FindMeWheelStyle.CLASSIC_RADIAL) {
            drawClassicRadial(raw, centerX, centerY, visibleSize, hoveredLocal, stateCode);
            raw.scale(1.0 / BITMAP_SCALE, 1.0 / BITMAP_SCALE);
            return;
        }
        for (int i = 0; i < visibleSize; ++i) {
            CompanionWheelLayout.RosterSlot slot = CompanionWheelLayout.rosterSlot(style, CSS_WIDTH, CSS_HEIGHT,
                    centerX, centerY, i, visibleSize);
            boolean hovered = i == hoveredLocal;
            CompanionWheelVisualState state = stateAt(stateCode, i);
            if (style == FindMeWheelStyle.FOLDED_SHARDS) {
                drawFoldedRosterShard(raw, slot, hovered, state);
            } else {
                drawRosterCard(raw, slot, hovered, state, style == FindMeWheelStyle.SIX_WING);
            }
        }
        raw.scale(1.0 / BITMAP_SCALE, 1.0 / BITMAP_SCALE);
    }

    private static void drawClassicRadial(Graphics2D g, int centerX, int centerY, int visibleSize,
                                          int hoveredLocal, int stateCode) {
        int outer = CompanionWheelLayout.OUTER_RADIUS;
        int sectorOuter = CompanionWheelLayout.SECTOR_OUTER_RADIUS;
        int inner = CompanionWheelLayout.CENTER_CANCEL_RADIUS + 1;
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < visibleSize; ++i) {
            boolean hovered = i == hoveredLocal;
            CompanionWheelVisualState state = stateAt(stateCode, i);
            Path2D sector = radialSector(centerX, centerY, i, visibleSize, inner, sectorOuter,
                    CompanionWheelLayout.RADIAL_SECTOR_GAP);
            Color accent = stateColor(state, state == CompanionWheelVisualState.AVAILABLE ? 188 : 138);
            Color fill = hovered && state == CompanionWheelVisualState.AVAILABLE
                    ? new Color(238, 243, 241, 132) : accent;
            g.setColor(fill);
            g.fill(sector);
            g.setColor(state == CompanionWheelVisualState.AVAILABLE
                    ? (hovered ? new Color(244, 247, 246, 225) : new Color(204, 214, 214, 142))
                    : stateColor(state, 238));
            if (hovered || (state != CompanionWheelVisualState.AVAILABLE
                    && state != CompanionWheelVisualState.PENDING)) {
                g.draw(sector);
            }
        }

        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(235, 240, 238, 222));
        g.drawOval(centerX - outer - 4, centerY - outer - 4, (outer + 4) * 2, (outer + 4) * 2);
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(new Color(202, 216, 216, 128));
        g.drawOval(centerX - outer + 4, centerY - outer + 4, (outer - 4) * 2, (outer - 4) * 2);

    }

    private static Path2D radialSector(int centerX, int centerY, int index, int visibleSize,
                                       int innerRadius, int outerRadius, double gap) {
        int count = Math.max(1, visibleSize);
        double step = Math.PI * 2.0 / count;
        double center = CompanionWheelLayout.slotAngle(index, count);
        double start = center - step / 2.0 + gap;
        double end = center + step / 2.0 - gap;
        int samples = Math.max(8, (int)Math.ceil((end - start) * outerRadius / 4.0));
        Path2D path = new Path2D.Double();
        path.moveTo(centerX + Math.cos(start) * innerRadius, centerY + Math.sin(start) * innerRadius);
        path.lineTo(centerX + Math.cos(start) * outerRadius, centerY + Math.sin(start) * outerRadius);
        for (int sample = 1; sample <= samples; ++sample) {
            double angle = start + (end - start) * sample / samples;
            path.lineTo(centerX + Math.cos(angle) * outerRadius, centerY + Math.sin(angle) * outerRadius);
        }
        for (int sample = samples; sample >= 0; --sample) {
            double angle = start + (end - start) * sample / samples;
            path.lineTo(centerX + Math.cos(angle) * innerRadius, centerY + Math.sin(angle) * innerRadius);
        }
        path.closePath();
        return path;
    }

    private static void drawCommandCanvas(Graphics2D raw, FindMeWheelStyle style, int hoveredLocal,
                                          int availableMask, int actionCount, float fade) {
        prepare(raw, fade);
        drawSharedSurface(raw, style == FindMeWheelStyle.TACTICAL_STRIP);
        int centerX = CSS_WIDTH / 2;
        int centerY = CSS_HEIGHT / 2;
        int count = Math.max(0, actionCount);
        if (style == FindMeWheelStyle.CLASSIC_RADIAL) {
            drawClassicCommandRadial(raw, centerX, centerY, count, hoveredLocal, availableMask);
            raw.scale(1.0 / BITMAP_SCALE, 1.0 / BITMAP_SCALE);
            return;
        }
        for (int i = 0; i < count; ++i) {
            CompanionWheelLayout.RosterSlot bounds = CompanionWheelLayout.rosterSlot(style, CSS_WIDTH,
                    CSS_HEIGHT, centerX, centerY, i, count);
            boolean available = (availableMask & 1 << i) != 0;
            boolean hovered = i == hoveredLocal && available;
            if (style == FindMeWheelStyle.FOLDED_SHARDS) {
                drawFoldedRosterShard(raw, bounds, hovered, false, false, false);
            } else {
                drawRosterCard(raw, bounds, hovered, false, false, false);
            }
            if (!available) {
                raw.setColor(new Color(8, 13, 15, 118));
                raw.fillRect(bounds.left(), bounds.top(), bounds.width(), bounds.height());
            }
        }
        if (style != FindMeWheelStyle.TACTICAL_STRIP) {
            raw.setColor(new Color(8, 16, 19, 196));
            raw.fillOval(centerX - 13, centerY - 13, 26, 26);
            raw.setColor(new Color(225, 232, 231, 190));
            raw.drawOval(centerX - 13, centerY - 13, 26, 26);
        }
        raw.scale(1.0 / BITMAP_SCALE, 1.0 / BITMAP_SCALE);
    }

    private static void drawClassicCommandRadial(Graphics2D g, int centerX, int centerY, int count,
                                                  int hoveredLocal, int availableMask) {
        int outer = CompanionWheelLayout.OUTER_RADIUS;
        int sectorOuter = CompanionWheelLayout.SECTOR_OUTER_RADIUS;
        int inner = CompanionWheelLayout.CENTER_CANCEL_RADIUS + 1;
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i < count; ++i) {
            boolean available = (availableMask & 1 << i) != 0;
            boolean hovered = i == hoveredLocal && available;
            Path2D sector = radialSector(centerX, centerY, i, count, inner, sectorOuter,
                    CompanionWheelLayout.RADIAL_SECTOR_GAP);
            g.setColor(!available
                    ? new Color(17, 23, 25, 174)
                    : hovered ? new Color(238, 243, 241, 146) : new Color(20, 30, 34, 188));
            g.fill(sector);
            g.setColor(!available
                    ? new Color(105, 113, 115, 116)
                    : hovered ? new Color(244, 247, 246, 232) : new Color(204, 214, 214, 142));
            g.draw(sector);
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(235, 240, 238, 222));
        g.drawOval(centerX - outer - 4, centerY - outer - 4, (outer + 4) * 2, (outer + 4) * 2);
    }

    private static void prepare(Graphics2D g, float fade) {
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, CSS_WIDTH * BITMAP_SCALE, CSS_HEIGHT * BITMAP_SCALE);
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.0f,
                Math.min(1.0f, (float)(fade * Config.guiOpacity)))));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(BITMAP_SCALE, BITMAP_SCALE);
    }

    private static void drawSharedSurface(Graphics2D g, boolean tacticalStrip) {
        if (tacticalStrip) {
            int top = CSS_HEIGHT - CompanionWheelLayout.STRIP_CARD_HEIGHT - 15;
            g.setColor(new Color(8, 17, 21, 224));
            g.fillRect(0, top, CSS_WIDTH, CSS_HEIGHT - top);
            g.setColor(new Color(32, 198, 232, 245));
            g.fillRect(0, top, 4, CSS_HEIGHT - top);
        }
    }

    private static void drawRosterCard(Graphics2D g, CompanionWheelLayout.RosterSlot slot,
                                       boolean hovered, boolean selected, boolean deployed, boolean ridden) {
        drawRosterCard(g, slot, hovered, selected ? CompanionWheelVisualState.PENDING
                : deployed || ridden ? CompanionWheelVisualState.DEPLOYED : CompanionWheelVisualState.AVAILABLE);
    }

    private static void drawRosterCard(Graphics2D g, CompanionWheelLayout.RosterSlot slot,
                                       boolean hovered, CompanionWheelVisualState state) {
        drawRosterCard(g, slot, hovered, state, false);
    }

    private static void drawRosterCard(Graphics2D g, CompanionWheelLayout.RosterSlot slot,
                                       boolean hovered, CompanionWheelVisualState state,
                                       boolean silhouetteOutline) {
        int lift = hovered ? -2 : 0;
        int left = slot.left();
        int top = slot.top() + lift;
        int right = slot.right();
        int bottom = slot.bottom() + lift;
        g.setColor(hovered ? new Color(237, 241, 239, 242) : new Color(25, 35, 39, 235));
        g.fillRect(left, top, slot.width(), slot.height());
        Path2D band = new Path2D.Float();
        band.moveTo(left, top + slot.height() * 0.57);
        band.lineTo(right, top + slot.height() * 0.48);
        band.lineTo(right, bottom);
        band.lineTo(left, bottom);
        band.closePath();
        g.setColor(hovered ? new Color(45, 88, 99, 230) : new Color(57, 72, 75, 220));
        g.fill(band);
        if (silhouetteOutline && state != CompanionWheelVisualState.AVAILABLE) {
            g.setColor(stateColor(state, 248));
            g.setStroke(new BasicStroke(2.0f));
            g.drawRect(left, top, Math.max(0, right - left - 1), Math.max(0, bottom - top - 1));
            g.setStroke(new BasicStroke(1.0f));
        } else {
            drawStateEdges(g, left, top, right, bottom, state);
        }
    }

    private static void drawFoldedRosterShard(Graphics2D g, CompanionWheelLayout.RosterSlot slot,
                                               boolean hovered, boolean selected, boolean deployed, boolean ridden) {
        drawFoldedRosterShard(g, slot, hovered, selected ? CompanionWheelVisualState.PENDING
                : deployed || ridden ? CompanionWheelVisualState.DEPLOYED : CompanionWheelVisualState.AVAILABLE);
    }

    private static void drawFoldedRosterShard(Graphics2D g, CompanionWheelLayout.RosterSlot slot,
                                               boolean hovered, CompanionWheelVisualState state) {
        int left = slot.left();
        int top = slot.top() - (hovered ? 2 : 0);
        int right = slot.right();
        int bottom = slot.bottom() - (hovered ? 2 : 0);
        Path2D shard = new Path2D.Float();
        shard.moveTo(left + 7, top);
        shard.lineTo(right - 8, top);
        shard.lineTo(right, bottom);
        shard.lineTo(left, bottom - 7);
        shard.closePath();
        g.setColor(hovered ? new Color(237, 241, 239, 244) : new Color(23, 32, 36, 238));
        g.fill(shard);
        Path2D band = new Path2D.Float();
        band.moveTo(left + 2, top + slot.height() * 0.58);
        band.lineTo(right - 4, top + slot.height() * 0.42);
        band.lineTo(right - 1, bottom - 1);
        band.lineTo(left + 1, bottom - 7);
        band.closePath();
        g.setColor(hovered ? new Color(42, 87, 98, 234) : new Color(50, 66, 69, 224));
        g.fill(band);
        if (state != CompanionWheelVisualState.AVAILABLE) {
            g.setColor(stateColor(state, 248));
            g.setStroke(new BasicStroke(2.0f));
            g.drawLine(left + 7, top, right - 8, top);
            g.drawLine(left + 7, top, left, bottom - 7);
            g.setStroke(new BasicStroke(1.0f));
        }
    }

    private static void drawStateEdges(Graphics2D g, int left, int top, int right, int bottom,
                                       CompanionWheelVisualState state) {
        if (state != CompanionWheelVisualState.AVAILABLE) {
            g.setColor(stateColor(state, 248));
            g.fillRect(left, top, 2, bottom - top);
            g.fillRect(left, top, right - left, 2);
        }
    }

    private static CompanionWheelVisualState stateAt(int stateCode, int index) {
        int ordinal = stateCode >> (index * 3) & 7;
        CompanionWheelVisualState[] values = CompanionWheelVisualState.values();
        return ordinal < values.length ? values[ordinal] : CompanionWheelVisualState.AVAILABLE;
    }

    private static Color stateColor(CompanionWheelVisualState state, int alpha) {
        return switch (state) {
            case PENDING -> new Color(139, 146, 148, alpha);
            case DEPLOYED -> new Color(32, 198, 232, alpha);
            case SWITCHING -> new Color(255, 194, 71, alpha);
            case CRITICAL -> new Color(255, 112, 82, alpha);
            case DEAD -> new Color(227, 72, 80, alpha);
            case AVAILABLE -> new Color(20, 30, 34, alpha);
        };
    }
}
