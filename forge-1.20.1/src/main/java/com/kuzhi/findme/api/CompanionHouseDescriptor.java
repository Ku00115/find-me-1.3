package com.kuzhi.findme.api;

import com.kuzhi.findme.common.SavedPosition;
import java.util.Set;
import java.util.UUID;

/** Read-only public projection of a FindMe house. */
public record CompanionHouseDescriptor(
        UUID houseId,
        UUID ownerUuid,
        SavedPosition position,
        String displayName,
        Set<UUID> residentUuids) {

    public CompanionHouseDescriptor {
        if (houseId == null || position == null) {
            throw new IllegalArgumentException("House descriptors require an id and position");
        }
        displayName = displayName == null ? "" : displayName;
        residentUuids = Set.copyOf(residentUuids == null ? Set.of() : residentUuids);
    }
}
