package com.kuzhi.findme.server.home;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.HouseCommandAction;
import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.SmallHouseBlockEntity;
import com.kuzhi.findme.network.HouseCommandPacket;
import com.kuzhi.findme.network.HousePagePacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.ui.WarehouseEntityService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Server-side data and permission facade for the AUI small-house page. */
public final class HousePageService {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 8.0 * 8.0;

    private HousePageService() {
    }

    public static void open(ServerPlayer player, BlockPos clickedPos) {
        if (player == null || clickedPos == null) return;
        if (!FindMeModuleService.require(player, FindMeModule.HOUSES)) return;
        if (!(player.serverLevel().getBlockEntity(clickedPos) instanceof SmallHouseBlockEntity blockEntity)) {
            return;
        }
        FindMeWorldSavedData world = FindMeWorldSavedData.get(player.server);
        FindMeWorldSavedData.HouseRecord house = world.house(blockEntity.houseId()).orElse(null);
        if (blockEntity.owner() == null) {
            UUID legacyOwner = uniqueLegacyOwner(player, clickedPos, world);
            if (!player.getUUID().equals(legacyOwner) && !player.hasPermissions(2)) {
                com.kuzhi.findme.server.ui.CompanionMessageService.tell(player,
                        "message.find_me.house_unclaimed", ChatFormatting.YELLOW);
                return;
            }
            blockEntity.initializeOwner(player);
        } else if (house == null) {
            blockEntity.refreshWorldRecord();
        }
        house = world.house(blockEntity.houseId()).orElse(null);
        if (house != null) {
            send(player, house);
        }
    }

    public static void handle(ServerPlayer player, HouseCommandPacket packet) {
        if (player == null || packet == null || packet.action() == null) return;
        if (!FindMeModuleService.require(player, FindMeModule.HOUSES)) return;
        FindMeDebugLogger.info("house", "command received action={} house={} companion={} player={}",
                packet.action(), packet.houseId(), packet.companionId(), player.getUUID());
        FindMeWorldSavedData world = FindMeWorldSavedData.get(player.server);
        FindMeWorldSavedData.HouseRecord house = world.house(packet.houseId()).orElse(null);
        if (house == null) {
            FindMeDebugLogger.info("house", "command rejected reason=house_missing action={} house={} companion={}",
                    packet.action(), packet.houseId(), packet.companionId());
            return;
        }
        ServerLevel houseLevel = houseLevel(player, house);
        if (houseLevel == null || !(houseLevel.getBlockEntity(house.position().blockPos()) instanceof SmallHouseBlockEntity blockEntity)
                || !blockEntity.houseId().equals(house.houseId()) || !houseLevel.getBlockState(house.position().blockPos()).is(ModBlocks.SMALL_HOUSE.get())) {
            FindMeDebugLogger.info("house", "command rejected reason=invalid_house_block action={} house={} companion={}",
                    packet.action(), packet.houseId(), packet.companionId());
            return;
        }
        if (!withinInteractionDistance(player.serverLevel() == houseLevel, player.getX(), player.getY(),
                player.getZ(), house.position())) {
            FindMeDebugLogger.info("house", "command rejected reason=too_far action={} house={} companion={}",
                    packet.action(), packet.houseId(), packet.companionId());
            com.kuzhi.findme.server.ui.CompanionMessageService.tell(player,
                    "message.find_me.house_too_far", ChatFormatting.YELLOW);
            return;
        }
        if (packet.action() == HouseCommandAction.REFRESH) {
            send(player, house);
            return;
        }
        if (!player.getUUID().equals(house.owner())) {
            FindMeDebugLogger.info("house", "command rejected reason=not_owner action={} house={} companion={}",
                    packet.action(), packet.houseId(), packet.companionId());
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        boolean changed = switch (packet.action()) {
            case RENAME -> rename(blockEntity, packet.value());
            case ASSIGN -> assign(player, data, world, house, packet.companionId(), houseLevel);
            case REMOVE -> remove(player, data, world, house, packet.companionId());
            case SET_RESIDENT_MODE -> setResidentMode(player, data, world, house,
                    packet.companionId(), packet.value());
            case SET_ALL_RESIDENT_MODES -> setAllResidentModes(player, data, world, house, packet.value());
            case SET_SETTINGS -> setSettings(player, data, world, house, packet.value());
            case REFRESH -> false;
            case RENAME_RESIDENT -> WarehouseEntityService.renameEntity(player, data, packet.companionId(), packet.value());
        };
        if (changed) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        }
        FindMeWorldSavedData.HouseRecord updated = world.house(house.houseId()).orElse(house);
        FindMeDebugLogger.info("house", "command result action={} house={} companion={} changed={} residents={}",
                packet.action(), house.houseId(), packet.companionId(), changed, updated.residents().size());
        send(player, updated);
    }

    private static boolean rename(SmallHouseBlockEntity blockEntity, String value) {
        String name = value == null ? "" : value.trim();
        if (name.length() > 64) name = name.substring(0, 64);
        if (name.equals(blockEntity.displayName())) return false;
        blockEntity.setDisplayName(name);
        return true;
    }

    private static boolean setSettings(ServerPlayer player, PlayerCompanionData data,
                                       FindMeWorldSavedData world,
                                       FindMeWorldSavedData.HouseRecord house, String value) {
        String[] parts = value == null ? new String[0] : value.split(",", -1);
        if (parts.length != 3) return false;
        try {
            int capacity = Integer.parseInt(parts[0].trim());
            int patrolRadius = Integer.parseInt(parts[1].trim());
            int hardRadius = Integer.parseInt(parts[2].trim());
            boolean changed = world.setHouseSettings(house.houseId(), capacity, patrolRadius, hardRadius);
            if (changed) {
                FindMeWorldSavedData.HouseRecord updated = world.house(house.houseId()).orElse(house);
                for (UUID resident : updated.residents()) {
                    CompanionHomeResidentService.refreshResidentMode(player, data, resident,
                            updated.residentMode(resident));
                }
            }
            return changed;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static boolean withinInteractionDistance(boolean sameDimension, double playerX, double playerY,
                                             double playerZ, SavedPosition housePosition) {
        if (!sameDimension || housePosition == null) {
            return false;
        }
        double dx = playerX - (housePosition.x() + 0.5);
        double dy = playerY - (housePosition.y() + 0.5);
        double dz = playerZ - (housePosition.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= MAX_INTERACTION_DISTANCE_SQR;
    }

    private static boolean setResidentMode(ServerPlayer player, PlayerCompanionData data,
                                           FindMeWorldSavedData world,
                                           FindMeWorldSavedData.HouseRecord house, UUID uuid,
                                           String value) {
        if (uuid == null || !houseResidents(data, house).contains(uuid)) {
            return false;
        }
        HouseResidentMode mode;
        try {
            mode = HouseResidentMode.valueOf(value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (!house.residents().contains(uuid)) {
            world.addResident(house.houseId(), uuid);
        }
        if (!world.setResidentMode(house.houseId(), uuid, mode)) {
            return false;
        }
        CompanionHomeResidentService.refreshResidentMode(player, data, uuid, mode);
        return true;
    }

    private static boolean setAllResidentModes(ServerPlayer player, PlayerCompanionData data,
                                               FindMeWorldSavedData world,
                                               FindMeWorldSavedData.HouseRecord house, String value) {
        HouseResidentMode mode;
        try {
            mode = HouseResidentMode.valueOf(value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        boolean changed = false;
        for (UUID resident : house.residents()) {
            if (world.setResidentMode(house.houseId(), resident, mode)) {
                CompanionHomeResidentService.refreshResidentMode(player, data, resident, mode);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean assign(ServerPlayer player, PlayerCompanionData data, FindMeWorldSavedData world,
                                  FindMeWorldSavedData.HouseRecord house, UUID uuid, ServerLevel houseLevel) {
        CompanionKind kind = uuid == null ? null : data.kindOf(uuid).orElse(null);
        if (kind == null || data.deadList().contains(uuid)) return false;
        if (houseResidents(data, house).contains(uuid)) return false;
        if (!CompanionHomeService.hasResidentCapacity(house, uuid, data)) {
            com.kuzhi.findme.server.ui.CompanionMessageService.tell(player,
                    "message.find_me.house_full", ChatFormatting.YELLOW,
                    house.residentCapacity());
            return false;
        }
        UUID oldHouse = data.homeHouseId(uuid).orElse(null);
        SavedPosition oldHome = data.homePosition(uuid).orElse(null);
        SavedPosition oldNest = data.homeNestBlock(uuid).orElse(null);
        CompanionLifecycleState oldState = data.lifecycleState(uuid);
        BlockPos pos = house.position().blockPos();
        SavedPosition home = SavedPosition.of(houseLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), 0.0f);
        SavedPosition source = SavedPosition.of(houseLevel, pos.getX(), pos.getY(), pos.getZ(), 0.0f, 0.0f);
        data.setHomePosition(uuid, home);
        data.setHomeNestBlock(uuid, source);
        data.setHomeHouseId(uuid, house.houseId());

        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        boolean transitioned;
        if (entity instanceof LivingEntity living && living.isAlive()) {
            transitioned = CompanionHomeResidentService.isAssignedHouseVisible(player, data, uuid)
                    ? CompanionHomeResidentService.sendHomeIfPossible(player, data, kind, living)
                    : CompanionStorageService.storeHomeResidentSilently(player, data, living, "house:assign_out_of_view");
        } else if (data.storedEntity(uuid).isPresent()) {
            data.clearDeployed(kind, uuid);
            data.setLifecycleState(uuid, CompanionLifecycleState.HOME_STORED);
            transitioned = true;
        } else {
            transitioned = false;
        }
        if (!transitioned) {
            restoreAssignment(data, uuid, oldHome, oldNest, oldHouse, oldState);
            FindMeDebugLogger.info("house", "assign rejected reason=transition_failed house={} companion={} oldHouse={}",
                    house.houseId(), uuid, oldHouse);
            return false;
        }
        world.removeResidentFromAllHouses(uuid);
        world.addResident(house.houseId(), uuid);
        return true;
    }

    private static void restoreAssignment(PlayerCompanionData data, UUID uuid, SavedPosition oldHome,
                                          SavedPosition oldNest, UUID oldHouse,
                                          CompanionLifecycleState oldState) {
        data.clearHomePosition(uuid);
        if (oldHome != null) data.setHomePosition(uuid, oldHome);
        if (oldNest != null) data.setHomeNestBlock(uuid, oldNest);
        if (oldHouse != null) data.setHomeHouseId(uuid, oldHouse);
        data.setLifecycleState(uuid, oldState);
    }

    private static boolean remove(ServerPlayer player, PlayerCompanionData data, FindMeWorldSavedData world,
                                  FindMeWorldSavedData.HouseRecord house, UUID uuid) {
        if (uuid == null) {
            FindMeDebugLogger.info("house", "remove rejected reason=missing_uuid house={}", house.houseId());
            return false;
        }
        UUID assignedHouse = data.homeHouseId(uuid).orElse(null);
        boolean listedByHouse = house.residents().contains(uuid);
        boolean mappedToHouse = houseResidents(data, house).contains(uuid);
        if (!listedByHouse && !mappedToHouse) {
            FindMeDebugLogger.info("house", "remove rejected reason=not_resident house={} companion={} assignedHouse={}",
                    house.houseId(), uuid, assignedHouse);
            return false;
        }
        if (!mappedToHouse) {
            world.removeResident(house.houseId(), uuid);
            FindMeDebugLogger.info("house",
                    "remove repaired stale index house={} companion={} assignedHouse={} listed={}",
                    house.houseId(), uuid, assignedHouse, listedByHouse);
            return true;
        }
        CompanionKind kind = data.kindOf(uuid).orElse(null);
        if (kind == null) {
            data.clearHomePosition(uuid);
            CompanionHomeResidentService.clearResident(uuid);
            world.removeResident(house.houseId(), uuid);
            FindMeDebugLogger.info("house",
                    "remove repaired unregistered assignment house={} companion={}", house.houseId(), uuid);
            return true;
        }
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            if (!CompanionLifecycleFacade.storeLiving(player, data, living,
                    CompanionTransientStateService.Reason.AUTO_STORE, "house:remove_resident")) {
                FindMeDebugLogger.info("house", "remove rejected reason=resident_store_failed house={} companion={} entity={}",
                        house.houseId(), uuid, FindMeDebugLogger.entity(living));
                return false;
            }
            data.clearDeployed(kind, uuid);
        }
        data.clearHomePosition(uuid);
        CompanionLifecycleState detachedState = detachedLifecycleState(data.deadList().contains(uuid),
                data.isDeployed(kind, uuid), data.hasStoredEntity(uuid));
        if (detachedState != CompanionLifecycleState.DEPLOYED) {
            data.clearDeployed(kind, uuid);
        }
        data.setLifecycleState(uuid, detachedState);
        world.removeResident(house.houseId(), uuid);
        FindMeDebugLogger.info("house", "remove accepted house={} companion={} assignedHouse={} listed={} clearedAssignment={}",
                house.houseId(), uuid, assignedHouse, listedByHouse, true);
        return true;
    }

    static boolean hasAuthoritativeHouseAssignment(UUID assignedHouse, UUID houseId) {
        return assignedHouse != null && assignedHouse.equals(houseId);
    }

    static CompanionLifecycleState detachedLifecycleState(boolean dead, boolean deployed,
                                                           boolean storedSnapshot) {
        if (dead) return CompanionLifecycleState.DEAD;
        if (deployed) return CompanionLifecycleState.DEPLOYED;
        return storedSnapshot ? CompanionLifecycleState.STORED : CompanionLifecycleState.RECOVERY;
    }

    private static void send(ServerPlayer player, FindMeWorldSavedData.HouseRecord house) {
        FindMeWorldSavedData world = FindMeWorldSavedData.get(player.server);
        PlayerCompanionData data = registryData(player, house.owner());
        Set<UUID> houseResidents = houseResidents(data, house);
        List<HousePagePacket.Resident> residents = new ArrayList<>();
        for (UUID uuid : houseResidents) {
            if (data.kindOf(uuid).isPresent()) {
                residents.add(resident(player, data, uuid, false, house.residentMode(uuid)));
            }
        }
        List<HousePagePacket.Resident> available = new ArrayList<>();
        if (player.getUUID().equals(house.owner())) {
            for (CompanionKind kind : CompanionKind.values()) {
                for (UUID uuid : data.list(kind)) {
                    UUID assignedHouse = assignedHouseId(world, data, uuid);
                    if (!data.deadList().contains(uuid) && !houseResidents.contains(uuid)
                            && !house.houseId().equals(assignedHouse)) {
                        HouseResidentMode mode = assignedHouse == null ? HouseResidentMode.WANDER
                                : world.residentMode(assignedHouse, uuid);
                        available.add(resident(player, data, uuid, assignedHouse != null, mode));
                    }
                }
            }
            available.sort(Comparator.comparing(HousePagePacket.Resident::otherHouse));
        }
        String ownerName = player.getUUID().equals(house.owner()) ? player.getGameProfile().getName() : house.owner().toString().substring(0, 8);
        ServerPlayer owner = player.getServer().getPlayerList().getPlayer(house.owner());
        if (owner != null) ownerName = owner.getGameProfile().getName();
        ModNetwork.sendToPlayer(player, new HousePagePacket(house.houseId(), house.owner(), ownerName,
                house.displayName().isBlank() ? "Small House" : house.displayName(),
                !player.getUUID().equals(house.owner()), house.residentCapacity(), house.patrolRadius(), house.hardRadius(),
                residents, available));
    }

    private static Set<UUID> houseResidents(PlayerCompanionData data,
                                             FindMeWorldSavedData.HouseRecord house) {
        if (data == null || house == null) {
            return Set.of();
        }
        Set<UUID> residents = new LinkedHashSet<>();
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                boolean assignedById = data.homeHouseId(uuid).filter(house.houseId()::equals).isPresent();
                boolean assignedByLegacyPosition = data.homeHouseId(uuid).isEmpty()
                        && data.homeNestBlock(uuid)
                        .filter(nest -> nest.dimension().equals(house.position().dimension())
                                && nest.blockPos().equals(house.position().blockPos()))
                        .isPresent();
                if (assignedById || assignedByLegacyPosition) {
                    residents.add(uuid);
                }
            }
        }
        return Set.copyOf(residents);
    }

    private static UUID assignedHouseId(FindMeWorldSavedData world, PlayerCompanionData data, UUID uuid) {
        UUID explicit = data.homeHouseId(uuid).orElse(null);
        if (explicit != null) {
            return explicit;
        }
        return data.homeNestBlock(uuid)
                .flatMap(world::houseAt)
                .map(FindMeWorldSavedData.HouseRecord::houseId)
                .orElse(null);
    }

    private static PlayerCompanionData registryData(ServerPlayer viewer, UUID owner) {
        ServerPlayer onlineOwner = owner == null ? null : viewer.getServer().getPlayerList().getPlayer(owner);
        if (onlineOwner != null) {
            return CompanionDataService.data(onlineOwner);
        }
        CompoundTag container = new CompoundTag();
        FindMeWorldSavedData.get(viewer.server).playerRoot(owner).ifPresent(root -> container.put("find_me", root.copy()));
        return PlayerCompanionData.load(container);
    }

    private static UUID uniqueLegacyOwner(ServerPlayer viewer, BlockPos housePos,
                                          FindMeWorldSavedData world) {
        Set<UUID> possibleOwners = new LinkedHashSet<>(world.playerUuids());
        for (ServerPlayer online : viewer.getServer().getPlayerList().getPlayers()) {
            possibleOwners.add(online.getUUID());
        }
        UUID match = null;
        for (UUID candidate : possibleOwners) {
            ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(candidate);
            PlayerCompanionData data = online == null
                    ? CompanionDataService.data(viewer.getServer(), candidate)
                    : CompanionDataService.data(online);
            boolean assigned = false;
            for (CompanionKind kind : CompanionKind.values()) {
                for (UUID uuid : data.list(kind)) {
                    SavedPosition nest = data.homeNestBlock(uuid).orElse(null);
                    if (nest != null && nest.dimension().equals(viewer.serverLevel().dimension())
                            && nest.blockPos().equals(housePos)) {
                        assigned = true;
                        break;
                    }
                }
                if (assigned) break;
            }
            if (!assigned) continue;
            if (match != null && !match.equals(candidate)) {
                return null;
            }
            match = candidate;
        }
        return match;
    }

    private static HousePagePacket.Resident resident(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                                     boolean otherHouse, HouseResidentMode mode) {
        CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        String type = entity == null ? data.storedEntityType(uuid).orElse("")
                : net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();
        String name = data.displayName(uuid).orElseGet(() -> entity != null
                ? entity.getDisplayName().getString()
                : data.storedEntityName(uuid).orElse(uuid.toString().substring(0, 8)));
        boolean active = data.isDeployed(kind, uuid) || CompanionHomeResidentService.isResident(uuid);
        // The house page creates at most eight visible preview elements. Sending a full entity NBT snapshot
        // for every off-screen resident defeats that virtualization and stalls the first page open. The
        // owner's normal companion sync already supplies exact preview data; read-only viewers use a
        // lightweight type-based preview for the visible cards.
        return new HousePagePacket.Resident(uuid, name, type, kind, active, data.deadList().contains(uuid),
                data.isCritical(uuid), otherHouse, mode, null);
    }

    private static ServerLevel houseLevel(ServerPlayer player, FindMeWorldSavedData.HouseRecord house) {
        ResourceKey<Level> key = house.position().dimension();
        return player.getServer().getLevel(key);
    }
}
