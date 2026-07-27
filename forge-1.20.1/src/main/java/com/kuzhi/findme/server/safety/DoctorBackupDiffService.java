package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.NbtFingerprint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class DoctorBackupDiffService {
    private DoctorBackupDiffService() {
    }

    static Result compare(PlayerCompanionData current, PlayerCompanionData backup, int requestedPage, int pageSize) {
        Set<UUID> ids = new LinkedHashSet<>();
        collectIds(current, ids);
        collectIds(backup, ids);
        ArrayList<Comparison> comparisons = new ArrayList<>();
        for (UUID uuid : ids) {
            RecordState currentState = snapshot(current, uuid);
            RecordState backupState = snapshot(backup, uuid);
            if (currentState == null && backupState == null) continue;
            boolean snapshotChanged = currentState != null && backupState != null
                    && currentState.stored() && backupState.stored()
                    && !currentState.snapshotFingerprint().equals(backupState.snapshotFingerprint());
            String kind = currentState == null ? "ADDED"
                    : backupState == null ? "REMOVED"
                    : sameState(currentState, backupState) ? "SAME" : "CHANGED";
            if ("SAME".equals(kind)) {
                continue;
            }
            comparisons.add(new Comparison(backupState, currentState, kind, snapshotChanged));
        }
        comparisons.sort(Comparator.comparingInt((Comparison row) -> kindRank(row.kind()))
                .thenComparing(row -> displayCategory(row.backup(), row.current()))
                .thenComparing(row -> displayName(row.backup(), row.current()), String.CASE_INSENSITIVE_ORDER));
        int totalChanges = comparisons.size();
        int pages = Math.max(1, (comparisons.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = Math.min(comparisons.size(), page * pageSize);
        int to = Math.min(comparisons.size(), from + pageSize);
        List<Comparison> visible = List.copyOf(comparisons.subList(from, to));
        return new Result(page, pages, totalChanges, visible);
    }

    private static boolean sameState(RecordState current, RecordState backup) {
        return current.name().equals(backup.name())
                && current.entityType().equals(backup.entityType())
                && current.category().equals(backup.category())
                && current.lifecycle().equals(backup.lifecycle())
                && current.team().equals(backup.team())
                && current.home().equals(backup.home())
                && current.stored() == backup.stored()
                && current.snapshotFingerprint().equals(backup.snapshotFingerprint());
    }

    private static int kindRank(String kind) {
        return switch (kind) {
            case "CHANGED" -> 0;
            case "ADDED" -> 1;
            case "REMOVED" -> 2;
            default -> 3;
        };
    }

    private static String displayCategory(RecordState backup, RecordState current) {
        RecordState value = backup != null ? backup : current;
        return value == null ? "UNKNOWN" : value.category();
    }

    private static String displayName(RecordState backup, RecordState current) {
        RecordState value = backup != null ? backup : current;
        return value == null ? "" : value.name();
    }

    static List<RecordState> records(PlayerCompanionData data) {
        Set<UUID> ids = new LinkedHashSet<>();
        collectIds(data, ids);
        return ids.stream().map(uuid -> snapshot(data, uuid)).filter(Objects::nonNull)
                .sorted(Comparator.comparing(RecordState::category).thenComparing(RecordState::name))
                .toList();
    }

    private static void collectIds(PlayerCompanionData data, Set<UUID> target) {
        for (CompanionKind kind : CompanionKind.values()) target.addAll(data.list(kind));
        target.addAll(data.vehicleList());
        target.addAll(data.deadList());
        target.addAll(data.storedEntityIds());
    }

    private static RecordState snapshot(PlayerCompanionData data, UUID uuid) {
        boolean known = data.contains(uuid) || data.containsVehicle(uuid) || data.deadList().contains(uuid)
                || data.storedEntityIds().contains(uuid);
        if (!known) return null;
        CompoundTag stored = data.rawStoredEntityForDiagnostics(uuid).orElse(null);
        DeadCompanionRecord dead = data.deadRecord(uuid).orElse(null);
        String type = stored != null && stored.contains("id") ? stored.getString("id")
                : dead == null ? "" : dead.entityType();
        String name = data.displayName(uuid).orElse(dead == null ? "" : dead.customName());
        if (name == null || name.isBlank()) name = type == null || type.isBlank() ? shortUuid(uuid) : type;
        String category = data.containsVehicle(uuid) ? "VEHICLE"
                : data.kindOf(uuid).map(Enum::name).orElse(data.deadList().contains(uuid) ? "DEAD" : "UNKNOWN");
        return new RecordState(uuid, name, type == null ? "" : type, category, data.lifecycleState(uuid).name(),
                team(data, uuid), home(data, uuid), stored != null, fingerprint(stored),
                stored == null ? null : stored.copy());
    }

    private static String team(PlayerCompanionData data, UUID uuid) {
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            for (int index = 0; index < data.teamCount(target); index++) {
                if (!data.team(target, index).contains(uuid)) continue;
                String name = data.teamName(target, index);
                return target.name() + ":" + index + ":" + (name == null ? "" : name);
            }
        }
        return "";
    }

    private static String home(PlayerCompanionData data, UUID uuid) {
        if (data.homeHouseId(uuid).isPresent()) return "HOUSE:" + data.homeHouseId(uuid).orElseThrow();
        if (data.homeNestBlock(uuid).isPresent()) return "NEST:" + data.homeNestBlock(uuid).orElseThrow();
        return "";
    }

    private static String shortUuid(UUID uuid) {
        String value = uuid == null ? "" : uuid.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String fingerprint(Tag tag) {
        return tag == null ? "" : NbtFingerprint.sha256(tag);
    }

    record Result(int page, int pageCount, int totalChanges, List<Comparison> comparisons) {
    }

    record Comparison(RecordState backup, RecordState current, String kind, boolean snapshotChanged) {
    }

    record RecordState(UUID uuid, String name, String entityType, String category, String lifecycle,
                       String team, String home, boolean stored, String snapshotFingerprint, CompoundTag previewTag) {
        RecordState {
            name = Objects.requireNonNullElse(name, "");
            entityType = Objects.requireNonNullElse(entityType, "");
            category = Objects.requireNonNullElse(category, "UNKNOWN");
            lifecycle = Objects.requireNonNullElse(lifecycle, "RECOVERY");
            team = Objects.requireNonNullElse(team, "");
            home = Objects.requireNonNullElse(home, "");
            snapshotFingerprint = Objects.requireNonNullElse(snapshotFingerprint, "");
            previewTag = previewTag == null ? null : previewTag.copy();
        }
    }
}
