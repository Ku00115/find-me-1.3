package com.kuzhi.findme.client;

import com.kuzhi.findme.common.FindMeWheelStyle;

final class CompanionWheelLayout {
    static final int VIEWPORT_WIDTH = 427;
    static final int VIEWPORT_HEIGHT = 240;
    static final int BACKDROP_RADIUS = 90;
    static final int INNER_BACKDROP_RADIUS = 20;
    static final int CENTER_RADIUS = 13;
    static final int CENTER_CANCEL_RADIUS = 35;
    static final int SLOT_RADIUS = 22;
    static final int HOVERED_SLOT_RADIUS = 25;
    static final int ITEM_RADIUS = 62;
    static final int OUTER_RADIUS = 86;
    // Keep radial sectors inside the decorative outer ring.
    static final int SECTOR_OUTER_RADIUS = OUTER_RADIUS - 5;
    // Keep the immediate-mode fallback aligned with the AUI wheel spacing.
    static final double RADIAL_SECTOR_GAP = 0.035;
    static final int SECTOR_INNER_RADIUS = 21;
    static final int PAGE_SIZE = 6;
    static final int SIX_WING_CARD_WIDTH = 34;
    static final int SIX_WING_CARD_HEIGHT = 62;
    static final int STRIP_CARD_HEIGHT = 58;
    static final int STRIP_CARD_GAP = 4;

    private CompanionWheelLayout() {
    }

    static Page page(int entryCount, int currentPage) {
        int count = Math.max(1, (entryCount + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(currentPage, count - 1));
        int start = page * PAGE_SIZE;
        int end = Math.min(entryCount, start + PAGE_SIZE);
        return new Page(page, count, start, end);
    }

    static int hoveredSlot(int centerX, int centerY, int mouseX, int mouseY, int visibleSize) {
        if (visibleSize <= 0) {
            return -1;
        }
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < (double)CENTER_CANCEL_RADIUS) {
            return -1;
        }
        if (distance > (double)OUTER_RADIUS) {
            return -1;
        }
        double angle = Math.atan2(dy, dx);
        double start = -Math.PI / 2.0 - Math.PI / (double)Math.max(1, visibleSize);
        double normalized = normalize(angle - start);
        int index = (int)(normalized / (Math.PI * 2.0 / (double)Math.max(1, visibleSize)));
        return index >= 0 && index < visibleSize ? index : -1;
    }

    static Slot slot(int centerX, int centerY, int index, int visibleSize) {
        double angle = angleFor(index, visibleSize);
        int x = centerX + (int)Math.round(Math.cos(angle) * (double)ITEM_RADIUS);
        int y = centerY + (int)Math.round(Math.sin(angle) * (double)ITEM_RADIUS);
        return new Slot(x, y);
    }

    static RosterSlot rosterSlot(FindMeWheelStyle layout, int screenWidth, int screenHeight,
                                 int centerX, int centerY, int index, int visibleSize) {
        if (layout == FindMeWheelStyle.TACTICAL_STRIP) {
            int viewportWidth = Math.min(screenWidth, VIEWPORT_WIDTH);
            int viewportHeight = Math.min(screenHeight, VIEWPORT_HEIGHT);
            int viewportLeft = (screenWidth - viewportWidth) / 2;
            int viewportTop = (screenHeight - viewportHeight) / 2;
            int cardWidth = Math.max(29, Math.min(36,
                    (viewportWidth - 30 - STRIP_CARD_GAP * Math.max(0, visibleSize - 1)) / Math.max(1, visibleSize)));
            int totalWidth = cardWidth * visibleSize + STRIP_CARD_GAP * Math.max(0, visibleSize - 1);
            int left = viewportLeft + (viewportWidth - totalWidth) / 2 + index * (cardWidth + STRIP_CARD_GAP);
            int top = viewportTop + viewportHeight - STRIP_CARD_HEIGHT - 10;
            return new RosterSlot(left + cardWidth / 2, top + STRIP_CARD_HEIGHT / 2 - 3,
                    left, top, cardWidth, STRIP_CARD_HEIGHT);
        }

        if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
            Slot radial = slot(centerX, centerY, index, visibleSize);
            int width = 42;
            int height = 48;
            return new RosterSlot(radial.x(), radial.y(), radial.x() - width / 2, radial.y() - height / 2,
                    width, height);
        }

        double angle = angleFor(index, visibleSize);
        if (layout == FindMeWheelStyle.FOLDED_SHARDS) {
            int x = centerX + (int)Math.round(Math.cos(angle) * 80.0);
            int y = centerY + (int)Math.round(Math.sin(angle) * 66.0);
            int width = 48;
            int height = 42;
            return new RosterSlot(x, y, x - width / 2, y - height / 2, width, height);
        }
        int x = centerX + (int)Math.round(Math.cos(angle) * 82.0);
        int y = centerY + (int)Math.round(Math.sin(angle) * 72.0);
        return new RosterSlot(x, y - 2, x - SIX_WING_CARD_WIDTH / 2, y - SIX_WING_CARD_HEIGHT / 2,
                SIX_WING_CARD_WIDTH, SIX_WING_CARD_HEIGHT);
    }

    static int hoveredRosterSlot(FindMeWheelStyle layout, int screenWidth, int screenHeight,
                                 int centerX, int centerY, int mouseX, int mouseY, int visibleSize) {
        if (visibleSize <= 0) {
            return -1;
        }
        if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
            return hoveredSlot(centerX, centerY, mouseX, mouseY, visibleSize);
        }
        if (layout == FindMeWheelStyle.TACTICAL_STRIP) {
            for (int i = 0; i < visibleSize; ++i) {
                RosterSlot slot = rosterSlot(layout, screenWidth, screenHeight, centerX, centerY, i, visibleSize);
                if (mouseX >= slot.left() && mouseX < slot.right()
                        && mouseY >= slot.top() && mouseY < slot.bottom()) {
                    return i;
                }
            }
            return -1;
        }

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < CENTER_CANCEL_RADIUS || distance > 112.0) {
            return -1;
        }
        double angle = Math.atan2(dy, dx);
        double start = -Math.PI / 2.0 - Math.PI / Math.max(1, visibleSize);
        double normalized = normalize(angle - start);
        int index = (int)(normalized / (Math.PI * 2.0 / Math.max(1, visibleSize)));
        return index >= 0 && index < visibleSize ? index : -1;
    }

    private static double angleFor(int index, int size) {
        return -Math.PI / 2.0 + Math.PI * 2.0 * (double)index / (double)Math.max(1, size);
    }

    static double slotAngle(int index, int size) {
        return angleFor(index, size);
    }

    private static double normalize(double angle) {
        double full = Math.PI * 2.0;
        double value = angle % full;
        return value < 0.0 ? value + full : value;
    }

    record Page(int index, int count, int start, int end) {
        int visibleSize() {
            return this.end - this.start;
        }
    }

    record Slot(int x, int y) {
    }

    record RosterSlot(int x, int y, int left, int top, int width, int height) {
        int right() {
            return this.left + this.width;
        }

        int bottom() {
            return this.top + this.height;
        }
    }

    static PreviewBounds rosterPreviewBounds(FindMeWheelStyle layout, RosterSlot slot, boolean hovered) {
        if (layout == FindMeWheelStyle.CLASSIC_RADIAL) {
            int lift = hovered ? -1 : 0;
            return new PreviewBounds(slot.x() - 17, slot.y() - 17 + lift, 34, 34);
        }
        int lift = hovered ? -2 : 0;
        int top = slot.top() + (layout == FindMeWheelStyle.FOLDED_SHARDS ? 5 : 10) + lift;
        int height = layout == FindMeWheelStyle.FOLDED_SHARDS
                ? Math.max(16, slot.height() - 12) : Math.max(18, slot.height() - 29);
        return new PreviewBounds(slot.left() + 2, top, Math.max(12, slot.width() - 4), height);
    }

    record PreviewBounds(int left, int top, int width, int height) {
    }

    static int viewportLeft(int screenWidth) {
        return (screenWidth - Math.min(screenWidth, VIEWPORT_WIDTH)) / 2;
    }

    static int viewportTop(int screenHeight) {
        return (screenHeight - Math.min(screenHeight, VIEWPORT_HEIGHT)) / 2;
    }
}
