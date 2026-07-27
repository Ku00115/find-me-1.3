package com.kuzhi.findme.api.client;

import com.kuzhi.findme.client.ClientWheelPresentationState;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** Client-only registry for addon pages linked from FindMe's integration editor. */
public final class FindMeClientIntegrationRegistry {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private FindMeClientIntegrationRegistry() {
    }

    public static void register(String id, Component label, Consumer<List<String>> opener) {
        if (id == null || id.isBlank() || label == null || opener == null) return;
        ENTRIES.removeIf(entry -> entry.id().equals(id));
        ENTRIES.add(new Entry(id, label, opener));
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static boolean open(String id, List<String> entityTypes) {
        for (Entry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                entry.opener().accept(List.copyOf(entityTypes));
                return true;
            }
        }
        return false;
    }

    /** Current FindMe typography classes for addon-owned ApricityUI pages. */
    public static String typographyClasses() {
        return ClientWheelPresentationState.typographyClasses();
    }

    /** Whether addon-owned ApricityUI pages should use FindMe's transition motion. */
    public static boolean uiAnimationsEnabled() {
        return ClientWheelPresentationState.uiAnimations();
    }

    public record Entry(String id, Component label, Consumer<List<String>> opener) {
    }
}
