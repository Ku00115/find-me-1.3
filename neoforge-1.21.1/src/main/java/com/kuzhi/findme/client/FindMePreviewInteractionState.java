package com.kuzhi.findme.client;

import java.util.HashMap;
import java.util.Map;

/** Shared camera controls for interactive entity previews. */
public final class FindMePreviewInteractionState {
    private static final Map<String, MutableView> VIEWS = new HashMap<>();

    private FindMePreviewInteractionState() {
    }

    public static View view(String id) {
        MutableView view = VIEWS.computeIfAbsent(normalize(id), ignored -> new MutableView());
        return new View(view.yaw, view.pitch, view.zoom, view.offsetX, view.offsetY);
    }

    public static void rotate(String id, double deltaX, double deltaY) {
        MutableView view = VIEWS.computeIfAbsent(normalize(id), ignored -> new MutableView());
        view.yaw = wrap(view.yaw + (float)deltaX * 0.75f);
        view.pitch = clamp(view.pitch + (float)deltaY * 0.55f, -35.0f, 35.0f);
    }

    public static void pan(String id, double deltaX, double deltaY) {
        MutableView view = VIEWS.computeIfAbsent(normalize(id), ignored -> new MutableView());
        view.offsetX = clamp(view.offsetX + (float)deltaX, -80.0f, 80.0f);
        view.offsetY = clamp(view.offsetY + (float)deltaY, -80.0f, 80.0f);
    }

    public static void zoom(String id, double wheelDelta) {
        MutableView view = VIEWS.computeIfAbsent(normalize(id), ignored -> new MutableView());
        float factor = wheelDelta > 0.0 ? 0.90f : 1.10f;
        view.zoom = clamp(view.zoom * factor, 0.35f, 3.0f);
    }

    private static String normalize(String id) {
        return id == null || id.isBlank() ? "default" : id;
    }

    private static float wrap(float value) {
        float wrapped = value % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record View(float yaw, float pitch, float zoom, float offsetX, float offsetY) {
    }

    private static final class MutableView {
        private float yaw = CompanionDetailPreviewRenderer.DEFAULT_PREVIEW_YAW;
        private float pitch;
        private float zoom = 1.0f;
        private float offsetX;
        private float offsetY;
    }
}
