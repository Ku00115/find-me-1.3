package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.WarehouseOperationResultPacket;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.common.WarehouseEntityAction;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class WarehouseEntityService {
    private static final String BACKUP_REQUIRED_FAILED = "FindMe could not create a safety backup. Operation cancelled.";

    private WarehouseEntityService() {
    }

    public static void handle(ServerPlayer player, WarehouseEntityAction action, UUID uuid, String value) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (uuid == null) {
            result(player, false, "Invalid target.");
            return;
        }
        String busyReason = CompanionLifecycleFacade.busyReason(player, data, uuid);
        if (action != WarehouseEntityAction.RENAME && busyReason != null) {
            FindMeDebugLogger.lifecycle("WAREHOUSE_REJECTED_LOCKED", player, uuid, null,
                    busyReason, "WAREHOUSE", "warehouse:" + action.name().toLowerCase(), data.storedEntity(uuid).isPresent(), false);
            result(player, false, "This target is busy. Try again later.");
            return;
        }
        switch (action) {
            case RENAME -> rename(player, data, uuid, value);
            case MOVE_TO_COMPANION -> move(player, data, uuid, CompanionKind.COMPANION);
            case MOVE_TO_MOUNT -> move(player, data, uuid, CompanionKind.MOUNT);
            case REMOVE_FROM_TEAM -> removeFromTeam(player, data, uuid);
            case RELEASE -> release(player, data, uuid);
            case DELETE_DEAD -> deleteDead(player, data, uuid);
        }
    }

    public static void notifyAutoTeam(ServerPlayer player, PlayerCompanionData data, UUID uuid, String name) {
        int team = data.teamIndexOf(uuid);
        if (team >= 0) {
            result(player, true, (name == null || name.isBlank() ? "New member" : name) + " joined team " + (team + 1));
        }
    }

    private static void rename(ServerPlayer player, PlayerCompanionData data, UUID uuid, String value) {
        if (!renameEntity(player, data, uuid, value)) {
            result(player, false, "Target no longer exists.");
            return;
        }
        result(player, true, "Name saved.");
    }

    public static boolean renameEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, String value) {
        if (player == null || data == null || uuid == null) return false;
        if (!data.contains(uuid) && !data.containsVehicle(uuid) && !data.deadList().contains(uuid)) {
            return false;
        }
        String name = value == null ? "" : value.trim();
        int max = data.uiSettings().nameMaxLength();
        if (name.length() > max) {
            name = name.substring(0, max);
        }
        if (!data.uiSettings().allowNameColors()) {
            name = name.replace("\u00A7", "");
        }
        data.setDisplayName(uuid, name);
        DeadCompanionRecord old = data.deadRecord(uuid).orElse(null);
        if (old != null) {
            data.putDeadRecord(new DeadCompanionRecord(old.recordId(), old.sourceEntityId(), name, old.entityType(), old.previousKind(),
                    old.previousTeamIndex(), old.deathTime(), old.worldDay(), old.dimension(), old.x(), old.y(), old.z(), old.deathCause(),
                    old.recoverable(), old.recoveryRequirements()));
        }
        saveAndSync(player, data);
        return true;
    }

    private static void move(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind target) {
        CompanionCategoryTransferService.Result transfer =
                CompanionCategoryTransferService.transfer(player, data, uuid, target);
        result(player, transfer.success(), transfer.message());
    }

    private static void removeFromTeam(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (!data.removeTeamMember(uuid)) {
            result(player, false, "Target is not in a team.");
            return;
        }
        CompanionDataService.save(player, data);
        CompanionTeamService.syncToClient(player);
        result(player, true, "Removed from team.");
    }

    private static void release(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (data.containsVehicle(uuid)) {
            if (!VehicleManager.releaseVehicle(player, data, uuid, VehicleManager.locateEntity(player.getServer(), data, uuid).orElse(null))) {
                result(player, false, "Vehicle no longer exists.");
                return;
            }
            data.removeTeamMember(uuid);
            saveAndSync(player, data);
            result(player, true, "Vehicle binding removed.");
            return;
        }
        CompanionKind kind = data.kindOf(uuid).orElse(null);
        if (kind == null) {
            result(player, false, "Target no longer exists.");
            return;
        }
        int index = data.list(kind).indexOf(uuid);
        if (!CompanionListService.releaseAndRemove(player, data, kind, index, uuid)) {
            result(player, false, BACKUP_REQUIRED_FAILED);
            return;
        }
        CompanionTeamService.syncToClient(player);
        result(player, true, "Binding removed.");
    }

    private static void deleteDead(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        int index = data.deadList().indexOf(uuid);
        if (index < 0) {
            result(player, false, "Death record no longer exists.");
            return;
        }
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_delete_dead_record")) {
            result(player, false, BACKUP_REQUIRED_FAILED);
            return;
        }
        if (!data.removeDeadAt(index)) {
            result(player, false, "Death record no longer exists.");
            return;
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncDeadToClient(player);
        result(player, true, "Death record deleted. It will not revive.");
    }

    private static void saveAndSync(ServerPlayer player, PlayerCompanionData data) {
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        VehicleManager.syncToClient(player);
        CompanionSyncService.syncDeadToClient(player);
        CompanionTeamService.syncToClient(player);
    }

    private static void result(ServerPlayer player, boolean success, String message) {
        ModNetwork.sendToPlayer(player, new WarehouseOperationResultPacket(success, message));
    }
}

