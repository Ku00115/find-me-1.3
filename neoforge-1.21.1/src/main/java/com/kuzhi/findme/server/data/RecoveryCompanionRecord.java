package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Persisted diagnostic identity for a registered creature whose authoritative entity data is unavailable. */
public record RecoveryCompanionRecord(
        UUID recordId,
        UUID sourceEntityId,
        String customName,
        String entityType,
        CompanionKind kind,
        int previousTeamIndex,
        long detectedAt,
        long lastCheckedAt,
        String dimension,
        double x,
        double y,
        double z,
        String reason,
        String detail,
        CompanionLifecycleState sourceState) {

    public RecoveryCompanionRecord {
        recordId = recordId == null ? UUID.randomUUID() : recordId;
        sourceEntityId = sourceEntityId == null ? recordId : sourceEntityId;
        customName = safe(customName);
        entityType = safe(entityType);
        kind = kind == null ? CompanionKind.COMPANION : kind;
        dimension = safe(dimension);
        reason = safe(reason);
        detail = safe(detail);
        sourceState = sourceState == null ? CompanionLifecycleState.RECOVERY : sourceState;
    }

    public RecoveryCompanionRecord checkedAt(long tick, String newDetail) {
        return new RecoveryCompanionRecord(recordId, sourceEntityId, customName, entityType, kind,
                previousTeamIndex, detectedAt, tick, dimension, x, y, z, reason,
                newDetail == null || newDetail.isBlank() ? detail : newDetail, sourceState);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("recordId", recordId);
        tag.putUUID("sourceEntityId", sourceEntityId);
        tag.putString("customName", customName);
        tag.putString("entityType", entityType);
        tag.putString("kind", kind.name());
        tag.putInt("previousTeamIndex", previousTeamIndex);
        tag.putLong("detectedAt", detectedAt);
        tag.putLong("lastCheckedAt", lastCheckedAt);
        tag.putString("dimension", dimension);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putString("reason", reason);
        tag.putString("detail", detail);
        tag.putString("sourceState", sourceState.name());
        return tag;
    }

    public static RecoveryCompanionRecord load(CompoundTag tag) {
        UUID sourceId = tag.hasUUID("sourceEntityId") ? tag.getUUID("sourceEntityId") : null;
        UUID recordId = tag.hasUUID("recordId") ? tag.getUUID("recordId") : null;
        if (recordId == null) {
            String identity = sourceId == null
                    ? "find_me:invalid_recovery_record:" + NbtFingerprint.sha256(tag)
                    : "find_me:legacy_recovery:" + sourceId;
            recordId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        }
        if (sourceId == null) sourceId = recordId;
        CompanionKind kind = enumValue(CompanionKind.class, tag.getString("kind"), CompanionKind.COMPANION);
        CompanionLifecycleState sourceState = enumValue(CompanionLifecycleState.class,
                tag.getString("sourceState"), CompanionLifecycleState.RECOVERY);
        return new RecoveryCompanionRecord(recordId, sourceId, tag.getString("customName"),
                tag.getString("entityType"), kind, tag.getInt("previousTeamIndex"),
                tag.getLong("detectedAt"), tag.getLong("lastCheckedAt"), tag.getString("dimension"),
                tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getString("reason"),
                tag.getString("detail"), sourceState);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
