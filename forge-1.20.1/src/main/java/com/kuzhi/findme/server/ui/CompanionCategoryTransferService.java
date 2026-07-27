package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionCategoryTransferService {
    private CompanionCategoryTransferService() {
    }

    public static Result transfer(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                  CompanionKind targetKind) {
        CompanionKind sourceKind = data.kindOf(uuid).orElse(null);
        if (sourceKind == null || !data.contains(sourceKind, uuid)) {
            return Result.failure("Target no longer exists.");
        }
        if (sourceKind == targetKind) {
            return Result.failure("Category unchanged.");
        }
        FindMeModule targetModule = targetKind == CompanionKind.MOUNT
                ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!FindMeModuleService.enabled(targetModule)) {
            return Result.failure("The destination module is disabled.");
        }

        String busyReason = CompanionLifecycleFacade.busyReason(player, data, uuid);
        if (busyReason != null) {
            logRejected(player, data, uuid, sourceKind, targetKind, busyReason);
            return Result.failure("This creature is busy. Try again later.");
        }
        Entity liveEntity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        boolean homeResident = liveEntity instanceof LivingEntity living
                && CompanionHomeResidentService.isHomeResident(living);
        boolean loaded = liveEntity != null;
        boolean stored = data.storedEntity(uuid).isPresent();
        if (!homeResident && (loaded || !stored || data.isDeployed(sourceKind, uuid))) {
            logRejected(player, data, uuid, sourceKind, targetKind,
                    loaded ? "ENTITY_LOADED" : "NOT_STORED");
            return Result.failure("Recall this creature before changing its category.");
        }
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_category_transfer")) {
            return Result.failure("FindMe could not create a safety backup. Operation cancelled.");
        }
        if (homeResident) {
            LivingEntity living = (LivingEntity)liveEntity;
            if (!CompanionStorageService.snapshotAndDiscardHomeResidentForSummon(player, data, living)) {
                logRejected(player, data, uuid, sourceKind, targetKind, "HOME_RESIDENT_STORE_FAILED");
                return Result.failure("FindMe could not temporarily recall this house resident.");
            }
            data.clearDeployed(sourceKind, uuid);
            data.setLifecycleState(uuid, com.kuzhi.findme.common.CompanionLifecycleState.HOME_STORED);
        }
        if (!data.transferCategory(uuid, targetKind)) {
            if (homeResident) {
                data.setLifecycleState(uuid, com.kuzhi.findme.common.CompanionLifecycleState.HOME_STORED);
                CompanionDataService.save(player, data);
                if (CompanionHomeResidentService.restoreResidentNow(player, data, sourceKind, uuid)) {
                    CompanionDataService.save(player, data);
                }
            }
            return Result.failure("Category changed while the operation was running. Try again.");
        }

        CompanionDataService.save(player, data);
        if (homeResident && CompanionHomeResidentService.isAssignedHouseVisible(player, data, uuid)) {
            CompanionHomeResidentService.restoreResidentNow(player, data, targetKind, uuid);
            CompanionDataService.save(player, data);
        }
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionTeamService.syncToClient(player);
        FindMeDebugLogger.lifecycle("CATEGORY_TRANSFER_COMMITTED", player, uuid, null,
                sourceKind.name(), targetKind.name(), "warehouse:category_transfer", true, false);
        return Result.success(targetKind == CompanionKind.MOUNT
                ? "Moved to mounts." : "Moved to companions.");
    }

    private static void logRejected(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                    CompanionKind sourceKind, CompanionKind targetKind, String reason) {
        FindMeDebugLogger.lifecycle("CATEGORY_TRANSFER_REJECTED", player, uuid, null,
                sourceKind.name(), targetKind.name(), reason,
                data.storedEntity(uuid).isPresent(), false);
    }

    public record Result(boolean success, String message) {
        static Result success(String message) {
            return new Result(true, message);
        }

        static Result failure(String message) {
            return new Result(false, message);
        }
    }
}
