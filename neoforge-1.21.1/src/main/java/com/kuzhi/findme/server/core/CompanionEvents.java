package com.kuzhi.findme.server.core;

import com.kuzhi.findme.server.lifecycle.CompanionPlayerLifecycleService;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.command.MountRosterTransactionService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.server.command.CompanionCommands;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.vehicle.VehicleSeatService;
import com.kuzhi.findme.server.safety.CompanionDeathService;
import com.kuzhi.findme.server.safety.CompanionThreatMemoryService;
import com.kuzhi.findme.server.safety.CompanionCriticalStateService;
import com.kuzhi.findme.server.safety.CompanionRecoveryService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.api.ExternalActionLeaseService;
import com.kuzhi.findme.server.api.CompanionActionRequestService;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

public class CompanionEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CompanionCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTickPre(ServerTickEvent.Pre event) {
        CompanionSyncService.beginServerTick(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        CompanionTickService.serverPost(event.getServer());
        CompanionSyncService.flushServerTick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        com.kuzhi.findme.server.lifecycle.CompanionContractService.resetServerState(event.getServer());
        com.kuzhi.findme.server.lifecycle.CompanionWaystoneJourneyService.resetServerState(event.getServer());
        CompanionWheelTransactionService.resetServerState();
        MountRosterTransactionService.resetServerState();
        CompanionOperationLockService.resetServerState();
        ExternalActionLeaseService.resetServerState(event.getServer());
        CompanionActionRequestService.resetServerState(event.getServer());
        com.kuzhi.findme.server.lifecycle.CompanionTeamOrderService.resetServerState();
        CompanionTacticalOrderService.resetServerState();
        CompanionSafetyService.resetRuntimeState();
        CompanionRecoveryService.resetServerState();
        com.kuzhi.findme.server.home.CompanionHomeResidentService.resetServerState();
        com.kuzhi.findme.server.lifecycle.CompanionStorageService.resetServerState();
        com.kuzhi.findme.server.lifecycle.CompanionTemporaryForcedRideService.resetServerState();
        com.kuzhi.findme.server.data.CompanionDataService.resetServerState(event.getServer());
        CompanionSyncService.resetServerState();
        CompanionEntityLookup.resetServerState(event.getServer());
        com.kuzhi.findme.server.safety.CompanionThreatResolver.finishServerTick(event.getServer());
        FindMePerformanceMonitor.resetServerState();
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        CompanionTickService.playerPost(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        CompanionSafetyService.handleWakeUp(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onNamePaperEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        CompanionInteractionService.handleEntityInteract(event);
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            CompanionEntityLookup.trackEntity(event.getLevel().getServer(), event.getEntity());
        }
        VehicleSeatService.handleEntityJoinLevel(event);
    }

    @SubscribeEvent
    public void onEntityTeleport(EntityTeleportEvent event) {
        CompanionPlayerLifecycleService.handleTeleport(event);
    }

    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        CompanionPlayerLifecycleService.handleTravelToDimension(event);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CompanionPlayerLifecycleService.handleChangedDimension(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CompanionPlayerLifecycleService.handleLogout(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        CompanionPlayerLifecycleService.handleLogin(event);
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            CompanionEntityLookup.untrackEntity(event.getLevel().getServer(), event.getEntity());
        }
        CompanionDeathService.handleEntityLeaveLevel(event);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        CompanionPlayerLifecycleService.handleClone(event);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        CompanionDeathService.handleLivingDeath(event);
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        CompanionDeathService.handleLivingDrops(event);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingIncomingDamageEvent event) {
        CompanionThreatMemoryService.handleIncomingDamage(event);
        CompanionCriticalStateService.handleIncomingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof net.minecraft.server.level.ServerPlayer owner) {
            CompanionTacticalOrderService.onOwnerTargeted(owner, event.getEntity());
        }
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        CompanionPlayerLifecycleService.handleLivingFall(event);
    }

}
