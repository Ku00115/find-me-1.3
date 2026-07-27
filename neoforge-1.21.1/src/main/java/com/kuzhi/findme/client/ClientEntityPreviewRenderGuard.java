package com.kuzhi.findme.client;

/** Distinguishes entity renders inside GUI previews from ordinary world renders. */
final class ClientEntityPreviewRenderGuard {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private ClientEntityPreviewRenderGuard() {
    }

    static void enter() {
        DEPTH.get()[0]++;
    }

    static void exit() {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) depth[0]--;
    }

    static boolean active() {
        return DEPTH.get()[0] > 0;
    }
}
