package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class ClientMountRosterState {
    private static Selection selected;

    private ClientMountRosterState() {
    }

    public static List<Entry> entries() {
        List<Entry> findMe = new ArrayList<>();
        appendFindMe(findMe);
        return composeSections(findMe, List.of());
    }

    static List<Entry> composeSections(List<Entry> findMe, List<Entry> vehicles) {
        List<Entry> result = new ArrayList<>();
        if (findMe != null) result.addAll(findMe);
        if (vehicles != null) result.addAll(vehicles);
        return List.copyOf(result);
    }

    public static void select(MountRosterSource source, UUID uuid) {
        if (source != null && uuid != null) selected = new Selection(source, uuid);
    }

    public static Entry selectedEntry() {
        List<Entry> entries = entries();
        if (selected != null) {
            for (Entry entry : entries) {
                if (selected.matches(entry)) return entry;
            }
            selected = null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        UUID riddenUuid = minecraft.player != null && minecraft.player.getVehicle() != null
                ? minecraft.player.getVehicle().getUUID() : null;
        if (riddenUuid != null) {
            for (Entry entry : entries) {
                if (!entry.empty() && riddenUuid.equals(entry.uuid)) {
                    selected = new Selection(entry.source, entry.uuid);
                    return entry;
                }
            }
        }
        for (Entry entry : entries) {
            if (!entry.empty() && entry.ridden()) {
                selected = new Selection(entry.source, entry.uuid);
                return entry;
            }
        }
        UUID activeMount = ClientCompanionState.activeUuid(CompanionKind.MOUNT);
        for (Entry entry : entries) {
            if (entry.source == MountRosterSource.FIND_ME && entry.uuid != null
                    && entry.uuid.equals(activeMount)) {
                selected = new Selection(entry.source, entry.uuid);
                return entry;
            }
        }
        Entry fallback = entries.stream().filter(entry -> !entry.empty()).findFirst().orElse(null);
        if (fallback != null) selected = new Selection(fallback.source, fallback.uuid);
        return fallback;
    }

    public static boolean isSelected(MountRosterSource source, UUID uuid) {
        if (selected == null) selectedEntry();
        return selected != null && selected.source == source && selected.uuid.equals(uuid);
    }

    public static Entry selectRelative(int direction) {
        List<Entry> available = entries().stream().filter(entry -> !entry.empty() && entry.alive()).toList();
        if (available.isEmpty()) return null;
        Entry current = selectedEntry();
        int index = indexOf(available, current);
        int next = Math.floorMod(index + (direction >= 0 ? 1 : -1), available.size());
        Entry entry = available.get(next);
        selected = new Selection(entry.source, entry.uuid);
        return entry;
    }

    private static int indexOf(List<Entry> entries, Entry target) {
        if (target == null || target.uuid == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.source == target.source && target.uuid.equals(entry.uuid)) return i;
        }
        return -1;
    }

    public static void reset() {
        selected = null;
    }

    private static void appendFindMe(List<Entry> result) {
        Map<UUID, CompanionListPacket.Entry> byUuid = new HashMap<>();
        for (CompanionListPacket.Entry entry : ClientCompanionState.allEntries(CompanionKind.MOUNT)) {
            byUuid.put(entry.uuid(), entry);
        }
        boolean appended = appendTeams(result, MountRosterSource.FIND_ME, CompanionTeamTarget.MOUNT,
                byUuid, Map.of());
        if (!appended) {
            int team = ClientCompanionTeamState.currentTeam(CompanionTeamTarget.MOUNT);
            int index = 0;
            for (CompanionListPacket.Entry entry : ClientCompanionState.entries(CompanionKind.MOUNT)) {
                result.add(Entry.findMe(entry, index++, team));
            }
        }
        for (CompanionListPacket.Entry ghost : ClientCompanionWheelController.deathGhosts(CompanionKind.MOUNT)) {
            if (result.stream().noneMatch(entry -> ghost.uuid().equals(entry.uuid))) {
                result.add(Entry.findMe(ghost, -1, -1));
            }
        }
    }

    private static void appendVehicles(List<Entry> result) {
        Map<UUID, VehicleListPacket.Entry> byUuid = new HashMap<>();
        for (VehicleListPacket.Entry entry : ClientVehicleState.allEntries()) byUuid.put(entry.uuid(), entry);
        boolean appended = appendTeams(result, MountRosterSource.VEHICLE, CompanionTeamTarget.VEHICLE,
                Map.of(), byUuid);
        if (!appended) {
            int team = ClientCompanionTeamState.currentTeam(CompanionTeamTarget.VEHICLE);
            int index = 0;
            for (VehicleListPacket.Entry entry : ClientVehicleState.wheelEntries()) {
                result.add(Entry.vehicle(entry, index++, team));
            }
        }
    }

    private static boolean appendTeams(List<Entry> result, MountRosterSource source,
                                       CompanionTeamTarget target,
                                       Map<UUID, CompanionListPacket.Entry> mounts,
                                       Map<UUID, VehicleListPacket.Entry> vehicles) {
        boolean appended = false;
        for (ClientCompanionTeamState.TeamEntry team : ClientCompanionTeamState.entries(target)) {
            int pageStart = result.size();
            int memberIndex = 0;
            for (UUID uuid : team.uuids()) {
                CompanionListPacket.Entry mount = mounts.get(uuid);
                VehicleListPacket.Entry vehicle = vehicles.get(uuid);
                if (mount != null) result.add(Entry.findMe(mount, memberIndex, team.index()));
                if (vehicle != null) result.add(Entry.vehicle(vehicle, memberIndex, team.index()));
                memberIndex++;
            }
            if (result.size() == pageStart) continue;
            while ((result.size() - pageStart) % CompanionWheelLayout.PAGE_SIZE != 0) {
                result.add(Entry.placeholder(source, -1));
            }
            appended = true;
        }
        return appended;
    }

    public record Entry(MountRosterSource source, UUID uuid, int sourceSlot, int teamIndex,
                        CompanionListPacket.Entry findMe, VehicleListPacket.Entry vehicle) {
        static Entry findMe(CompanionListPacket.Entry entry, int sourceSlot, int teamIndex) {
            return new Entry(MountRosterSource.FIND_ME, entry.uuid(), sourceSlot, teamIndex, entry, null);
        }

        static Entry vehicle(VehicleListPacket.Entry entry, int sourceSlot, int teamIndex) {
            return new Entry(MountRosterSource.VEHICLE, entry.uuid(), sourceSlot, teamIndex, null, entry);
        }

        static Entry placeholder(MountRosterSource source, int sourceSlot) {
            return new Entry(source, null, sourceSlot, -1, null, null);
        }

        public boolean empty() { return uuid == null; }
        public Key key() { return empty() ? null : new Key(source, uuid); }
        public boolean deployed() {
            return vehicle != null ? vehicle.deployed() || vehicle.ridden()
                    : findMe != null && (findMe.deployed() || findMe.ridden());
        }
        public boolean ridden() {
            return vehicle != null ? vehicle.ridden() : findMe != null && findMe.ridden();
        }
        public boolean alive() {
            return vehicle != null ? vehicle.alive() : findMe != null && findMe.alive();
        }
        public String name() {
            return vehicle != null ? vehicle.name()
                    : findMe == null ? "" : findMe.name();
        }
        public CompanionListPacket.Entry asPreviewEntry() {
            return vehicle != null ? vehicle.asPreviewEntry() : findMe;
        }
    }

    public record Key(MountRosterSource source, UUID uuid) {
    }

    private record Selection(MountRosterSource source, UUID uuid) {
        boolean matches(Entry entry) {
            return entry != null && source == entry.source && uuid.equals(entry.uuid);
        }
    }
}
