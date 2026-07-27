package com.kuzhi.findme.client;

final class CompanionScreenLayout {
    static final int MAX_PANEL_WIDTH = 404;
    static final int MAX_PANEL_HEIGHT = 316;
    static final int MIN_PANEL_WIDTH = 260;
    static final int MIN_PANEL_HEIGHT = 190;
    static final int SIDE_MARGIN = 16;
    static final int TOP_MARGIN = 18;
    static final int BOTTOM_MARGIN = 28;

    private CompanionScreenLayout() {
    }

    static int panelWidth(int screenWidth) {
        int available = Math.max(160, screenWidth - SIDE_MARGIN * 2);
        return Math.min(MAX_PANEL_WIDTH, Math.max(Math.min(MIN_PANEL_WIDTH, available), available));
    }

    static int panelHeight(int screenHeight) {
        return Math.min(MAX_PANEL_HEIGHT, Math.max(MIN_PANEL_HEIGHT, screenHeight - TOP_MARGIN * 2));
    }

    static int panelX(int screenWidth) {
        return screenWidth / 2 - CompanionScreenLayout.panelWidth(screenWidth) / 2;
    }

    static int panelY(int screenHeight) {
        int panelHeight = CompanionScreenLayout.panelHeight(screenHeight);
        return Math.max(TOP_MARGIN, Math.min(screenHeight - panelHeight - BOTTOM_MARGIN, screenHeight / 2 - panelHeight / 2));
    }
}
