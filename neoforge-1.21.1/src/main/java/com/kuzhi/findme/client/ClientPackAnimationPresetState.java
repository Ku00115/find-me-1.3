package com.kuzhi.findme.client;

import com.kuzhi.findme.network.PackAnimationPresetListPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientPackAnimationPresetState {
    private static boolean editMode;
    private static List<PackAnimationPresetListPacket.Entry> entries = List.of();

    private ClientPackAnimationPresetState() {
    }

    public static void setEditMode(boolean enabled) {
        editMode = enabled;
    }

    public static boolean editMode() {
        return editMode;
    }

    public static void update(boolean replace, List<PackAnimationPresetListPacket.Entry> newEntries) {
        if (replace) {
            entries = List.copyOf(newEntries);
        } else {
            ArrayList<PackAnimationPresetListPacket.Entry> merged = new ArrayList<>(entries);
            Map<String, Integer> positions = new HashMap<>();
            for (int i = 0; i < merged.size(); i++) positions.put(merged.get(i).entityType(), i);
            for (PackAnimationPresetListPacket.Entry entry : newEntries) {
                Integer position = positions.get(entry.entityType());
                if (position == null) {
                    positions.put(entry.entityType(), merged.size());
                    merged.add(entry);
                } else {
                    merged.set(position, entry);
                }
            }
            entries = List.copyOf(merged);
        }
        FindMeAuiPackEditorScreen.refreshCurrent();
    }

    public static void applyLocal(List<PackAnimationPresetListPacket.Entry> changedEntries) {
        mergeInPlace(changedEntries);
    }

    private static void mergeInPlace(List<PackAnimationPresetListPacket.Entry> changedEntries) {
        ArrayList<PackAnimationPresetListPacket.Entry> merged = new ArrayList<>(entries);
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < merged.size(); i++) positions.put(merged.get(i).entityType(), i);
        for (PackAnimationPresetListPacket.Entry entry : changedEntries) {
            Integer position = positions.get(entry.entityType());
            if (position == null) {
                positions.put(entry.entityType(), merged.size());
                merged.add(entry);
            } else {
                merged.set(position, entry);
            }
        }
        entries = List.copyOf(merged);
    }

    public static List<PackAnimationPresetListPacket.Entry> entries() {
        return entries;
    }
}
