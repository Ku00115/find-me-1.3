package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionTeamTarget;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-side team data used by the wheel until the AUI manager is rebuilt. */
public final class ClientCompanionTeamState {
    private static final Map<CompanionTeamTarget, List<List<UUID>>> TEAMS = new EnumMap<>(CompanionTeamTarget.class);
    private static final Map<CompanionTeamTarget, Integer> CURRENT = new EnumMap<>(CompanionTeamTarget.class);
    private static final Map<CompanionTeamTarget, List<TeamEntry>> ENTRIES = new EnumMap<>(CompanionTeamTarget.class);
    private static long revision;
    private static long serverRevision = -1L;
    private static int preferredMountTeam;

    static {
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            TEAMS.put(target, new ArrayList<>(List.of(new ArrayList<>())));
            CURRENT.put(target, 0);
            ENTRIES.put(target, List.of(new TeamEntry(target, 0, 1, true, "", List.of())));
        }
    }

    private ClientCompanionTeamState() {
    }

    public static void update(long incomingServerRevision, List<TeamEntry> entries) {
        if (incomingServerRevision < serverRevision) {
            return;
        }
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            TEAMS.put(target, new ArrayList<>(List.of(new ArrayList<>())));
            ENTRIES.put(target, new ArrayList<>(List.of(new TeamEntry(target, 0, 1, true, "", List.of()))));
        }
        for (TeamEntry entry : entries) {
            if (entry.target() == null || entry.index() < 0) {
                continue;
            }
            List<List<UUID>> teams = TEAMS.get(entry.target());
            while (teams.size() <= entry.index()) {
                teams.add(new ArrayList<>());
            }
            teams.set(entry.index(), new ArrayList<>(entry.uuids()));
            List<TeamEntry> metadata = ENTRIES.get(entry.target());
            while (metadata.size() <= entry.index()) {
                int placeholderIndex = metadata.size();
                metadata.add(new TeamEntry(entry.target(), placeholderIndex, placeholderIndex + 1, true, "", List.of()));
            }
            metadata.set(entry.index(), new TeamEntry(entry.target(), entry.index(), entry.number(), entry.autoJoin(), entry.name(), entry.uuids()));
        }
        CURRENT.replaceAll((target, index) -> {
            int preferred = target == CompanionTeamTarget.MOUNT ? preferredMountTeam : index;
            return Math.min(preferred, TEAMS.get(target).size() - 1);
        });
        serverRevision = incomingServerRevision;
        revision++;
    }

    public static int nextNonEmptyTeam(CompanionTeamTarget target, List<UUID> wheelUuids, double direction) {
        int index = peekNextNonEmptyTeam(target, wheelUuids, direction);
        if (index >= 0 && CURRENT.getOrDefault(target, 0) != index) {
            CURRENT.put(target, index);
            revision++;
        }
        return index;
    }

    public static int peekNextNonEmptyTeam(CompanionTeamTarget target, List<UUID> wheelUuids, double direction) {
        List<List<UUID>> teams = TEAMS.getOrDefault(target, List.of());
        if (teams.size() <= 1 || direction == 0.0) {
            return -1;
        }
        int current = CURRENT.getOrDefault(target, 0);
        int step = direction < 0.0 ? 1 : -1;
        for (int offset = 1; offset < teams.size(); ++offset) {
            int index = Math.floorMod(current + offset * step, teams.size());
            if (teams.get(index).stream().anyMatch(wheelUuids::contains)) {
                return index;
            }
        }
        return -1;
    }

    public static List<UUID> currentMembers(CompanionTeamTarget target) {
        List<List<UUID>> teams = TEAMS.getOrDefault(target, List.of());
        int index = CURRENT.getOrDefault(target, 0);
        return index >= 0 && index < teams.size() ? List.copyOf(teams.get(index)) : List.of();
    }

    public static int currentTeam(CompanionTeamTarget target) {
        return CURRENT.getOrDefault(target, 0);
    }

    public static List<TeamEntry> entries(CompanionTeamTarget target) {
        return List.copyOf(ENTRIES.getOrDefault(target, List.of()));
    }

    public static void selectTeam(CompanionTeamTarget target, int index) {
        List<List<UUID>> teams = TEAMS.getOrDefault(target, List.of());
        if (index >= 0 && index < teams.size() && CURRENT.getOrDefault(target, 0) != index) {
            CURRENT.put(target, index);
            revision++;
        }
    }

    public static void applyDefaultTeam(int index) {
        preferredMountTeam = Math.max(0, index);
        selectTeam(CompanionTeamTarget.MOUNT, index);
    }

    public static int appendTeam(CompanionTeamTarget target) {
        ArrayList<List<UUID>> teams = new ArrayList<>(TEAMS.getOrDefault(target, List.of()));
        ArrayList<TeamEntry> entries = new ArrayList<>(ENTRIES.getOrDefault(target, List.of()));
        int index = teams.size();
        int number = 1;
        while (containsTeamNumber(entries, number)) number++;
        teams.add(new ArrayList<>());
        entries.add(new TeamEntry(target, index, number, true, "", List.of()));
        TEAMS.put(target, teams);
        ENTRIES.put(target, entries);
        CURRENT.put(target, index);
        revision++;
        return index;
    }

    public static boolean toggleAutoJoin(CompanionTeamTarget target, int index) {
        ArrayList<TeamEntry> entries = new ArrayList<>(ENTRIES.getOrDefault(target, List.of()));
        if (index < 0 || index >= entries.size()) return false;
        TeamEntry old = entries.get(index);
        entries.set(index, new TeamEntry(target, index, old.number(), !old.autoJoin(), old.name(), old.uuids()));
        ENTRIES.put(target, entries);
        revision++;
        return true;
    }

    public static List<UUID> members(CompanionTeamTarget target, int index) {
        List<List<UUID>> teams = TEAMS.getOrDefault(target, List.of());
        return index >= 0 && index < teams.size() ? List.copyOf(teams.get(index)) : List.of();
    }

    public static boolean moveTeam(CompanionTeamTarget target, int from, int to) {
        List<List<UUID>> existingTeams = TEAMS.getOrDefault(target, List.of());
        List<TeamEntry> existingEntries = ENTRIES.getOrDefault(target, List.of());
        if (from < 0 || from >= existingTeams.size() || to < 0 || to >= existingTeams.size() || from == to) {
            return false;
        }
        ArrayList<List<UUID>> teams = new ArrayList<>(existingTeams);
        teams.add(to, teams.remove(from));
        TEAMS.put(target, teams);

        ArrayList<TeamEntry> entries = new ArrayList<>(existingEntries);
        if (from < entries.size() && to < entries.size()) {
            entries.add(to, entries.remove(from));
            for (int i = 0; i < entries.size(); ++i) {
                TeamEntry entry = entries.get(i);
                entries.set(i, new TeamEntry(target, i, entry.number(), entry.autoJoin(), entry.name(), List.copyOf(teams.get(i))));
            }
            ENTRIES.put(target, entries);
        }
        CURRENT.put(target, remapMovedIndex(CURRENT.getOrDefault(target, 0), from, to));
        revision++;
        return true;
    }

    public static boolean replaceMembers(CompanionTeamTarget target, int teamIndex, List<UUID> members) {
        List<List<UUID>> existingTeams = TEAMS.getOrDefault(target, List.of());
        if (teamIndex < 0 || teamIndex >= existingTeams.size() || members == null) {
            return false;
        }
        ArrayList<List<UUID>> teams = new ArrayList<>(existingTeams);
        List<UUID> copy = List.copyOf(members);
        teams.set(teamIndex, new ArrayList<>(copy));
        TEAMS.put(target, teams);

        ArrayList<TeamEntry> entries = new ArrayList<>(ENTRIES.getOrDefault(target, List.of()));
        if (teamIndex < entries.size()) {
            TeamEntry old = entries.get(teamIndex);
            entries.set(teamIndex, new TeamEntry(target, teamIndex, old.number(), old.autoJoin(), old.name(), copy));
            ENTRIES.put(target, entries);
        }
        revision++;
        return true;
    }

    public static long revision() {
        return revision;
    }

    public static long serverRevision() {
        return serverRevision;
    }

    public static int currentMemberIndex(CompanionTeamTarget target, UUID uuid) {
        return currentMembers(target).indexOf(uuid);
    }

    public static void reset() {
        preferredMountTeam = 0;
        TEAMS.clear();
        CURRENT.clear();
        ENTRIES.clear();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            TEAMS.put(target, new ArrayList<>(List.of(new ArrayList<>())));
            CURRENT.put(target, 0);
            ENTRIES.put(target, List.of(new TeamEntry(target, 0, 1, true, "", List.of())));
        }
        serverRevision = -1L;
        revision++;
    }

    private static int remapMovedIndex(int selected, int from, int to) {
        if (selected == from) return to;
        if (from < to && selected > from && selected <= to) return selected - 1;
        if (from > to && selected >= to && selected < from) return selected + 1;
        return selected;
    }

    private static boolean containsTeamNumber(List<TeamEntry> entries, int number) {
        return entries.stream().anyMatch(entry -> entry.number() == number);
    }

    public record TeamEntry(CompanionTeamTarget target, int index, int number, boolean autoJoin, String name, List<UUID> uuids) {
    }
}
