package com.kuzhi.findme.client;

import com.kuzhi.findme.common.FindMeModule;
import net.minecraft.client.Minecraft;

public final class ClientFindMeModuleState {
    private static long configuredMask = defaultMask();
    private static long effectiveMask = defaultMask();
    private static long availableMask = defaultMask();
    private static boolean canManage;

    private ClientFindMeModuleState() {
    }

    public static void update(long configured, long effective, long available, boolean manager) {
        configuredMask = configured;
        effectiveMask = effective;
        availableMask = available;
        canManage = manager;
        if (Minecraft.getInstance().screen instanceof FindMeAuiHouseScreen
                && !enabled(FindMeModule.HOUSES)) {
            Minecraft.getInstance().setScreen(null);
        } else if (Minecraft.getInstance().screen instanceof FindMeAuiPackEditorScreen
                && !enabled(FindMeModule.MANAGEMENT)) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    public static boolean configured(FindMeModule module) {
        return module != null && (configuredMask & module.bit()) != 0L;
    }

    public static boolean enabled(FindMeModule module) {
        return module != null && (effectiveMask & module.bit()) != 0L;
    }

    public static boolean available(FindMeModule module) {
        return module != null && (availableMask & module.bit()) != 0L;
    }

    public static boolean canManage() {
        return canManage;
    }

    public static void reset() {
        configuredMask = defaultMask();
        effectiveMask = defaultMask();
        availableMask = defaultMask();
        canManage = false;
    }

    private static long defaultMask() {
        long mask = 0L;
        for (FindMeModule module : FindMeModule.values()) {
            if (module.defaultEnabled()) {
                mask |= module.bit();
            }
        }
        return mask;
    }
}
