package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;

public final class ClientCompanionState {
    private static final Map<CompanionKind, List<CompanionListPacket.Entry>> ENTRIES = new EnumMap<CompanionKind, List<CompanionListPacket.Entry>>(CompanionKind.class);
    private static final Map<CompanionKind, List<CompanionListPacket.Entry>> ALL_ENTRIES = new EnumMap<CompanionKind, List<CompanionListPacket.Entry>>(CompanionKind.class);
    private static final Map<CompanionKind, Integer> ACTIVE = new EnumMap<CompanionKind, Integer>(CompanionKind.class);
    private static final Map<CompanionKind, Long> SERVER_REVISIONS = new EnumMap<>(CompanionKind.class);
    private static List<DeadCompanionListPacket.Entry> DEAD_ENTRIES = List.of();
    private static long revision;

    private ClientCompanionState() {
    }

    public static void update(CompanionKind kind, long serverRevision, int activeIndex,
                              List<CompanionListPacket.Entry> entries, List<CompanionListPacket.Entry> allEntries) {
        if (serverRevision < serverRevision(kind)) {
            return;
        }
        List<CompanionListPacket.Entry> previous = previousEntries(kind);
        List<CompanionListPacket.Entry> merged = mergePreviewTags(previous, entries);
        List<CompanionListPacket.Entry> mergedAll = mergePreviewTags(previous, allEntries);
        ENTRIES.put(kind, List.copyOf(merged));
        ALL_ENTRIES.put(kind, List.copyOf(mergedAll));
        ACTIVE.put(kind, activeIndex);
        SERVER_REVISIONS.put(kind, serverRevision);
        ClientCompanionWheelController.observeRoster(kind, mergedAll.isEmpty() ? merged : mergedAll);
        revision++;
    }

    public static List<CompanionListPacket.Entry> entries(CompanionKind kind) {
        List<CompanionListPacket.Entry> base = filterUnavailableIntegrations(
                ENTRIES.getOrDefault((Object)kind, List.of()));
        List<UUID> members = ClientCompanionTeamState.currentMembers(kind == CompanionKind.MOUNT ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.COMPANION);
        if (members.isEmpty()) return base;
        Map<UUID, CompanionListPacket.Entry> byUuid = allEntries(kind).stream().collect(Collectors.toMap(CompanionListPacket.Entry::uuid, Function.identity(), (a, b) -> a));
        ArrayList<CompanionListPacket.Entry> preview = new ArrayList<>();
        for (UUID uuid : members) {
            CompanionListPacket.Entry entry = byUuid.get(uuid);
            if (entry != null) preview.add(entry);
        }
        return preview.isEmpty() ? base : List.copyOf(preview);
    }

    public static List<CompanionListPacket.Entry> allEntries(CompanionKind kind) {
        List<CompanionListPacket.Entry> all = ALL_ENTRIES.getOrDefault((Object)kind, List.of());
        return filterUnavailableIntegrations(all.isEmpty() ? ENTRIES.getOrDefault((Object)kind, List.of()) : all);
    }

    public static int activeIndex(CompanionKind kind) {
        List<CompanionListPacket.Entry> visible = entries(kind);
        UUID activeUuid = activeUuid(kind);
        if (activeUuid == null) return -1;
        for (int i = 0; i < visible.size(); ++i) if (activeUuid.equals(visible.get(i).uuid())) return i;
        return -1;
    }

    public static UUID activeUuid(CompanionKind kind) {
        int serverIndex = ACTIVE.getOrDefault((Object) kind, -1);
        List<CompanionListPacket.Entry> base = ENTRIES.getOrDefault((Object) kind, List.of());
        return serverIndex >= 0 && serverIndex < base.size() ? base.get(serverIndex).uuid() : null;
    }

    public static int serverWheelIndex(CompanionKind kind, UUID uuid) {
        List<CompanionListPacket.Entry> base = ENTRIES.getOrDefault((Object)kind, List.of());
        for (int i = 0; i < base.size(); ++i) if (base.get(i).uuid().equals(uuid)) return i;
        return -1;
    }

    public static void updateDead(List<DeadCompanionListPacket.Entry> entries) {
        DEAD_ENTRIES = List.copyOf(entries);
        ClientCompanionWheelController.observeDead(entries);
        revision++;
    }

    public static long revision() {
        return revision;
    }

    public static long serverRevision(CompanionKind kind) {
        return kind == null ? -1L : SERVER_REVISIONS.getOrDefault(kind, -1L);
    }

    public static List<DeadCompanionListPacket.Entry> deadEntries() {
        return DEAD_ENTRIES;
    }

    public static void reset() {
        ENTRIES.clear();
        ALL_ENTRIES.clear();
        ACTIVE.clear();
        SERVER_REVISIONS.clear();
        DEAD_ENTRIES = List.of();
        revision++;
    }

    private static List<CompanionListPacket.Entry> previousEntries(CompanionKind kind) {
        List<CompanionListPacket.Entry> wheel = ENTRIES.getOrDefault(kind, List.of());
        List<CompanionListPacket.Entry> all = ALL_ENTRIES.getOrDefault(kind, List.of());
        if (wheel.isEmpty()) {
            return all;
        }
        if (all.isEmpty()) {
            return wheel;
        }
        ArrayList<CompanionListPacket.Entry> previous = new ArrayList<>(wheel.size() + all.size());
        previous.addAll(wheel);
        previous.addAll(all);
        return previous;
    }

    private static List<CompanionListPacket.Entry> mergePreviewTags(List<CompanionListPacket.Entry> previous, List<CompanionListPacket.Entry> incoming) {
        Map<java.util.UUID, CompanionListPacket.Entry> previousByUuid = previous.stream()
                .collect(Collectors.toMap(CompanionListPacket.Entry::uuid, Function.identity(), ClientCompanionState::preferPreviewEntry));
        ArrayList<CompanionListPacket.Entry> merged = new ArrayList<>(incoming.size());
        for (CompanionListPacket.Entry entry : incoming) {
            CompoundTag previewTag = entry.previewTag();
            CompanionListPacket.Entry old = previousByUuid.get(entry.uuid());
            if (previewTag == null && old != null && old.previewTag() != null && !entry.entityType().isBlank() && entry.entityType().equals(old.entityType())) {
                previewTag = old.previewTag().copy();
            }
            merged.add(copyWithPreview(entry, previewTag));
        }
        return merged;
    }

    private static CompanionListPacket.Entry copyWithPreview(CompanionListPacket.Entry entry, CompoundTag previewTag) {
        CompanionMoveType moveType = entry.moveType();
        return new CompanionListPacket.Entry(entry.uuid(), entry.entityId(), entry.entityType(), entry.name(), entry.loaded(), entry.alive(),
                entry.deployed(), entry.ridden(), entry.hasHome(), entry.homeResident(), entry.tacticalAction(),
                entry.health(), entry.maxHealth(), entry.armor(),
                moveType, entry.summonAnimation(), entry.rescueAnimation(), entry.storageAnimation(),
                entry.switchAnimation(), entry.summonStyle(), entry.rescueStyle(), entry.storageStyle(),
                entry.spellBindings(), entry.magicState(), previewTag);
    }

    private static CompanionListPacket.Entry preferPreviewEntry(CompanionListPacket.Entry left, CompanionListPacket.Entry right) {
        if (left.previewTag() == null || left.previewTag().isEmpty()) {
            return right;
        }
        if (right.previewTag() == null || right.previewTag().isEmpty()) {
            return left;
        }
        return left;
    }

    private static List<CompanionListPacket.Entry> filterUnavailableIntegrations(List<CompanionListPacket.Entry> entries) {
        if (entries.isEmpty()) {
            return entries;
        }
        List<CompanionListPacket.Entry> visible = entries.stream()
                .filter(entry -> !"cobblemon:pokemon".equals(entry.entityType()))
                .toList();
        return visible.size() == entries.size() ? entries : visible;
    }
}
