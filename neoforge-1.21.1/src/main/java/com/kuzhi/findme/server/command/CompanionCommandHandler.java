package com.kuzhi.findme.server.command;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.CompanionEscortService;
import com.kuzhi.findme.server.home.CompanionHomeService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionCommandHandler {
    private CompanionCommandHandler() {
    }

    public static void handle(ServerPlayer player, CompanionKind kind, CompanionAction action, int targetEntityId) {
        FindMeModule feature = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (requiresGameplayModule(action) && !FindMeModuleService.require(player, feature)) {
            return;
        }
        if (isHouseAction(action) && !FindMeModuleService.require(player, FindMeModule.HOUSES)) {
            return;
        }
        if (isManagementAction(action) && !FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        switch (action) {
            case SUMMON -> CompanionLifecycleCommandHandler.summonActive(player, data, kind, "command:" + action.name().toLowerCase());
            case STORE_CURRENT -> CompanionLifecycleCommandHandler.collectCurrentRide(player, data);
            case RETURN_HOME -> CompanionLifecycleCommandHandler.collectActive(player, data, kind, "command:" + action.name().toLowerCase());
            case RIDE_HOME -> CompanionRideHomeJourneyService.start(player, data, kind, targetEntityId);
            case NEXT -> selectNext(player, data, kind);
            case PREVIOUS -> selectPrevious(player, data, kind);
            case SELECT -> select(player, data, kind, targetEntityId, false);
            case SELECT_SUMMON -> select(player, data, kind, targetEntityId, true);
            case RECALL -> recall(player, data, kind, targetEntityId);
            case REMOVE -> remove(player, data, kind, targetEntityId);
            case MOVE_OTHER -> CompanionListCommandHandler.moveToOther(player, data, kind, targetEntityId);
            case ASSIGN_SLOT -> CompanionListCommandHandler.assignWheelSlot(player, kind, targetEntityId % 1000, targetEntityId / 1000);
            case REORDER -> reorder(player, data, kind, targetEntityId);
            case SET_HOME -> CompanionHomeCommandHandler.setIndexHome(player, data, kind, targetEntityId);
            case GO_HOME -> CompanionHomeCommandHandler.sendIndexHome(player, data, kind, targetEntityId);
            case CLEAR_HOME -> CompanionHomeCommandHandler.clearIndexHome(player, data, kind, targetEntityId);
            case DEAD_SYNC -> CompanionStatusCommandHandler.syncDead(player, data);
            case DEAD_REMOVE -> removeDead(player, data, kind, targetEntityId);
            case ESCORT -> CompanionStatusCommandHandler.toggleEscortWheel(player, data, kind, targetEntityId);
            case ESCORT_LIST -> CompanionStatusCommandHandler.toggleEscortList(player, data, kind, targetEntityId);
            case SYNC -> CompanionStatusCommandHandler.syncList(player, kind);
        }
    }

    public static void handleWheelIntent(ServerPlayer player, UUID requestId, CompanionKind kind,
                                         CompanionAction action, UUID targetUuid, long expectedRevision) {
        PlayerCompanionData data = CompanionDataService.data(player);
        long serverRevision = CompanionDataService.revision(player);
        if (requestId == null || expectedRevision > serverRevision) {
            CompanionWheelTransactionService.reject(player,
                    requestId == null ? UUID.randomUUID() : requestId, kind, action,
                    targetUuid == null ? new UUID(0L, 0L) : targetUuid, "invalid_revision");
            return;
        }
        if (expectedRevision >= 0L && expectedRevision < serverRevision) {
            com.kuzhi.findme.server.core.FindMeDebugLogger.info("wheel-transaction",
                    "stale request revalidated player={} request={} expectedRevision={} serverRevision={} target={}",
                    player.getUUID(), requestId, expectedRevision, serverRevision, targetUuid);
        }
        boolean supported = action == CompanionAction.SELECT || action == CompanionAction.SELECT_SUMMON
                || action == CompanionAction.RECALL || action == CompanionAction.RIDE_HOME
                || action == CompanionAction.ESCORT || action == CompanionAction.GO_HOME;
        FindMeModule feature = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        boolean modulesReady = (!requiresGameplayModule(action) || FindMeModuleService.enabled(feature))
                && (!isHouseAction(action) || FindMeModuleService.enabled(FindMeModule.HOUSES));
        boolean targetRegistered = targetUuid != null && data.contains(kind, targetUuid);
        boolean busy = targetUuid != null && CompanionLifecycleFacade.isBusy(player, data, targetUuid);
        boolean cancellingSwitch = busy && action == CompanionAction.RECALL
                && CompanionWheelTransactionService.canRecallActiveSwitch(player, kind, targetUuid);
        boolean accepted = supported && targetUuid != null && targetRegistered
                && !data.deadList().contains(targetUuid)
                && modulesReady
                && (action == CompanionAction.SELECT || !busy || cancellingSwitch);
        String rejection = !supported ? "unsupported_action"
                : !targetRegistered ? "target_not_registered"
                : data.deadList().contains(targetUuid) ? "target_dead"
                : !modulesReady ? "module_disabled"
                : busy && action != CompanionAction.SELECT && !cancellingSwitch ? "target_busy" : "rejected";
        if (accepted && CompanionWheelTransactionService.begin(player, data, requestId, kind, action,
                targetUuid, expectedRevision)) {
            boolean dispatched = dispatchWheelUuid(player, data, kind, action, targetUuid);
            if (!dispatched) {
                CompanionWheelTransactionService.fail(player, requestId, "dispatch_failed");
            } else if (action == CompanionAction.SELECT || action == CompanionAction.ESCORT
                    || action == CompanionAction.GO_HOME) {
                CompanionWheelTransactionService.completeImmediate(player, requestId, "committed");
            }
        } else if (supported && targetUuid != null && targetRegistered && !modulesReady) {
            requireWheelModules(player, kind, action);
            CompanionWheelTransactionService.reject(player, requestId, kind, action, targetUuid, rejection);
        } else if (!accepted) {
            com.kuzhi.findme.server.core.FindMeDebugLogger.info("command",
                    "wheel intent rejected player={} kind={} action={} target={} supported={} contained={} dead={} modulesReady={} busy={}",
                    player.getUUID(), kind, action, targetUuid, supported,
                    targetRegistered,
                    targetUuid != null && data.deadList().contains(targetUuid), modulesReady,
                    busy);
            CompanionWheelTransactionService.reject(player, requestId, kind, action,
                    targetUuid == null ? new UUID(0L, 0L) : targetUuid, rejection);
        }
    }

    private static boolean dispatchWheelUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                             CompanionAction action, UUID targetUuid) {
        return switch (action) {
            case SELECT -> CompanionWheelCommandHandler.selectUuid(player, data, kind, targetUuid, false);
            case SELECT_SUMMON -> CompanionWheelCommandHandler.selectUuid(player, data, kind, targetUuid, true);
            case RECALL -> CompanionWheelCommandHandler.recallUuid(player, data, kind, targetUuid);
            case RIDE_HOME -> CompanionRideHomeJourneyService.start(player, data, kind, targetUuid);
            case ESCORT -> {
                CompanionEscortService.toggleUuidForCommand(player, data, kind, targetUuid);
                yield true;
            }
            case GO_HOME -> CompanionHomeService.sendUuidHome(player, data, kind, targetUuid) > 0;
            default -> false;
        };
    }

    private static void requireWheelModules(ServerPlayer player, CompanionKind kind, CompanionAction action) {
        if (requiresGameplayModule(action)) {
            FindMeModuleService.require(player,
                    kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS);
        }
        if (isHouseAction(action)) {
            FindMeModuleService.require(player, FindMeModule.HOUSES);
        }
    }

    private static boolean isHouseAction(CompanionAction action) {
        return action == CompanionAction.SET_HOME || action == CompanionAction.GO_HOME
                || action == CompanionAction.CLEAR_HOME || action == CompanionAction.RIDE_HOME;
    }

    private static boolean requiresGameplayModule(CompanionAction action) {
        return action == CompanionAction.SUMMON || action == CompanionAction.SELECT_SUMMON
                || action == CompanionAction.ESCORT || action == CompanionAction.RIDE_HOME
                ;
    }

    private static boolean isManagementAction(CompanionAction action) {
        return action == CompanionAction.MOVE_OTHER || action == CompanionAction.ASSIGN_SLOT
                || action == CompanionAction.REORDER || action == CompanionAction.REMOVE
                || action == CompanionAction.DEAD_REMOVE || action == CompanionAction.ESCORT_LIST;
    }

    public static int list(ServerPlayer player, CompanionKind kind) {
        if (!FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return 0;
        }
        return CompanionListCommandHandler.list(player, kind);
    }

    public static int escortList(ServerPlayer player, CompanionKind kind, int index) {
        handle(player, kind, CompanionAction.ESCORT_LIST, index);
        return 1;
    }

    public static int removeAt(ServerPlayer player, CompanionKind kind, int index) {
        if (!FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return 0;
        }
        return CompanionListCommandHandler.remove(player, kind, index);
    }

    public static void rename(ServerPlayer player, CompanionKind kind, int index, String name) {
        if (!FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return;
        }
        CompanionListCommandHandler.rename(player, kind, index, name);
    }

    public static void handleHouseHome(ServerPlayer player, CompanionKind kind, CompanionAction action, int index, BlockPos housePos, ResourceLocation dimensionId) {
        CompanionHomeCommandHandler.handleHouseHome(player, kind, action, index, housePos, dimensionId);
    }

    private static void selectNext(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        CompanionWheelCommandHandler.selectNext(player, data, kind);
    }

    private static void selectPrevious(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        CompanionWheelCommandHandler.selectPrevious(player, data, kind);
    }

    private static void select(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId, boolean summon) {
        CompanionWheelCommandHandler.select(player, data, kind, targetEntityId, summon);
    }

    private static void remove(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        CompanionWheelCommandHandler.remove(player, data, kind, targetEntityId);
    }

    private static void recall(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        CompanionWheelCommandHandler.recall(player, data, kind, targetEntityId);
    }

    private static void reorder(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        CompanionWheelCommandHandler.reorder(player, data, kind, targetEntityId);
    }

    private static void removeDead(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        CompanionDeadCommandHandler.removeDead(player, data, kind, targetEntityId);
    }
}

