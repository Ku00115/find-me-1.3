package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.safety.CompanionIntegrityService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.ui.CompanionTeamService;
import com.kuzhi.findme.server.ui.FindMeSettingsService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.data.FindMeWorldMigrationService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.vehicle.VehicleSeatService;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.command.MountRosterTransactionService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class CompanionPlayerLifecycleService {
    private CompanionPlayerLifecycleService() {
    }

    public static void handleTravel(ServerPlayer player) {
        if (CompanionRideHomeJourneyService.bypassesTravelLifecycle(player)
                || CompanionWaystoneJourneyService.bypassesTravelLifecycle(player)) {
            FindMeMod.LOGGER.info("[FindMe ride-home] skipped normal travel collection for internal player teleport player={}",
                    player.getUUID());
            return;
        }
        FindMeApi.cancelTemporaryActionsForPlayer(player.getServer(), player.getUUID(), "player_travel");
        CompanionPreSpawnPresentationService.cancelPlayer(player.getUUID());
        CompanionWheelTransactionService.cancelForPlayer(player, "player_travel");
        MountRosterTransactionService.cancelForPlayer(player, "player_travel");
        CompanionRideHomeJourneyService.cancelForPlayer(player, "external_player_travel");
        CompanionWaystoneJourneyService.cancelForPlayer(player, "external_player_travel");
        VehicleSeatService.cleanup(player);
        PlayerCompanionData data = CompanionDataService.data(player);
        CompanionHomeResidentService.collectAllForPlayerSilently(player, data, "home:lifecycle_travel");
        CompanionLifecycleFacade.collectPlayerForLifecycle(player, CompanionLifecycleFacade.LifecycleMode.TRAVEL);
    }

    public static void handleTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            handleTravel(player);
        }
    }

    public static void handleTravelToDimension(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            handleTravel(player);
        }
    }

    public static void handleChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            handleTravel(serverPlayer);
        }
    }

    public static void handleLogout(ServerPlayer player) {
        CompanionPreSpawnPresentationService.cancelPlayer(player.getUUID());
        CompanionWheelTransactionService.cancelForPlayer(player, "player_logout");
        MountRosterTransactionService.cancelForPlayer(player, "player_logout");
        FindMeApi.cancelTemporaryActionsForPlayer(player.getServer(), player.getUUID(), "player_logout");
        CompanionRideHomeJourneyService.cancelForPlayer(player, "player_logout");
        CompanionWaystoneJourneyService.cancelForPlayer(player, "player_logout");
        VehicleSeatService.cleanup(player);
        try {
            PlayerCompanionData data = CompanionDataService.data(player);
            CompanionHomeResidentService.collectAllForPlayerSilently(player, data, "home:lifecycle_logout");
            CompanionLifecycleFacade.collectPlayerForLifecycle(player, CompanionLifecycleFacade.LifecycleMode.LOGOUT);
        } catch (RuntimeException exception) {
            FindMeMod.LOGGER.error(
                    "FindMe skipped persistent logout collection after a data failure; source data remains available for retry: player={} uuid={}",
                    player.getGameProfile().getName(),
                    player.getUUID(),
                    exception
            );
        } finally {
            CompanionSafetyService.forgetPlayer(player);
            CompanionHomeResidentService.forgetPlayer(player);
            CompanionRegistrationService.forgetPlayer(player);
            CompanionSyncService.forgetPlayer(player);
            PackAnimationPresetService.forgetEditor(player);
            CobblemonCompat.forgetPlayer(player);
        }
    }

    public static void handleLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            handleLogout(serverPlayer);
        }
    }

    public static void handleLogin(ServerPlayer player) {
        VehicleSeatService.cleanup(player);
        FindMeWorldMigrationService.ensureMigrated(player);
        CompanionSafetyService.ensureLoginSafetyBackup(player);
        FindMeModuleService.reconcilePlayer(player);
        CompanionIntegrityService.checkOnLogin(player);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionSyncService.syncDeadToClient(player);
        VehicleManager.syncToClient(player);
        CompanionTeamService.syncToClient(player);
        FindMeSettingsService.sync(player);
    }

    public static void handleLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            handleLogin(serverPlayer);
        }
    }

    public static void handleDeath(ServerPlayer player) {
        CompanionPreSpawnPresentationService.cancelPlayer(player.getUUID());
        FindMeApi.cancelTemporaryActionsForPlayer(player.getServer(), player.getUUID(), "player_death");
        CompanionWheelTransactionService.cancelForPlayer(player, "player_death");
        MountRosterTransactionService.cancelForPlayer(player, "player_death");
        CompanionRideHomeJourneyService.cancelForPlayer(player, "player_death");
        CompanionWaystoneJourneyService.cancelForPlayer(player, "player_death");
        PlayerCompanionData data = CompanionDataService.data(player);
        CompanionHomeResidentService.collectAllForPlayerSilently(player, data, "home:lifecycle_owner_death");
        CompanionLifecycleFacade.collectPlayerForLifecycle(player, CompanionLifecycleFacade.LifecycleMode.DEATH);
    }

    public static void syncClone(ServerPlayer player) {
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionSyncService.syncDeadToClient(player);
        VehicleManager.syncToClient(player);
        CompanionTeamService.syncToClient(player);
    }

    public static void handleClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            FindMeWorldMigrationService.copyOnClone(
                    event.getOriginal() instanceof ServerPlayer original ? original : serverPlayer,
                    serverPlayer
            );
            syncClone(serverPlayer);
        }
    }

    public static void handleLivingFall(LivingFallEvent event) {
        LivingEntity living = event.getEntity();
        if (living instanceof ServerPlayer player && player.getVehicle() != null) {
            player.fallDistance = 0.0f;
            event.setCanceled(true);
            return;
        }
        if (living.getPassengers().stream().anyMatch(ServerPlayer.class::isInstance)) {
            living.fallDistance = 0.0f;
            event.setCanceled(true);
        }
    }
}

