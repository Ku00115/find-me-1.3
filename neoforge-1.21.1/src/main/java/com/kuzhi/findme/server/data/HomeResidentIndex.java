package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Immutable home-only projection of a player root, cached by the world data revision. */
public final class HomeResidentIndex {
    private static final HomeResidentIndex EMPTY = new HomeResidentIndex(List.of());
    private final List<Entry> entries;

    private HomeResidentIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static HomeResidentIndex empty() {
        return EMPTY;
    }

    public List<Entry> entries() {
        return entries;
    }

    static HomeResidentIndex fromRoot(CompoundTag root) {
        if (root == null || root.isEmpty()) return EMPTY;
        Map<UUID, SavedPosition> homePositions = positions(root.getList("homePositions", Tag.TAG_COMPOUND));
        Map<UUID, SavedPosition> homeNests = positions(root.getList("homeNestBlocks", Tag.TAG_COMPOUND));
        Map<UUID, UUID> houseIds = uuidMap(root.getList("homeHouseIds", Tag.TAG_COMPOUND));
        Map<UUID, CompanionLifecycleState> states = lifecycleStates(root);
        Set<UUID> stored = new HashSet<>();
        Map<UUID, Dimensions> dimensions = new HashMap<>();
        ListTag storedEntities = root.getList("storedEntities", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedEntities.size(); index++) {
            CompoundTag record = storedEntities.getCompound(index);
            if (!record.hasUUID("uuid")) continue;
            UUID uuid = record.getUUID("uuid");
            stored.add(uuid);
            CompoundTag entity = record.getCompound("entity");
            dimensions.put(uuid, new Dimensions(positive(entity.getFloat("CompanionPreviewWidth"), 1.0f),
                    positive(entity.getFloat("CompanionPreviewHeight"), 1.8f)));
        }

        List<Entry> result = new ArrayList<>();
        appendKind(root, CompanionKind.MOUNT, result, states, stored, dimensions,
                homePositions, homeNests, houseIds);
        appendKind(root, CompanionKind.COMPANION, result, states, stored, dimensions,
                homePositions, homeNests, houseIds);
        return result.isEmpty() ? EMPTY : new HomeResidentIndex(result);
    }

    private static void appendKind(CompoundTag root, CompanionKind kind, List<Entry> result,
                                   Map<UUID, CompanionLifecycleState> states, Set<UUID> stored,
                                   Map<UUID, Dimensions> dimensions, Map<UUID, SavedPosition> homePositions,
                                   Map<UUID, SavedPosition> homeNests, Map<UUID, UUID> houseIds) {
        String prefix = kind.name().toLowerCase();
        Set<UUID> deployed = uuidSet(root.getList(prefix + "DeployedList", Tag.TAG_COMPOUND));
        if (root.hasUUID(prefix + "Deployed")) deployed.add(root.getUUID(prefix + "Deployed"));
        ListTag roster = root.getList(prefix + "s", Tag.TAG_COMPOUND);
        for (int index = 0; index < roster.size(); index++) {
            CompoundTag record = roster.getCompound(index);
            if (!record.hasUUID("uuid")) continue;
            UUID uuid = record.getUUID("uuid");
            boolean isStored = stored.contains(uuid);
            CompanionLifecycleState state = states.get(uuid);
            if (state == null) {
                state = deployed.contains(uuid) ? CompanionLifecycleState.DEPLOYED
                        : isStored ? homeNests.containsKey(uuid)
                        ? CompanionLifecycleState.HOME_STORED : CompanionLifecycleState.STORED
                        : CompanionLifecycleState.RECOVERY;
            }
            Dimensions size = dimensions.getOrDefault(uuid, new Dimensions(1.0f, 1.8f));
            result.add(new Entry(uuid, kind, state, deployed.contains(uuid), isStored,
                    homePositions.get(uuid), homeNests.get(uuid), houseIds.get(uuid), size.width, size.height));
        }
    }

    private static Map<UUID, SavedPosition> positions(ListTag list) {
        Map<UUID, SavedPosition> result = new HashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            if (record.hasUUID("uuid") && record.contains("position", Tag.TAG_COMPOUND)) {
                result.put(record.getUUID("uuid"), SavedPosition.load(record.getCompound("position")));
            }
        }
        return result;
    }

    private static Map<UUID, UUID> uuidMap(ListTag list) {
        Map<UUID, UUID> result = new HashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            if (record.hasUUID("uuid") && record.hasUUID("value")) {
                result.put(record.getUUID("uuid"), record.getUUID("value"));
            }
        }
        return result;
    }

    private static Map<UUID, CompanionLifecycleState> lifecycleStates(CompoundTag root) {
        Map<UUID, CompanionLifecycleState> result = new HashMap<>();
        ListTag list = root.getList("lifecycleStates", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            if (!record.hasUUID("uuid")) continue;
            try {
                result.put(record.getUUID("uuid"), CompanionLifecycleState.valueOf(record.getString("state")));
            } catch (IllegalArgumentException ignored) {
                result.put(record.getUUID("uuid"), CompanionLifecycleState.RECOVERY);
            }
        }
        return result;
    }

    private static Set<UUID> uuidSet(ListTag list) {
        Set<UUID> result = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            if (record.hasUUID("uuid")) result.add(record.getUUID("uuid"));
        }
        return result;
    }

    private static float positive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0f ? value : fallback;
    }

    public record Entry(UUID uuid, CompanionKind kind, CompanionLifecycleState lifecycleState,
                        boolean deployed, boolean stored, SavedPosition homePosition,
                        SavedPosition homeNestBlock, UUID houseId, float width, float height) {
        public boolean hasHomeAssignment() {
            return houseId != null || homeNestBlock != null;
        }
    }

    private record Dimensions(float width, float height) {
    }
}
