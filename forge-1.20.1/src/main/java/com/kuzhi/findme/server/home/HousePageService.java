package com.kuzhi.findme.server.home;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.HouseCommandAction;
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
import com.kuzhi.findme.server.safety.CompanionSafetyService;
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
        if (packet.action() == HouseCommandAction.REFRESH) {
            send(player, house);
            return;
        }
        if (!player.getUUID().equals(house.owner())) {
            FindMeDebugLogger.info("house", "command rejected reason=not_owner action={} house={} companion={}",
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
        PlayerCompanionData data = CompanionDataService.data(player);
        boolean changed = switch (packet.action()) {
            case RENAME -> rename(blockEntity, packet.value());
            case ASSIGN -> assign(player, data, world, house, packet.companionId(), houseLevel);
            case REMOVE -> remove(player, data, world, house, packet.companionId());
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

    private static boolean assign(ServerPlayer player, PlayerCompanionData data, FindMeWorldSavedData world,
                                  FindMeWorldSavedData.HouseRecord house, UUID uuid, ServerLevel houseLevel) {
        CompanionKind kind = uuid == null ? null : data.kindOf(uuid).orElse(null);
        if (kind == null || data.deadList().contains(uuid)) return false;
        if (data.homeHouseId(uuid).filter(house.houseId()::equals).isPresent()) return false;
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
        if (oldHouse != null) world.removeResident(oldHouse, uuid);
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
        boolean mappedToHouse = house.houseId().equals(assignedHouse);
        if (!listedByHouse && !mappedToHouse) {
            FindMeDebugLogger.info("house", "remove rejected reason=not_resident house={} companion={} assignedHouse={}",
                    house.houseId(), uuid, assignedHouse);
            return false;
        }
        CompanionKind kind = data.kindOf(uuid).orElse(null);
        if (kind == null) {
            FindMeDebugLogger.info("house", "remove rejected reason=missing_kind house={} companion={}", house.houseId(), uuid);
            return false;
        }
        CompanionLifecycleState previousState = data.lifecycleState(uuid);
        boolean removeAuthoritativeAssignment = mappedToHouse || assignedHouse == null;
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (removeAuthoritativeAssignment && entity instanceof LivingEntity living && living.isAlive()) {
            if (!CompanionLifecycleFacade.storeLiving(player, data, living,
                    CompanionTransientStateService.Reason.AUTO_STORE, "house:remove_resident")) {
                FindMeDebugLogger.info("house", "remove rejected reason=resident_store_failed house={} companion={} entity={}",
                        house.houseId(), uuid, FindMeDebugLogger.entity(living));
                return false;
            }
            data.clearDeployed(kind, uuid);
        }
        if (removeAuthoritativeAssignment) {
            data.clearHomePosition(uuid);
            if (previousState == CompanionLifecycleState.HOME_ACTIVE
                    || previousState == CompanionLifecycleState.HOME_STORED) {
                data.setLifecycleState(uuid, data.isDeployed(kind, uuid)
                        ? CompanionLifecycleState.DEPLOYED
                        : CompanionLifecycleState.STORED);
            }
        }
        world.removeResident(house.houseId(), uuid);
        FindMeDebugLogger.info("house", "remove accepted house={} companion={} assignedHouse={} listed={} clearedAssignment={}",
                house.houseId(), uuid, assignedHouse, listedByHouse, removeAuthoritativeAssignment);
        return true;
    }

    private static void send(ServerPlayer player, FindMeWorldSavedData.HouseRecord house) {
        FindMeWorldSavedData world = FindMeWorldSavedData.get(player.server);
        PlayerCompanionData data = registryData(player, house.owner());
        if (player.getUUID().equals(house.owner()) && repairHouseAssignments(player, data, world)) {
            CompanionDataService.save(player, data);
            house = world.house(house.houseId()).orElse(house);
        }
        List<HousePagePacket.Resident> residents = new ArrayList<>();
        for (UUID uuid : house.residents()) {
            if (data.kindOf(uuid).isPresent()) residents.add(resident(player, data, uuid, false));
        }
        List<HousePagePacket.Resident> available = new ArrayList<>();
        if (player.getUUID().equals(house.owner())) {
            for (CompanionKind kind : CompanionKind.values()) {
                for (UUID uuid : data.list(kind)) {
                    UUID assignedHouse = data.homeHouseId(uuid).orElse(null);
                    if (!data.deadList().contains(uuid) && !house.residents().contains(uuid)
                            && !house.houseId().equals(assignedHouse)) {
                        available.add(resident(player, data, uuid, assignedHouse != null));
                    }
                }
            }
            available.sort(Comparator.comparing(HousePagePacket.Resident::otherHouse));
        }
        String ownerName = player.getUUID().equals(house.owner()) ? player.getGameProfile().getName() : house.owner().toString().substring(0, 8);
        ServerPlayer owner = player.getServer().getPlayerList().getPlayer(house.owner());
        if (owner != null) ownerName = owner.getGameProfile().getName();
        ModNetwork.sendToPlayer(player, new HousePagePacket(house.houseId(), house.owner(), ownerName,
                house.displayName().isBlank() ? "Small House" : house.displayName(), !player.getUUID().equals(house.owner()), residents, available));
    }

    private static boolean repairHouseAssignments(ServerPlayer owner, PlayerCompanionData data,
                                                  FindMeWorldSavedData world) {
        List<FindMeWorldSavedData.HouseRecord> houses = world.housesOwnedBy(owner.getUUID());
        Set<UUID> registered = new LinkedHashSet<>();
        for (CompanionKind kind : CompanionKind.values()) registered.addAll(data.list(kind));
        int missingIds = 0;
        int membershipChanges = 0;
        for (UUID uuid : registered) {
            FindMeWorldSavedData.HouseRecord desired = desiredHouse(data, uuid, houses);
            if (desired == null) continue;
            if (data.homeHouseId(uuid).isEmpty()) missingIds++;
            for (FindMeWorldSavedData.HouseRecord candidate : houses) {
                boolean shouldContain = candidate.houseId().equals(desired.houseId());
                if (candidate.residents().contains(uuid) != shouldContain) membershipChanges++;
            }
        }
        if (missingIds <= 0 && membershipChanges <= 0) return false;
        if (!CompanionSafetyService.createForcedBackup(owner, data, "before_house_assignment_repair")) {
            FindMeDebugLogger.info("house", "assignment repair rejected reason=backup_failed owner={} missingIds={} membershipChanges={}",
                    owner.getUUID(), missingIds, membershipChanges);
            return false;
        }
        int repairedIds = 0;
        int addedMemberships = 0;
        int removedMemberships = 0;
        for (UUID uuid : registered) {
            FindMeWorldSavedData.HouseRecord desired = desiredHouse(data, uuid, houses);
            if (desired == null) continue;
            if (data.homeHouseId(uuid).isEmpty()) {
                data.setHomeHouseId(uuid, desired.houseId());
                repairedIds++;
            }
            for (FindMeWorldSavedData.HouseRecord candidate : houses) {
                boolean listed = candidate.residents().contains(uuid);
                if (candidate.houseId().equals(desired.houseId())) {
                    if (!listed) {
                        world.addResident(candidate.houseId(), uuid);
                        addedMemberships++;
                    }
                } else if (listed) {
                    world.removeResident(candidate.houseId(), uuid);
                    removedMemberships++;
                }
            }
        }
        FindMeDebugLogger.info("house", "assignment repair completed owner={} repairedIds={} addedMemberships={} removedMemberships={}",
                owner.getUUID(), repairedIds, addedMemberships, removedMemberships);
        return repairedIds > 0 || addedMemberships > 0 || removedMemberships > 0;
    }

    private static FindMeWorldSavedData.HouseRecord desiredHouse(PlayerCompanionData data, UUID uuid,
                                                                 List<FindMeWorldSavedData.HouseRecord> houses) {
        UUID assigned = data.homeHouseId(uuid).orElse(null);
        if (assigned != null) {
            for (FindMeWorldSavedData.HouseRecord house : houses) {
                if (house.houseId().equals(assigned)) return house;
            }
            return null;
        }
        SavedPosition nest = data.homeNestBlock(uuid).orElse(null);
        if (nest == null) return null;
        for (FindMeWorldSavedData.HouseRecord house : houses) {
            if (nest.dimension().equals(house.position().dimension())
                    && nest.blockPos().equals(house.position().blockPos())) {
                return house;
            }
        }
        return null;
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

    private static HousePagePacket.Resident resident(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                                     boolean otherHouse) {
        CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        CompoundTag stored = data.storedEntity(uuid).orElse(null);
        String type = entity == null ? stored == null ? "" : CompanionEntitySnapshots.storedEntityType(stored)
                : net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();
        String name = data.displayName(uuid).orElseGet(() -> entity != null
                ? entity.getDisplayName().getString()
                : stored == null ? uuid.toString().substring(0, 8) : CompanionEntitySnapshots.storedEntityName(stored, uuid));
        boolean active = data.isDeployed(kind, uuid) || CompanionHomeResidentService.isResident(uuid);
        // The house page creates at most eight visible preview elements. Sending a full entity NBT snapshot
        // for every off-screen resident defeats that virtualization and stalls the first page open. The
        // owner's normal companion sync already supplies exact preview data; read-only viewers use a
        // lightweight type-based preview for the visible cards.
        return new HousePagePacket.Resident(uuid, name, type, kind, active, data.deadList().contains(uuid), otherHouse, null);
    }

    private static ServerLevel houseLevel(ServerPlayer player, FindMeWorldSavedData.HouseRecord house) {
        ResourceKey<Level> key = house.position().dimension();
        return player.getServer().getLevel(key);
    }
}
