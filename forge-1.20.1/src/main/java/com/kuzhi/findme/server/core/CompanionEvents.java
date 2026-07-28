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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CompanionEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CompanionCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CompanionTickService.serverPost(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        com.kuzhi.findme.server.lifecycle.CompanionWaystoneJourneyService.resetServerState(event.getServer());
        CompanionWheelTransactionService.resetServerState();
        MountRosterTransactionService.resetServerState();
        CompanionOperationLockService.resetServerState();
        com.kuzhi.findme.server.lifecycle.CompanionTeamOrderService.resetServerState();
        CompanionTacticalOrderService.resetServerState();
        CompanionSafetyService.resetRuntimeState();
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

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        CompanionPlayerLifecycleService.handleLivingFall(event);
    }

}
