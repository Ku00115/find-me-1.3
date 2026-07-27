package com.kuzhi.findme.client;

import com.kuzhi.findme.network.VehicleListPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class ClientVehicleState {
    private static int activeIndex = -1;
    private static List<VehicleListPacket.Entry> wheelEntries = List.of();
    private static List<VehicleListPacket.Entry> allEntries = List.of();
    private static long revision;
    private static long serverRevision = -1L;

    private ClientVehicleState() {
    }

    public static void update(long incomingRevision, int active, List<VehicleListPacket.Entry> wheel,
                              List<VehicleListPacket.Entry> all) {
        if (incomingRevision < serverRevision) return;
        serverRevision = incomingRevision;
        activeIndex = active;
        List<VehicleListPacket.Entry> previous = new ArrayList<>(wheelEntries.size() + allEntries.size());
        previous.addAll(wheelEntries);
        previous.addAll(allEntries);
        List<VehicleListPacket.Entry> mergedWheel = mergePreviewTags(previous, wheel);
        List<VehicleListPacket.Entry> mergedAll = mergePreviewTags(previous, all);
        wheelEntries = List.copyOf(mergedWheel);
        allEntries = List.copyOf(completeRoster(mergedAll, mergedWheel));
        revision++;
    }

    public static int activeIndex() {
        List<VehicleListPacket.Entry> visible = wheelEntries();
        UUID activeUuid = activeIndex >= 0 && activeIndex < wheelEntries.size() ? wheelEntries.get(activeIndex).uuid() : null;
        if (activeUuid == null) return -1;
        for (int i = 0; i < visible.size(); ++i) if (activeUuid.equals(visible.get(i).uuid())) return i;
        return -1;
    }

    public static List<VehicleListPacket.Entry> wheelEntries() {
        List<UUID> members = ClientCompanionTeamState.currentMembers(com.kuzhi.findme.common.CompanionTeamTarget.VEHICLE);
        if (members.isEmpty()) return wheelEntries;
        Map<UUID, VehicleListPacket.Entry> byUuid = allEntries.stream().collect(Collectors.toMap(VehicleListPacket.Entry::uuid, Function.identity(), (a, b) -> a));
        ArrayList<VehicleListPacket.Entry> preview = new ArrayList<>();
        for (UUID uuid : members) {
            VehicleListPacket.Entry entry = byUuid.get(uuid);
            if (entry != null) preview.add(entry);
        }
        return preview.isEmpty() ? wheelEntries : List.copyOf(preview);
    }

    public static List<VehicleListPacket.Entry> allEntries() {
        return allEntries;
    }

    public static long revision() {
        return revision;
    }

    public static long serverRevision() {
        return serverRevision;
    }

    public static int serverWheelIndex(UUID uuid) {
        for (int i = 0; i < wheelEntries.size(); ++i) {
            if (wheelEntries.get(i).uuid().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    public static void reset() {
        activeIndex = -1;
        wheelEntries = List.of();
        allEntries = List.of();
        serverRevision = -1L;
        revision++;
    }

    private static List<VehicleListPacket.Entry> mergePreviewTags(List<VehicleListPacket.Entry> previous, List<VehicleListPacket.Entry> incoming) {
        Map<java.util.UUID, VehicleListPacket.Entry> previousByUuid = previous.stream()
                .collect(Collectors.toMap(VehicleListPacket.Entry::uuid, Function.identity(), ClientVehicleState::preferPreviewEntry));
        ArrayList<VehicleListPacket.Entry> merged = new ArrayList<>(incoming.size());
        for (VehicleListPacket.Entry entry : incoming) {
            CompoundTag previewTag = entry.previewTag();
            VehicleListPacket.Entry old = previousByUuid.get(entry.uuid());
            if (previewTag == null && old != null && old.previewTag() != null && !entry.entityType().isBlank() && entry.entityType().equals(old.entityType())) {
                previewTag = old.previewTag().copy();
            }
            merged.add(new VehicleListPacket.Entry(entry.uuid(), entry.entityId(), entry.entityType(), entry.name(),
                    entry.loaded(), entry.alive(), entry.deployed(), entry.ridden(), entry.summonAnimation(),
                    entry.rescueAnimation(), entry.storageAnimation(), entry.switchAnimation(), entry.summonStyle(),
                    entry.rescueStyle(), entry.storageStyle(), previewTag));
        }
        return merged;
    }

    private static VehicleListPacket.Entry preferPreviewEntry(VehicleListPacket.Entry left, VehicleListPacket.Entry right) {
        if (left.previewTag() == null || left.previewTag().isEmpty()) {
            return right;
        }
        if (right.previewTag() == null || right.previewTag().isEmpty()) {
            return left;
        }
        return left;
    }

    private static List<VehicleListPacket.Entry> completeRoster(List<VehicleListPacket.Entry> all,
                                                                 List<VehicleListPacket.Entry> wheel) {
        ArrayList<VehicleListPacket.Entry> complete = new ArrayList<>(all.size() + wheel.size());
        Map<UUID, VehicleListPacket.Entry> byUuid = new java.util.LinkedHashMap<>();
        for (VehicleListPacket.Entry entry : all) byUuid.put(entry.uuid(), entry);
        for (VehicleListPacket.Entry entry : wheel) byUuid.putIfAbsent(entry.uuid(), entry);
        complete.addAll(byUuid.values());
        return complete;
    }
}
