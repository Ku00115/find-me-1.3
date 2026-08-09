package com.kuzhi.findme.server.data;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.VehicleSeatOffset;
import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public class PlayerCompanionData {
    static final int SAFETY_SCHEMA_VERSION = 1;
    private static final int VAULT_LIMIT_DEFAULT = 30;
    private static final int BACKUP_LIMIT_DEFAULT = 6;
    public static final int TEAM_SIZE = FindMeUiSettings.TEAM_CAPACITY;

    // Core creature roster and wheel state.
    final Map<CompanionKind, List<UUID>> companions = new EnumMap<CompanionKind, List<UUID>>(CompanionKind.class);
    final Map<CompanionKind, List<UUID>> wheelSlots = new EnumMap<CompanionKind, List<UUID>>(CompanionKind.class);
    final Map<CompanionKind, Integer> activeIndexes = new EnumMap<CompanionKind, Integer>(CompanionKind.class);
    final Map<CompanionKind, UUID> previous = new EnumMap<CompanionKind, UUID>(CompanionKind.class);
    final Map<CompanionKind, UUID> deployed = new EnumMap<CompanionKind, UUID>(CompanionKind.class);
    final Map<CompanionKind, List<UUID>> deployedLists = new EnumMap<CompanionKind, List<UUID>>(CompanionKind.class);

    // Team and warehouse organization.
    final Map<CompanionTeamTarget, List<List<UUID>>> teams = new EnumMap<>(CompanionTeamTarget.class);
    final Map<CompanionTeamTarget, List<Boolean>> teamAutoJoin = new EnumMap<>(CompanionTeamTarget.class);
    final Map<CompanionTeamTarget, List<String>> teamNames = new EnumMap<>(CompanionTeamTarget.class);
    final Map<CompanionTeamTarget, List<Integer>> teamNumbers = new EnumMap<>(CompanionTeamTarget.class);

    // Death records and safety recovery.
    final List<UUID> deadCompanions = new ArrayList<UUID>();
    final Map<UUID, CompanionKind> deadKinds = new HashMap<UUID, CompanionKind>();
    final Map<UUID, DeadCompanionRecord> deadRecords = new HashMap<>();
    final Map<UUID, RecoveryCompanionRecord> recoveryRecords = new HashMap<>();
    final List<VaultEntry> vault = new ArrayList<VaultEntry>();
    final List<BackupEntry> backups = new ArrayList<BackupEntry>();

    // Live/stored entity state.
    final Map<UUID, SavedPosition> origins = new HashMap<UUID, SavedPosition>();
    final Map<UUID, SavedPosition> lastKnownPositions = new HashMap<UUID, SavedPosition>();
    final Map<UUID, SavedPosition> homePositions = new HashMap<UUID, SavedPosition>();
    final Map<UUID, SavedPosition> homeNestBlocks = new HashMap<UUID, SavedPosition>();
    final Map<UUID, UUID> homeHouseIds = new HashMap<UUID, UUID>();
    final Map<UUID, CompoundTag> storedEntities = new HashMap<UUID, CompoundTag>();
    final Map<UUID, String> displayNames = new HashMap<UUID, String>();
    final Map<UUID, CompanionLifecycleState> lifecycleStates = new HashMap<>();
    final Set<UUID> criticalCompanions = new HashSet<>();

    // Visual and pack/player customization.
    final Map<UUID, EnumMap<CompanionAnimationPurpose, CompanionAnimationStyle>> animationStyles = new HashMap<>();
    final Map<UUID, EnumMap<CompanionEffectPurpose, CompanionEffectStyle>> effectStyles = new HashMap<>();
    public static final int COMPANION_SPELL_SLOT_COUNT = 3;
    final Map<UUID, List<CompanionSpellBinding>> spellBindings = new HashMap<>();
    final List<CompoundTag> pendingSpellItemReturns = new ArrayList<>();
    float companionMagicMana = -1.0F;
    long companionMagicManaTick;
    float companionMagicCapacity = -1.0F;
    final Set<String> bindingCinematicSeenTypes = new HashSet<>();
    final Set<UUID> lifecycleChanges = new HashSet<>();
    FindMeUiSettings uiSettings = FindMeUiSettings.defaults();

    // Mount and vehicle classification.
    final Set<UUID> mountEligible = new HashSet<UUID>();
    final Set<UUID> vehicleMounts = new HashSet<UUID>();

    // Vehicle roster and seat data.
    final List<UUID> vehicles = new ArrayList<UUID>();
    final List<UUID> vehicleWheelSlots = new ArrayList<UUID>();
    final Map<UUID, VehicleSeatOffset> vehicleSeatOffsets = new HashMap<UUID, VehicleSeatOffset>();
    boolean vehicleWheelSlotsConfigured;
    UUID deployedVehicle;
    int vehicleActiveIndex;
    long mountReadyAt;
    long companionReadyAt;
    int safetySchemaVersion = SAFETY_SCHEMA_VERSION;

    public PlayerCompanionData() {
        for (CompanionKind kind : CompanionKind.values()) {
            this.companions.put(kind, new ArrayList());
            this.wheelSlots.put(kind, new ArrayList());
            this.deployedLists.put(kind, new ArrayList());
            this.activeIndexes.put(kind, 0);
        }
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            ArrayList<List<UUID>> targetTeams = new ArrayList<>();
            targetTeams.add(new ArrayList<>());
            this.teams.put(target, targetTeams);
            ArrayList<Boolean> targetAutoJoin = new ArrayList<>();
            targetAutoJoin.add(true);
            this.teamAutoJoin.put(target, targetAutoJoin);
            this.teamNames.put(target, new ArrayList<>(List.of("")));
            this.teamNumbers.put(target, new ArrayList<>(List.of(1)));
        }
    }

    public static PlayerCompanionData load(CompoundTag persistentData) {
        return PlayerCompanionDataCodec.load(persistentData);
    }

    public void save(CompoundTag persistentData) {
        PlayerCompanionDataCodec.save(this, persistentData);
    }

    public boolean add(CompanionKind kind, UUID uuid) {
        int previousMagicContributors = companionCreatureCount();
        List<UUID> list = this.companions.get((Object)kind);
        boolean clearedDead = this.deadCompanions.remove(uuid);
        clearedDead |= this.deadKinds.remove(uuid) != null;
        clearedDead |= this.deadRecords.remove(uuid) != null;
        clearedDead |= this.recoveryRecords.remove(uuid) != null;
        clearedDead |= this.criticalCompanions.remove(uuid);
        int previousTeamIndex = this.teamIndexOf(uuid);
        if (list.contains(uuid)) {
            return clearedDead;
        }
        CompanionKind other = PlayerCompanionData.other(kind);
        this.companions.get((Object)other).remove(uuid);
        this.clearDeployed(other, uuid);
        this.removeFromTeams(uuid);
        list.add(uuid);
        markLifecycleChanged(uuid);
        if (kind == CompanionKind.MOUNT) {
            this.markMountEligible(uuid);
        }
        int teamIndex;
        CompanionTeamTarget target = targetFor(kind);
        if (previousTeamIndex >= 0 && previousTeamIndex < this.teamCount(target)
                && this.teams.get(target).get(previousTeamIndex).size() < TEAM_SIZE) {
            this.teams.get(target).get(previousTeamIndex).add(uuid);
            teamIndex = previousTeamIndex;
        } else {
            teamIndex = this.autoAssignTeam(target, uuid);
        }
        this.activeIndexes.put(kind, list.size() - 1);
        reconcileCompanionMagicContributors(previousMagicContributors);
        return true;
    }

    public boolean transferCategory(UUID uuid, CompanionKind targetKind) {
        if (uuid == null || targetKind == null) {
            return false;
        }
        CompanionKind sourceKind = targetKind == CompanionKind.MOUNT
                ? CompanionKind.COMPANION : CompanionKind.MOUNT;
        List<UUID> source = this.companions.get(sourceKind);
        List<UUID> target = this.companions.get(targetKind);
        if (!source.contains(uuid) || target.contains(uuid)) {
            return false;
        }

        int sourceIndex = source.indexOf(uuid);
        UUID sourceActive = this.active(sourceKind).orElse(null);
        UUID targetActive = this.active(targetKind).orElse(null);
        int previousTeamIndex = this.teamIndexOf(uuid);
        int previousTeamMemberIndex = previousTeamIndex < 0 ? -1
                : this.teams.get(targetFor(sourceKind)).get(previousTeamIndex).indexOf(uuid);
        CompanionTeamTarget destinationTeam = targetFor(targetKind);

        source.remove(uuid);
        this.wheelSlots.get(sourceKind).remove(uuid);
        this.clearDeployed(sourceKind, uuid);
        if (uuid.equals(this.previous.get(sourceKind))) {
            this.previous.remove(sourceKind);
        }
        if (sourceActive != null && !sourceActive.equals(uuid)) {
            this.setActiveUuid(sourceKind, sourceActive);
        } else {
            this.activeIndexes.put(sourceKind, source.isEmpty() ? 0 : Math.min(sourceIndex, source.size() - 1));
        }

        this.removeFromTeams(uuid);
        target.add(Math.min(sourceIndex, target.size()), uuid);
        if (targetActive != null) {
            this.setActiveUuid(targetKind, targetActive);
        }
        if (targetKind == CompanionKind.MOUNT) {
            this.mountEligible.add(uuid);
        } else {
            this.mountEligible.remove(uuid);
            this.vehicleMounts.remove(uuid);
        }

        if (previousTeamIndex >= 0) {
            int assignedTeam = previousTeamIndex < this.teamCount(destinationTeam)
                    && this.teams.get(destinationTeam).get(previousTeamIndex).size() < TEAM_SIZE
                    ? previousTeamIndex : firstTeamWithRoom(destinationTeam);
            if (assignedTeam < 0) assignedTeam = this.createTeam(destinationTeam);
            List<UUID> team = this.teams.get(destinationTeam).get(assignedTeam);
            boolean destinationTeamApplied = this.teamMatchesAppliedWheel(destinationTeam, team);
            int insertAt = assignedTeam == previousTeamIndex
                    ? Math.min(Math.max(0, previousTeamMemberIndex), team.size()) : team.size();
            team.add(insertAt, uuid);
            if (destinationTeamApplied) {
                this.replaceWheelSlots(targetKind, team);
                if (targetActive != null) this.setActiveUuid(targetKind, targetActive);
            }
        }
        return true;
    }

    private int firstTeamWithRoom(CompanionTeamTarget target) {
        for (int index = 0; index < this.teamCount(target); index++) {
            if (this.teams.get(target).get(index).size() < TEAM_SIZE) return index;
        }
        return -1;
    }

    public boolean contains(UUID uuid) {
        for (List<UUID> list : this.companions.values()) {
            if (!list.contains(uuid)) continue;
            return true;
        }
        return false;
    }

    public boolean contains(CompanionKind kind, UUID uuid) {
        return this.companions.get((Object)kind).contains(uuid);
    }

    public Optional<UUID> active(CompanionKind kind) {
        List<UUID> list = this.companions.get((Object)kind);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        int index = Math.floorMod(this.activeIndexes.getOrDefault((Object)kind, 0), list.size());
        this.activeIndexes.put(kind, index);
        return Optional.of(list.get(index));
    }

    public List<UUID> list(CompanionKind kind) {
        return List.copyOf((Collection)this.companions.get((Object)kind));
    }

    public int activeIndex(CompanionKind kind) {
        List<UUID> list = this.companions.get((Object)kind);
        if (list.isEmpty()) {
            return -1;
        }
        int index = Math.floorMod(this.activeIndexes.getOrDefault((Object)kind, 0), list.size());
        this.activeIndexes.put(kind, index);
        return index;
    }

    public boolean setActiveIndex(CompanionKind kind, int index) {
        List<UUID> list = this.companions.get((Object)kind);
        if (index < 0 || index >= list.size()) {
            return false;
        }
        this.activeIndexes.put(kind, index);
        return true;
    }

    public boolean setActiveUuid(CompanionKind kind, UUID uuid) {
        List<UUID> list = this.companions.get((Object)kind);
        int index = list.indexOf(uuid);
        if (index < 0) {
            return false;
        }
        this.activeIndexes.put(kind, index);
        return true;
    }

    public Optional<UUID> uuidAt(CompanionKind kind, int index) {
        List<UUID> list = this.companions.get((Object)kind);
        if (index < 0 || index >= list.size()) {
            return Optional.empty();
        }
        return Optional.of(list.get(index));
    }

    public Optional<UUID> previous(CompanionKind kind) {
        return Optional.ofNullable(this.previous.get((Object)kind));
    }

    public void rememberPrevious(CompanionKind kind, UUID uuid) {
        this.previous.put(kind, uuid);
    }

    public Optional<UUID> deployed(CompanionKind kind) {
        List<UUID> list = this.deployedLists.get((Object)kind);
        if (list != null && !list.isEmpty()) {
            return Optional.of(list.get(0));
        }
        return Optional.ofNullable(this.deployed.get((Object)kind));
    }

    public void setDeployed(CompanionKind kind, UUID uuid) {
        List<UUID> list = this.deployedLists.get((Object)kind);
        if (kind == CompanionKind.MOUNT) {
            list.clear();
            list.add(uuid);
            this.deployed.put(kind, uuid);
            return;
        }
        list.remove(uuid);
        list.add(uuid);
        this.deployed.put(kind, list.get(0));
    }

    public void clearDeployed(CompanionKind kind) {
        this.deployedLists.get((Object)kind).clear();
        this.deployed.remove((Object)kind);
    }

    public void clearDeployed(CompanionKind kind, UUID uuid) {
        UUID first;
        List<UUID> list = this.deployedLists.get((Object)kind);
        list.remove(uuid);
        UUID uUID = first = list.isEmpty() ? null : list.get(0);
        if (first == null) {
            this.deployed.remove((Object)kind);
        } else {
            this.deployed.put(kind, first);
        }
    }

    public boolean isDeployed(CompanionKind kind, UUID uuid) {
        return this.deployedLists.get((Object)kind).contains(uuid);
    }

    public List<UUID> deployedList(CompanionKind kind) {
        return List.copyOf((Collection)this.deployedLists.get((Object)kind));
    }

    public void cycle(CompanionKind kind, int delta) {
        List<UUID> list = this.companions.get((Object)kind);
        if (!list.isEmpty()) {
            this.activeIndexes.put(kind, Math.floorMod(this.activeIndexes.getOrDefault((Object)kind, 0) + delta, list.size()));
        }
    }

    public void remove(UUID uuid) {
        if (uuid != null && (contains(uuid) || this.deadCompanions.contains(uuid))) markLifecycleChanged(uuid);
        int previousMagicContributors = companionCreatureCount();
        this.vaultSnapshot(uuid, this.kindOf(uuid).orElse(CompanionKind.COMPANION), "remove", VAULT_LIMIT_DEFAULT);
        for (List<UUID> list : this.companions.values()) {
            list.remove(uuid);
        }
        this.origins.remove(uuid);
        this.lastKnownPositions.remove(uuid);
        this.homePositions.remove(uuid);
        this.homeNestBlocks.remove(uuid);
        this.homeHouseIds.remove(uuid);
        this.storedEntities.remove(uuid);
        this.displayNames.remove(uuid);
        this.lifecycleStates.remove(uuid);
        this.criticalCompanions.remove(uuid);
        this.animationStyles.remove(uuid);
        this.effectStyles.remove(uuid);
        queueSpellItemReturns(uuid);
        for (List<UUID> slots : this.wheelSlots.values()) {
            slots.remove(uuid);
        }
        this.deployed.values().removeIf(uuid::equals);
        for (List<UUID> list : this.deployedLists.values()) {
            list.remove(uuid);
        }
        this.mountEligible.remove(uuid);
        this.vehicleMounts.remove(uuid);
        this.vehicles.remove(uuid);
        this.vehicleWheelSlots.remove(uuid);
        this.vehicleSeatOffsets.remove(uuid);
        this.removeFromTeams(uuid);
        if (uuid.equals(this.deployedVehicle)) {
            this.deployedVehicle = null;
        }
        this.deadCompanions.remove(uuid);
        this.deadKinds.remove(uuid);
        this.deadRecords.remove(uuid);
        this.recoveryRecords.remove(uuid);
        reconcileCompanionMagicContributors(previousMagicContributors);
    }

    public void replaceUuid(UUID oldUuid, UUID newUuid) {
        if (oldUuid == null || newUuid == null || oldUuid.equals(newUuid)) {
            return;
        }
        for (List<UUID> list : this.companions.values()) {
            replaceInList(list, oldUuid, newUuid);
        }
        for (List<UUID> list : this.wheelSlots.values()) {
            replaceInList(list, oldUuid, newUuid);
        }
        for (List<UUID> list : this.deployedLists.values()) {
            replaceInList(list, oldUuid, newUuid);
        }
        for (List<List<UUID>> targetTeams : this.teams.values()) {
            for (List<UUID> team : targetTeams) {
                replaceInList(team, oldUuid, newUuid);
            }
        }
        for (Map.Entry<CompanionKind, UUID> entry : new ArrayList<>(this.previous.entrySet())) {
            if (oldUuid.equals(entry.getValue())) {
                this.previous.put(entry.getKey(), newUuid);
            }
        }
        for (Map.Entry<CompanionKind, UUID> entry : new ArrayList<>(this.deployed.entrySet())) {
            if (oldUuid.equals(entry.getValue())) {
                this.deployed.put(entry.getKey(), newUuid);
            }
        }
        moveMapEntry(this.origins, oldUuid, newUuid);
        moveMapEntry(this.lastKnownPositions, oldUuid, newUuid);
        moveMapEntry(this.homePositions, oldUuid, newUuid);
        moveMapEntry(this.homeNestBlocks, oldUuid, newUuid);
        moveMapEntry(this.homeHouseIds, oldUuid, newUuid);
        moveMapEntry(this.storedEntities, oldUuid, newUuid);
        moveMapEntry(this.displayNames, oldUuid, newUuid);
        moveMapEntry(this.lifecycleStates, oldUuid, newUuid);
        if (this.criticalCompanions.remove(oldUuid)) {
            this.criticalCompanions.add(newUuid);
        }
        moveMapEntry(this.animationStyles, oldUuid, newUuid);
        moveMapEntry(this.effectStyles, oldUuid, newUuid);
        moveMapEntry(this.spellBindings, oldUuid, newUuid);
        replaceInList(this.deadCompanions, oldUuid, newUuid);
        CompanionKind deadKind = this.deadKinds.remove(oldUuid);
        if (deadKind != null) {
            this.deadKinds.put(newUuid, deadKind);
        }
        DeadCompanionRecord deadRecord = this.deadRecords.remove(oldUuid);
        if (deadRecord != null) {
            this.deadRecords.put(newUuid, new DeadCompanionRecord(deadRecord.recordId(), newUuid, deadRecord.customName(), deadRecord.entityType(),
                    deadRecord.previousKind(), deadRecord.previousTeamIndex(), deadRecord.deathTime(), deadRecord.worldDay(), deadRecord.dimension(),
                    deadRecord.x(), deadRecord.y(), deadRecord.z(), deadRecord.deathCause(), deadRecord.recoverable(), deadRecord.recoveryRequirements()));
        }
        RecoveryCompanionRecord recoveryRecord = this.recoveryRecords.remove(oldUuid);
        if (recoveryRecord != null) {
            this.recoveryRecords.put(newUuid, new RecoveryCompanionRecord(recoveryRecord.recordId(), newUuid,
                    recoveryRecord.customName(), recoveryRecord.entityType(), recoveryRecord.kind(),
                    recoveryRecord.previousTeamIndex(), recoveryRecord.detectedAt(), recoveryRecord.lastCheckedAt(),
                    recoveryRecord.dimension(), recoveryRecord.x(), recoveryRecord.y(), recoveryRecord.z(),
                    recoveryRecord.reason(), recoveryRecord.detail(), recoveryRecord.sourceState()));
        }
        if (this.mountEligible.remove(oldUuid)) {
            this.mountEligible.add(newUuid);
        }
        if (this.vehicleMounts.remove(oldUuid)) {
            this.vehicleMounts.add(newUuid);
        }
        replaceInList(this.vehicles, oldUuid, newUuid);
        replaceInList(this.vehicleWheelSlots, oldUuid, newUuid);
        moveMapEntry(this.vehicleSeatOffsets, oldUuid, newUuid);
        if (oldUuid.equals(this.deployedVehicle)) {
            this.deployedVehicle = newUuid;
        }
    }

    public List<UUID> team(CompanionTeamTarget target, int index) {
        if (target == null || index < 0 || index >= this.teamCount(target)) {
            return List.of();
        }
        return List.copyOf(this.teams.get(target).get(index));
    }

    public int teamCount(CompanionTeamTarget target) {
        if (target == null) {
            return 0;
        }
        return this.teams.get(target).size();
    }

    public int createTeam(CompanionTeamTarget target) {
        if (target == null) {
            return -1;
        }
        List<List<UUID>> targetTeams = this.teams.get(target);
        targetTeams.add(new ArrayList<>());
        this.teamAutoJoin.get(target).add(true);
        this.teamNames.get(target).add("");
        Set<Integer> usedNumbers = new HashSet<>(this.teamNumbers.get(target));
        int number = 1;
        while (usedNumbers.contains(number)) number++;
        this.teamNumbers.get(target).add(number);
        return targetTeams.size() - 1;
    }

    public int teamNumber(CompanionTeamTarget target, int index) {
        return target != null && index >= 0 && index < teamCount(target)
                ? this.teamNumbers.get(target).get(index) : index + 1;
    }

    public String teamName(CompanionTeamTarget target, int index) {
        return target != null && index >= 0 && index < teamCount(target) ? teamNames.get(target).get(index) : "";
    }

    public boolean setTeamName(CompanionTeamTarget target, int index, String name) {
        if (target == null || index < 0 || index >= teamCount(target)) return false;
        String value = name == null ? "" : name.trim();
        teamNames.get(target).set(index, value.length() > 48 ? value.substring(0, 48) : value);
        return true;
    }

    public boolean reorderTeam(CompanionTeamTarget target, int from, int to) {
        if (target == null || from < 0 || from >= teamCount(target) || to < 0 || to >= teamCount(target) || from == to) return false;
        teams.get(target).add(to, teams.get(target).remove(from));
        teamAutoJoin.get(target).add(to, teamAutoJoin.get(target).remove(from));
        teamNames.get(target).add(to, teamNames.get(target).remove(from));
        teamNumbers.get(target).add(to, teamNumbers.get(target).remove(from));
        return true;
    }

    public boolean teamAutoJoin(CompanionTeamTarget target, int index) {
        return target != null && index >= 0 && index < this.teamCount(target) && this.teamAutoJoin.get(target).get(index);
    }

    public boolean setTeamAutoJoin(CompanionTeamTarget target, int index, boolean enabled) {
        if (target == null || index < 0 || index >= this.teamCount(target)) {
            return false;
        }
        this.teamAutoJoin.get(target).set(index, enabled);
        return true;
    }

    public boolean deleteTeam(CompanionTeamTarget target, int index) {
        if (target == null || index < 0 || index >= this.teamCount(target) || this.teamCount(target) <= 1) {
            return false;
        }
        this.teams.get(target).remove(index);
        this.teamAutoJoin.get(target).remove(index);
        this.teamNames.get(target).remove(index);
        this.teamNumbers.get(target).remove(index);
        return true;
    }

    void ensureTeamCount(CompanionTeamTarget target, int count) {
        if (target == null) {
            return;
        }
        List<List<UUID>> targetTeams = this.teams.get(target);
        int required = Math.max(1, count);
        while (targetTeams.size() < required) {
            targetTeams.add(new ArrayList<>());
            this.teamAutoJoin.get(target).add(true);
            this.teamNames.get(target).add("");
            this.teamNumbers.get(target).add(nextAvailableTeamNumber(target));
        }
    }

    void setTeamNumbers(CompanionTeamTarget target, int[] numbers) {
        if (target == null || numbers == null) return;
        List<Integer> targetNumbers = this.teamNumbers.get(target);
        Set<Integer> used = new HashSet<>();
        for (int index = 0; index < targetNumbers.size(); index++) {
            int candidate = index < numbers.length ? numbers[index] : -1;
            if (candidate <= 0 || !used.add(candidate)) {
                candidate = 1;
                while (used.contains(candidate)) candidate++;
                used.add(candidate);
            }
            targetNumbers.set(index, candidate);
        }
    }

    private int nextAvailableTeamNumber(CompanionTeamTarget target) {
        Set<Integer> used = new HashSet<>(this.teamNumbers.get(target));
        int number = 1;
        while (used.contains(number)) number++;
        return number;
    }

    void resetTeams() {
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            List<List<UUID>> targetTeams = this.teams.get(target);
            targetTeams.clear();
            targetTeams.add(new ArrayList<>());
            List<Boolean> targetAutoJoin = this.teamAutoJoin.get(target);
            targetAutoJoin.clear();
            targetAutoJoin.add(true);
            List<String> targetNames = this.teamNames.get(target);
            targetNames.clear();
            targetNames.add("");
            List<Integer> targetNumbers = this.teamNumbers.get(target);
            targetNumbers.clear();
            targetNumbers.add(1);
        }
    }

    public boolean setTeam(CompanionTeamTarget target, int index, List<UUID> uuids) {
        if (target == null || index < 0 || index >= this.teamCount(target) || uuids == null) {
            return false;
        }
        List<UUID> team = this.teams.get(target).get(index);
        boolean appliedToWheel = this.teamMatchesAppliedWheel(target, team);
        UUID activeUuid = switch (target) {
            case MOUNT -> this.active(CompanionKind.MOUNT).orElse(null);
            case COMPANION -> this.active(CompanionKind.COMPANION).orElse(null);
            case VEHICLE -> this.activeVehicle().orElse(null);
        };
        int available = TEAM_SIZE;
        team.clear();
        for (UUID uuid : uuids) {
            if (uuid == null || team.contains(uuid) || !this.allowedInTeam(target, uuid)) continue;
            this.removeFromTeams(uuid);
            if (team.size() >= available) break;
            team.add(uuid);
        }
        if (appliedToWheel) this.replaceAppliedWheel(target, team, activeUuid);
        return true;
    }

    private boolean teamMatchesAppliedWheel(CompanionTeamTarget target, List<UUID> team) {
        return switch (target) {
            case MOUNT -> this.wheelOrder(CompanionKind.MOUNT).equals(team);
            case COMPANION -> this.wheelOrder(CompanionKind.COMPANION).equals(team);
            case VEHICLE -> this.vehicleWheelOrder().equals(team);
        };
    }

    private void replaceAppliedWheel(CompanionTeamTarget target, List<UUID> team, UUID activeUuid) {
        switch (target) {
            case MOUNT -> {
                this.replaceWheelSlots(CompanionKind.MOUNT, team);
                if (activeUuid != null) this.setActiveUuid(CompanionKind.MOUNT, activeUuid);
            }
            case COMPANION -> {
                this.replaceWheelSlots(CompanionKind.COMPANION, team);
                if (activeUuid != null) this.setActiveUuid(CompanionKind.COMPANION, activeUuid);
            }
            case VEHICLE -> {
                this.vehicleWheelSlotsConfigured = true;
                this.vehicleWheelSlots.clear();
                for (UUID uuid : team) {
                    if (uuid != null && this.vehicles.contains(uuid) && !this.vehicleWheelSlots.contains(uuid)) {
                        this.vehicleWheelSlots.add(uuid);
                    }
                }
                if (activeUuid != null) this.setActiveVehicleUuid(activeUuid);
            }
        }
    }

    public int autoAssignTeam(CompanionTeamTarget target, UUID uuid) {
        if (target == null || uuid == null || !this.allowedInTeam(target, uuid) || !this.uiSettings.autoJoinTeams()) {
            return -1;
        }
        int assigned = this.teamIndexOf(uuid);
        if (assigned >= 0) {
            return assigned;
        }
        List<List<UUID>> targetTeams = this.teams.get(target);
        for (int candidate = 0; candidate < targetTeams.size(); ++candidate) {
            if (this.teamAutoJoin(target, candidate) && targetTeams.get(candidate).size() < TEAM_SIZE) {
                targetTeams.get(candidate).add(uuid);
                return candidate;
            }
        }
        if (!this.uiSettings.autoCreateTeams()) {
            return -1;
        }
        int candidate = this.createTeam(target);
        targetTeams.get(candidate).add(uuid);
        return candidate;
    }

    public int teamIndexOf(UUID uuid) {
        if (uuid == null) {
            return -1;
        }
        int count = 0;
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            count = Math.max(count, this.teamCount(target));
        }
        for (int i = 0; i < count; ++i) {
            for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
                if (i < this.teamCount(target) && this.teams.get(target).get(i).contains(uuid)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int teamMemberCount(int index) {
        if (index < 0) {
            return 0;
        }
        int count = 0;
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            if (index < this.teamCount(target)) {
                count += this.teams.get(target).get(index).size();
            }
        }
        return count;
    }

    public List<UUID> combinedTeam(int index) {
        if (index < 0) {
            return List.of();
        }
        ArrayList<UUID> members = new ArrayList<>();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            if (index >= this.teamCount(target)) {
                continue;
            }
            for (UUID uuid : this.teams.get(target).get(index)) {
                if (!members.contains(uuid)) {
                    members.add(uuid);
                }
            }
        }
        return List.copyOf(members);
    }

    public boolean removeTeamMember(UUID uuid) {
        int previous = this.teamIndexOf(uuid);
        this.removeFromTeams(uuid);
        return previous >= 0;
    }

    public FindMeUiSettings uiSettings() {
        return this.uiSettings;
    }

    public void setUiSettings(FindMeUiSettings settings) {
        this.uiSettings = settings == null ? FindMeUiSettings.defaults() : settings.normalized();
    }

    public boolean applyTeamToWheel(CompanionTeamTarget target, int index) {
        if (target == null || index < 0 || index >= this.teamCount(target)) {
            return false;
        }
        List<UUID> team = this.team(target, index);
        switch (target) {
            case MOUNT -> {
                this.replaceWheelSlots(CompanionKind.MOUNT, team);
                this.setFirstActive(CompanionKind.MOUNT, team);
            }
            case COMPANION -> {
                this.replaceWheelSlots(CompanionKind.COMPANION, team);
                this.setFirstActive(CompanionKind.COMPANION, team);
            }
            case VEHICLE -> {
                this.vehicleWheelSlotsConfigured = true;
                this.vehicleWheelSlots.clear();
                for (UUID uuid : team) {
                    if (uuid != null && this.vehicles.contains(uuid) && !this.vehicleWheelSlots.contains(uuid)) {
                        this.vehicleWheelSlots.add(uuid);
                    }
                }
                this.vehicleActiveIndex = 0;
            }
        }
        return true;
    }

    public boolean allowedInTeam(CompanionTeamTarget target, UUID uuid) {
        if (isRecovery(uuid)) return false;
        return switch (target) {
            case MOUNT -> this.companions.get(CompanionKind.MOUNT).contains(uuid);
            case COMPANION -> this.companions.get(CompanionKind.COMPANION).contains(uuid);
            case VEHICLE -> this.vehicles.contains(uuid);
        };
    }

    private void replaceWheelSlots(CompanionKind kind, List<UUID> uuids) {
        List<UUID> slots = this.wheelSlots.get(kind);
        List<UUID> source = this.companions.get(kind);
        slots.clear();
        for (UUID uuid : uuids) {
            if (uuid != null && source.contains(uuid) && !slots.contains(uuid)) {
                slots.add(uuid);
            }
        }
    }

    private void setFirstActive(CompanionKind kind, List<UUID> uuids) {
        List<UUID> source = this.companions.get(kind);
        for (UUID uuid : uuids) {
            int index = source.indexOf(uuid);
            if (index >= 0) {
                this.activeIndexes.put(kind, index);
                return;
            }
        }
        this.activeIndexes.put(kind, 0);
    }

    private void removeFromTeams(UUID uuid) {
        for (List<List<UUID>> targetTeams : this.teams.values()) {
            for (List<UUID> team : targetTeams) {
                team.remove(uuid);
            }
        }
    }

    private static void replaceInList(List<UUID> list, UUID oldUuid, UUID newUuid) {
        for (int i = 0; i < list.size(); ++i) {
            if (oldUuid.equals(list.get(i))) {
                list.set(i, newUuid);
            }
        }
    }

    private static <T> void moveMapEntry(Map<UUID, T> map, UUID oldUuid, UUID newUuid) {
        if (!map.containsKey(oldUuid)) {
            return;
        }
        T value = map.remove(oldUuid);
        map.put(newUuid, value);
    }

    public Optional<CompanionKind> kindOf(UUID uuid) {
        for (CompanionKind kind : CompanionKind.values()) {
            if (this.companions.get((Object)kind).contains(uuid)) {
                return Optional.of(kind);
            }
        }
        return Optional.ofNullable(this.deadKinds.get(uuid));
    }

    public boolean markDead(UUID uuid, CompanionKind kind) {
        return PlayerCompanionDeadService.markDead(this, uuid, kind, VAULT_LIMIT_DEFAULT);
    }

    public List<UUID> deadList() {
        return PlayerCompanionDeadService.deadList(this);
    }

    public Optional<UUID> deadUuidAt(int index) {
        return PlayerCompanionDeadService.deadUuidAt(this, index);
    }

    public Optional<DeadCompanionRecord> deadRecord(UUID uuid) {
        return Optional.ofNullable(this.deadRecords.get(uuid));
    }

    public List<DeadCompanionRecord> deadRecords() {
        ArrayList<DeadCompanionRecord> records = new ArrayList<>();
        for (UUID uuid : this.deadCompanions) {
            DeadCompanionRecord record = this.deadRecords.get(uuid);
            if (record != null) {
                records.add(record);
            }
        }
        return List.copyOf(records);
    }

    public void putDeadRecord(DeadCompanionRecord record) {
        if (record != null) {
            this.deadRecords.put(record.sourceEntityId(), record);
        }
    }

    public boolean removeDeadAt(int index) {
        return PlayerCompanionDeadService.removeDeadAt(this, index, VAULT_LIMIT_DEFAULT);
    }

    public Optional<RecoveryCompanionRecord> recoveryRecord(UUID uuid) {
        return Optional.ofNullable(this.recoveryRecords.get(uuid));
    }

    public boolean isRecovery(UUID uuid) {
        return uuid != null && this.recoveryRecords.containsKey(uuid)
                && lifecycleState(uuid) == CompanionLifecycleState.RECOVERY;
    }

    public List<RecoveryCompanionRecord> recoveryRecords() {
        ArrayList<RecoveryCompanionRecord> records = new ArrayList<>();
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : this.companions.get(kind)) {
                RecoveryCompanionRecord record = this.recoveryRecords.get(uuid);
                if (record != null && lifecycleState(uuid) == CompanionLifecycleState.RECOVERY) records.add(record);
            }
        }
        return List.copyOf(records);
    }

    public List<UUID> recoveryList() {
        return recoveryRecords().stream().map(RecoveryCompanionRecord::sourceEntityId).toList();
    }

    public void putRecoveryRecord(RecoveryCompanionRecord record) {
        if (record != null) this.recoveryRecords.put(record.sourceEntityId(), record);
    }

    public void clearRecoveryRecord(UUID uuid) {
        if (uuid != null) this.recoveryRecords.remove(uuid);
    }

    void purgeDeadFromLiveLists() {
        PlayerCompanionDeadService.purgeDeadFromLiveLists(this);
    }

    public void markMountEligible(UUID uuid) {
        this.mountEligible.add(uuid);
    }

    public boolean isMountEligible(UUID uuid) {
        return this.mountEligible.contains(uuid);
    }

    public void markVehicleMount(UUID uuid) {
        this.vehicleMounts.add(uuid);
        this.markMountEligible(uuid);
    }

    public boolean isVehicleMount(UUID uuid) {
        return this.vehicleMounts.contains(uuid);
    }

    public CompanionEffectStyle effectStyle(UUID uuid, CompanionEffectPurpose purpose) {
        return this.effectStyle(uuid, purpose, "");
    }

    public CompanionEffectStyle effectStyle(UUID uuid, CompanionEffectPurpose purpose, String entityType) {
        EnumMap<CompanionEffectPurpose, CompanionEffectStyle> styles = this.effectStyles.get(uuid);
        if (styles != null && styles.containsKey(purpose)) {
            return styles.get(purpose).visualStyle();
        }
        return PackAnimationPresetService.style(entityType, purpose);
    }

    public void setEffectStyle(UUID uuid, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        if (style != null && style.isLegacyAnimationValue()) {
            setAnimationStyle(uuid, purpose == CompanionEffectPurpose.STORAGE
                    ? CompanionAnimationPurpose.STORAGE
                    : purpose == CompanionEffectPurpose.RESCUE
                    ? CompanionAnimationPurpose.RESCUE
                    : CompanionAnimationPurpose.SUMMON,
                    style == CompanionEffectStyle.GROUND_SINK
                            ? CompanionAnimationStyle.GROUND_SINK
                            : CompanionAnimationStyle.GROUND_EMERGE);
            style = CompanionEffectStyle.MAGIC_CIRCLE;
        }
        EnumMap<CompanionEffectPurpose, CompanionEffectStyle> styles = this.effectStyles.computeIfAbsent(uuid, ignored -> new EnumMap<>(CompanionEffectPurpose.class));
        if (style == null) styles.remove(purpose); else styles.put(purpose, style);
        if (styles.isEmpty()) this.effectStyles.remove(uuid);
    }

    public CompanionAnimationStyle animationStyle(UUID uuid, CompanionAnimationPurpose purpose) {
        return this.animationStyle(uuid, purpose, "");
    }

    public CompanionAnimationStyle animationStyle(UUID uuid, CompanionAnimationPurpose purpose, String entityType) {
        EnumMap<CompanionAnimationPurpose, CompanionAnimationStyle> styles = this.animationStyles.get(uuid);
        if (styles != null && styles.containsKey(purpose)) {
            return styles.get(purpose);
        }
        return PackAnimationPresetService.animationStyle(entityType, purpose);
    }

    public void setAnimationStyle(UUID uuid, CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
        EnumMap<CompanionAnimationPurpose, CompanionAnimationStyle> styles = this.animationStyles.computeIfAbsent(uuid,
                ignored -> new EnumMap<>(CompanionAnimationPurpose.class));
        if (style == null) styles.remove(purpose); else styles.put(purpose, style);
        if (styles.isEmpty()) this.animationStyles.remove(uuid);
    }

    public boolean hasSeenBindingCinematic(String entityType) {
        return entityType != null && this.bindingCinematicSeenTypes.contains(entityType);
    }

    public List<CompanionSpellBinding> spellBindings(UUID uuid) {
        List<CompanionSpellBinding> bindings = uuid == null ? null : this.spellBindings.get(uuid);
        if (bindings == null || bindings.isEmpty()) return List.of();
        return java.util.Collections.unmodifiableList(new ArrayList<>(bindings));
    }

    public int companionMagicContributorCount(int contributionLimit) {
        int count = companionCreatureCount();
        return contributionLimit <= 0 ? count : Math.min(count, contributionLimit);
    }

    int companionCreatureCount() {
        return this.companions.get(CompanionKind.COMPANION).size()
                + this.companions.get(CompanionKind.MOUNT).size();
    }

    void reconcileCompanionMagicContributors(int previousCount) {
        int limit = Config.companionMagicContributionLimit;
        float oldCapacity = (limit <= 0 ? previousCount : Math.min(previousCount, limit)) * 100.0F;
        float newCapacity = companionMagicContributorCount(limit) * 100.0F;
        if (this.companionMagicMana < 0.0F || this.companionMagicCapacity < 0.0F) {
            this.companionMagicMana = newCapacity;
        } else {
            float mana = Math.min(oldCapacity, this.companionMagicMana);
            if (newCapacity > oldCapacity) mana += newCapacity - oldCapacity;
            this.companionMagicMana = Math.min(newCapacity, mana);
        }
        this.companionMagicCapacity = newCapacity;
    }

    public float companionMagicMana() {
        return this.companionMagicMana;
    }

    public long companionMagicManaTick() {
        return this.companionMagicManaTick;
    }

    public float companionMagicCapacity() {
        return this.companionMagicCapacity;
    }

    public void setCompanionMagicMana(float mana, long tick) {
        this.companionMagicMana = Float.isFinite(mana) ? Math.max(0.0F, mana) : -1.0F;
        this.companionMagicManaTick = Math.max(0L, tick);
    }

    public void setCompanionMagicMana(float mana, long tick, float capacity) {
        setCompanionMagicMana(mana, tick);
        this.companionMagicCapacity = Float.isFinite(capacity) ? Math.max(0.0F, capacity) : -1.0F;
    }

    public Optional<CompanionSpellBinding> spellBinding(UUID uuid, int slot) {
        List<CompanionSpellBinding> bindings = uuid == null ? null : this.spellBindings.get(uuid);
        return slot < 0 || slot >= COMPANION_SPELL_SLOT_COUNT || bindings == null || slot >= bindings.size()
                ? Optional.empty() : Optional.ofNullable(bindings.get(slot));
    }

    public void setSpellBinding(UUID uuid, int slot, CompanionSpellBinding binding) {
        if (uuid == null || slot < 0 || slot >= COMPANION_SPELL_SLOT_COUNT) return;
        List<CompanionSpellBinding> bindings = new ArrayList<>(this.spellBindings.getOrDefault(uuid, List.of()));
        while (bindings.size() < COMPANION_SPELL_SLOT_COUNT) bindings.add(null);
        bindings.set(slot, binding);
        if (bindings.stream().allMatch(java.util.Objects::isNull)) this.spellBindings.remove(uuid);
        else this.spellBindings.put(uuid, bindings);
    }

    public Optional<CompanionSpellBinding> clearSpellBinding(UUID uuid, int slot) {
        Optional<CompanionSpellBinding> previous = spellBinding(uuid, slot);
        setSpellBinding(uuid, slot, null);
        return previous;
    }

    public void queueSpellItemReturns(UUID uuid) {
        List<CompanionSpellBinding> bindings = uuid == null ? null : this.spellBindings.remove(uuid);
        if (bindings == null) return;
        for (CompanionSpellBinding binding : bindings) {
            if (binding == null || binding.itemTag().isEmpty()) continue;
            CompoundTag item = binding.itemTag();
            item.putByte("Count", (byte)1);
            item.putInt("count", 1);
            this.pendingSpellItemReturns.add(item);
        }
    }

    public boolean hasPendingSpellItemReturns() {
        return !this.pendingSpellItemReturns.isEmpty();
    }

    public List<CompoundTag> drainPendingSpellItemReturns() {
        List<CompoundTag> result = this.pendingSpellItemReturns.stream().map(CompoundTag::copy).toList();
        this.pendingSpellItemReturns.clear();
        return result;
    }

    public boolean markBindingCinematicSeen(String entityType) {
        return entityType != null && !entityType.isBlank() && this.bindingCinematicSeenTypes.add(entityType);
    }

    public void resetBindingCinematicHistory() {
        this.bindingCinematicSeenTypes.clear();
    }

    public boolean addVehicle(UUID uuid) {
        int previousMagicContributors = companionCreatureCount();
        boolean changed = false;
        for (List<UUID> list : this.companions.values()) {
            changed |= list.remove(uuid);
        }
        for (List<UUID> slots : this.wheelSlots.values()) {
            slots.remove(uuid);
        }
        this.removeFromTeams(uuid);
        this.deadCompanions.remove(uuid);
        this.deadKinds.remove(uuid);
        this.vehicleMounts.remove(uuid);
        queueSpellItemReturns(uuid);
        if (!this.vehicles.contains(uuid)) {
            this.vehicles.add(uuid);
            changed = true;
        }
        int teamIndex = this.autoAssignTeam(CompanionTeamTarget.VEHICLE, uuid);
        if (teamIndex < 0) {
            this.vehicleWheelSlotsConfigured = true;
            if (!this.vehicleWheelSlots.contains(uuid)) {
                this.vehicleWheelSlots.add(uuid);
                changed = true;
            }
        }
        reconcileCompanionMagicContributors(previousMagicContributors);
        return changed;
    }

    public boolean containsVehicle(UUID uuid) {
        return this.vehicles.contains(uuid);
    }

    public Optional<VehicleSeatOffset> vehicleSeatOffset(UUID uuid) {
        return Optional.ofNullable(this.vehicleSeatOffsets.get(uuid));
    }

    public void setVehicleSeatOffset(UUID uuid, VehicleSeatOffset offset) {
        this.vehicleSeatOffsets.put(uuid, offset);
    }

    public boolean clearVehicleSeatOffset(UUID uuid) {
        return this.vehicleSeatOffsets.remove(uuid) != null;
    }

    public List<UUID> vehicleList() {
        return List.copyOf(this.vehicles);
    }

    public List<UUID> vehicleWheelOrder() {
        this.vehicleWheelSlotsConfigured = true;
        this.vehicleWheelSlots.removeIf(uuid -> !this.vehicles.contains(uuid));
        return List.copyOf(this.vehicleWheelSlots);
    }

    public List<UUID> vehicleWarehouseOrder() {
        this.vehicleWheelOrder();
        ArrayList<UUID> warehouse = new ArrayList<UUID>();
        for (UUID uuid : this.vehicles) {
            if (!this.vehicleWheelSlots.contains(uuid)) {
                warehouse.add(uuid);
            }
        }
        return List.copyOf(warehouse);
    }

    public int activeVehicleIndex() {
        if (this.vehicleWheelSlots.isEmpty()) {
            return -1;
        }
        this.vehicleActiveIndex = Math.floorMod(this.vehicleActiveIndex, this.vehicleWheelSlots.size());
        return this.vehicleActiveIndex;
    }

    public Optional<UUID> activeVehicle() {
        int index = this.activeVehicleIndex();
        return index < 0 ? Optional.empty() : Optional.of(this.vehicleWheelSlots.get(index));
    }

    public Optional<UUID> vehicleWheelUuidAt(int index) {
        this.vehicleWheelOrder();
        if (index < 0 || index >= this.vehicleWheelSlots.size()) {
            return Optional.empty();
        }
        return Optional.of(this.vehicleWheelSlots.get(index));
    }

    public boolean setActiveVehicleIndex(int index) {
        this.vehicleWheelOrder();
        if (index < 0 || index >= this.vehicleWheelSlots.size()) {
            return false;
        }
        this.vehicleActiveIndex = index;
        return true;
    }

    public boolean setActiveVehicleUuid(UUID uuid) {
        this.vehicleWheelOrder();
        int index = this.vehicleWheelSlots.indexOf(uuid);
        if (index < 0) {
            return false;
        }
        this.vehicleActiveIndex = index;
        return true;
    }

    public void setDeployedVehicle(UUID uuid) {
        this.deployedVehicle = uuid;
    }

    public Optional<UUID> deployedVehicle() {
        return Optional.ofNullable(this.deployedVehicle);
    }

    public CompanionLifecycleState lifecycleState(UUID uuid) {
        if (uuid == null) {
            return CompanionLifecycleState.RECOVERY;
        }
        CompanionLifecycleState explicit = this.lifecycleStates.get(uuid);
        if (explicit != null) {
            return explicit;
        }
        if (this.deadCompanions.contains(uuid) || this.deadRecords.containsKey(uuid)) {
            return CompanionLifecycleState.DEAD;
        }
        if (this.deployedLists.values().stream().anyMatch(list -> list.contains(uuid)) || uuid.equals(this.deployedVehicle)) {
            return CompanionLifecycleState.DEPLOYED;
        }
        if (this.storedEntities.containsKey(uuid)) {
            return this.homeNestBlocks.containsKey(uuid)
                    ? CompanionLifecycleState.HOME_STORED
                    : CompanionLifecycleState.STORED;
        }
        return CompanionLifecycleState.RECOVERY;
    }

    public void setLifecycleState(UUID uuid, CompanionLifecycleState state) {
        if (uuid != null && state != null) {
            CompanionLifecycleState previous = lifecycleState(uuid);
            this.lifecycleStates.put(uuid, state);
            if (state != CompanionLifecycleState.RECOVERY) this.recoveryRecords.remove(uuid);
            if (previous != state) markLifecycleChanged(uuid);
        }
    }

    public void clearLifecycleState(UUID uuid) {
        if (uuid != null) {
            if (this.lifecycleStates.remove(uuid) != null) markLifecycleChanged(uuid);
        }
    }

    void markLifecycleChanged(UUID uuid) {
        if (uuid != null) this.lifecycleChanges.add(uuid);
    }

    Set<UUID> lifecycleChanges() {
        return Set.copyOf(this.lifecycleChanges);
    }

    void clearLifecycleChanges() {
        this.lifecycleChanges.clear();
    }

    public Set<UUID> storedEntityIds() {
        return Set.copyOf(this.storedEntities.keySet());
    }

    public boolean isCritical(UUID uuid) {
        return uuid != null && this.criticalCompanions.contains(uuid);
    }

    public Set<UUID> criticalCompanions() {
        return Set.copyOf(this.criticalCompanions);
    }

    public boolean setCritical(UUID uuid, boolean critical) {
        if (uuid == null) return false;
        boolean changed = critical ? this.criticalCompanions.add(uuid) : this.criticalCompanions.remove(uuid);
        if (changed) markLifecycleChanged(uuid);
        return changed;
    }

    public Optional<CompoundTag> rawStoredEntityForDiagnostics(UUID uuid) {
        CompoundTag tag = this.storedEntities.get(uuid);
        return tag == null ? Optional.empty() : Optional.of(tag.copy());
    }

    public Map<String, Set<UUID>> metadataIdsForDiagnostics() {
        Map<String, Set<UUID>> metadata = new HashMap<>();
        metadata.put("display-name", Set.copyOf(this.displayNames.keySet()));
        metadata.put("animation-style", Set.copyOf(this.animationStyles.keySet()));
        metadata.put("effect-style", Set.copyOf(this.effectStyles.keySet()));
        metadata.put("spell-binding", Set.copyOf(this.spellBindings.keySet()));
        metadata.put("critical", Set.copyOf(this.criticalCompanions));
        metadata.put("origin", Set.copyOf(this.origins.keySet()));
        metadata.put("last-known-position", Set.copyOf(this.lastKnownPositions.keySet()));
        metadata.put("home-position", Set.copyOf(this.homePositions.keySet()));
        metadata.put("home-nest", Set.copyOf(this.homeNestBlocks.keySet()));
        metadata.put("vehicle-seat", Set.copyOf(this.vehicleSeatOffsets.keySet()));
        return Map.copyOf(metadata);
    }

    public boolean backupChecksumKnown(BackupEntry backup) {
        return PlayerCompanionArchiveService.hasChecksum(backup);
    }

    public boolean backupChecksumMatches(BackupEntry backup) {
        return PlayerCompanionArchiveService.checksumMatches(backup);
    }

    public boolean backupStateMeaningful(BackupEntry backup) {
        return backup != null && PlayerCompanionDataCodec.isMeaningfulRoot(backup.state());
    }

    public boolean hasMeaningfulBackupState() {
        return PlayerCompanionDataCodec.isMeaningfulRoot(PlayerCompanionDataCodec.createBackupState(this));
    }

    public boolean isVehicleDeployed(UUID uuid) {
        return uuid != null && uuid.equals(this.deployedVehicle);
    }

    public void clearDeployedVehicle(UUID uuid) {
        if (uuid == null || uuid.equals(this.deployedVehicle)) {
            this.deployedVehicle = null;
        }
    }

    public boolean addVehicleWheelSlot(UUID uuid) {
        this.vehicleWheelSlotsConfigured = true;
        if (!this.vehicles.contains(uuid) || this.vehicleWheelSlots.contains(uuid)) {
            return false;
        }
        this.vehicleWheelSlots.add(uuid);
        return true;
    }

    public boolean removeVehicleWheelSlot(int index) {
        this.vehicleWheelOrder();
        this.vehicleWheelSlotsConfigured = true;
        if (index < 0 || index >= this.vehicleWheelSlots.size()) {
            return false;
        }
        this.vehicleWheelSlots.remove(index);
        if (!this.vehicleWheelSlots.isEmpty()) {
            this.vehicleActiveIndex = Math.min(this.vehicleActiveIndex, this.vehicleWheelSlots.size() - 1);
        } else {
            this.vehicleActiveIndex = 0;
        }
        return true;
    }

    public boolean removeVehicleWheelSlot(UUID uuid) {
        this.vehicleWheelOrder();
        this.vehicleWheelSlotsConfigured = true;
        int index = this.vehicleWheelSlots.indexOf(uuid);
        return index >= 0 && this.removeVehicleWheelSlot(index);
    }

    public boolean reorderVehicleWheel(int from, int to) {
        this.vehicleWheelOrder();
        this.vehicleWheelSlotsConfigured = true;
        if (from < 0 || from >= this.vehicleWheelSlots.size() || to < 0 || to >= this.vehicleWheelSlots.size()) {
            return false;
        }
        UUID uuid = this.vehicleWheelSlots.remove(from);
        this.vehicleWheelSlots.add(to, uuid);
        this.vehicleActiveIndex = to;
        return true;
    }

    public void setOrigin(UUID uuid, SavedPosition position) {
        PlayerCompanionEntityStateService.setOrigin(this, uuid, position);
    }

    public Optional<SavedPosition> origin(UUID uuid) {
        return PlayerCompanionEntityStateService.origin(this, uuid);
    }

    public void setLastKnownPosition(UUID uuid, SavedPosition position) {
        PlayerCompanionEntityStateService.setLastKnownPosition(this, uuid, position);
    }

    public Optional<SavedPosition> lastKnownPosition(UUID uuid) {
        return PlayerCompanionEntityStateService.lastKnownPosition(this, uuid);
    }

    public void setHomePosition(UUID uuid, SavedPosition position) {
        PlayerCompanionEntityStateService.setHomePosition(this, uuid, position);
        markLifecycleChanged(uuid);
    }

    public Optional<SavedPosition> homePosition(UUID uuid) {
        return PlayerCompanionEntityStateService.homePosition(this, uuid);
    }

    public void clearHomePosition(UUID uuid) {
        PlayerCompanionEntityStateService.clearHomePosition(this, uuid);
        markLifecycleChanged(uuid);
    }

    public void setHouseResidentPosition(UUID uuid, SavedPosition position) {
        PlayerCompanionEntityStateService.setHouseResidentPosition(this, uuid, position);
        markLifecycleChanged(uuid);
    }

    public void setHomeNestBlock(UUID uuid, SavedPosition position) {
        PlayerCompanionEntityStateService.setHomeNestBlock(this, uuid, position);
        markLifecycleChanged(uuid);
    }

    public Optional<SavedPosition> homeNestBlock(UUID uuid) {
        return PlayerCompanionEntityStateService.homeNestBlock(this, uuid);
    }

    public void clearHomeNestBlock(UUID uuid) {
        PlayerCompanionEntityStateService.clearHomeNestBlock(this, uuid);
        markLifecycleChanged(uuid);
    }

    public void setHomeHouseId(UUID uuid, UUID houseId) {
        PlayerCompanionEntityStateService.setHomeHouseId(this, uuid, houseId);
        markLifecycleChanged(uuid);
    }

    public Optional<UUID> homeHouseId(UUID uuid) {
        return PlayerCompanionEntityStateService.homeHouseId(this, uuid);
    }

    public void clearHomeHouseId(UUID uuid) {
        PlayerCompanionEntityStateService.clearHomeHouseId(this, uuid);
        markLifecycleChanged(uuid);
    }

    public int clearHomesForNestBlock(SavedPosition source) {
        return PlayerCompanionEntityStateService.clearHomesForNestBlock(this, source);
    }

    public void storeEntity(UUID uuid, CompoundTag tag) {
        PlayerCompanionEntityStateService.storeEntity(this, uuid, tag);
    }

    public Optional<CompoundTag> storedEntity(UUID uuid) {
        return PlayerCompanionEntityStateService.storedEntity(this, uuid);
    }

    public boolean hasStoredEntity(UUID uuid) {
        return PlayerCompanionEntityStateService.hasStoredEntity(this, uuid);
    }

    public Optional<String> storedEntityType(UUID uuid) {
        return PlayerCompanionEntityStateService.storedEntityType(this, uuid);
    }

    public Optional<String> storedEntityName(UUID uuid) {
        return PlayerCompanionEntityStateService.storedEntityName(this, uuid);
    }

    public Optional<String> displayName(UUID uuid) {
        return PlayerCompanionEntityStateService.displayName(this, uuid);
    }

    public void setDisplayName(UUID uuid, String name) {
        PlayerCompanionEntityStateService.setDisplayName(this, uuid, name);
    }

    public void removeStoredEntity(UUID uuid) {
        PlayerCompanionEntityStateService.removeStoredEntity(this, uuid);
    }

    public void addVaultSnapshot(UUID uuid, CompanionKind kind, String name, CompoundTag entityTag, long savedAt, String reason, int limit) {
        PlayerCompanionArchiveService.addVaultSnapshot(this, uuid, kind, name, entityTag, savedAt, reason, limit);
    }

    public void vaultSnapshot(UUID uuid, CompanionKind kind, String reason, int limit) {
        PlayerCompanionArchiveService.vaultSnapshot(this, uuid, kind, reason, limit);
    }

    public List<VaultEntry> vaultList() {
        return PlayerCompanionArchiveService.vaultList(this);
    }

    public List<CompoundTag> recoverySnapshotsFromArchives(UUID uuid) {
        return PlayerCompanionArchiveService.recoverySnapshots(this, uuid);
    }

    public void createBackup(long savedAt, String reason, int limit) {
        PlayerCompanionArchiveService.createBackup(this, savedAt, reason, limit);
    }

    public void createBackup(long savedAt, String reason, int limit, boolean manual) {
        PlayerCompanionArchiveService.createBackup(this, savedAt, reason, limit, manual);
    }

    public void createBackup(long savedAt, String reason, int limit, boolean manual, CompoundTag previewContents) {
        PlayerCompanionArchiveService.createBackup(this, savedAt, reason, limit, manual, previewContents);
    }

    public List<BackupEntry> backupList() {
        return PlayerCompanionArchiveService.backupList(this);
    }

    public Optional<BackupEntry> backupAt(int index) {
        return PlayerCompanionArchiveService.backupAt(this, index);
    }

    public boolean renameManualBackup(int index, long expectedSavedAt, String name) {
        return PlayerCompanionArchiveService.renameManualBackup(this, index, expectedSavedAt, name);
    }

    public void restoreBackup(BackupEntry backup) {
        PlayerCompanionArchiveService.restoreBackup(this, backup);
    }

    public boolean deleteBackup(int index, long expectedSavedAt) {
        return PlayerCompanionArchiveService.deleteBackup(this, index, expectedSavedAt);
    }

    public long lastBackupAt() {
        return PlayerCompanionArchiveService.lastBackupAt(this);
    }

    public void trimVault(int limit) {
        PlayerCompanionArchiveService.trimVault(this, limit);
    }

    public List<UUID> wheelOrder(CompanionKind kind) {
        return PlayerCompanionWheelService.wheelOrder(this, kind);
    }

    public int activeWheelIndex(CompanionKind kind) {
        return PlayerCompanionWheelService.activeWheelIndex(this, kind);
    }

    public Optional<UUID> wheelUuidAt(CompanionKind kind, int index) {
        return PlayerCompanionWheelService.wheelUuidAt(this, kind, index);
    }

    public boolean setWheelSlot(CompanionKind kind, int sourceIndex, int slotIndex) {
        return PlayerCompanionWheelService.setWheelSlot(this, kind, sourceIndex, slotIndex);
    }

    public boolean reorder(CompanionKind kind, int from, int to) {
        return PlayerCompanionWheelService.reorder(this, kind, from, to);
    }

    public long readyAt(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? this.mountReadyAt : this.companionReadyAt;
    }

    public void setReadyAt(CompanionKind kind, long tick) {
        if (kind == CompanionKind.MOUNT) {
            this.mountReadyAt = tick;
        } else {
            this.companionReadyAt = tick;
        }
    }

    public int safetySchemaVersion() {
        return this.safetySchemaVersion;
    }

    public static int currentSafetySchemaVersion() {
        return SAFETY_SCHEMA_VERSION;
    }

    public void markSafetySchemaCurrent() {
        this.safetySchemaVersion = SAFETY_SCHEMA_VERSION;
    }

    private static CompanionKind other(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? CompanionKind.COMPANION : CompanionKind.MOUNT;
    }

    private static CompanionTeamTarget targetFor(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.COMPANION;
    }

    public static record VaultEntry(UUID uuid, CompanionKind kind, String entityType, String name, CompoundTag entityTag, long savedAt, String reason) {
        public VaultEntry {
            entityType = entityType == null ? "" : entityType;
            name = name == null ? "" : name;
            reason = reason == null ? "" : reason;
            entityTag = entityTag == null ? new CompoundTag() : entityTag.copy();
        }

        @Override
        public CompoundTag entityTag() {
            return this.entityTag.copy();
        }
    }

    public static record BackupEntry(long savedAt, long createdAtEpochMillis, String reason, CompoundTag state,
                                     int formatVersion, String checksum, boolean manual) {
        public BackupEntry(long savedAt, String reason, CompoundTag state) {
            this(savedAt, 0L, reason, state, 0, "", false);
        }

        public BackupEntry(long savedAt, String reason, CompoundTag state, int formatVersion,
                           String checksum, boolean manual) {
            this(savedAt, 0L, reason, state, formatVersion, checksum, manual);
        }

        public BackupEntry {
            reason = reason == null ? "" : reason;
            state = state == null ? new CompoundTag() : state.copy();
            checksum = checksum == null ? "" : checksum;
        }

        @Override
        public CompoundTag state() {
            return this.state.copy();
        }
    }
}

