package com.kuzhi.findme.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/** Client-only HUD placement. Values are normalized so GUI scale changes stay stable. */
public final class ClientFindMeHudLayout {
    static final float HUD_SCALE = 0.5f;
    static final int HUD_WIDTH = 196;
    static final int HUD_CARD_HEIGHT = 38;
    static final int HUD_GAP = 4;
    static final int HUD_MAX_ENTRIES = 6;
    private static final double DEFAULT_RIGHT = 0.02;
    private static final double DEFAULT_TOP = 0.02;
    private static final double DEFAULT_SCALE = 1.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("find_me/hud_layout.json");
    private static Layout cached;

    private ClientFindMeHudLayout() {
    }

    static Layout current() {
        if (cached == null) cached = read();
        return cached;
    }

    static void save(Layout value) {
        cached = value == null ? defaults() : value.normalized();
        try {
            Files.createDirectories(PATH.getParent());
            JsonObject object = new JsonObject();
            object.addProperty("right", cached.rightRatio());
            object.addProperty("top", cached.topRatio());
            object.addProperty("scale", cached.scale());
            object.addProperty("visible", cached.visible());
            Files.writeString(PATH, GSON.toJson(object), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    static boolean toggleVisible() {
        Layout value = current();
        boolean visible = !value.visible();
        save(new Layout(value.rightRatio(), value.topRatio(), value.scale(), visible));
        return visible;
    }

    public static void reset() {
        save(defaults());
    }

    static Layout defaults() {
        return new Layout(DEFAULT_RIGHT, DEFAULT_TOP, DEFAULT_SCALE, true);
    }

    static int displayWidth() {
        return displayWidth(DEFAULT_SCALE);
    }

    static int displayWidth(double layoutScale) {
        return scaled(HUD_WIDTH, layoutScale);
    }

    static int displayCardHeight() {
        return displayCardHeight(DEFAULT_SCALE);
    }

    static int displayCardHeight(double layoutScale) {
        return scaled(HUD_CARD_HEIGHT, layoutScale);
    }

    static int displayGap() {
        return displayGap(DEFAULT_SCALE);
    }

    static int displayGap(double layoutScale) {
        return scaled(HUD_GAP, layoutScale);
    }

    static int displayHeight() {
        return displayHeight(DEFAULT_SCALE);
    }

    static int displayHeight(double layoutScale) {
        return displayCardHeight(layoutScale) * HUD_MAX_ENTRIES
                + displayGap(layoutScale) * (HUD_MAX_ENTRIES - 1);
    }

    private static int scaled(int value, double layoutScale) {
        return Math.max(1, (int) Math.round(value * HUD_SCALE * normalizedScale(layoutScale)));
    }

    private static double normalizedScale(double value) {
        return Math.max(0.25, Math.min(3.0, value));
    }

    static int left(Layout layout, int screenWidth) {
        return left(layout, screenWidth, displayWidth());
    }

    static int left(Layout layout, int screenWidth, int contentWidth) {
        int right = Math.round((float)(layout.rightRatio() * screenWidth));
        int width = Math.max(1, contentWidth);
        return Math.max(0, Math.min(Math.max(0, screenWidth - width), screenWidth - width - right));
    }

    static int top(Layout layout, int screenHeight) {
        return top(layout, screenHeight, displayHeight());
    }

    static int top(Layout layout, int screenHeight, int contentHeight) {
        int height = Math.max(1, contentHeight);
        return Math.max(0, Math.min(Math.max(0, screenHeight - height),
                Math.round((float)(layout.topRatio() * screenHeight))));
    }

    static Layout fromPixels(double left, double top, int screenWidth, int screenHeight) {
        return fromPixels(left, top, screenWidth, screenHeight, current().visible());
    }

    static Layout fromPixels(double left, double top, int screenWidth, int screenHeight, boolean visible) {
        return fromPixels(left, top, screenWidth, screenHeight, visible, displayWidth(), displayHeight());
    }

    static Layout fromPixels(double left, double top, int screenWidth, int screenHeight, boolean visible,
                             int contentWidth, int contentHeight) {
        return fromPixels(left, top, screenWidth, screenHeight, visible, contentWidth, contentHeight,
                current().scale());
    }

    static Layout fromPixels(double left, double top, int screenWidth, int screenHeight, boolean visible,
                             int contentWidth, int contentHeight, double scale) {
        int width = Math.max(1, contentWidth);
        int height = Math.max(1, contentHeight);
        int maxLeft = Math.max(0, screenWidth - width);
        int maxTop = Math.max(0, screenHeight - height);
        double clampedLeft = Math.max(0.0, Math.min(maxLeft, left));
        double clampedTop = Math.max(0.0, Math.min(maxTop, top));
        return new Layout((screenWidth - width - clampedLeft) / Math.max(1.0, screenWidth),
                clampedTop / Math.max(1.0, screenHeight), scale, visible).normalized();
    }

    private static Layout read() {
        if (!Files.isRegularFile(PATH)) return defaults();
        try {
            JsonObject object = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), JsonObject.class);
            if (object == null) return defaults();
            return new Layout(number(object, "right", DEFAULT_RIGHT), number(object, "top", DEFAULT_TOP),
                    number(object, "scale", DEFAULT_SCALE),
                    !object.has("visible") || object.get("visible").getAsBoolean()).normalized();
        } catch (IOException | JsonParseException | IllegalStateException ignored) {
            return defaults();
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    record Layout(double rightRatio, double topRatio, double scale, boolean visible) {
        Layout withScale(double value) {
            return new Layout(rightRatio, topRatio, value, visible).normalized();
        }

        Layout normalized() {
            return new Layout(Math.max(0.0, Math.min(0.90, rightRatio)),
                    Math.max(0.0, Math.min(0.90, topRatio)), normalizedScale(scale), visible);
        }
    }
}
