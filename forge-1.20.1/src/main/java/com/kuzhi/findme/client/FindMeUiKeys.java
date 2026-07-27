package com.kuzhi.findme.client;

import org.lwjgl.glfw.GLFW;

final class FindMeUiKeys {
    static final int NUMBER_SLOT_COUNT = 9;

    private FindMeUiKeys() {
    }

    static int numberSlot(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            return keyCode - GLFW.GLFW_KEY_1;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return keyCode - GLFW.GLFW_KEY_KP_1;
        }
        return -1;
    }

    static boolean isWheelModeSwitch(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_TAB;
    }

    static boolean isFollowModeSwitch(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_F;
    }

    static boolean isCancel(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE;
    }

    static boolean isSelectAll(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_A;
    }

    static boolean isSave(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_S;
    }

    static boolean isConfirm(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }

    static boolean isPrimaryMouse(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    static boolean isSecondaryMouse(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    static boolean isMiddleMouse(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
    }
}
