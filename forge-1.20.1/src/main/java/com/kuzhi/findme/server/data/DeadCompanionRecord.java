package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public record DeadCompanionRecord(
        UUID recordId,
        UUID sourceEntityId,
        String customName,
        String entityType,
        CompanionKind previousKind,
        int previousTeamIndex,
        long deathTime,
        long worldDay,
        String dimension,
        double x,
        double y,
        double z,
        String deathCause,
        boolean recoverable,
        String recoveryRequirements) {

    public DeadCompanionRecord {
        recordId = recordId == null ? UUID.randomUUID() : recordId;
        sourceEntityId = sourceEntityId == null ? recordId : sourceEntityId;
        customName = safe(customName);
        entityType = safe(entityType);
        previousKind = previousKind == null ? CompanionKind.COMPANION : previousKind;
        dimension = safe(dimension);
        deathCause = safe(deathCause);
        recoveryRequirements = safe(recoveryRequirements);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("recordId", this.recordId);
        tag.putUUID("sourceEntityId", this.sourceEntityId);
        tag.putString("customName", this.customName);
        tag.putString("entityType", this.entityType);
        tag.putString("previousKind", this.previousKind.name());
        tag.putInt("previousTeamIndex", this.previousTeamIndex);
        tag.putLong("deathTime", this.deathTime);
        tag.putLong("worldDay", this.worldDay);
        tag.putString("dimension", this.dimension);
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putString("deathCause", this.deathCause);
        tag.putBoolean("recoverable", this.recoverable);
        tag.putString("recoveryRequirements", this.recoveryRequirements);
        return tag;
    }

    public static DeadCompanionRecord load(CompoundTag tag) {
        UUID sourceId = tag.hasUUID("sourceEntityId") ? tag.getUUID("sourceEntityId") : null;
        UUID recordId = tag.hasUUID("recordId") ? tag.getUUID("recordId") : null;
        if (sourceId == null && recordId != null) {
            sourceId = recordId;
        }
        if (recordId == null) {
            String identity = sourceId == null
                    ? "find_me:invalid_dead_record:" + NbtFingerprint.sha256(tag)
                    : "find_me:legacy_dead:" + sourceId;
            recordId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }
        if (sourceId == null) {
            sourceId = recordId;
        }
        CompanionKind kind;
        try {
            kind = CompanionKind.valueOf(tag.getString("previousKind"));
        } catch (IllegalArgumentException ignored) {
            kind = CompanionKind.COMPANION;
        }
        return new DeadCompanionRecord(recordId, sourceId, tag.getString("customName"), tag.getString("entityType"), kind,
                tag.getInt("previousTeamIndex"), tag.getLong("deathTime"), tag.getLong("worldDay"), tag.getString("dimension"),
                tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getString("deathCause"),
                tag.getBoolean("recoverable"), tag.getString("recoveryRequirements"));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
