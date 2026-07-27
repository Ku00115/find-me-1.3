package com.kuzhi.findme.client;

import net.minecraft.nbt.CompoundTag;

final class CompanionPreviewTags {
    private CompanionPreviewTags() {
    }

    static CompoundTag sanitized(CompoundTag source, String entityType) {
        CompoundTag tag = source.copy();
        if (entityType != null && !entityType.isBlank()) {
            tag.putString("id", entityType);
        }
        tag.remove("UUID");
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("Passengers");
        tag.remove("Leash");
        tag.remove("Vehicle");
        tag.remove("PortalCooldown");
        if ("cobblemon:pokemon".equals(entityType)) {
            // Cobblemon 1.7.3 reads PoseType unconditionally during entity loading.
            tag.remove("Pose");
            tag.putString("PoseType", "STAND");
        }
        return tag;
    }
}
