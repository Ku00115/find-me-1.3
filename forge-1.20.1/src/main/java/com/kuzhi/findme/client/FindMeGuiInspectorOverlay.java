package com.kuzhi.findme.client;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Read-only AUI probe for the currently open Minecraft screen. */
final class FindMeGuiInspectorOverlay {
    private static final String PATH = "findme/inspector/inspector.html";
    private static final int MAX_VISIBLE_SLOTS = 24;
    private static Document document;
    private static Screen sourceScreen;
    private static boolean visible;

    private FindMeGuiInspectorOverlay() {
    }

    static void toggle(Screen screen) {
        if (visible) {
            close();
        } else if (screen != null) {
            open(screen);
        }
    }

    static void open(Screen screen) {
        if (screen == null) return;
        sourceScreen = screen;
        visible = true;
        ensureDocument();
        renderSnapshot(screen);
    }

    static void close() {
        visible = false;
        sourceScreen = null;
        if (document != null) {
            Element root = document.getElementById("findme-gui-inspector-root");
            if (root != null) root.setAttribute("style", "display:none");
        }
    }

    static void render(GuiGraphics graphics, Screen screen) {
        if (!visible || sourceScreen != screen || Minecraft.getInstance().screen != screen) {
            if (visible && sourceScreen != screen) close();
            return;
        }
        ensureDocument();
        document.tickFrame();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 1000.0f);
        Base.drawScreenDocument(graphics.pose(), document);
        graphics.pose().popPose();
    }

    private static void ensureDocument() {
        if (document == null || !document.isActive()) {
            document = Document.create(PATH);
        }
    }

    private static void renderSnapshot(Screen screen) {
        if (document == null || document.body == null) return;
        AbstractContainerMenu menu = null;
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            menu = containerScreen.getMenu();
        }

        List<Slot> slots = menu == null ? List.of() : menu.slots;
        int width = Math.max(1, screen.width);
        int height = Math.max(1, screen.height);
        int panelWidth = Math.min(470, Math.max(310, width - 24));
        int panelHeight = Math.min(330, Math.max(220, height - 24));
        int left = Math.max(12, (width - panelWidth) / 2);
        int top = Math.max(12, (height - panelHeight) / 2);
        String screenClass = screen.getClass().getName();
        String menuClass = menu == null ? "none" : menu.getClass().getName();

        StringBuilder rows = new StringBuilder();
        int visibleSlots = Math.min(MAX_VISIBLE_SLOTS, slots.size());
        for (int i = 0; i < visibleSlots; ++i) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getItem();
            String itemName = stack.isEmpty() ? "empty" : stack.getHoverName().getString();
            String count = stack.isEmpty() ? "" : " x" + stack.getCount();
            rows.append("<div class='slot-row'><b>")
                    .append(slot.index)
                    .append("</b><span>")
                    .append(escape(itemName))
                    .append(escape(count))
                    .append("</span><small>")
                    .append(slot.x).append(", ").append(slot.y)
                    .append("</small></div>");
        }
        if (slots.isEmpty()) {
            rows.append("<div class='empty-row'>No AbstractContainerMenu slots detected.</div>");
        }

        String markup = "<div id='findme-gui-inspector-root' class='inspector-root' style='left:"
                + left + "px;top:" + top + "px;width:" + panelWidth + "px;height:" + panelHeight + "px'>"
                + "<div class='inspector-head'><div><small>APRICITYOS PROBE</small>"
                + "<strong>GUI Inspector</strong></div><kbd>F8 CLOSE</kbd></div>"
                + "<div class='summary'><div><small>SCREEN</small><span>" + escape(shortName(screenClass))
                + "</span></div><div><small>MENU</small><span>" + escape(shortName(menuClass))
                + "</span></div><div><small>SLOTS</small><span>" + slots.size() + "</span></div></div>"
                + "<div class='detail'><small>SCREEN CLASS</small><span>" + escape(screenClass)
                + "</span><small>MENU CLASS</small><span>" + escape(menuClass) + "</span></div>"
                + "<div class='slot-title'><span>SLOT SNAPSHOT</span><small>showing " + visibleSlots + " / "
                + slots.size() + "</small></div><div class='slot-list'>" + rows + "</div></div>";
        document.body.setInnerHTML(markup);
        document.rebuildSelectorIndex();
        document.reapplyStylesFromCache();
    }

    private static String shortName(String className) {
        if (className == null || className.isBlank()) return "none";
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
