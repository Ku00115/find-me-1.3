package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable hot-path roster projection, invalidated with the player-root revision. */
public final class CompanionRuntimeIndex {
    private static final CompanionRuntimeIndex EMPTY = new CompanionRuntimeIndex(Map.of());
    private final Map<UUID, Entry> entries;

    private CompanionRuntimeIndex(Map<UUID, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static CompanionRuntimeIndex empty() {
        return EMPTY;
    }

    static CompanionRuntimeIndex fromRoot(CompoundTag root) {
        HomeResidentIndex homeIndex = HomeResidentIndex.fromRoot(root);
        if (homeIndex.entries().isEmpty()) return EMPTY;
        Map<UUID, Entry> result = new HashMap<>();
        for (HomeResidentIndex.Entry entry : homeIndex.entries()) {
            result.put(entry.uuid(), new Entry(entry.kind(), entry.lifecycleState(),
                    entry.deployed(), entry.stored(), entry.hasHomeAssignment()));
        }
        return new CompanionRuntimeIndex(result);
    }

    public boolean contains(UUID uuid) {
        return uuid != null && entries.containsKey(uuid);
    }

    public boolean contains(CompanionKind kind, UUID uuid) {
        Entry entry = uuid == null ? null : entries.get(uuid);
        return entry != null && entry.kind == kind;
    }

    public Entry entry(UUID uuid) {
        return uuid == null ? null : entries.get(uuid);
    }

    public Set<UUID> uuids() {
        return entries.keySet();
    }

    public record Entry(CompanionKind kind, CompanionLifecycleState lifecycleState,
                        boolean deployed, boolean stored, boolean hasHome) {
    }
}
