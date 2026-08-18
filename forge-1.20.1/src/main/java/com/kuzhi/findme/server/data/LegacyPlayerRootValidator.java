package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionTeamTarget;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class LegacyPlayerRootValidator {
    private static final String[] UUID_LISTS = {
            "mounts", "companions", "mountWheelSlots", "companionWheelSlots",
            "mountDeployedList", "companionDeployedList", "mountEligible",
            "vehicleMounts", "vehicles", "vehicleWheelSlots"
    };
    private static final String[] POSITION_LISTS = {
            "origins", "lastKnownPositions", "homePositions", "homeNestBlocks"
    };
    private static final String[] UUID_FIELDS = {
            "mountPrevious", "companionPrevious", "mountDeployed", "companionDeployed", "vehicleDeployed"
    };
    private static final String[] NUMBER_FIELDS = {
            "mountIndex", "companionIndex", "vehicleIndex", "mountReadyAt", "companionReadyAt",
            "emergencyRescueReadyAt", "safetySchemaVersion"
    };
    private static final String[] COMPOUND_FIELDS = {
            "uiSettings", "teamCounts", "teamAutoJoinDisabled"
    };

    private LegacyPlayerRootValidator() {
    }

    static Optional<String> firstError(CompoundTag root) {
        if (root == null) {
            return Optional.of("$ expected compound, actual=null");
        }
        for (String key : UUID_LISTS) {
            Optional<String> error = validateList(root, key, LegacyPlayerRootValidator::uuidEntry);
            if (error.isPresent()) return error;
        }
        for (String key : POSITION_LISTS) {
            Optional<String> error = validateList(root, key, LegacyPlayerRootValidator::positionEntry);
            if (error.isPresent()) return error;
        }
        Optional<String> error = validateList(root, "deadCompanions", LegacyPlayerRootValidator::deadEntry);
        if (error.isPresent()) return error;
        error = validateList(root, "storedEntities", entry -> compoundPayloadEntry(entry, "entity"));
        if (error.isPresent()) return error;
        error = validateList(root, "displayNames", entry -> stringPayloadEntry(entry, "name"));
        if (error.isPresent()) return error;
        error = validateList(root, "homeHouseIds", entry -> uuidPayloadEntry(entry, "houseId"));
        if (error.isPresent()) return error;
        error = validateList(root, "lifecycleStates", LegacyPlayerRootValidator::lifecycleEntry);
        if (error.isPresent()) return error;
        error = validateList(root, "effectStyles", LegacyPlayerRootValidator::effectEntry);
        if (error.isPresent()) return error;
        error = validateList(root, "animationStyles", LegacyPlayerRootValidator::animationEntry);
        if (error.isPresent()) return error;
        error = validateList(root, "vault", entry -> compoundPayloadEntry(entry, "entity"));
        if (error.isPresent()) return error;
        error = validateList(root, "backups", entry -> require(entry, "state", Tag.TAG_COMPOUND));
        if (error.isPresent()) return error;
        error = validateList(root, "teams", LegacyPlayerRootValidator::teamEntry);
        if (error.isPresent()) return error;
        error = validateList(root, "deadRecords", LegacyPlayerRootValidator::deadRecordEntry);
        if (error.isPresent()) return error;

        for (String key : UUID_FIELDS) {
            if (root.contains(key) && !root.hasUUID(key)) {
                return Optional.of("$." + key + " expected UUID int-array");
            }
        }
        for (String key : NUMBER_FIELDS) {
            if (root.contains(key) && !root.contains(key, Tag.TAG_ANY_NUMERIC)) {
                return Optional.of("$." + key + " expected number");
            }
        }
        for (String key : COMPOUND_FIELDS) {
            if (root.contains(key) && !root.contains(key, Tag.TAG_COMPOUND)) {
                return Optional.of("$." + key + " expected compound");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateList(CompoundTag root, String key, EntryCheck check) {
        if (!root.contains(key)) {
            return Optional.empty();
        }
        Tag raw = root.get(key);
        if (!(raw instanceof ListTag list)) {
            return Optional.of("$." + key + " expected list");
        }
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof CompoundTag entry)) {
                return Optional.of("$." + key + "[" + index + "] expected compound");
            }
            Optional<String> error = check.validate(entry);
            if (error.isPresent()) {
                return Optional.of("$." + key + "[" + index + "]." + error.get());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> uuidEntry(CompoundTag entry) {
        return entry.hasUUID("uuid") ? Optional.empty() : Optional.of("uuid expected UUID int-array");
    }

    private static Optional<String> positionEntry(CompoundTag entry) {
        Optional<String> error = uuidEntry(entry);
        if (error.isPresent()) return error;
        error = require(entry, "position", Tag.TAG_COMPOUND);
        if (error.isPresent()) return error;
        CompoundTag position = entry.getCompound("position");
        if (!position.contains("dimension", Tag.TAG_STRING) || position.getString("dimension").isBlank()) {
            return Optional.of("position.dimension expected non-empty string");
        }
        for (String axis : new String[]{"x", "y", "z"}) {
            if (!position.contains(axis, Tag.TAG_ANY_NUMERIC)) {
                return Optional.of("position." + axis + " expected number");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> deadEntry(CompoundTag entry) {
        Optional<String> error = uuidEntry(entry);
        if (error.isPresent() || !entry.contains("kind")) return error;
        return validEnum(entry, "kind", CompanionKind.class);
    }

    private static Optional<String> compoundPayloadEntry(CompoundTag entry, String payload) {
        Optional<String> error = uuidEntry(entry);
        return error.isPresent() ? error : require(entry, payload, Tag.TAG_COMPOUND);
    }

    private static Optional<String> stringPayloadEntry(CompoundTag entry, String payload) {
        Optional<String> error = uuidEntry(entry);
        return error.isPresent() ? error : require(entry, payload, Tag.TAG_STRING);
    }

    private static Optional<String> uuidPayloadEntry(CompoundTag entry, String payload) {
        Optional<String> error = uuidEntry(entry);
        if (error.isPresent()) return error;
        return entry.hasUUID(payload) ? Optional.empty() : Optional.of(payload + " expected UUID int-array");
    }

    private static Optional<String> lifecycleEntry(CompoundTag entry) {
        Optional<String> error = uuidEntry(entry);
        return error.isPresent() ? error : validEnum(entry, "state", CompanionLifecycleState.class);
    }

    private static Optional<String> effectEntry(CompoundTag entry) {
        Optional<String> error = uuidEntry(entry);
        if (error.isPresent()) return error;
        error = validEnum(entry, "purpose", CompanionEffectPurpose.class);
        return error.isPresent() ? error : validEnum(entry, "style", CompanionEffectStyle.class);
    }

    private static Optional<String> animationEntry(CompoundTag entry) {
        Optional<String> error = uuidEntry(entry);
        if (error.isPresent()) return error;
        error = validEnum(entry, "purpose", CompanionAnimationPurpose.class);
        return error.isPresent() ? error : validEnum(entry, "style", CompanionAnimationStyle.class);
    }

    private static Optional<String> teamEntry(CompoundTag entry) {
        Optional<String> error = validEnum(entry, "target", CompanionTeamTarget.class);
        if (error.isPresent()) return error;
        error = require(entry, "index", Tag.TAG_ANY_NUMERIC);
        if (error.isPresent()) return error;
        CompoundTag nested = new CompoundTag();
        Tag entries = entry.get("entries");
        if (entries != null) nested.put("entries", entries.copy());
        return validateList(nested, "entries", LegacyPlayerRootValidator::uuidEntry)
                .map(value -> value.substring(2));
    }

    private static Optional<String> deadRecordEntry(CompoundTag entry) {
        if (!entry.hasUUID("sourceEntityId") && !entry.hasUUID("recordId")) {
            return Optional.of("sourceEntityId expected UUID int-array");
        }
        if (entry.contains("sourceEntityId") && !entry.hasUUID("sourceEntityId")) {
            return Optional.of("sourceEntityId expected UUID int-array");
        }
        if (entry.contains("recordId") && !entry.hasUUID("recordId")) {
            return Optional.of("recordId expected UUID int-array");
        }
        if (entry.contains("previousKind")) {
            return validEnum(entry, "previousKind", CompanionKind.class);
        }
        return Optional.empty();
    }

    private static Optional<String> require(CompoundTag entry, String key, int type) {
        return entry.contains(key, type) ? Optional.empty() : Optional.of(key + " expected " + typeName(type));
    }

    private static <E extends Enum<E>> Optional<String> validEnum(CompoundTag entry, String key, Class<E> type) {
        if (!entry.contains(key, Tag.TAG_STRING)) {
            return Optional.of(key + " expected string");
        }
        try {
            Enum.valueOf(type, entry.getString(key));
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.of(key + " has unsupported value '" + entry.getString(key) + "'");
        }
    }

    private static String typeName(int type) {
        return switch (type) {
            case Tag.TAG_COMPOUND -> "compound";
            case Tag.TAG_STRING -> "string";
            case Tag.TAG_ANY_NUMERIC -> "number";
            default -> "tag(" + type + ")";
        };
    }

    @FunctionalInterface
    private interface EntryCheck {
        Optional<String> validate(CompoundTag entry);
    }
}
