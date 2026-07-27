package com.kuzhi.findme.server.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.kuzhi.findme.common.SavedPosition;

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
    private final Map<UUID, MigrationRecord> migrations = new HashMap<>();
    private final Map<UUID, HouseRecord> houses = new HashMap<>();
    private final Map<String, SavedPosition> destroyedHousePositions = new HashMap<>();
    private final Map<UUID, Integer> destroyedHouseReconciliations = new HashMap<>();
    private int destroyedHouseRevision;

    FindMeWorldSavedData() {
    }

    public static FindMeWorldSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FindMeWorldSavedData::new, FindMeWorldSavedData::load),
                DATA_NAME
        );
    }

    public Optional<CompoundTag> playerRoot(UUID playerUuid) {
        CompoundTag root = playerRoots.get(playerUuid);
        return root == null ? Optional.empty() : Optional.of(root.copy());
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

    public boolean hasAuthoritativePlayer(UUID playerUuid) {
        return playerRoots.containsKey(playerUuid) || migrations.containsKey(playerUuid);
    }

    public Optional<MigrationRecord> migration(UUID playerUuid) {
        return Optional.ofNullable(migrations.get(playerUuid));
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

    public void registerHouse(UUID houseId, UUID owner, SavedPosition position, String displayName) {
        if (houseId == null || position == null) {
            return;
        }
        HouseRecord previous = houses.get(houseId);
        Set<UUID> residents = previous == null ? new HashSet<>() : previous.residents();
        houses.put(houseId, new HouseRecord(houseId, owner, position, displayName, residents));
        setDirty();
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
        if (house != null && companionUuid != null && house.residents.add(companionUuid)) {
            setDirty();
        }
    }

    public void removeResident(UUID houseId, UUID companionUuid) {
        HouseRecord house = houses.get(houseId);
        if (house != null && companionUuid != null && house.residents.remove(companionUuid)) {
            setDirty();
        }
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
        long revision = playerRevision(playerUuid) + 1L;
        playerRevisions.put(playerUuid, revision);
        setDirty();
        return revision;
    }

    void removePlayerRoot(UUID playerUuid) {
        if (playerUuid != null && playerRoots.remove(playerUuid) != null) {
            homeResidentIndexes.remove(playerUuid);
            companionRuntimeIndexes.remove(playerUuid);
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

    private static FindMeWorldSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
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
            ListTag residentList = entry.getList("residents", Tag.TAG_COMPOUND);
            for (int residentIndex = 0; residentIndex < residentList.size(); residentIndex++) {
                CompoundTag resident = residentList.getCompound(residentIndex);
                if (resident.hasUUID("uuid")) {
                    residents.add(resident.getUUID("uuid"));
                }
            }
            data.houses.put(entry.getUUID("houseId"), new HouseRecord(
                    entry.getUUID("houseId"),
                    entry.hasUUID("owner") ? entry.getUUID("owner") : null,
                    SavedPosition.load(entry.getCompound("position")),
                    entry.getString("displayName"),
                    residents));
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
            ListTag residents = new ListTag();
            for (UUID residentUuid : house.residents()) {
                CompoundTag resident = new CompoundTag();
                resident.putUUID("uuid", residentUuid);
                residents.add(resident);
            }
            entry.put("residents", residents);
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

        public HouseRecord(UUID houseId, UUID owner, SavedPosition position, String displayName, Set<UUID> residents) {
            this.houseId = houseId;
            this.owner = owner;
            this.position = position;
            this.displayName = displayName == null ? "" : displayName;
            this.residents = new HashSet<>(residents == null ? Set.of() : residents);
        }

        public UUID houseId() { return houseId; }
        public UUID owner() { return owner; }
        public SavedPosition position() { return position; }
        public String displayName() { return displayName; }
        public Set<UUID> residents() { return residents; }
        public HouseRecord copy() { return new HouseRecord(houseId, owner, position, displayName, residents); }
    }
}
