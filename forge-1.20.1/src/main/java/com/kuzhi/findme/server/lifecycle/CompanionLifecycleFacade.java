package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CompanionLifecycleFacade {
    private CompanionLifecycleFacade() {
    }

    public static boolean summonActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, String source) {
        if (!FindMeModuleService.require(player, moduleFor(kind))) {
            return false;
        }
        Optional<UUID> active = data.active(kind);
        if (active.isPresent() && rejectBusy(player, data, active.get(), kind, "FACADE_SUMMON_REJECTED_LOCKED", source)) {
            return false;
        }
        active.ifPresent(uuid -> {
            CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.DEPLOY);
            logEntry("FACADE_SUMMON", player, data, uuid, "ENTRY", "DEPLOY", source);
        });
        boolean result = CompanionSummonService.summonActive(player, data, kind);
        active.ifPresent(uuid -> logResult(result ? "FACADE_SUMMON_DONE" : "FACADE_SUMMON_FAILED",
                player, data, uuid, source));
        return result;
    }

    public static boolean summonActiveForTacticalOrder(ServerPlayer player, PlayerCompanionData data,
                                                        CompanionKind kind, String source) {
        if (!FindMeModuleService.require(player, moduleFor(kind))) {
            return false;
        }
        Optional<UUID> active = data.active(kind);
        if (active.isPresent() && rejectBusy(player, data, active.get(), kind,
                "FACADE_TACTICAL_SUMMON_REJECTED_LOCKED", source)) {
            return false;
        }
        active.ifPresent(uuid -> {
            CompanionTransientStateService.cancelTargetForDeploy(player, data, uuid,
                    CompanionTransientStateService.Reason.DEPLOY);
            logEntry("FACADE_TACTICAL_SUMMON", player, data, uuid, "ENTRY", "DEPLOY", source);
        });
        boolean result = CompanionSummonService.summonActiveForTacticalOrder(player, data, kind);
        active.ifPresent(uuid -> logResult(result ? "FACADE_TACTICAL_SUMMON_DONE" : "FACADE_TACTICAL_SUMMON_FAILED",
                player, data, uuid, source));
        return result;
    }

    public static boolean summonActiveForMountSwitch(ServerPlayer player, PlayerCompanionData data, String source) {
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return false;
        }
        Optional<UUID> active = data.active(CompanionKind.MOUNT);
        if (active.isPresent() && rejectBusy(player, data, active.get(), CompanionKind.MOUNT,
                "FACADE_MOUNT_SWITCH_REJECTED_LOCKED", source)) {
            return false;
        }
        active.ifPresent(uuid -> {
            CompanionTransientStateService.cancelTarget(player, data, uuid,
                    CompanionTransientStateService.Reason.DEPLOY);
            logEntry("FACADE_MOUNT_SWITCH", player, data, uuid, "ENTRY", "DEPLOY", source);
        });
        boolean result = CompanionSummonService.summonActiveForMountSwitch(player, data);
        active.ifPresent(uuid -> logResult(result ? "FACADE_MOUNT_SWITCH_DONE" : "FACADE_MOUNT_SWITCH_FAILED",
                player, data, uuid, source));
        return result;
    }

    public static boolean summonActiveDirect(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, String source) {
        if (!FindMeModuleService.require(player, moduleFor(kind))) {
            return false;
        }
        Optional<UUID> active = data.active(kind);
        if (active.isPresent() && rejectBusy(player, data, active.get(), kind, "FACADE_SUMMON_DIRECT_REJECTED_LOCKED", source)) {
            return false;
        }
        active.ifPresent(uuid -> {
            CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.DEPLOY);
            logEntry("FACADE_SUMMON_DIRECT", player, data, uuid, "ENTRY", "DEPLOY", source);
        });
        boolean result = CompanionSummonService.summonActive(player, data, kind);
        active.ifPresent(uuid -> logResult(result ? "FACADE_SUMMON_DIRECT_DONE" : "FACADE_SUMMON_DIRECT_FAILED",
                player, data, uuid, source));
        return result;
    }

    public static void collectActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, String source) {
        Optional<UUID> uuid = data.active(kind)
                .filter(active -> data.isDeployed(kind, active))
                .or(() -> data.deployed(kind))
                .or(() -> data.active(kind));
        if (uuid.isPresent() && isBusy(player, data, uuid.get())) {
            logEntry("FACADE_COLLECT_REJECTED_LOCKED", player, data, uuid.get(), "ENTRY", "LOCKED", source);
            CompanionSummonLineService.showBusy(player, data, kind, uuid.get());
            return;
        }
        uuid.ifPresent(value -> {
            CompanionTransientStateService.cancelTarget(player, data, value, CompanionTransientStateService.Reason.MANUAL_STORE);
            logEntry("FACADE_COLLECT", player, data, value, "ENTRY", "STORE", source);
        });
        CompanionCollectionService.collectActive(player, data, kind);
        uuid.ifPresent(value -> logResult("FACADE_COLLECT_DONE", player, data, value, source));
    }

    public static boolean collectWheel(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int wheelIndex, String source) {
        Optional<UUID> uuid = data.wheelUuidAt(kind, wheelIndex);
        if (uuid.isEmpty()) {
            return false;
        }
        if (isBusy(player, data, uuid.get())) {
            logEntry("FACADE_COLLECT_REJECTED_LOCKED", player, data, uuid.get(), "ENTRY", "LOCKED", source);
            CompanionSummonLineService.showBusy(player, data, kind, uuid.get());
            return true;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid.get(), CompanionTransientStateService.Reason.MANUAL_STORE);
        logEntry("FACADE_COLLECT", player, data, uuid.get(), "ENTRY", "STORE", source);
        boolean result = CompanionCollectionService.collectIndex(player, data, kind, wheelIndex);
        logResult("FACADE_COLLECT_DONE", player, data, uuid.get(), source);
        return result;
    }

    public static boolean summonOrStoreActiveVehicle(ServerPlayer player, PlayerCompanionData data, String source) {
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return false;
        }
        Optional<UUID> uuid = data.activeVehicle();
        uuid.ifPresent(value -> logEntry("FACADE_VEHICLE_SUMMON", player, data, value, "ENTRY", "DEPLOY_OR_STORE", source));
        boolean result = VehicleManager.summonOrStoreActive(player, data);
        uuid.ifPresent(value -> logResult("FACADE_VEHICLE_SUMMON_DONE", player, data, value, source));
        return result;
    }

    public static boolean recallVehicleIndex(ServerPlayer player, PlayerCompanionData data, int wheelIndex, String source) {
        Optional<UUID> uuid = data.vehicleWheelUuidAt(wheelIndex);
        if (uuid.isEmpty()) {
            return false;
        }
        logEntry("FACADE_VEHICLE_RECALL", player, data, uuid.get(), "ENTRY", "STORE", source);
        boolean result = VehicleManager.recallIndex(player, data, wheelIndex);
        logResult("FACADE_VEHICLE_RECALL_DONE", player, data, uuid.get(), source);
        return result;
    }

    public static boolean storeBoundTarget(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity target, String source) {
        if (player.getVehicle() == target || target.hasPassenger(player)) {
            return true;
        }
        UUID uuid = target.getUUID();
        if (!storeLiving(player, data, target, CompanionTransientStateService.Reason.BINDING, source)) {
            logResult("FACADE_BIND_STORE_FAILED", player, data, uuid, source);
            return false;
        }
        data.clearDeployed(kind, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        logResult("FACADE_BIND_STORE_DONE", player, data, uuid, source);
        return true;
    }

    public static boolean storeLiving(ServerPlayer player, PlayerCompanionData data, LivingEntity living, CompanionTransientStateService.Reason reason, String source) {
        if (player == null || data == null || living == null || !living.isAlive()) {
            return false;
        }
        UUID uuid = living.getUUID();
        String busyReason = busyReason(player, data, uuid);
        if (busyReason != null && !canStoreThroughBusy(reason, busyReason)) {
            rejectBusy(player, data, uuid, data.kindOf(uuid).orElse(CompanionKind.COMPANION), "FACADE_STORE_REJECTED_LOCKED", source);
            return false;
        }
        if (busyReason != null) {
            FindMeDebugLogger.lifecycle("FACADE_STORE_OVERRIDES_PENDING", player, uuid, living,
                    busyReason, "STORE", source, data.storedEntity(uuid).isPresent(), true);
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid, reason);
        logEntry("FACADE_STORE", player, data, uuid, "ACTIVE", "STORE", source);
        boolean result = CompanionStorageService.storeAndDiscard(player, data, living);
        if (result) {
            data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
            CompanionDataService.save(player, data);
        }
        logResult(result ? "FACADE_STORE_DONE" : "FACADE_STORE_FAILED", player, data, uuid, source);
        return result;
    }

    public static Optional<Entity> restoreStored(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, String source) {
        return restoreStored(player.serverLevel(), player, data, uuid, pos, yRot, xRot, source);
    }

    public static Optional<Entity> restoreStored(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredForHome(ServerLevel level, ServerPlayer player,
                                                        PlayerCompanionData data, CompanionKind kind,
                                                        UUID uuid, BlockPos pos, float yRot, float xRot,
                                                        String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityForHome(
                level, player, data, uuid, pos, yRot, xRot);
        if (result.isPresent()) {
            Entity restored = result.get();
            data.clearDeployed(kind, uuid);
            data.setLifecycleState(uuid, CompanionLifecycleState.HOME_ACTIVE);
            data.setLastKnownPosition(uuid, SavedPosition.of(restored.level(), restored.getX(), restored.getY(),
                    restored.getZ(), restored.getYRot(), restored.getXRot()));
            logResult("FACADE_HOME_RESTORE_DONE", player, data, uuid, source);
        } else {
            FindMeDebugLogger.lifecycle("FACADE_HOME_RESTORE_FAILED", player, uuid, null,
                    "HOME_STORED", "HOME_STORED", source, data.storedEntity(uuid).isPresent(), false);
        }
        return result;
    }

    public static boolean recallVehicleUuid(ServerPlayer player, PlayerCompanionData data, UUID uuid, String source) {
        if (uuid == null || !data.containsVehicle(uuid)) return false;
        logEntry("FACADE_VEHICLE_RECALL", player, data, uuid, "ENTRY", "STORE", source);
        boolean result = VehicleManager.recallUuid(player, data, uuid);
        logResult("FACADE_VEHICLE_RECALL_DONE", player, data, uuid, source);
        return result;
    }

    public static boolean collectUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                      UUID uuid, String source) {
        if (uuid == null || !data.contains(kind, uuid)) {
            return false;
        }
        if (isBusy(player, data, uuid)) {
            logEntry("FACADE_COLLECT_REJECTED_LOCKED", player, data, uuid, "ENTRY", "LOCKED", source);
            CompanionSummonLineService.showBusy(player, data, kind, uuid);
            return true;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid,
                CompanionTransientStateService.Reason.MANUAL_STORE);
        logEntry("FACADE_COLLECT", player, data, uuid, "ENTRY", "STORE", source);
        boolean result = CompanionCollectionService.collectUuid(player, data, kind, uuid);
        logResult("FACADE_COLLECT_DONE", player, data, uuid, source);
        return result;
    }

    public static Optional<Entity> restoreStoredDirect(ServerPlayer player, PlayerCompanionData data,
                                                        UUID uuid, BlockPos pos, float yRot, float xRot,
                                                        String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityDirect(
                player.serverLevel(), player, data, uuid, pos, yRot, xRot);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    static Optional<Entity> restoreStoredForJourney(ServerLevel level, ServerPlayer player,
                                                    PlayerCompanionData data, UUID uuid, BlockPos pos,
                                                    float yRot, float xRot, String source) {
        if (!beginJourneyRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityForJourney(
                level, player, data, uuid, pos, yRot, xRot);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredFresh(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityFresh(level, player, data, uuid, pos, yRot, xRot);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredForArrival(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityForArrival(player, data, uuid, pos, yRot, xRot, focus, durationTicks, style, purpose);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredAfterPresentation(ServerLevel level, ServerPlayer player,
            PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot,
            boolean freshRestore, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityAfterPresentation(
                level, player, data, uuid, pos, yRot, xRot, freshRestore);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredForArrival(ServerPlayer player, PlayerCompanionData data,
            UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks,
            RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
            CompanionAnimationPurpose animationPurpose, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityForArrival(player, data,
                uuid, pos, yRot, xRot, focus, durationTicks, style, purpose, animationPurpose);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredForArrival(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityForArrival(level, player, data, uuid, pos, yRot, xRot, focus, durationTicks, style, purpose);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredFreshForArrival(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityFreshForArrival(level, player, data, uuid, pos, yRot, xRot, focus, durationTicks, style, purpose);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static Optional<Entity> restoreStoredFreshForArrival(ServerLevel level, ServerPlayer player,
            PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus,
            int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
            CompanionAnimationPurpose animationPurpose, String source) {
        if (!beginRestore(player, data, uuid, source)) {
            return Optional.empty();
        }
        Optional<Entity> result = CompanionEntityTransferService.restoreStoredEntityFreshForArrival(level,
                player, data, uuid, pos, yRot, xRot, focus, durationTicks, style, purpose, animationPurpose);
        finishRestore(player, data, uuid, result, source);
        return result;
    }

    public static void collectPlayerForLifecycle(ServerPlayer player, LifecycleMode mode) {
        PlayerCompanionData data = CompanionDataService.data(player);
        CompanionTransientStateService.cancelPlayerAll(player, mode.reason);
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.deployedList(kind)) {
                logEntry("FACADE_LIFECYCLE_COLLECT", player, data, uuid, "DEPLOYED", "STORE", mode.source);
            }
        }
        data.deployedVehicle().ifPresent(uuid -> logEntry("FACADE_LIFECYCLE_COLLECT_VEHICLE", player, data, uuid, "DEPLOYED", "STORE", mode.source));
        switch (mode) {
            case TRAVEL -> CompanionCollectionService.collectDeployedForTravel(player);
            case LOGOUT -> CompanionCollectionService.collectDeployedForLogout(player);
            case DEATH -> CompanionCollectionService.collectDeployedOnDeath(player);
        }
        VehicleManager.collectDeployed(player);
        PlayerCompanionData fresh = CompanionDataService.data(player);
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.deployedList(kind)) {
                logResult("FACADE_LIFECYCLE_COLLECT_DONE", player, fresh, uuid, mode.source);
            }
        }
        data.deployedVehicle().ifPresent(uuid -> logResult("FACADE_LIFECYCLE_COLLECT_VEHICLE_DONE", player, fresh, uuid, mode.source));
    }

    public static String busyReason(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        CompanionOperationLockService.ActiveOperation operation = CompanionOperationLockService.get(uuid);
        if (operation != null) {
            return operation.operation().name();
        }
        if (CompanionStorageService.isStoragePending(uuid)) {
            return "STORAGE_PENDING";
        }
        if (VehicleManager.isPendingSummon(uuid)) {
            return "VEHICLE_SUMMON_PENDING";
        }
        if (CompanionPreSpawnPresentationService.isPending(uuid)) {
            return "PRE_SPAWN_PRESENTATION";
        }
        // A busy-state query runs from several tick services. It must never load the
        // entity's last-known chunk just to inspect a transient arrival sequence.
        Entity entity = player == null ? null
                : CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
        if (entity instanceof LivingEntity living && CompanionArrivalSequenceService.isPending(living)) {
            return "ARRIVAL_PENDING";
        }
        return null;
    }

    public static boolean isBusy(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        return busyReason(player, data, uuid) != null;
    }

    private static boolean beginRestore(ServerPlayer player, PlayerCompanionData data, UUID uuid, String source) {
        if (rejectBusy(player, data, uuid, data.kindOf(uuid).orElse(null),
                "FACADE_RESTORE_REJECTED_LOCKED", source)) {
            return false;
        }
        return prepareRestore(player, data, uuid, source);
    }

    private static boolean beginJourneyRestore(ServerPlayer player, PlayerCompanionData data,
                                               UUID uuid, String source) {
        if (!CompanionOperationLockService.heldBy(player, uuid,
                CompanionOperationLockService.Operation.JOURNEY)) {
            FindMeDebugLogger.lifecycle("FACADE_JOURNEY_RESTORE_REJECTED", player, uuid, null,
                    "UNOWNED", "DEPLOY", source, data.storedEntity(uuid).isPresent(), false);
            return false;
        }
        return prepareRestore(player, data, uuid, source);
    }

    private static boolean prepareRestore(ServerPlayer player, PlayerCompanionData data,
                                          UUID uuid, String source) {
        CompanionTransientStateService.cancelTargetForDeploy(player, data, uuid,
                CompanionTransientStateService.Reason.DEPLOY);
        logEntry("FACADE_RESTORE", player, data, uuid, "STORED", "DEPLOY", source);
        return true;
    }

    private static void finishRestore(ServerPlayer player, PlayerCompanionData data, UUID uuid, Optional<Entity> result, String source) {
        if (result.isPresent()) {
            data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
            CompanionDataService.save(player, data);
            logResult("FACADE_RESTORE_DONE", player, data, uuid, source);
        } else {
            FindMeDebugLogger.lifecycle("FACADE_RESTORE_FAILED", player, uuid, null, "STORED", "STORED", source,
                    CompanionDataService.data(player).storedEntity(uuid).isPresent(), false);
        }
    }

    private static void logEntry(String operation, ServerPlayer player, PlayerCompanionData data, UUID uuid, String fromState, String toState, String source) {
        if (!FindMeDebugLogger.enabled()) {
            return;
        }
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid)
                .or(() -> CompanionEntityLookup.findEntity(player.getServer(), uuid))
                .orElse(null);
        FindMeDebugLogger.lifecycle(operation, player, uuid, entity, fromState, toState, source,
                data.storedEntity(uuid).isPresent(), entity != null);
    }

    private static boolean rejectBusy(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind kind, String operation, String source) {
        String busyReason = busyReason(player, data, uuid);
        if (busyReason == null) {
            return false;
        }
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid)
                .or(() -> CompanionEntityLookup.findEntity(player.getServer(), uuid))
                .orElse(null);
        FindMeDebugLogger.lifecycle(operation, player, uuid, entity,
                busyReason, "LOCKED", source, data.storedEntity(uuid).isPresent(), entity != null);
        if (kind != null) {
            CompanionSummonLineService.showBusy(player, data, kind, uuid);
        }
        return true;
    }

    private static boolean canStoreThroughBusy(CompanionTransientStateService.Reason reason, String busyReason) {
        return reason == CompanionTransientStateService.Reason.FAILURE_RECOVERY
                && "ARRIVAL_PENDING".equals(busyReason);
    }

    private static void logResult(String operation, ServerPlayer player, PlayerCompanionData data, UUID uuid, String source) {
        if (!FindMeDebugLogger.enabled()) {
            return;
        }
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid)
                .or(() -> CompanionEntityLookup.findEntity(player.getServer(), uuid))
                .orElse(null);
        String state = stateOf(data, uuid, entity);
        FindMeDebugLogger.lifecycle(operation, player, uuid, entity, "UNKNOWN", state, source,
                data.storedEntity(uuid).isPresent(), entity != null);
    }

    private static String stateOf(PlayerCompanionData data, UUID uuid, Entity entity) {
        return data.lifecycleState(uuid).name();
    }

    private static FindMeModule moduleFor(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
    }

    public static boolean collectCurrentRide(ServerPlayer player, PlayerCompanionData data, String source) {
        Entity ride = player.getVehicle();
        if (ride == null || ride.isRemoved()) {
            return false;
        }
        UUID uuid = ride.getUUID();
        boolean externalPassenger = ride.getPassengers().stream().anyMatch(passenger -> passenger != player);
        if (!CompanionRideStorageConfirmationService.consumeOrRequest(player, uuid, externalPassenger)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.find_me.vehicle_passengers_confirm")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return false;
        }
        if (ride instanceof LivingEntity living && data.contains(CompanionKind.MOUNT, uuid)) {
            if (player.getVehicle() == ride) {
                player.stopRiding();
            }
            for (Entity passenger : java.util.List.copyOf(ride.getPassengers())) {
                passenger.stopRiding();
            }
            boolean stored = storeLiving(player, data, living, CompanionTransientStateService.Reason.MANUAL_STORE, source);
            if (stored) {
                data.clearDeployed(CompanionKind.MOUNT, uuid);
                CompanionDataService.save(player, data);
                CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            }
            return stored;
        }
        return com.kuzhi.findme.server.vehicle.VehicleManager.collectIfFindMeVehicle(player, data, ride);
    }

    public enum LifecycleMode {
        TRAVEL(CompanionTransientStateService.Reason.DIMENSION_CHANGE, "lifecycle:travel"),
        LOGOUT(CompanionTransientStateService.Reason.PLAYER_LOGOUT, "lifecycle:logout"),
        DEATH(CompanionTransientStateService.Reason.PLAYER_DEATH, "lifecycle:death");

        private final CompanionTransientStateService.Reason reason;
        private final String source;

        LifecycleMode(CompanionTransientStateService.Reason reason, String source) {
            this.reason = reason;
            this.source = source;
        }
    }
}


