package com.kuzhi.findme.server.data;

import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class PlayerCompanionDataCodec {
    private static final String ROOT = "find_me";

    private PlayerCompanionDataCodec() {
    }

    private static boolean isLegacyManualBackup(String reason) {
        return "manual".equalsIgnoreCase(reason) || "aui_manual".equalsIgnoreCase(reason);
    }

    static PlayerCompanionData load(CompoundTag persistentData) {
        CompoundTag entry;
        PlayerCompanionData data = new PlayerCompanionData();
        boolean hasFindMeRoot = persistentData.contains(ROOT, 10);
        CompoundTag root = persistentData.getCompound(ROOT);
        data.safetySchemaVersion = hasFindMeRoot && root.contains("safetySchemaVersion", 99)
                ? root.getInt("safetySchemaVersion")
                : (hasFindMeRoot ? 0 : PlayerCompanionData.SAFETY_SCHEMA_VERSION);
        for (CompanionKind kind : CompanionKind.values()) {
            ListTag list = root.getList(kind.name().toLowerCase() + "s", 10);
            List<UUID> uuids = data.companions.get((Object)kind);
            uuids.clear();
            for (int i = 0; i < list.size(); ++i) {
                entry = list.getCompound(i);
                if (!entry.hasUUID("uuid")) continue;
                uuids.add(entry.getUUID("uuid"));
            }
            data.activeIndexes.put(kind, root.getInt(kind.name().toLowerCase() + "Index"));
            ListTag slots = root.getList(kind.name().toLowerCase() + "WheelSlots", 10);
            List<UUID> slotUuids = data.wheelSlots.get((Object)kind);
            slotUuids.clear();
            for (int i = 0; i < slots.size(); ++i) {
                CompoundTag entry2 = slots.getCompound(i);
                if (!entry2.hasUUID("uuid")) continue;
                slotUuids.add(entry2.getUUID("uuid"));
            }
            if (root.hasUUID(kind.name().toLowerCase() + "Previous")) {
                data.previous.put(kind, root.getUUID(kind.name().toLowerCase() + "Previous"));
            }
            if (root.hasUUID(kind.name().toLowerCase() + "Deployed")) {
                data.setDeployed(kind, root.getUUID(kind.name().toLowerCase() + "Deployed"));
            }
            ListTag deployedList = root.getList(kind.name().toLowerCase() + "DeployedList", 10);
            for (int i = 0; i < deployedList.size(); ++i) {
                CompoundTag entry3 = deployedList.getCompound(i);
                if (!entry3.hasUUID("uuid")) continue;
                data.setDeployed(kind, entry3.getUUID("uuid"));
            }
        }
        ListTag origins = root.getList("origins", 10);
        ListTag dead = root.getList("deadCompanions", 10);
        for (int i = 0; i < dead.size(); ++i) {
            CompoundTag deadEntry = dead.getCompound(i);
            if (!deadEntry.hasUUID("uuid")) continue;
            UUID uuid = deadEntry.getUUID("uuid");
            data.deadCompanions.add(uuid);
            if (deadEntry.contains("kind")) {
                try {
                    data.deadKinds.put(uuid, CompanionKind.valueOf(deadEntry.getString("kind")));
                } catch (IllegalArgumentException ignored) {
                    data.deadKinds.put(uuid, CompanionKind.COMPANION);
                }
            }
        }
        data.purgeDeadFromLiveLists();
        for (int i = 0; i < origins.size(); ++i) {
            CompoundTag entry4 = origins.getCompound(i);
            if (!entry4.hasUUID("uuid") || !entry4.contains("position", 10)) continue;
            data.origins.put(entry4.getUUID("uuid"), SavedPosition.load(entry4.getCompound("position")));
        }
        ListTag locations = root.getList("lastKnownPositions", 10);
        for (int i = 0; i < locations.size(); ++i) {
            CompoundTag entry5 = locations.getCompound(i);
            if (!entry5.hasUUID("uuid") || !entry5.contains("position", 10)) continue;
            data.lastKnownPositions.put(entry5.getUUID("uuid"), SavedPosition.load(entry5.getCompound("position")));
        }
        ListTag homes = root.getList("homePositions", 10);
        for (int i = 0; i < homes.size(); ++i) {
            CompoundTag homeEntry = homes.getCompound(i);
            if (!homeEntry.hasUUID("uuid") || !homeEntry.contains("position", 10)) continue;
            data.homePositions.put(homeEntry.getUUID("uuid"), SavedPosition.load(homeEntry.getCompound("position")));
        }
        ListTag homeNestBlocks = root.getList("homeNestBlocks", 10);
        for (int i = 0; i < homeNestBlocks.size(); ++i) {
            CompoundTag sourceEntry = homeNestBlocks.getCompound(i);
            if (!sourceEntry.hasUUID("uuid") || !sourceEntry.contains("position", 10)) continue;
            data.homeNestBlocks.put(sourceEntry.getUUID("uuid"), SavedPosition.load(sourceEntry.getCompound("position")));
        }
        readUuidMap(root.getList("homeHouseIds", 10), data.homeHouseIds);
        ListTag stored = root.getList("storedEntities", 10);
        for (int i = 0; i < stored.size(); ++i) {
            CompoundTag entry6 = stored.getCompound(i);
            if (!entry6.hasUUID("uuid") || !entry6.contains("entity", 10)) continue;
            data.storedEntities.put(entry6.getUUID("uuid"), CompanionStorageService.sanitizedStoredTag(entry6.getCompound("entity")));
        }
        data.criticalCompanions.clear();
        data.criticalCompanions.addAll(readUuidList(root.getList("criticalCompanions", 10)));
        data.criticalCompanions.removeIf(uuid -> !data.contains(uuid) && !data.storedEntities.containsKey(uuid));
        ListTag names = root.getList("displayNames", 10);
        for (int i = 0; i < names.size(); ++i) {
            String name;
            CompoundTag entry7 = names.getCompound(i);
            if (!entry7.hasUUID("uuid") || (name = entry7.getString("name")).isBlank()) continue;
            data.displayNames.put(entry7.getUUID("uuid"), name);
        }
        ListTag vaultList = root.getList("vault", 10);
        for (int i = 0; i < vaultList.size(); ++i) {
            CompoundTag vaultEntry = vaultList.getCompound(i);
            if (!vaultEntry.hasUUID("uuid") || !vaultEntry.contains("entity", 10)) continue;
            data.vault.add(PlayerCompanionDataCodec.loadVaultEntry(vaultEntry));
        }
        ListTag backupList = root.getList("backups", 10);
        for (int i = 0; i < backupList.size(); ++i) {
            CompoundTag backupEntry = backupList.getCompound(i);
            if (!backupEntry.contains("state", 10)) continue;
            data.backups.add(new PlayerCompanionData.BackupEntry(backupEntry.getLong("savedAt"),
                    backupEntry.getLong("createdAtEpochMillis"), backupEntry.getString("reason"),
                    backupEntry.getCompound("state"),
                    backupEntry.getInt("formatVersion"), backupEntry.getString("checksum"),
                    backupEntry.contains("manual") ? backupEntry.getBoolean("manual")
                            : isLegacyManualBackup(backupEntry.getString("reason"))));
        }
        PlayerCompanionArchiveService.normalizeBackups(data, 6, 4);
        ListTag mountEligible = root.getList("mountEligible", 10);
        for (int i = 0; i < mountEligible.size(); ++i) {
            CompoundTag entry8 = mountEligible.getCompound(i);
            if (!entry8.hasUUID("uuid")) continue;
            data.markMountEligible(entry8.getUUID("uuid"));
        }
        ListTag vehicleMounts = root.getList("vehicleMounts", 10);
        for (int i = 0; i < vehicleMounts.size(); ++i) {
            CompoundTag entry8 = vehicleMounts.getCompound(i);
            if (!entry8.hasUUID("uuid")) continue;
            UUID uuid = entry8.getUUID("uuid");
            data.markVehicleMount(uuid);
            data.addVehicle(uuid);
        }
        readEffectStyles(root, data);
        readAnimationStyles(root, data);
        readSpellBindings(root, data);
        readPendingSpellItemReturns(root, data);
        ListTag vehicles = root.getList("vehicles", 10);
        data.vehicles.clear();
        for (int i = 0; i < vehicles.size(); ++i) {
            CompoundTag vehicleEntry = vehicles.getCompound(i);
            if (!vehicleEntry.hasUUID("uuid")) continue;
            UUID uuid = vehicleEntry.getUUID("uuid");
            if (!data.vehicles.contains(uuid)) {
                data.vehicles.add(uuid);
            }
        }
        for (UUID legacyVehicle : List.copyOf(data.vehicleMounts)) {
            if (!data.vehicles.contains(legacyVehicle)) {
                data.vehicles.add(legacyVehicle);
            }
        }
        boolean hasVehicleWheelSlots = root.getBoolean("vehicleWheelSlotsConfigured")
                || !root.getList("vehicleWheelSlots", 10).isEmpty();
        data.vehicleWheelSlotsConfigured = hasVehicleWheelSlots;
        ListTag vehicleSlots = root.getList("vehicleWheelSlots", 10);
        data.vehicleWheelSlots.clear();
        for (int i = 0; i < vehicleSlots.size(); ++i) {
            CompoundTag slotEntry = vehicleSlots.getCompound(i);
            if (!slotEntry.hasUUID("uuid")) continue;
            UUID uuid = slotEntry.getUUID("uuid");
            if (data.vehicles.contains(uuid) && !data.vehicleWheelSlots.contains(uuid)) {
                data.vehicleWheelSlots.add(uuid);
            }
        }
        if (!hasVehicleWheelSlots && data.vehicleWheelSlots.isEmpty()) {
            data.vehicleWheelSlots.addAll(data.vehicles);
            data.vehicleWheelSlotsConfigured = true;
        }
        data.vehicleActiveIndex = root.getInt("vehicleIndex");
        if (root.hasUUID("vehicleDeployed")) {
            UUID deployedVehicle = root.getUUID("vehicleDeployed");
            if (data.vehicles.contains(deployedVehicle)) {
                data.deployedVehicle = deployedVehicle;
            }
        }
        for (UUID vehicleUuid : data.vehicles) {
            data.queueSpellItemReturns(vehicleUuid);
            data.companions.get(CompanionKind.MOUNT).remove(vehicleUuid);
            data.companions.get(CompanionKind.COMPANION).remove(vehicleUuid);
            data.wheelSlots.get(CompanionKind.MOUNT).remove(vehicleUuid);
            data.wheelSlots.get(CompanionKind.COMPANION).remove(vehicleUuid);
            data.clearDeployed(CompanionKind.MOUNT, vehicleUuid);
            data.clearDeployed(CompanionKind.COMPANION, vehicleUuid);
        }
        readTeams(root, data);
        readDeadRecords(root, data);
        data.setUiSettings(com.kuzhi.findme.common.FindMeUiSettings.load(root.getCompound("uiSettings")));
        data.setCompanionDeploymentLimit(root.contains("companionDeploymentLimit", 99)
                ? root.getInt("companionDeploymentLimit") : 2);
        data.setCreatureArrivalVoice(!root.contains("creatureArrivalVoice")
                || root.getBoolean("creatureArrivalVoice"));
        data.bindingCinematicSeenTypes.addAll(readStringValueList(
                root.getList("bindingCinematicSeenTypes", 10), "entityType"));
        data.mountReadyAt = root.getLong("mountReadyAt");
        data.companionReadyAt = root.getLong("companionReadyAt");
        if (root.contains("companionMagicMana", 99)) {
            data.setCompanionMagicMana(root.getFloat("companionMagicMana"), root.getLong("companionMagicManaTick"),
                    root.contains("companionMagicCapacity", 99) ? root.getFloat("companionMagicCapacity") : -1.0F);
        }
        readLifecycleStates(root, data);
        readRecoveryRecords(root, data);
        data.clearLifecycleChanges();
        return data;
    }

    /** Decodes an authoritative world root without exposing or copying that root. */
    static PlayerCompanionData loadRoot(CompoundTag root) {
        CompoundTag container = new CompoundTag();
        if (root != null) container.put(ROOT, root);
        return load(container);
    }

    static boolean hasMeaningfulRoot(CompoundTag persistentData) {
        return persistentData.contains(ROOT, 10) && isMeaningfulRoot(persistentData.getCompound(ROOT));
    }

    static boolean hasRoot(CompoundTag persistentData) {
        return persistentData.contains(ROOT, 10);
    }

    static CompoundTag rootCopy(CompoundTag persistentData) {
        return persistentData.contains(ROOT, 10) ? persistentData.getCompound(ROOT).copy() : new CompoundTag();
    }

    /** The caller must immediately pass this to a boundary that performs its own defensive copy. */
    static CompoundTag rootForImmediateStore(CompoundTag persistentData) {
        return persistentData.contains(ROOT, 10) ? persistentData.getCompound(ROOT) : new CompoundTag();
    }

    static void putRoot(CompoundTag persistentData, CompoundTag root) {
        if (root != null) {
            persistentData.put(ROOT, (Tag)root.copy());
        }
    }

    static boolean isMeaningfulRoot(CompoundTag root) {
        if (root == null || root.getAllKeys().isEmpty()) {
            return false;
        }
        for (CompanionKind kind : CompanionKind.values()) {
            String prefix = kind.name().toLowerCase();
            if (!root.getList(prefix + "s", 10).isEmpty()
                    || !root.getList(prefix + "WheelSlots", 10).isEmpty()
                    || !root.getList(prefix + "DeployedList", 10).isEmpty()
                    || root.hasUUID(prefix + "Previous")
                    || root.hasUUID(prefix + "Deployed")) {
                return true;
            }
        }
        return !root.getList("deadCompanions", 10).isEmpty()
                || !root.getList("origins", 10).isEmpty()
                || !root.getList("lastKnownPositions", 10).isEmpty()
                || !root.getList("homePositions", 10).isEmpty()
                || !root.getList("homeNestBlocks", 10).isEmpty()
                || !root.getList("storedEntities", 10).isEmpty()
                || !root.getList("displayNames", 10).isEmpty()
                || !root.getList("lifecycleStates", 10).isEmpty()
                || !root.getList("criticalCompanions", 10).isEmpty()
                || !root.getList("animationStyles", 10).isEmpty()
                || !root.getList("effectStyles", 10).isEmpty()
                || !root.getList("spellBindings", 10).isEmpty()
                || !root.getList("vault", 10).isEmpty()
                || !root.getList("backups", 10).isEmpty()
                || !root.getList("mountEligible", 10).isEmpty()
                || !root.getList("vehicleMounts", 10).isEmpty()
                || !root.getList("vehicles", 10).isEmpty()
                || !root.getList("vehicleWheelSlots", 10).isEmpty()
                || !root.getList("teams", 10).isEmpty()
                || root.contains("teamCounts", 10)
                || root.contains("teamNumbers", 10)
                || root.contains("teamAutoJoinDisabled", 10)
                || !root.getList("deadRecords", 10).isEmpty()
                || !root.getList("recoveryRecords", 10).isEmpty()
                || root.contains("uiSettings", 10)
                || !root.getList("bindingCinematicSeenTypes", 10).isEmpty()
                || root.contains("companionMagicMana", 99)
                || root.hasUUID("vehicleDeployed");
    }

    static void save(PlayerCompanionData data, CompoundTag persistentData) {
        CompoundTag entry;
        CompoundTag root = new CompoundTag();
        for (CompanionKind kind : CompanionKind.values()) {
            UUID deployedUuid;
            root.put(kind.name().toLowerCase() + "s", (Tag)uuidList(data.companions.get((Object)kind)));
            root.put(kind.name().toLowerCase() + "WheelSlots", (Tag)uuidList(data.wheelSlots.get((Object)kind)));
            root.putInt(kind.name().toLowerCase() + "Index", data.activeIndexes.getOrDefault((Object)kind, 0).intValue());
            UUID previousUuid = data.previous.get((Object)kind);
            if (previousUuid != null) {
                root.putUUID(kind.name().toLowerCase() + "Previous", previousUuid);
            }
            if ((deployedUuid = data.deployed.get((Object)kind)) != null) {
                root.putUUID(kind.name().toLowerCase() + "Deployed", deployedUuid);
            }
            root.put(kind.name().toLowerCase() + "DeployedList", (Tag)uuidList(data.deployedLists.get((Object)kind)));
        }
        root.put("deadCompanions", (Tag)deadList(data));
        root.put("origins", (Tag)positionMap(data.origins));
        root.put("lastKnownPositions", (Tag)positionMap(data.lastKnownPositions));
        root.put("homePositions", (Tag)positionMap(data.homePositions));
        root.put("homeNestBlocks", (Tag)positionMap(data.homeNestBlocks));
        root.put("homeHouseIds", (Tag)uuidMap(data.homeHouseIds));
        root.put("storedEntities", (Tag)storedEntityList(data.storedEntities));
        root.put("displayNames", (Tag)displayNameList(data.displayNames));
        root.put("lifecycleStates", (Tag)lifecycleStateList(data));
        root.put("criticalCompanions", (Tag)sortedUuidList(data.criticalCompanions));
        root.put("animationStyles", (Tag)animationStyleList(data));
        root.put("effectStyles", (Tag)effectStyleList(data));
        root.put("spellBindings", (Tag)spellBindingList(data));
        root.put("pendingSpellItemReturns", (Tag)pendingSpellItemReturnList(data));
        ListTag vaultList = new ListTag();
        for (PlayerCompanionData.VaultEntry vaultEntry : data.vault) {
            vaultList.add((Tag)saveVaultEntry(vaultEntry));
        }
        root.put("vault", (Tag)vaultList);
        ListTag backupList = new ListTag();
        for (PlayerCompanionData.BackupEntry backup : data.backups) {
            CompoundTag backupTag = new CompoundTag();
            backupTag.putLong("savedAt", backup.savedAt());
            backupTag.putLong("createdAtEpochMillis", backup.createdAtEpochMillis());
            backupTag.putString("reason", backup.reason());
            backupTag.put("state", (Tag)backup.state());
            backupTag.putInt("formatVersion", backup.formatVersion());
            backupTag.putString("checksum", backup.checksum());
            backupTag.putBoolean("manual", backup.manual());
            backupList.add(backupTag);
        }
        root.put("backups", (Tag)backupList);
        root.put("mountEligible", (Tag)sortedUuidList(data.mountEligible));
        root.put("vehicleMounts", (Tag)sortedUuidList(data.vehicleMounts));
        root.put("vehicles", (Tag)uuidList(data.vehicles));
        root.put("vehicleWheelSlots", (Tag)uuidList(data.vehicleWheelSlots));
        root.put("teams", (Tag)teamList(data));
        root.put("teamCounts", teamCounts(data));
        root.put("teamNumbers", teamNumbers(data));
        root.put("teamAutoJoinDisabled", teamAutoJoinDisabled(data));
        root.put("deadRecords", (Tag)deadRecordList(data));
        root.put("recoveryRecords", (Tag)recoveryRecordList(data));
        root.put("uiSettings", data.uiSettings.save());
        root.putInt("companionDeploymentLimit", data.companionDeploymentLimit());
        root.putBoolean("creatureArrivalVoice", data.creatureArrivalVoice());
        root.put("bindingCinematicSeenTypes", stringValueList(data.bindingCinematicSeenTypes, "entityType"));
        if (data.companionMagicMana >= 0.0F) {
            root.putFloat("companionMagicMana", data.companionMagicMana);
            root.putLong("companionMagicManaTick", data.companionMagicManaTick);
            if (data.companionMagicCapacity >= 0.0F) {
                root.putFloat("companionMagicCapacity", data.companionMagicCapacity);
            }
        }
        root.putBoolean("vehicleWheelSlotsConfigured", data.vehicleWheelSlotsConfigured || !data.vehicles.isEmpty());
        root.putInt("vehicleIndex", data.vehicleActiveIndex);
        if (data.deployedVehicle != null) {
            root.putUUID("vehicleDeployed", data.deployedVehicle);
        }
        root.putLong("mountReadyAt", data.mountReadyAt);
        root.putLong("companionReadyAt", data.companionReadyAt);
        root.putInt("safetySchemaVersion", data.safetySchemaVersion);
        persistentData.put(ROOT, (Tag)root);
    }

    static CompoundTag normalizedRoot(CompoundTag root) {
        CompoundTag source = new CompoundTag();
        putRoot(source, root);
        PlayerCompanionData data = load(source);
        CompoundTag normalized = new CompoundTag();
        save(data, normalized);
        return rootCopy(normalized);
    }


    static CompoundTag createBackupState(PlayerCompanionData data) {
        CompoundTag root = new CompoundTag();
        for (CompanionKind kind : CompanionKind.values()) {
            root.put(kind.name().toLowerCase() + "s", (Tag)uuidList(data.companions.get((Object)kind)));
            root.put(kind.name().toLowerCase() + "WheelSlots", (Tag)uuidList(data.wheelSlots.get((Object)kind)));
            root.putInt(kind.name().toLowerCase() + "Index", data.activeIndexes.getOrDefault((Object)kind, 0).intValue());
            UUID previousUuid = data.previous.get((Object)kind);
            if (previousUuid != null) {
                root.putUUID(kind.name().toLowerCase() + "Previous", previousUuid);
            }
            UUID deployedUuid = data.deployed.get((Object)kind);
            if (deployedUuid != null) {
                root.putUUID(kind.name().toLowerCase() + "Deployed", deployedUuid);
            }
            root.put(kind.name().toLowerCase() + "DeployedList", (Tag)uuidList(data.deployedLists.get((Object)kind)));
        }
        root.put("deadCompanions", (Tag)deadList(data));
        root.put("origins", (Tag)positionMap(data.origins));
        root.put("lastKnownPositions", (Tag)positionMap(data.lastKnownPositions));
        root.put("homePositions", (Tag)positionMap(data.homePositions));
        root.put("homeNestBlocks", (Tag)positionMap(data.homeNestBlocks));
        root.put("homeHouseIds", (Tag)uuidMap(data.homeHouseIds));
        root.put("storedEntities", (Tag)storedEntityList(data.storedEntities));
        root.put("displayNames", (Tag)displayNameList(data.displayNames));
        root.put("lifecycleStates", (Tag)lifecycleStateList(data));
        root.put("criticalCompanions", (Tag)sortedUuidList(data.criticalCompanions));
        root.put("animationStyles", (Tag)animationStyleList(data));
        root.put("effectStyles", (Tag)effectStyleList(data));
        root.put("spellBindings", (Tag)spellBindingList(data));
        root.put("pendingSpellItemReturns", (Tag)pendingSpellItemReturnList(data));
        root.put("mountEligible", (Tag)sortedUuidList(data.mountEligible));
        root.put("vehicleMounts", (Tag)sortedUuidList(data.vehicleMounts));
        root.put("vehicles", (Tag)uuidList(data.vehicles));
        root.put("vehicleWheelSlots", (Tag)uuidList(data.vehicleWheelSlots));
        root.put("teams", (Tag)teamList(data));
        root.put("teamCounts", teamCounts(data));
        root.put("teamNumbers", teamNumbers(data));
        root.put("teamAutoJoinDisabled", teamAutoJoinDisabled(data));
        root.put("deadRecords", (Tag)deadRecordList(data));
        root.put("recoveryRecords", (Tag)recoveryRecordList(data));
        ListTag vaultList = new ListTag();
        for (PlayerCompanionData.VaultEntry vaultEntry : data.vault) {
            vaultList.add((Tag)saveVaultEntry(vaultEntry));
        }
        root.put("vault", (Tag)vaultList);
        root.put("uiSettings", data.uiSettings.save());
        root.putInt("companionDeploymentLimit", data.companionDeploymentLimit());
        root.putBoolean("creatureArrivalVoice", data.creatureArrivalVoice());
        root.put("bindingCinematicSeenTypes", stringValueList(data.bindingCinematicSeenTypes, "entityType"));
        if (data.companionMagicMana >= 0.0F) {
            root.putFloat("companionMagicMana", data.companionMagicMana);
            root.putLong("companionMagicManaTick", data.companionMagicManaTick);
            if (data.companionMagicCapacity >= 0.0F) {
                root.putFloat("companionMagicCapacity", data.companionMagicCapacity);
            }
        }
        root.putBoolean("vehicleWheelSlotsConfigured", data.vehicleWheelSlotsConfigured || !data.vehicles.isEmpty());
        root.putInt("vehicleIndex", data.vehicleActiveIndex);
        if (data.deployedVehicle != null) {
            root.putUUID("vehicleDeployed", data.deployedVehicle);
        }
        return root;
    }

    static void restoreBackupState(PlayerCompanionData data, CompoundTag root) {
        for (CompanionKind kind : CompanionKind.values()) {
            data.companions.get((Object)kind).clear();
            data.companions.get((Object)kind).addAll(readUuidList(root.getList(kind.name().toLowerCase() + "s", 10)));
            data.wheelSlots.get((Object)kind).clear();
            data.wheelSlots.get((Object)kind).addAll(readUuidList(root.getList(kind.name().toLowerCase() + "WheelSlots", 10)));
            data.activeIndexes.put(kind, root.getInt(kind.name().toLowerCase() + "Index"));
            data.previous.remove((Object)kind);
            if (root.hasUUID(kind.name().toLowerCase() + "Previous")) {
                data.previous.put(kind, root.getUUID(kind.name().toLowerCase() + "Previous"));
            }
            data.deployed.remove((Object)kind);
            if (root.hasUUID(kind.name().toLowerCase() + "Deployed")) {
                data.deployed.put(kind, root.getUUID(kind.name().toLowerCase() + "Deployed"));
            }
            data.deployedLists.get((Object)kind).clear();
            data.deployedLists.get((Object)kind).addAll(readUuidList(root.getList(kind.name().toLowerCase() + "DeployedList", 10)));
        }
        data.deadCompanions.clear();
        data.deadKinds.clear();
        ListTag deadList = root.getList("deadCompanions", 10);
        for (int i = 0; i < deadList.size(); ++i) {
            CompoundTag entry = deadList.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            UUID uuid = entry.getUUID("uuid");
            data.deadCompanions.add(uuid);
            try {
                data.deadKinds.put(uuid, CompanionKind.valueOf(entry.getString("kind")));
            }
            catch (IllegalArgumentException ignored) {
                data.deadKinds.put(uuid, CompanionKind.COMPANION);
            }
        }
        readPositionMap(root.getList("origins", 10), data.origins);
        readPositionMap(root.getList("lastKnownPositions", 10), data.lastKnownPositions);
        readPositionMap(root.getList("homePositions", 10), data.homePositions);
        readPositionMap(root.getList("homeNestBlocks", 10), data.homeNestBlocks);
        readUuidMap(root.getList("homeHouseIds", 10), data.homeHouseIds);
        data.storedEntities.clear();
        ListTag storedList = root.getList("storedEntities", 10);
        for (int i = 0; i < storedList.size(); ++i) {
            CompoundTag entry = storedList.getCompound(i);
            if (!entry.hasUUID("uuid") || !entry.contains("entity", 10)) continue;
            data.storedEntities.put(entry.getUUID("uuid"), CompanionStorageService.sanitizedStoredTag(entry.getCompound("entity")));
        }
        data.criticalCompanions.clear();
        data.criticalCompanions.addAll(readUuidList(root.getList("criticalCompanions", 10)));
        data.criticalCompanions.removeIf(uuid -> !data.contains(uuid) && !data.storedEntities.containsKey(uuid));
        data.displayNames.clear();
        ListTag nameList = root.getList("displayNames", 10);
        for (int i = 0; i < nameList.size(); ++i) {
            CompoundTag entry = nameList.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            data.setDisplayName(entry.getUUID("uuid"), entry.getString("name"));
        }
        data.effectStyles.clear();
        data.animationStyles.clear();
        readEffectStyles(root, data);
        readAnimationStyles(root, data);
        data.mountEligible.clear();
        data.mountEligible.addAll(readUuidList(root.getList("mountEligible", 10)));
        data.vehicleMounts.clear();
        data.vehicleMounts.addAll(readUuidList(root.getList("vehicleMounts", 10)));
        data.vehicles.clear();
        data.vehicles.addAll(readUuidList(root.getList("vehicles", 10)));
        for (UUID legacyVehicle : data.vehicleMounts) {
            if (!data.vehicles.contains(legacyVehicle)) {
                data.vehicles.add(legacyVehicle);
            }
        }
        data.vehicleWheelSlots.clear();
        boolean hasVehicleWheelSlots = root.getBoolean("vehicleWheelSlotsConfigured")
                || !root.getList("vehicleWheelSlots", 10).isEmpty();
        data.vehicleWheelSlotsConfigured = hasVehicleWheelSlots;
        data.vehicleWheelSlots.addAll(readUuidList(root.getList("vehicleWheelSlots", 10)));
        data.vehicleWheelSlots.removeIf(uuid -> !data.vehicles.contains(uuid));
        if (!hasVehicleWheelSlots && data.vehicleWheelSlots.isEmpty()) {
            data.vehicleWheelSlots.addAll(data.vehicles);
            data.vehicleWheelSlotsConfigured = true;
        }
        data.vehicleActiveIndex = root.getInt("vehicleIndex");
        data.deployedVehicle = root.hasUUID("vehicleDeployed") ? root.getUUID("vehicleDeployed") : null;
        if (data.deployedVehicle != null && !data.vehicles.contains(data.deployedVehicle)) {
            data.deployedVehicle = null;
        }
        readTeams(root, data);
        readDeadRecords(root, data);
        if (root.contains("vault", 9)) {
            data.vault.clear();
            ListTag vaultList = root.getList("vault", 10);
            for (int i = 0; i < vaultList.size(); ++i) {
                CompoundTag vaultEntry = vaultList.getCompound(i);
                if (!vaultEntry.hasUUID("uuid") || !vaultEntry.contains("entity", 10)) continue;
                data.vault.add(loadVaultEntry(vaultEntry));
            }
        }
        data.setUiSettings(com.kuzhi.findme.common.FindMeUiSettings.load(root.getCompound("uiSettings")));
        data.setCompanionDeploymentLimit(root.contains("companionDeploymentLimit", 99)
                ? root.getInt("companionDeploymentLimit") : 2);
        data.setCreatureArrivalVoice(!root.contains("creatureArrivalVoice")
                || root.getBoolean("creatureArrivalVoice"));
        data.bindingCinematicSeenTypes.clear();
        data.bindingCinematicSeenTypes.addAll(readStringValueList(
                root.getList("bindingCinematicSeenTypes", 10), "entityType"));
        data.lifecycleStates.clear();
        readLifecycleStates(root, data);
        readRecoveryRecords(root, data);
        data.spellBindings.clear();
        readSpellBindings(root, data);
        readPendingSpellItemReturns(root, data);
        data.setCompanionMagicMana(root.contains("companionMagicMana", 99)
                        ? root.getFloat("companionMagicMana") : -1.0F,
                root.getLong("companionMagicManaTick"),
                root.contains("companionMagicCapacity", 99)
                        ? root.getFloat("companionMagicCapacity") : -1.0F);
    }

    private static void readDeadRecords(CompoundTag root, PlayerCompanionData data) {
        data.deadRecords.clear();
        ListTag records = root.getList("deadRecords", 10);
        for (int i = 0; i < records.size(); ++i) {
            DeadCompanionRecord record = DeadCompanionRecord.load(records.getCompound(i));
            if (data.deadCompanions.contains(record.sourceEntityId())) {
                data.deadRecords.put(record.sourceEntityId(), record);
            }
        }
        for (UUID uuid : data.deadCompanions) {
            if (data.deadRecords.containsKey(uuid)) {
                continue;
            }
            CompanionKind kind = data.deadKinds.getOrDefault(uuid, CompanionKind.COMPANION);
            CompoundTag stored = data.storedEntities.get(uuid);
            String entityType = stored == null ? "" : stored.getString("CompanionRescueType");
            String name = data.displayNames.getOrDefault(uuid, stored == null ? uuid.toString().substring(0, 8) : stored.getString("CompanionRescueName"));
            SavedPosition position = data.lastKnownPositions.get(uuid);
            UUID recordId = UUID.nameUUIDFromBytes(("find_me:legacy_dead:" + uuid).getBytes(StandardCharsets.UTF_8));
            data.deadRecords.put(uuid, new DeadCompanionRecord(recordId, uuid, name, entityType, kind, -1, 0L, 0L,
                    position == null ? "" : position.dimension().location().toString(), position == null ? 0.0 : position.x(),
                    position == null ? 0.0 : position.y(), position == null ? 0.0 : position.z(), "鏃х増璁板綍", false, ""));
        }
    }

    private static ListTag deadRecordList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        ArrayList<DeadCompanionRecord> records = new ArrayList<>(data.deadRecords());
        records.sort(Comparator.comparing(DeadCompanionRecord::sourceEntityId));
        for (DeadCompanionRecord record : records) {
            list.add(record.save());
        }
        return list;
    }

    private static void readRecoveryRecords(CompoundTag root, PlayerCompanionData data) {
        data.recoveryRecords.clear();
        ListTag records = root.getList("recoveryRecords", 10);
        for (int i = 0; i < records.size(); i++) {
            RecoveryCompanionRecord record = RecoveryCompanionRecord.load(records.getCompound(i));
            if (data.contains(record.sourceEntityId())
                    && data.lifecycleState(record.sourceEntityId()) == CompanionLifecycleState.RECOVERY) {
                data.recoveryRecords.put(record.sourceEntityId(), record);
            }
        }
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.companions.get(kind)) {
                if (data.lifecycleStates.get(uuid) != CompanionLifecycleState.RECOVERY
                        || data.recoveryRecords.containsKey(uuid)) continue;
                CompoundTag stored = data.storedEntities.get(uuid);
                String entityType = stored == null ? "" : CompanionEntitySnapshots.storedEntityType(stored);
                String name = data.displayNames.getOrDefault(uuid,
                        stored == null ? uuid.toString().substring(0, 8)
                                : CompanionEntitySnapshots.storedEntityName(stored, uuid));
                SavedPosition position = data.lastKnownPositions.get(uuid);
                UUID recordId = UUID.nameUUIDFromBytes(("find_me:legacy_recovery:" + uuid)
                        .getBytes(StandardCharsets.UTF_8));
                data.recoveryRecords.put(uuid, new RecoveryCompanionRecord(recordId, uuid, name, entityType,
                        kind, data.teamIndexOf(uuid), 0L, 0L,
                        position == null ? "" : position.dimension().location().toString(),
                        position == null ? 0.0 : position.x(), position == null ? 0.0 : position.y(),
                        position == null ? 0.0 : position.z(), "legacy_unresolved",
                        "No authoritative live entity or valid stored snapshot was recorded.",
                        CompanionLifecycleState.RECOVERY));
            }
        }
    }

    private static ListTag recoveryRecordList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        ArrayList<RecoveryCompanionRecord> records = new ArrayList<>(data.recoveryRecords());
        records.sort(Comparator.comparing(RecoveryCompanionRecord::sourceEntityId));
        for (RecoveryCompanionRecord record : records) list.add(record.save());
        return list;
    }

    private static void readTeams(CompoundTag root, PlayerCompanionData data) {
        ListTag list = root.getList("teams", 10);
        data.resetTeams();
        CompoundTag counts = root.getCompound("teamCounts");
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            if (counts.contains(target.name(), 99)) {
                data.ensureTeamCount(target, Math.max(1, counts.getInt(target.name())));
            }
        }
        CompoundTag numbers = root.getCompound("teamNumbers");
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            data.setTeamNumbers(target, numbers.getIntArray(target.name()));
        }
        for (int i = 0; i < list.size(); ++i) {
            CompoundTag entry = list.getCompound(i);
            try {
                CompanionTeamTarget target = CompanionTeamTarget.valueOf(entry.getString("target"));
                int index = entry.getInt("index");
                if (index < 0) {
                    continue;
                }
                data.ensureTeamCount(target, index + 1);
                data.setTeam(target, index, readUuidList(entry.getList("entries", 10)));
                data.setTeamName(target, index, entry.getString("name"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag disabled = root.getCompound("teamAutoJoinDisabled");
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            for (int index : disabled.getIntArray(target.name())) {
                data.setTeamAutoJoin(target, index, false);
            }
        }
    }

    private static ListTag teamList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            for (int i = 0; i < data.teamCount(target); ++i) {
                List<UUID> team = data.team(target, i);
                if (team.isEmpty() && data.teamName(target, i).isBlank()) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putString("target", target.name());
                entry.putInt("index", i);
                entry.put("entries", (Tag)uuidList(team));
                entry.putString("name", data.teamName(target, i));
                list.add(entry);
            }
        }
        return list;
    }

    private static CompoundTag teamCounts(PlayerCompanionData data) {
        CompoundTag counts = new CompoundTag();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            counts.putInt(target.name(), data.teamCount(target));
        }
        return counts;
    }

    private static CompoundTag teamNumbers(PlayerCompanionData data) {
        CompoundTag numbers = new CompoundTag();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            int[] values = new int[data.teamCount(target)];
            for (int index = 0; index < values.length; index++) {
                values[index] = data.teamNumber(target, index);
            }
            numbers.putIntArray(target.name(), values);
        }
        return numbers;
    }

    private static CompoundTag teamAutoJoinDisabled(PlayerCompanionData data) {
        CompoundTag disabled = new CompoundTag();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            ArrayList<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < data.teamCount(target); ++i) {
                if (!data.teamAutoJoin(target, i)) {
                    indexes.add(i);
                }
            }
            int[] values = new int[indexes.size()];
            for (int i = 0; i < indexes.size(); ++i) {
                values[i] = indexes.get(i);
            }
            disabled.putIntArray(target.name(), values);
        }
        return disabled;
    }

    private static ListTag deadList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (UUID deadUuid : sortedUuids(data.deadCompanions)) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", deadUuid);
            CompanionKind kind = data.deadKinds.get(deadUuid);
            if (kind != null) {
                entry.putString("kind", kind.name());
            }
            list.add(entry);
        }
        return list;
    }

    private static void readEffectStyles(CompoundTag root, PlayerCompanionData data) {
        ListTag list = root.getList("effectStyles", 10);
        boolean legacyCombinedStyles = !root.contains("animationStyles");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            try {
                UUID uuid = entry.getUUID("uuid");
                CompanionEffectPurpose purpose = CompanionEffectPurpose.valueOf(entry.getString("purpose"));
                CompanionEffectStyle style = CompanionEffectStyle.valueOf(entry.getString("style"));
                data.setEffectStyle(uuid, purpose, style);
                if (legacyCombinedStyles && !style.isLegacyAnimationValue()) {
                    data.setAnimationStyle(uuid, animationPurpose(purpose), legacyAnimation(style));
                }
            }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private static CompanionAnimationPurpose animationPurpose(CompanionEffectPurpose purpose) {
        return switch (purpose) {
            case SUMMON -> CompanionAnimationPurpose.SUMMON;
            case RESCUE -> CompanionAnimationPurpose.RESCUE;
            case STORAGE -> CompanionAnimationPurpose.STORAGE;
        };
    }

    private static CompanionAnimationStyle legacyAnimation(CompanionEffectStyle style) {
        return style == CompanionEffectStyle.NONE || style == CompanionEffectStyle.ENDER
                || style == CompanionEffectStyle.VELOCITY_BURST
                ? CompanionAnimationStyle.NONE : CompanionAnimationStyle.STANDARD;
    }

    private static void readAnimationStyles(CompoundTag root, PlayerCompanionData data) {
        ListTag list = root.getList("animationStyles", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            try {
                data.setAnimationStyle(entry.getUUID("uuid"),
                        CompanionAnimationPurpose.valueOf(entry.getString("purpose")),
                        CompanionAnimationStyle.valueOf(entry.getString("style")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static ListTag animationStyleList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(data.animationStyles.keySet())) {
            Map<CompanionAnimationPurpose, CompanionAnimationStyle> styles = data.animationStyles.get(uuid);
            for (CompanionAnimationPurpose purpose : CompanionAnimationPurpose.values()) {
                CompanionAnimationStyle style = styles.get(purpose);
                if (style == null) continue;
                CompoundTag entry = new CompoundTag();
                entry.putUUID("uuid", uuid);
                entry.putString("purpose", purpose.name());
                entry.putString("style", style.name());
                list.add(entry);
            }
        }
        return list;
    }

    private static ListTag effectStyleList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(data.effectStyles.keySet())) {
            Map<CompanionEffectPurpose, CompanionEffectStyle> styles = data.effectStyles.get(uuid);
            for (CompanionEffectPurpose purpose : CompanionEffectPurpose.values()) {
                CompanionEffectStyle style = styles.get(purpose);
                if (style == null) continue;
                CompoundTag entry = new CompoundTag();
                entry.putUUID("uuid", uuid);
                entry.putString("purpose", purpose.name());
                entry.putString("style", style.name());
                list.add(entry);
            }
        }
        return list;
    }

    private static void readSpellBindings(CompoundTag root, PlayerCompanionData data) {
        ListTag bindings = root.getList("spellBindings", 10);
        for (int index = 0; index < bindings.size(); index++) {
            CompoundTag entry = bindings.getCompound(index);
            if (!entry.hasUUID("uuid")) {
                continue;
            }
            UUID uuid = entry.getUUID("uuid");
            if (entry.contains("slots", 9)) {
                ListTag slots = entry.getList("slots", 10);
                for (int slot = 0; slot < Math.min(slots.size(), PlayerCompanionData.COMPANION_SPELL_SLOT_COUNT); slot++) {
                    CompoundTag slotTag = slots.getCompound(slot);
                    if (slotTag.contains("binding", 10)) {
                        data.setSpellBinding(uuid, slot, CompanionSpellBinding.load(slotTag.getCompound("binding")));
                    }
                }
            } else if (entry.contains("binding", 10)) {
                // test.4/test.5 stored one binding directly; retain it as slot zero.
                data.setSpellBinding(uuid, 0, CompanionSpellBinding.load(entry.getCompound("binding")));
            }
        }
    }

    private static ListTag spellBindingList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(data.spellBindings.keySet())) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            ListTag slots = new ListTag();
            List<CompanionSpellBinding> bindings = data.spellBindings.get(uuid);
            for (int slot = 0; slot < PlayerCompanionData.COMPANION_SPELL_SLOT_COUNT; slot++) {
                CompoundTag slotTag = new CompoundTag();
                if (bindings != null && slot < bindings.size() && bindings.get(slot) != null) {
                    slotTag.put("binding", bindings.get(slot).save());
                }
                slots.add(slotTag);
            }
            entry.put("slots", slots);
            list.add(entry);
        }
        return list;
    }

    private static ListTag pendingSpellItemReturnList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (CompoundTag stored : data.pendingSpellItemReturns) {
            if (stored == null || stored.isEmpty()) continue;
            CompoundTag item = stored.copy();
            item.putByte("Count", (byte)1);
            item.putInt("count", 1);
            list.add(item);
        }
        return list;
    }

    private static void readPendingSpellItemReturns(CompoundTag root, PlayerCompanionData data) {
        data.pendingSpellItemReturns.clear();
        ListTag pendingReturns = root.getList("pendingSpellItemReturns", 10);
        for (int index = 0; index < pendingReturns.size(); index++) {
            CompoundTag item = pendingReturns.getCompound(index).copy();
            if (item.isEmpty()) continue;
            item.putByte("Count", (byte)1);
            item.putInt("count", 1);
            data.pendingSpellItemReturns.add(item);
        }
    }

    private static ListTag storedEntityList(Map<UUID, CompoundTag> storedEntities) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(storedEntities.keySet())) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.put("entity", (Tag)CompanionStorageService.sanitizedStoredTag(storedEntities.get(uuid)));
            list.add(entry);
        }
        return list;
    }

    private static ListTag displayNameList(Map<UUID, String> displayNames) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(displayNames.keySet())) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.putString("name", displayNames.get(uuid));
            list.add(entry);
        }
        return list;
    }

    private static void readLifecycleStates(CompoundTag root, PlayerCompanionData data) {
        ListTag states = root.getList("lifecycleStates", 10);
        for (int index = 0; index < states.size(); index++) {
            CompoundTag entry = states.getCompound(index);
            if (!entry.hasUUID("uuid")) {
                continue;
            }
            try {
                data.lifecycleStates.put(entry.getUUID("uuid"), CompanionLifecycleState.valueOf(entry.getString("state")));
            } catch (IllegalArgumentException ignored) {
                data.lifecycleStates.put(entry.getUUID("uuid"), CompanionLifecycleState.RECOVERY);
            }
        }
        if (!states.isEmpty()) {
            return;
        }
        for (UUID uuid : data.deadCompanions) {
            data.lifecycleStates.put(uuid, CompanionLifecycleState.DEAD);
        }
        for (List<UUID> deployed : data.deployedLists.values()) {
            for (UUID uuid : deployed) {
                data.lifecycleStates.put(uuid, CompanionLifecycleState.DEPLOYED);
            }
        }
        if (data.deployedVehicle != null) {
            data.lifecycleStates.put(data.deployedVehicle, CompanionLifecycleState.DEPLOYED);
        }
        for (UUID uuid : data.storedEntities.keySet()) {
            if (!data.lifecycleStates.containsKey(uuid)) {
                data.lifecycleStates.put(uuid, data.homeNestBlocks.containsKey(uuid)
                        ? CompanionLifecycleState.HOME_STORED
                        : CompanionLifecycleState.STORED);
            }
        }
    }

    private static ListTag lifecycleStateList(PlayerCompanionData data) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(data.lifecycleStates.keySet())) {
            CompoundTag state = new CompoundTag();
            state.putUUID("uuid", uuid);
            state.putString("state", data.lifecycleStates.get(uuid).name());
            list.add(state);
        }
        return list;
    }

    private static ListTag uuidList(Collection<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            list.add(entry);
        }
        return list;
    }

    private static ListTag stringValueList(Collection<String> values, String key) {
        ListTag list = new ListTag();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString(key, value);
            list.add(entry);
        }
        return list;
    }

    private static List<String> readStringValueList(ListTag list, String key) {
        ArrayList<String> values = new ArrayList<String>();
        for (int i = 0; i < list.size(); ++i) {
            String value = list.getCompound(i).getString(key);
            if (value.isBlank()) {
                continue;
            }
            values.add(value);
        }
        return values;
    }

    private static List<UUID> readUuidList(ListTag list) {
        ArrayList<UUID> uuids = new ArrayList<UUID>();
        for (int i = 0; i < list.size(); ++i) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("uuid")) continue;
            uuids.add(entry.getUUID("uuid"));
        }
        return uuids;
    }

    private static ListTag positionMap(Map<UUID, SavedPosition> positions) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(positions.keySet())) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            entry.put("position", (Tag)positions.get(uuid).save());
            list.add(entry);
        }
        return list;
    }

    private static ListTag uuidMap(Map<UUID, UUID> values) {
        ListTag list = new ListTag();
        for (UUID uuid : sortedUuids(values.keySet())) {
            CompoundTag value = new CompoundTag();
            value.putUUID("uuid", uuid);
            value.putUUID("value", values.get(uuid));
            list.add(value);
        }
        return list;
    }

    private static ListTag sortedUuidList(Collection<UUID> uuids) {
        return uuidList(sortedUuids(uuids));
    }

    private static List<UUID> sortedUuids(Collection<UUID> uuids) {
        ArrayList<UUID> sorted = new ArrayList<>(uuids);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    private static void readUuidMap(ListTag list, Map<UUID, UUID> target) {
        for (int index = 0; index < list.size(); index++) {
            CompoundTag value = list.getCompound(index);
            if (value.hasUUID("uuid") && value.hasUUID("value")) {
                target.put(value.getUUID("uuid"), value.getUUID("value"));
            }
        }
    }

    private static void readPositionMap(ListTag list, Map<UUID, SavedPosition> target) {
        target.clear();
        for (int i = 0; i < list.size(); ++i) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("uuid") || !entry.contains("position", 10)) continue;
            target.put(entry.getUUID("uuid"), SavedPosition.load(entry.getCompound("position")));
        }
    }

    private static PlayerCompanionData.VaultEntry loadVaultEntry(CompoundTag tag) {
        CompanionKind kind;
        try {
            kind = CompanionKind.valueOf(tag.getString("kind"));
        }
        catch (IllegalArgumentException ignored) {
            kind = CompanionKind.COMPANION;
        }
        return new PlayerCompanionData.VaultEntry(tag.getUUID("uuid"), kind, tag.getString("entityType"), tag.getString("name"), tag.getCompound("entity"), tag.getLong("savedAt"), tag.getString("reason"));
    }

    private static CompoundTag saveVaultEntry(PlayerCompanionData.VaultEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", entry.uuid());
        tag.putString("kind", entry.kind().name());
        tag.putString("entityType", entry.entityType());
        tag.putString("name", entry.name());
        tag.put("entity", (Tag)entry.entityTag());
        tag.putLong("savedAt", entry.savedAt());
        tag.putString("reason", entry.reason());
        return tag;
    }
}

