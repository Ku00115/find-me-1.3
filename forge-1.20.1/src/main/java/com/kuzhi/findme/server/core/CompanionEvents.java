package com.kuzhi.findme.server.core;

import com.kuzhi.findme.server.lifecycle.CompanionPlayerLifecycleService;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.command.MountRosterTransactionService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.server.command.CompanionCommands;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.vehicle.VehicleSeatService;
import com.kuzhi.findme.server.safety.CompanionDeathService;
import com.kuzhi.findme.server.safety.CompanionRecoveryService;
import com.kuzhi.findme.server.safety.CompanionThreatMemoryService;
import com.kuzhi.findme.server.safety.CompanionCriticalStateService;
import com.kuzhi.findme.server.api.ExternalActionLeaseService;
import com.kuzhi.findme.server.api.CompanionActionRequestService;
import com.kuzhi.findme.server.ui.CompanionSyncService;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CompanionEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CompanionCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            CompanionSyncService.beginServerTick(event.getServer());
        } else if (event.phase == TickEvent.Phase.END) {
            CompanionTickService.serverPost(event.getServer());
            CompanionSyncService.flushServerTick(event.getServer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                || !event.getState().is(com.kuzhi.findme.common.ModBlocks.SMALL_HOUSE.get())
                || !(player.serverLevel().getBlockEntity(event.getPos())
                instanceof com.kuzhi.findme.common.SmallHouseBlockEntity house)
                || house.owner() == null || house.isOwner(player) || player.hasPermissions(2)) {
            return;
        }
        event.setCanceled(true);
        com.kuzhi.findme.server.ui.CompanionMessageService.tell(player,
                "message.find_me.house_break_denied", net.minecraft.ChatFormatting.RED);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        com.kuzhi.findme.server.home.CompanionHomeResidentService.resetServerState();
        com.kuzhi.findme.server.lifecycle.CompanionStorageService.resetServerState();
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
        com.kuzhi.findme.server.lifecycle.CompanionTemporaryForcedRideService.resetServerState();
        com.kuzhi.findme.server.data.CompanionDataService.resetServerState(event.getServer());
        CompanionSyncService.resetServerState();
        CompanionEntityLookup.resetServerState(event.getServer());
        com.kuzhi.findme.server.safety.CompanionThreatResolver.finishServerTick(event.getServer());
        FindMePerformanceMonitor.resetServerState();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CompanionTickService.playerPost(event.player);
        }
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
    public void onLivingHurt(LivingHurtEvent event) {
        CompanionThreatMemoryService.handleIncomingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(LivingDamageEvent event) {
        CompanionCriticalStateService.handleIncomingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof net.minecraft.server.level.ServerPlayer owner) {
            CompanionTacticalOrderService.onOwnerTargeted(owner, event.getEntity());
        }
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        CompanionPlayerLifecycleService.handleLivingFall(event);
    }

}
