package com.kuzhi.findme.server.home;

import com.kuzhi.findme.server.ui.CompanionMessageService;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.server.lifecycle.CompanionEntityTransferService;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;


import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.SmallHouseBlockEntity;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.lifecycle.CompanionPlacementFinder;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class CompanionHomeService {
    private CompanionHomeService() {
    }

    public static int setActiveHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        Optional<UUID> maybeUuid = data.active(kind);
        if (maybeUuid.isEmpty()) {
            CompanionSummonLineService.showNoRegistered(player, kind);
            return 0;
        }
        return setHome(player, data, kind, maybeUuid.get());
    }

    public static int setIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        return setHome(player, data, kind, maybeUuid.get());
    }

    public static int clearActiveHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        Optional<UUID> maybeUuid = data.active(kind);
        if (maybeUuid.isEmpty()) {
            CompanionSummonLineService.showNoRegistered(player, kind);
            return 0;
        }
        return clearHome(player, data, kind, maybeUuid.get());
    }

    public static int clearIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        return clearHome(player, data, kind, maybeUuid.get());
    }

    public static int setIndexHomeAtHouse(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index, ServerLevel houseLevel, BlockPos housePos) {
        if (kind != CompanionKind.COMPANION) {
            CompanionMessageService.tell(player, "message.find_me.house_companion_only", ChatFormatting.YELLOW);
            return 0;
        }
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        UUID uuid = maybeUuid.get();
        FindMeWorldSavedData world = FindMeWorldSavedData.get(player.server);
        UUID oldHouseId = data.homeHouseId(uuid).orElse(null);
        SavedPosition home = SavedPosition.of(houseLevel, housePos.getX() + 0.5, housePos.getY() + 1.0, housePos.getZ() + 0.5, player.getYRot(), 0.0f);
        SavedPosition source = SavedPosition.of(houseLevel, housePos.getX(), housePos.getY(), housePos.getZ(), 0.0f, 0.0f);
        data.setHomePosition(uuid, home);
        data.setHomeNestBlock(uuid, source);
        if (houseLevel.getBlockEntity(housePos) instanceof SmallHouseBlockEntity house) {
            data.setHomeHouseId(uuid, house.houseId());
            if (oldHouseId != null) {
                world.removeResident(oldHouseId, uuid);
            }
            world.addResident(house.houseId(), uuid);
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionSummonLineService.showHomeSet(player, displayName(data, uuid));
        return 1;
    }

    public static int sendActiveHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        return sendActiveToSavedPosition(player, data, kind, true);
    }

    public static int sendIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        return sendToSavedPosition(player, data, kind, maybeUuid.get(), true);
    }

    public static int sendUuidHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        return sendToSavedPosition(player, data, kind, uuid, true);
    }

    public static int returnActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        return sendActiveToSavedPosition(player, data, kind, false);
    }

    public static void handleHouseHome(ServerPlayer player, CompanionKind kind, CompanionAction action, int index, BlockPos housePos, ResourceLocation dimensionId) {
        if (player == null || housePos == null || dimensionId == null) {
            return;
        }
        if (action != CompanionAction.SET_HOME && action != CompanionAction.GO_HOME && action != CompanionAction.CLEAR_HOME) {
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel houseLevel = player.getServer().getLevel(dimension);
        if (houseLevel == null || !houseLevel.isLoaded(housePos) || !houseLevel.getBlockState(housePos).is(ModBlocks.SMALL_HOUSE.get())
                || !(houseLevel.getBlockEntity(housePos) instanceof SmallHouseBlockEntity house)
                || house.owner() == null || !house.owner().equals(player.getUUID())) {
            CompanionMessageService.tell(player, "message.find_me.house_missing", ChatFormatting.YELLOW);
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<UUID> uuid = data.wheelUuidAt(kind, index);
        if (uuid.isPresent() && CompanionLifecycleFacade.isBusy(player, data, uuid.get())) {
            FindMeDebugLogger.lifecycle("HOME_ACTION_REJECTED_LOCKED", player, uuid.get(), null,
                    CompanionLifecycleFacade.busyReason(player, data, uuid.get()), "HOME", "home:" + action.name().toLowerCase(), data.storedEntity(uuid.get()).isPresent(), false);
            CompanionMessageService.tell(player, "message.find_me.busy", ChatFormatting.YELLOW, data.displayName(uuid.get()).orElse(uuid.get().toString().substring(0, 8)));
            return;
        }
        switch (action) {
            case SET_HOME -> setIndexHomeAtHouse(player, data, kind, index, houseLevel, housePos);
            case GO_HOME -> sendIndexHome(player, data, kind, index);
            case CLEAR_HOME -> clearIndexHome(player, data, kind, index);
            default -> {
            }
        }
    }

    public static void clearHomesForHouse(ServerLevel level, BlockPos housePos) {
        if (level == null || housePos == null || level.getServer() == null) {
            return;
        }
        SmallHouseBlockEntity house = level.getBlockEntity(housePos) instanceof SmallHouseBlockEntity value ? value : null;
        SavedPosition source = SavedPosition.of(level, housePos.getX(), housePos.getY(), housePos.getZ(), 0.0f, 0.0f);
        FindMeWorldSavedData world = FindMeWorldSavedData.get(level.getServer());
        FindMeWorldSavedData.HouseRecord record = house == null
                ? world.houseAt(source).orElse(null)
                : world.house(house.houseId()).orElseGet(() -> world.houseAt(source).orElse(null));
        if (record == null || record.owner() == null) {
            // Legacy 1.2 houses have no owner in their block entity. Process every already-migrated
            // player root now; the destroyed-house marker reconciles players that migrate later.
            Set<UUID> possibleOwners = new LinkedHashSet<>(world.playerUuids());
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                possibleOwners.add(player.getUUID());
            }
            for (UUID possibleOwner : possibleOwners) {
                ServerPlayer online = level.getServer().getPlayerList().getPlayer(possibleOwner);
                PlayerCompanionData data = online == null
                        ? CompanionDataService.data(level.getServer(), possibleOwner)
                        : CompanionDataService.data(online);
                if (hasLegacyHouseResidents(data, source)) {
                    if (online != null) {
                        CompanionSafetyService.createForcedBackup(online, data, "before_legacy_house_break_collect");
                    } else {
                        CompanionSafetyService.createForcedBackup(level.getServer(), possibleOwner, data,
                                "before_legacy_house_break_collect");
                    }
                }
                collectLegacyHouseResidents(level, possibleOwner, online, data, source);
            }
            if (record != null) world.removeHouse(record.houseId());
            else if (house != null) world.removeHouse(house.houseId());
            world.markDestroyedHouse(source);
            for (UUID possibleOwner : possibleOwners) {
                world.markDestroyedHousesReconciled(possibleOwner);
            }
            return;
        }

        UUID ownerUuid = record.owner();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        PlayerCompanionData data = owner == null
                ? CompanionDataService.data(level.getServer(), ownerUuid)
                : CompanionDataService.data(owner);
        if (owner != null) {
            CompanionSafetyService.createForcedBackup(owner, data, "before_house_break_collect");
        } else {
            CompanionSafetyService.createForcedBackup(level.getServer(), ownerUuid, data,
                    "before_house_break_collect");
        }

        Set<UUID> residents = new LinkedHashSet<>(record.residents());
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.homeHouseId(uuid).filter(record.houseId()::equals).isPresent()) residents.add(uuid);
            }
        }

        int stored = 0;
        int dead = 0;
        int recovered = 0;
        int failed = 0;
        for (UUID uuid : residents) {
            if (uuid == null) continue;
            if (data.deadList().contains(uuid)) {
                data.clearHomePosition(uuid);
                data.setLifecycleState(uuid, CompanionLifecycleState.DEAD);
                CompanionHomeResidentService.clearResident(uuid);
                dead++;
            } else {
                Entity entity = CompanionEntityLookup.findEntity(level.getServer(), uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    if (CompanionStorageService.storeDetachedHomeResident(level.getServer(), ownerUuid, data, living)) {
                        stored++;
                    } else {
                        data.clearHomePosition(uuid);
                        data.setLifecycleState(uuid, CompanionLifecycleState.RECOVERY);
                        failed++;
                    }
                } else {
                    data.clearHomePosition(uuid);
                    CompanionHomeResidentService.clearResident(uuid);
                    if (data.storedEntity(uuid).isPresent()) {
                        data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
                        stored++;
                    } else {
                        data.setLifecycleState(uuid, CompanionLifecycleState.RECOVERY);
                        recovered++;
                    }
                }
            }
            world.removeResident(record.houseId(), uuid);
        }
        CompanionDataService.save(level.getServer(), ownerUuid, data);
        world.removeHouse(record.houseId());
        world.markDestroyedHouse(source);
        world.markDestroyedHousesReconciled(ownerUuid);
        if (owner != null) {
            CompanionSyncService.syncToClient(owner, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(owner, CompanionKind.COMPANION);
        }
        FindMeDebugLogger.info("house", "broken house collected house={} owner={} residents={} stored={} dead={} recovery={} failed={}",
                record.houseId(), ownerUuid, residents.size(), stored, dead, recovered, failed);
    }

    private static void collectLegacyHouseResidents(ServerLevel level, UUID ownerUuid, ServerPlayer owner,
                                                     PlayerCompanionData data, SavedPosition source) {
        int matched = 0;
        int stored = 0;
        int recovery = 0;
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.homeNestBlock(uuid).filter(saved -> sameBlock(saved, source)).isEmpty()) {
                    continue;
                }
                matched++;
                if (data.deadList().contains(uuid)) {
                    data.clearHomePosition(uuid);
                    data.setLifecycleState(uuid, CompanionLifecycleState.DEAD);
                    CompanionHomeResidentService.clearResident(uuid);
                    continue;
                }
                Entity entity = CompanionEntityLookup.findEntity(level.getServer(), uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()
                        && CompanionStorageService.storeDetachedHomeResident(
                        level.getServer(), ownerUuid, data, living)) {
                    stored++;
                    continue;
                }
                data.clearHomePosition(uuid);
                CompanionHomeResidentService.clearResident(uuid);
                if (data.storedEntity(uuid).isPresent()) {
                    data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
                    stored++;
                } else {
                    data.setLifecycleState(uuid, CompanionLifecycleState.RECOVERY);
                    recovery++;
                }
            }
        }
        if (matched == 0) {
            return;
        }
        CompanionDataService.save(level.getServer(), ownerUuid, data);
        if (owner != null) {
            CompanionSyncService.syncToClient(owner, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(owner, CompanionKind.COMPANION);
        }
        FindMeDebugLogger.info("house",
                "legacy broken house reconciled owner={} position={} matched={} stored={} recovery={}",
                ownerUuid, source.blockPos(), matched, stored, recovery);
    }

    private static boolean hasLegacyHouseResidents(PlayerCompanionData data, SavedPosition source) {
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.homeNestBlock(uuid).filter(saved -> sameBlock(saved, source)).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sameBlock(SavedPosition first, SavedPosition second) {
        return first != null && second != null
                && first.dimension().equals(second.dimension())
                && first.blockPos().equals(second.blockPos());
    }

    public static int returnUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        return sendToSavedPosition(player, data, kind, uuid, false);
    }

    private static int sendActiveToSavedPosition(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, boolean requireHome) {
        Optional<UUID> maybeUuid = data.active(kind);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionMessageService.label(kind));
            return 0;
        }
        return sendToSavedPosition(player, data, kind, maybeUuid.get(), requireHome);
    }

    private static int setHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        if (rejectBusy(player, data, kind, uuid, "home:set")) {
            return 0;
        }
        SavedPosition home = SavedPosition.of(player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        data.setHomePosition(uuid, home);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionSummonLineService.showHomeSet(player, displayName(data, uuid));
        return 1;
    }

    private static int clearHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        if (rejectBusy(player, data, kind, uuid, "home:clear")) {
            return 0;
        }
        UUID houseId = data.homeHouseId(uuid).orElse(null);
        data.clearHomePosition(uuid);
        if (houseId != null) {
            FindMeWorldSavedData.get(player.server).removeResident(houseId, uuid);
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionSummonLineService.showHomeCleared(player, displayName(data, uuid));
        return 1;
    }

    public static int clearHomesForHouse(ServerPlayer player, PlayerCompanionData data, SavedPosition source) {
        int removed = data.clearHomesForNestBlock(source);
        if (removed > 0) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        }
        return removed;
    }

    public static int clearHomesForHouse(ServerPlayer player, PlayerCompanionData data, UUID houseId) {
        if (player == null || data == null || houseId == null) {
            return 0;
        }
        int removed = 0;
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.homeHouseId(uuid).filter(houseId::equals).isEmpty()) {
                    continue;
                }
                data.clearHomePosition(uuid);
                FindMeWorldSavedData.get(player.server).removeResident(houseId, uuid);
                removed++;
            }
        }
        if (removed > 0) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        }
        return removed;
    }

    private static int sendToSavedPosition(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid, boolean requireHome) {
        if (rejectBusy(player, data, kind, uuid, requireHome ? "home:go" : "home:return")) {
            return 0;
        }
        if (CompanionStorageService.isStoragePending(uuid)) {
            CompanionSummonLineService.showBusy(player, data, kind, uuid);
            return 0;
        }
        Optional<SavedPosition> maybeTarget = data.homePosition(uuid);
        boolean usingHome = maybeTarget.isPresent();
        if (maybeTarget.isEmpty() && !requireHome) {
            maybeTarget = data.origin(uuid);
        }
        if (maybeTarget.isEmpty()) {
            CompanionMessageService.tell(player, requireHome ? "message.find_me.no_home" : "message.find_me.no_origin", ChatFormatting.YELLOW, CompanionMessageService.label(kind));
            return 0;
        }
        SavedPosition target = maybeTarget.get();
        ServerLevel destination = player.getServer().getLevel(target.dimension());
        if (destination == null) {
            CompanionMessageService.tell(player, "message.find_me.dimension_unavailable", ChatFormatting.YELLOW, new Object[0]);
            destination = player.serverLevel();
        }
        ServerLevel restoreDestination = destination;
        BlockPos safe = CompanionPlacementFinder.findSafe(destination, target.blockPos()).orElse(target.blockPos());
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElseGet(() -> CompanionLifecycleFacade.restoreStored(restoreDestination, player, data, uuid, safe, target.yRot(), target.xRot(), "home:go").orElse(null));
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            CompanionMessageService.tell(player, "message.find_me.unavailable", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        if (player.getVehicle() == living) {
            player.stopRiding();
        }
        if (living.isPassenger()) {
            living.stopRiding();
        }
        living = CompanionEntityTransferService.moveEntityTo(living, destination, safe, target.yRot(), target.xRot());
        data.clearDeployed(kind, living.getUUID());
        data.setLastKnownPosition(living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
        data.setReadyAt(kind, player.serverLevel().getGameTime() + (long)Config.summonCooldownTicks);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionMessageService.tell(player, usingHome ? "message.find_me.returned_home" : "message.find_me.returned", ChatFormatting.GRAY, living.getDisplayName());
        return 1;
    }

    private static String displayName(PlayerCompanionData data, UUID uuid) {
        return data.displayName(uuid)
                .orElseGet(() -> data.storedEntity(uuid)
                        .map(tag -> CompanionEntitySnapshots.storedEntityName(tag, uuid))
                        .orElse(uuid.toString().substring(0, 8)));
    }

    private static boolean rejectBusy(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid, String source) {
        String busyReason = CompanionLifecycleFacade.busyReason(player, data, uuid);
        if (busyReason == null) {
            return false;
        }
        FindMeDebugLogger.lifecycle("HOME_ACTION_REJECTED_LOCKED", player, uuid, null,
                busyReason, "HOME", source, data.storedEntity(uuid).isPresent(), false);
        CompanionMessageService.tell(player, "message.find_me.busy", ChatFormatting.YELLOW, displayName(data, uuid));
        CompanionSyncService.syncToClient(player, kind);
        return true;
    }
}


