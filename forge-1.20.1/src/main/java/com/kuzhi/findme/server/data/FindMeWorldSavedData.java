package com.kuzhi.findme.server.data;

import com.kuzhi.findme.Config;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.HouseResidentMode;

/**
 * FindMe's world-owned persistence boundary.
 *
 * The legacy player NBT root is retained as a value here for the first data
 * cutover. PlayerCompanionData remains the field codec until the lifecycle
 * state model is migrated in the next implementation phase.
 */
public final class FindMeWorldSavedData extends SavedData {
    public static final String DATA_NAME = "find_me_world_data";
    public static final int FORMAT_VERSION = 1;

    private final Map<UUID, CompoundTag> playerRoots = new HashMap<>();
    private final Map<UUID, Long> playerRevisions = new HashMap<>();
    private final Map<UUID, HomeResidentIndex> homeResidentIndexes = new HashMap<>();
    private final Map<UUID, CompanionRuntimeIndex> companionRuntimeIndexes = new HashMap<>();
    private final Map<UUID, UUID> companionOwners = new HashMap<>();
    private boolean companionOwnersInitialized;
    private final Map<UUID, MigrationRecord> migrations = new HashMap<>();
    private final Map<UUID, HouseRecord> houses = new HashMap<>();
    private final Map<String, SavedPosition> destroyedHousePositions = new HashMap<>();
    private final Map<UUID, Integer> destroyedHouseReconciliations = new HashMap<>();
    private int destroyedHouseRevision;

    FindMeWorldSavedData() {
    }

    public static FindMeWorldSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FindMeWorldSavedData::load, FindMeWorldSavedData::new, DATA_NAME);
    }

    public Optional<CompoundTag> playerRoot(UUID playerUuid) {
        CompoundTag root = playerRoots.get(playerUuid);
        return root == null ? Optional.empty() : Optional.of(root.copy());
    }

    boolean hasPlayerRoot(UUID playerUuid) {
        return playerUuid != null && playerRoots.containsKey(playerUuid);
    }

    PlayerCompanionData decodePlayerData(UUID playerUuid) {
        return PlayerCompanionDataCodec.loadRoot(playerUuid == null ? null : playerRoots.get(playerUuid));
    }

    public long playerRevision(UUID playerUuid) {
        return playerUuid == null ? 0L : playerRevisions.getOrDefault(playerUuid, 0L);
    }

    public HomeResidentIndex homeResidentIndex(UUID playerUuid) {
        if (playerUuid == null) return HomeResidentIndex.empty();
        return homeResidentIndexes.computeIfAbsent(playerUuid,
                uuid -> HomeResidentIndex.fromRoot(playerRoots.get(uuid)));
    }

    public CompanionRuntimeIndex companionRuntimeIndex(UUID playerUuid) {
        if (playerUuid == null) return CompanionRuntimeIndex.empty();
        return companionRuntimeIndexes.computeIfAbsent(playerUuid,
                uuid -> CompanionRuntimeIndex.fromRoot(playerRoots.get(uuid)));
    }

    /** Derived reverse lookup for hot entity lifecycle events; player roots remain authoritative. */
    public Optional<UUID> companionOwner(UUID companionUuid) {
        if (companionUuid == null) return Optional.empty();
        ensureCompanionOwners();
        return Optional.ofNullable(companionOwners.get(companionUuid));
    }

    public boolean hasAuthoritativePlayer(UUID playerUuid) {
        return playerRoots.containsKey(playerUuid) || migrations.containsKey(playerUuid);
    }

    public Optional<MigrationRecord> migration(UUID playerUuid) {
        return Optional.ofNullable(migrations.get(playerUuid));
    }

    boolean hasMigration(UUID playerUuid) {
        return playerUuid != null && migrations.containsKey(playerUuid);
    }

    public Set<UUID> playerUuids() {
        return Set.copyOf(playerRoots.keySet());
    }

    public Optional<HouseRecord> house(UUID houseId) {
        HouseRecord house = houses.get(houseId);
        return house == null ? Optional.empty() : Optional.of(house.copy());
    }

    public Optional<HouseRecord> houseAt(SavedPosition position) {
        if (position == null) return Optional.empty();
        return houses.values().stream()
                .filter(house -> house.position().dimension().equals(position.dimension())
                        && house.position().blockPos().equals(position.blockPos()))
                .findFirst()
                .map(HouseRecord::copy);
    }

    public List<HouseRecord> housesOwnedBy(UUID owner) {
        if (owner == null) return List.of();
        return houses.values().stream()
                .filter(house -> owner.equals(house.owner()))
                .map(HouseRecord::copy)
                .toList();
    }

    public boolean registerHouse(UUID houseId, UUID owner, SavedPosition position, String displayName) {
        if (houseId == null || position == null) {
            return false;
        }
        HouseRecord previous = houses.get(houseId);
        if (previous != null && !sameBlock(previous.position(), position)) {
            return false;
        }
        Set<UUID> residents = previous == null ? new HashSet<>() : previous.residents();
        Map<UUID, HouseResidentMode> residentModes = previous == null ? new HashMap<>() : previous.residentModes();
        int capacity = previous == null ? Config.houseResidentCapacity : previous.residentCapacity();
        int patrolRadius = previous == null ? Config.housePatrolRadius : previous.patrolRadius();
        int hardRadius = previous == null ? Config.houseHardRadius : previous.hardRadius();
        houses.put(houseId, new HouseRecord(houseId, owner, position, displayName, residents, residentModes,
                capacity, patrolRadius, hardRadius));
        setDirty();
        return true;
    }

    public void removeHouse(UUID houseId) {
        if (houseId != null && houses.remove(houseId) != null) {
            setDirty();
        }
    }

    public void markDestroyedHouse(SavedPosition position) {
        if (position == null) {
            return;
        }
        destroyedHousePositions.put(houseKey(position), position);
        destroyedHouseRevision++;
        setDirty();
    }

    public boolean wasHouseDestroyed(SavedPosition position) {
        return position != null && destroyedHousePositions.containsKey(houseKey(position));
    }

    public boolean needsDestroyedHouseReconciliation(UUID playerUuid) {
        return playerUuid != null && destroyedHouseRevision > 0
                && destroyedHouseReconciliations.getOrDefault(playerUuid, 0) < destroyedHouseRevision;
    }

    public void markDestroyedHousesReconciled(UUID playerUuid) {
        if (playerUuid == null || !needsDestroyedHouseReconciliation(playerUuid)) {
            return;
        }
        destroyedHouseReconciliations.put(playerUuid, destroyedHouseRevision);
        setDirty();
    }

    public void addResident(UUID houseId, UUID companionUuid) {
        HouseRecord house = houses.get(houseId);
        if (house != null && companionUuid != null) {
            boolean changed = house.residents.add(companionUuid);
            changed |= house.residentModes.putIfAbsent(companionUuid, HouseResidentMode.REST) == null;
            if (changed) {
                setDirty();
            }
        }
    }

    public void removeResident(UUID houseId, UUID companionUuid) {
        HouseRecord house = houses.get(houseId);
        if (house != null && companionUuid != null) {
            boolean changed = house.residents.remove(companionUuid);
            changed |= house.residentModes.remove(companionUuid) != null;
            if (changed) {
                setDirty();
            }
        }
    }

    public HouseResidentMode residentMode(UUID houseId, UUID companionUuid) {
        HouseRecord house = houses.get(houseId);
        return house == null || companionUuid == null
                ? HouseResidentMode.WANDER
                : house.residentMode(companionUuid);
    }

    public boolean setResidentMode(UUID houseId, UUID companionUuid, HouseResidentMode mode) {
        HouseRecord house = houses.get(houseId);
        if (house == null || companionUuid == null || mode == null || !house.residents.contains(companionUuid)) {
            return false;
        }
        HouseResidentMode previous = house.residentModes.put(companionUuid, mode);
        if (previous == mode) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean setHouseSettings(UUID houseId, int residentCapacity, int patrolRadius, int hardRadius) {
        HouseRecord house = houses.get(houseId);
        if (house == null) return false;
        int capacity = HouseRecord.clampCapacity(residentCapacity);
        int patrol = HouseRecord.clampPatrolRadius(patrolRadius);
        int hard = HouseRecord.clampHardRadius(hardRadius, patrol);
        if (house.residentCapacity == capacity && house.patrolRadius == patrol && house.hardRadius == hard) {
            return false;
        }
        house.residentCapacity = capacity;
        house.patrolRadius = patrol;
        house.hardRadius = hard;
        setDirty();
        return true;
    }

    public int removeResidentFromAllHouses(UUID companionUuid) {
        if (companionUuid == null) {
            return 0;
        }
        int removed = 0;
        for (HouseRecord house : houses.values()) {
            if (house.residents.remove(companionUuid)) {
                house.residentModes.remove(companionUuid);
                removed++;
            }
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    private static boolean sameBlock(SavedPosition first, SavedPosition second) {
        return first != null && second != null
                && Objects.equals(first.dimension(), second.dimension())
                && first.blockPos().equals(second.blockPos());
    }

    static ListTag saveResidents(HouseRecord house) {
        ListTag residents = new ListTag();
        if (house == null) {
            return residents;
        }
        for (UUID residentUuid : house.residents()) {
            CompoundTag resident = new CompoundTag();
            resident.putUUID("uuid", residentUuid);
            resident.putString("mode", house.residentMode(residentUuid).name());
            residents.add(resident);
        }
        return residents;
    }

    public long putPlayerRoot(UUID playerUuid, CompoundTag root) {
        if (playerUuid == null || root == null) {
            return 0L;
        }
        CompoundTag previous = playerRoots.get(playerUuid);
        if (root.equals(previous)) {
            return playerRevision(playerUuid);
        }
        playerRoots.put(playerUuid, root.copy());
        homeResidentIndexes.remove(playerUuid);
        companionRuntimeIndexes.remove(playerUuid);
        refreshCompanionOwners(playerUuid);
        long revision = playerRevision(playerUuid) + 1L;
        playerRevisions.put(playerUuid, revision);
        setDirty();
        return revision;
    }

    void removePlayerRoot(UUID playerUuid) {
        if (playerUuid != null && playerRoots.remove(playerUuid) != null) {
            homeResidentIndexes.remove(playerUuid);
            companionRuntimeIndexes.remove(playerUuid);
            removeCompanionOwnerEntries(playerUuid);
            playerRevisions.remove(playerUuid);
            setDirty();
        }
    }

    public void markMigration(UUID playerUuid, String source, long migratedAt, String sourceChecksum, String result) {
        if (playerUuid == null) {
            return;
        }
        migrations.put(playerUuid, new MigrationRecord(FORMAT_VERSION, source, migratedAt, sourceChecksum, result));
        setDirty();
    }

    public void copyPlayer(UUID sourceUuid, UUID targetUuid) {
        if (sourceUuid == null || targetUuid == null || sourceUuid.equals(targetUuid)) {
            return;
        }
        CompoundTag root = playerRoots.get(sourceUuid);
        MigrationRecord migration = migrations.get(sourceUuid);
        if (root != null) {
            playerRoots.put(targetUuid, root.copy());
            homeResidentIndexes.remove(targetUuid);
            companionRuntimeIndexes.remove(targetUuid);
            refreshCompanionOwners(targetUuid);
            playerRevisions.put(targetUuid, Math.max(playerRevision(sourceUuid), playerRevision(targetUuid) + 1L));
        }
        if (migration != null) {
            migrations.put(targetUuid, migration);
        }
        if (root != null || migration != null) {
            setDirty();
        }
    }

    public static String checksum(CompoundTag root) {
        return NbtFingerprint.sha256(root);
    }

    private void ensureCompanionOwners() {
        if (companionOwnersInitialized) return;
        companionOwners.clear();
        playerRoots.keySet().stream().sorted().forEach(owner ->
                companionRuntimeIndex(owner).uuids().stream().sorted()
                        .forEach(companion -> companionOwners.putIfAbsent(companion, owner)));
        companionOwnersInitialized = true;
    }

    private void refreshCompanionOwners(UUID playerUuid) {
        if (!companionOwnersInitialized || playerUuid == null) return;
        removeCompanionOwnerEntries(playerUuid);
        companionRuntimeIndex(playerUuid).uuids().forEach(companion -> companionOwners.put(companion, playerUuid));
    }

    private void removeCompanionOwnerEntries(UUID playerUuid) {
        if (!companionOwnersInitialized || playerUuid == null) return;
        companionOwners.entrySet().removeIf(entry -> playerUuid.equals(entry.getValue()));
    }

    static FindMeWorldSavedData load(CompoundTag tag) {
        FindMeWorldSavedData data = new FindMeWorldSavedData();
        data.destroyedHouseRevision = Math.max(0, tag.getInt("destroyedHouseRevision"));
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (entry.hasUUID("uuid") && entry.contains("root", Tag.TAG_COMPOUND)) {
                UUID playerUuid = entry.getUUID("uuid");
                data.playerRoots.put(playerUuid, entry.getCompound("root").copy());
                data.playerRevisions.put(playerUuid, Math.max(0L, entry.getLong("revision")));
            }
        }

        ListTag migrations = tag.getList("migrations", Tag.TAG_COMPOUND);
        for (int index = 0; index < migrations.size(); index++) {
            CompoundTag entry = migrations.getCompound(index);
            if (!entry.hasUUID("uuid")) {
                continue;
            }
            data.migrations.put(entry.getUUID("uuid"), new MigrationRecord(
                    entry.getInt("version"),
                    entry.getString("source"),
                    entry.getLong("migratedAt"),
                    entry.getString("sourceChecksum"),
                    entry.getString("result")
            ));
        }
        ListTag houses = tag.getList("houses", Tag.TAG_COMPOUND);
        for (int index = 0; index < houses.size(); index++) {
            CompoundTag entry = houses.getCompound(index);
            if (!entry.hasUUID("houseId") || !entry.contains("position", Tag.TAG_COMPOUND)) {
                continue;
            }
            Set<UUID> residents = new HashSet<>();
            Map<UUID, HouseResidentMode> residentModes = new HashMap<>();
            ListTag residentList = entry.getList("residents", Tag.TAG_COMPOUND);
            for (int residentIndex = 0; residentIndex < residentList.size(); residentIndex++) {
                CompoundTag resident = residentList.getCompound(residentIndex);
                if (resident.hasUUID("uuid")) {
                    UUID residentUuid = resident.getUUID("uuid");
                    residents.add(residentUuid);
                    residentModes.put(residentUuid, HouseResidentMode.parse(resident.getString("mode")));
                }
            }
            data.houses.put(entry.getUUID("houseId"), new HouseRecord(
                    entry.getUUID("houseId"),
                    entry.hasUUID("owner") ? entry.getUUID("owner") : null,
                    SavedPosition.load(entry.getCompound("position")),
                    entry.getString("displayName"),
                    residents,
                    residentModes,
                    entry.contains("residentCapacity", Tag.TAG_INT)
                            ? entry.getInt("residentCapacity") : Config.houseResidentCapacity,
                    entry.contains("patrolRadius", Tag.TAG_INT)
                            ? entry.getInt("patrolRadius") : Config.housePatrolRadius,
                    entry.contains("hardRadius", Tag.TAG_INT)
                            ? entry.getInt("hardRadius") : Config.houseHardRadius));
        }
        ListTag destroyedHouses = tag.getList("destroyedHouses", Tag.TAG_COMPOUND);
        for (int index = 0; index < destroyedHouses.size(); index++) {
            CompoundTag entry = destroyedHouses.getCompound(index);
            if (!entry.contains("position", Tag.TAG_COMPOUND)) {
                continue;
            }
            SavedPosition position = SavedPosition.load(entry.getCompound("position"));
            data.destroyedHousePositions.put(houseKey(position), position);
        }
        if (!data.destroyedHousePositions.isEmpty() && data.destroyedHouseRevision == 0) {
            data.destroyedHouseRevision = 1;
        }
        ListTag destroyedHouseReconciliations = tag.getList("destroyedHouseReconciliations", Tag.TAG_COMPOUND);
        for (int index = 0; index < destroyedHouseReconciliations.size(); index++) {
            CompoundTag entry = destroyedHouseReconciliations.getCompound(index);
            if (entry.hasUUID("uuid")) {
                data.destroyedHouseReconciliations.put(entry.getUUID("uuid"), entry.getInt("revision"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("formatVersion", FORMAT_VERSION);

        ListTag players = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : playerRoots.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("uuid", entry.getKey());
            player.put("root", entry.getValue().copy());
            player.putLong("revision", playerRevision(entry.getKey()));
            players.add(player);
        }
        tag.put("players", players);

        ListTag migrations = new ListTag();
        for (Map.Entry<UUID, MigrationRecord> entry : this.migrations.entrySet()) {
            MigrationRecord record = entry.getValue();
            CompoundTag migration = new CompoundTag();
            migration.putUUID("uuid", entry.getKey());
            migration.putInt("version", record.version());
            migration.putString("source", record.source());
            migration.putLong("migratedAt", record.migratedAt());
            migration.putString("sourceChecksum", record.sourceChecksum());
            migration.putString("result", record.result());
            migrations.add(migration);
        }
        tag.put("migrations", migrations);

        ListTag houses = new ListTag();
        for (HouseRecord house : this.houses.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("houseId", house.houseId());
            if (house.owner() != null) {
                entry.putUUID("owner", house.owner());
            }
            entry.put("position", house.position().save());
            if (!house.displayName().isBlank()) {
                entry.putString("displayName", house.displayName());
            }
            entry.put("residents", saveResidents(house));
            entry.putInt("residentCapacity", house.residentCapacity());
            entry.putInt("patrolRadius", house.patrolRadius());
            entry.putInt("hardRadius", house.hardRadius());
            houses.add(entry);
        }
        tag.put("houses", houses);

        ListTag destroyedHouses = new ListTag();
        for (SavedPosition position : destroyedHousePositions.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put("position", position.save());
            destroyedHouses.add(entry);
        }
        tag.put("destroyedHouses", destroyedHouses);
        tag.putInt("destroyedHouseRevision", destroyedHouseRevision);
        ListTag destroyedHouseReconciliations = new ListTag();
        for (Map.Entry<UUID, Integer> reconciliation : this.destroyedHouseReconciliations.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", reconciliation.getKey());
            entry.putInt("revision", reconciliation.getValue());
            destroyedHouseReconciliations.add(entry);
        }
        tag.put("destroyedHouseReconciliations", destroyedHouseReconciliations);
        return tag;
    }

    private static String houseKey(SavedPosition position) {
        return position.dimension().location() + ":" + position.blockPos().getX() + ","
                + position.blockPos().getY() + "," + position.blockPos().getZ();
    }

    public record MigrationRecord(int version, String source, long migratedAt, String sourceChecksum, String result) {
        public MigrationRecord {
            source = source == null ? "unknown" : source;
            sourceChecksum = sourceChecksum == null ? "" : sourceChecksum;
            result = result == null ? "unknown" : result;
        }
    }

    public static final class HouseRecord {
        private final UUID houseId;
        private final UUID owner;
        private final SavedPosition position;
        private final String displayName;
        private final Set<UUID> residents;
        private final Map<UUID, HouseResidentMode> residentModes;
        private int residentCapacity;
        private int patrolRadius;
        private int hardRadius;

        public HouseRecord(UUID houseId, UUID owner, SavedPosition position, String displayName, Set<UUID> residents) {
            this(houseId, owner, position, displayName, residents, Map.of(), Config.houseResidentCapacity,
                    Config.housePatrolRadius, Config.houseHardRadius);
        }

        public HouseRecord(UUID houseId, UUID owner, SavedPosition position, String displayName, Set<UUID> residents,
                           Map<UUID, HouseResidentMode> residentModes) {
            this(houseId, owner, position, displayName, residents, residentModes, Config.houseResidentCapacity,
                    Config.housePatrolRadius, Config.houseHardRadius);
        }

        public HouseRecord(UUID houseId, UUID owner, SavedPosition position, String displayName, Set<UUID> residents,
                           Map<UUID, HouseResidentMode> residentModes, int residentCapacity,
                           int patrolRadius, int hardRadius) {
            this.houseId = houseId;
            this.owner = owner;
            this.position = position;
            this.displayName = displayName == null ? "" : displayName;
            this.residents = new HashSet<>(residents == null ? Set.of() : residents);
            this.residentModes = new HashMap<>();
            for (UUID resident : this.residents) {
                this.residentModes.put(resident, residentModes == null
                        ? HouseResidentMode.WANDER
                        : residentModes.getOrDefault(resident, HouseResidentMode.WANDER));
            }
            this.residentCapacity = clampCapacity(residentCapacity);
            this.patrolRadius = clampPatrolRadius(patrolRadius);
            this.hardRadius = clampHardRadius(hardRadius, this.patrolRadius);
        }

        public UUID houseId() { return houseId; }
        public UUID owner() { return owner; }
        public SavedPosition position() { return position; }
        public String displayName() { return displayName; }
        public Set<UUID> residents() { return residents; }
        public Map<UUID, HouseResidentMode> residentModes() { return residentModes; }
        public int residentCapacity() { return residentCapacity; }
        public int patrolRadius() { return patrolRadius; }
        public int hardRadius() { return hardRadius; }
        public HouseResidentMode residentMode(UUID resident) {
            return residentModes.getOrDefault(resident, HouseResidentMode.WANDER);
        }
        public HouseRecord copy() { return new HouseRecord(houseId, owner, position, displayName, residents,
                residentModes, residentCapacity, patrolRadius, hardRadius); }

        static int clampCapacity(int value) { return Math.max(1, Math.min(64, value)); }
        static int clampPatrolRadius(int value) { return Math.max(4, Math.min(256, value)); }
        static int clampHardRadius(int value, int patrolRadius) {
            return Math.max(patrolRadius, Math.max(8, Math.min(512, value)));
        }
    }
}
