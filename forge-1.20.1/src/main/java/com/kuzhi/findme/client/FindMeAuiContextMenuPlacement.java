package com.kuzhi.findme.client;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

/** Shared pointer anchoring and viewport clamping for FindMe AUI context menus. */
final class FindMeAuiContextMenuPlacement {
    static final Bounds EMPTY = new Bounds(0.0, 0.0, 0.0, 0.0);

    private FindMeAuiContextMenuPlacement() {
    }

    static Bounds place(Document document, String menuId, String pageSelector,
                        double anchorX, double anchorY, double fallbackX, double fallbackY,
                        double fallbackWidth, double fallbackHeight) {
        if (document == null) return EMPTY;
        Element menu = document.getElementById(menuId);
        Element page = menu == null ? null : menu.closest(pageSelector);
        if (menu == null || page == null) return EMPTY;

        Position pagePosition = Position.of(page);
        Size pageSize = Size.of(page);
        Size menuSize = Size.of(menu);
        double menuWidth = menuSize.width() > 1.0 ? menuSize.width() : fallbackWidth;
        double menuHeight = menuSize.height() > 1.0 ? menuSize.height() : fallbackHeight;
        double anchorLocalX = anchorX - pagePosition.x;
        double gap = 3.0;
        double rightSideX = anchorLocalX + gap;
        double leftSideX = anchorLocalX - menuWidth - gap;
        double localX = anchorX >= 0.0
                ? rightSideX + menuWidth <= pageSize.width() - 2.0 ? rightSideX : leftSideX
                : fallbackX;
        double localY = anchorY >= 0.0 ? anchorY - pagePosition.y - menuHeight * 0.5 : fallbackY;
        localX = Math.max(2.0, Math.min(localX, pageSize.width() - menuWidth - 2.0));
        localY = Math.max(2.0, Math.min(localY, pageSize.height() - menuHeight - 2.0));
        menu.setAttribute("style", "left:" + Math.round(localX) + "px;top:" + Math.round(localY) + "px");
        return new Bounds(pagePosition.x + localX, pagePosition.y + localY,
                pagePosition.x + localX + menuWidth, pagePosition.y + localY + menuHeight);
    }

    record Bounds(double left, double top, double right, double bottom) {
        boolean overlaps(double x, double y, double width, double height) {
            return x < right && x + width > left && y < bottom && y + height > top;
        }
    }
}
