package com.kuzhi.findme.api;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable, revisioned view of one FindMe-owned companion. */
public record CompanionDescriptor(
        UUID ownerUuid,
        UUID companionUuid,
        CompanionKind kind,
        CompanionLifecycleState lifecycle,
        CompanionMoveType movement,
        Optional<ResourceLocation> entityType,
        boolean deployed,
        boolean live,
        boolean stored,
        Optional<UUID> houseId,
        long revision) {

    public CompanionDescriptor {
        if (ownerUuid == null || companionUuid == null || kind == null || lifecycle == null || movement == null) {
            throw new IllegalArgumentException("Companion descriptors require identity, kind, lifecycle, and movement");
        }
        entityType = entityType == null ? Optional.empty() : entityType;
        houseId = houseId == null ? Optional.empty() : houseId;
        revision = Math.max(0L, revision);
    }
}
