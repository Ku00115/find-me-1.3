package com.kuzhi.findme.server.core;

import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.safety.CompanionRecoveryService;
import com.kuzhi.findme.server.safety.CompanionCriticalStateService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.vehicle.VehicleSeatService;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.lifecycle.CompanionArrivalSequenceService;
import com.kuzhi.findme.server.lifecycle.CompanionPreSpawnPresentationService;
import com.kuzhi.findme.server.lifecycle.CompanionContractService;
import com.kuzhi.findme.server.lifecycle.CompanionCollectionService;
import com.kuzhi.findme.server.lifecycle.CompanionEscortService;
import com.kuzhi.findme.server.lifecycle.CompanionMountCinematicFlowService;
import com.kuzhi.findme.server.lifecycle.CompanionTemporaryForcedRideService;
import com.kuzhi.findme.server.lifecycle.CompanionMountSettleProtectionService;
import com.kuzhi.findme.server.lifecycle.CompanionRegistrationService;
import com.kuzhi.findme.server.lifecycle.CompanionRetreatService;
import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import com.kuzhi.findme.server.lifecycle.CompanionWaystoneJourneyService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.lifecycle.CompanionSummonApproachService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.server.lifecycle.CompanionTeamOrderService;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.command.MountRosterTransactionService;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.server.api.ExternalActionLeaseService;
import com.kuzhi.findme.server.api.CompanionActionRequestService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.common.FindMeModule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class CompanionTickService {
    private CompanionTickService() {
    }

    public static void serverPost(MinecraftServer server) {
        long tickStartedAt = FindMePerformanceMonitor.start();
        CompanionRecoveryService.tick(server);
        CompanionCriticalStateService.tick(server);
        CompanionTemporaryForcedRideService.tick(server);
        long stageStartedAt = FindMePerformanceMonitor.start();
        FindMeModuleService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.MODULES, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionCollectionService.processPendingUnloadSnapshots(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.UNLOAD_SNAPSHOTS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionOperationLockService.tick(server);
        ExternalActionLeaseService.tick(server);
        CompanionActionRequestService.tick(server);
        CompanionWheelTransactionService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.OPERATION_LOCKS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        // Resolve reveal barriers before the cinematic pass. A stored mount that becomes
        // available at the magic-circle reveal can therefore enter its approach on this
        // same server tick instead of appearing motionless for one extra tick.
        CompanionPreSpawnPresentationService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.ARRIVALS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionMountCinematicFlowService.tickMountCinematics(server);
        CompanionSummonApproachService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.MOUNT_CINEMATICS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionRetreatService.tickRetreats(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.RETREATS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionRideHomeJourneyService.tick(server);
        CompanionWaystoneJourneyService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.RIDE_HOME, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionMountSettleProtectionService.tickSettledMountProtections(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.MOUNT_SETTLE, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionContractService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.CONTRACTS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionArrivalSequenceService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.ARRIVALS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionStorageService.tickStorageEffects(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.STORAGE_EFFECTS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionTeamOrderService.tick(server);
        CompanionTacticalOrderService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.TACTICAL_ORDERS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        FindMeApi.tickTemporaryActions(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.TEMPORARY_ACTIONS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        VehicleManager.tickPendingSummons(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.VEHICLE_SUMMONS, stageStartedAt);
        VehicleManager.tickVehicleCinematics(server);
        stageStartedAt = FindMePerformanceMonitor.start();
        VehicleSeatService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.VEHICLE_SEATS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        MountRosterTransactionService.tick(server);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.ROSTER_TRANSACTIONS, stageStartedAt);
        CompanionEntityLookup.finishServerTick(server);
        com.kuzhi.findme.server.safety.CompanionThreatResolver.finishServerTick(server);
        FindMePerformanceMonitor.finishServerTick(server, tickStartedAt);
    }

    public static void playerPost(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        long tickStartedAt = FindMePerformanceMonitor.start();
        long stageStartedAt = FindMePerformanceMonitor.start();
        if (FindMeModuleService.enabled(FindMeModule.RIDING)) {
            CompanionRegistrationService.promoteRiddenRegisteredCompanionToMount(serverPlayer);
        }
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.PLAYER_REGISTRATION, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        CompanionSafetyService.tickPlayerSafety(serverPlayer);
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.PLAYER_SAFETY, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        if (FindMeModuleService.enabled(FindMeModule.HOUSES)) {
            CompanionHomeResidentService.tickPlayer(serverPlayer);
        }
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.HOME_RESIDENTS, stageStartedAt);
        stageStartedAt = FindMePerformanceMonitor.start();
        if (FindMeModuleService.enabled(FindMeModule.COMPANIONS)) {
            CompanionEscortService.tick(serverPlayer);
        }
        FindMePerformanceMonitor.record(FindMePerformanceMonitor.ESCORTS, stageStartedAt);
        FindMePerformanceMonitor.finishPlayerTick(serverPlayer, tickStartedAt);
    }
}
