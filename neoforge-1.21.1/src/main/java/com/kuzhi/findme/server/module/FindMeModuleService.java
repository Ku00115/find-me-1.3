package com.kuzhi.findme.server.module;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import com.kuzhi.findme.network.FindMeModuleStatePacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.lifecycle.CompanionCollectionService;
import com.kuzhi.findme.server.lifecycle.CompanionEscortService;
import com.kuzhi.findme.server.lifecycle.CompanionMountCinematicFlowService;
import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import com.kuzhi.findme.server.lifecycle.CompanionRideStorageConfirmationService;
import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.vehicle.SableVehicleCompatibility;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class FindMeModuleService {
    private static MinecraftServer activeServer;
    private static long previousConfiguredMask = Long.MIN_VALUE;
    private static long previousEffectiveMask = Long.MIN_VALUE;

    private FindMeModuleService() {
    }

    public static boolean enabled(FindMeModule module) {
        return module != null && (effectiveMask() & module.bit()) != 0L;
    }

    public static boolean require(ServerPlayer player, FindMeModule module) {
        if (enabled(module)) {
            return true;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.find_me.module_disabled",
                    Component.translatable("module.find_me." + module.id())).withStyle(ChatFormatting.YELLOW), true);
        }
        return false;
    }

    public static boolean setEnabled(ServerPlayer player, FindMeModule module, boolean enabled) {
        if (player == null || module == null) {
            return false;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.find_me.module_permission_denied")
                    .withStyle(ChatFormatting.RED), false);
            sync(player);
            return false;
        }
        return setEnabled(player.getServer(), module, enabled, player.getGameProfile().getName());
    }

    public static boolean resetToDefaults(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.find_me.module_permission_denied")
                    .withStyle(ChatFormatting.RED), false);
            sync(player);
            return false;
        }
        Config.resetModulesToDefaults();
        FindMeDebugLogger.info("module", "modules reset actor={}", player.getGameProfile().getName());
        reconcile(player.getServer(), true);
        return true;
    }

    public static boolean setEnabled(CommandSourceStack source, FindMeModule module, boolean enabled) {
        if (source == null || module == null || !source.hasPermission(2)) {
            return false;
        }
        boolean changed = setEnabled(source.getServer(), module, enabled, source.getTextName());
        source.sendSuccess(() -> Component.translatable("message.find_me.module_changed",
                Component.translatable("module.find_me." + module.id()), enabled), true);
        return changed;
    }

    private static boolean setEnabled(MinecraftServer server, FindMeModule module, boolean enabled, String actor) {
        boolean changed = Config.moduleConfigured(module) != enabled;
        if (changed) {
            Config.setModuleConfigured(module, enabled);
            FindMeDebugLogger.info("module", "module changed actor={} module={} enabled={}", actor, module.id(), enabled);
        }
        reconcile(server, true);
        return changed;
    }

    public static void tick(MinecraftServer server) {
        reconcile(server, false);
    }

    public static void reconcilePlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        long disabled = allMask() & ~effectiveMask();
        for (FindMeModule module : FindMeModule.values()) {
            if ((disabled & module.bit()) != 0L) {
                disableRuntime(player, module);
            }
        }
        syncRuntimeViews(player);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ModNetwork.sendToPlayer(player, new FindMeModuleStatePacket(
                configuredMask(), effectiveMask(), availableMask(), player.hasPermissions(2)));
    }

    private static void reconcile(MinecraftServer server, boolean forceSync) {
        if (server == null) {
            return;
        }
        if (activeServer != server) {
            activeServer = server;
            previousConfiguredMask = Long.MIN_VALUE;
            previousEffectiveMask = Long.MIN_VALUE;
        }
        long configured = configuredMask();
        long effective = effectiveMask();
        if (previousConfiguredMask == Long.MIN_VALUE) {
            previousConfiguredMask = configured;
            previousEffectiveMask = effective;
            long disabled = allMask() & ~effective;
            for (FindMeModule module : FindMeModule.values()) {
                if ((disabled & module.bit()) != 0L) {
                    disableRuntime(server, module);
                }
            }
            forceSync = true;
        } else if (configured != previousConfiguredMask || effective != previousEffectiveMask) {
            long disabled = previousEffectiveMask & ~effective;
            for (FindMeModule module : FindMeModule.values()) {
                if ((disabled & module.bit()) != 0L) {
                    disableRuntime(server, module);
                }
            }
            previousConfiguredMask = configured;
            previousEffectiveMask = effective;
            forceSync = true;
        }
        if (forceSync) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sync(player);
            }
        }
    }

    private static void disableRuntime(MinecraftServer server, FindMeModule module) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            disableRuntime(player, module);
            syncRuntimeViews(player);
        }
        FindMeDebugLogger.info("module", "runtime disabled module={} onlinePlayers={}",
                module.id(), server.getPlayerList().getPlayerCount());
    }

    private static void disableRuntime(ServerPlayer player, FindMeModule module) {
        PlayerCompanionData data = CompanionDataService.data(player);
        switch (module) {
            case RIDING -> {
                CompanionRideHomeJourneyService.cancelForPlayer(player, "module_disabled");
                CompanionMountCinematicFlowService.cancelForPlayer(player, "module_disabled");
                CompanionRideStorageConfirmationService.clear(player);
                VehicleManager.cancelRuntimeForPlayer(player, "module_disabled");
                CompanionCollectionService.collectDeployedForModule(player, CompanionKind.MOUNT);
                VehicleManager.collectDeployed(player);
                CobblemonCompat.recallDeployed(player);
            }
            case COMPANIONS -> {
                CompanionTransientStateService.cancelCombat(player,
                        CompanionTransientStateService.Reason.MODULE_DISABLED);
                CompanionEscortService.cancelForTransition(player);
                CompanionCollectionService.collectDeployedForModule(player, CompanionKind.COMPANION);
            }
            case HOUSES -> {
                CompanionRideHomeJourneyService.cancelForPlayer(player, "house_module_disabled");
                CompanionHomeResidentService.collectAllForPlayerSilently(
                        player, data, "home:module_disabled");
            }
            case COBBLEMON_INTEGRATION -> CobblemonCompat.recallDeployed(player);
            case SABLE_INTEGRATION -> VehicleManager.collectDeployedSable(player);
            case MANAGEMENT -> PackAnimationPresetService.forgetEditor(player);
            case SLEEP_REVIVAL -> {
            }
        }
    }

    private static void syncRuntimeViews(ServerPlayer player) {
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        VehicleManager.syncToClient(player);
        CobblemonCompat.sync(player);
    }

    private static long allMask() {
        long mask = 0L;
        for (FindMeModule module : FindMeModule.values()) {
            mask |= module.bit();
        }
        return mask;
    }

    public static long configuredMask() {
        long mask = 0L;
        for (FindMeModule module : FindMeModule.values()) {
            if (Config.moduleConfigured(module)) {
                mask |= module.bit();
            }
        }
        return mask;
    }

    public static long availableMask() {
        long mask = 0L;
        for (FindMeModule module : FindMeModule.values()) {
            boolean available = switch (module) {
                case COBBLEMON_INTEGRATION -> CobblemonCompat.available();
                case SABLE_INTEGRATION -> SableVehicleCompatibility.available();
                default -> true;
            };
            if (available) {
                mask |= module.bit();
            }
        }
        return mask;
    }

    public static long effectiveMask() {
        long configured = configuredMask();
        long available = availableMask();
        long effective = configured & available;
        if ((effective & FindMeModule.COMPANIONS.bit()) == 0L) {
            effective &= ~FindMeModule.HOUSES.bit();
            effective &= ~FindMeModule.SLEEP_REVIVAL.bit();
        }
        if ((effective & FindMeModule.RIDING.bit()) == 0L) {
            effective &= ~FindMeModule.COBBLEMON_INTEGRATION.bit();
            effective &= ~FindMeModule.SABLE_INTEGRATION.bit();
        }
        return effective;
    }
}
