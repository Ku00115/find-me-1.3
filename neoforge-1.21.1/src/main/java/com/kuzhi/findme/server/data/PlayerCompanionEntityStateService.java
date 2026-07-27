package com.kuzhi.findme.server.data;

import com.kuzhi.findme.server.lifecycle.CompanionStorageService;

import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

final class PlayerCompanionEntityStateService {
    private PlayerCompanionEntityStateService() {
    }

    static void setOrigin(PlayerCompanionData data, UUID uuid, SavedPosition position) {
        data.origins.put(uuid, position);
    }

    static Optional<SavedPosition> origin(PlayerCompanionData data, UUID uuid) {
        return Optional.ofNullable(data.origins.get(uuid));
    }

    static void setLastKnownPosition(PlayerCompanionData data, UUID uuid, SavedPosition position) {
        data.lastKnownPositions.put(uuid, position);
    }

    static Optional<SavedPosition> lastKnownPosition(PlayerCompanionData data, UUID uuid) {
        return Optional.ofNullable(data.lastKnownPositions.get(uuid));
    }

    static void setHomePosition(PlayerCompanionData data, UUID uuid, SavedPosition position) {
        data.homePositions.put(uuid, position);
        data.homeNestBlocks.remove(uuid);
        data.homeHouseIds.remove(uuid);
    }

    static Optional<SavedPosition> homePosition(PlayerCompanionData data, UUID uuid) {
        return Optional.ofNullable(data.homePositions.get(uuid));
    }

    static void clearHomePosition(PlayerCompanionData data, UUID uuid) {
        data.homePositions.remove(uuid);
        data.homeNestBlocks.remove(uuid);
        data.homeHouseIds.remove(uuid);
    }

    static void setHomeNestBlock(PlayerCompanionData data, UUID uuid, SavedPosition position) {
        if (position == null) {
            data.homeNestBlocks.remove(uuid);
        } else {
            data.homeNestBlocks.put(uuid, position);
        }
    }

    static Optional<SavedPosition> homeNestBlock(PlayerCompanionData data, UUID uuid) {
        return Optional.ofNullable(data.homeNestBlocks.get(uuid));
    }

    static void clearHomeNestBlock(PlayerCompanionData data, UUID uuid) {
        data.homeNestBlocks.remove(uuid);
    }

    static void setHomeHouseId(PlayerCompanionData data, UUID uuid, UUID houseId) {
        if (houseId == null) {
            data.homeHouseIds.remove(uuid);
        } else {
            data.homeHouseIds.put(uuid, houseId);
        }
    }

    static Optional<UUID> homeHouseId(PlayerCompanionData data, UUID uuid) {
        return Optional.ofNullable(data.homeHouseIds.get(uuid));
    }

    static void clearHomeHouseId(PlayerCompanionData data, UUID uuid) {
        data.homeHouseIds.remove(uuid);
    }

    static int clearHomesForNestBlock(PlayerCompanionData data, SavedPosition source) {
        if (source == null) {
            return 0;
        }
        int removed = 0;
        for (UUID uuid : new ArrayList<>(data.homeNestBlocks.keySet())) {
            SavedPosition saved = data.homeNestBlocks.get(uuid);
            if (sameBlock(saved, source)) {
                data.homeNestBlocks.remove(uuid);
                data.homePositions.remove(uuid);
                data.homeHouseIds.remove(uuid);
                removed++;
            }
        }
        return removed;
    }

    private static boolean sameBlock(SavedPosition first, SavedPosition second) {
        return first != null
                && second != null
                && first.dimension().equals(second.dimension())
                && first.blockPos().equals(second.blockPos());
    }

    static void storeEntity(PlayerCompanionData data, UUID uuid, CompoundTag tag) {
        data.storedEntities.put(uuid, CompanionStorageService.sanitizedStoredTag(tag));
    }

    static Optional<CompoundTag> storedEntity(PlayerCompanionData data, UUID uuid) {
        CompoundTag tag = data.storedEntities.get(uuid);
        return tag == null ? Optional.empty() : Optional.of(tag.copy());
    }

    static Optional<String> displayName(PlayerCompanionData data, UUID uuid) {
        String name = data.displayNames.get(uuid);
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    static void setDisplayName(PlayerCompanionData data, UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            data.displayNames.remove(uuid);
        } else {
            data.displayNames.put(uuid, name);
        }
    }

    static void removeStoredEntity(PlayerCompanionData data, UUID uuid) {
        data.storedEntities.remove(uuid);
    }
}

