package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.common.FindMeSettingsAction;
import com.kuzhi.findme.network.FindMeSettingsPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import net.minecraft.server.level.ServerPlayer;

public final class FindMeSettingsService {
    private FindMeSettingsService() {
    }

    public static void sync(ServerPlayer player) {
        send(player, false);
    }

    public static void handle(ServerPlayer player, FindMeSettingsAction action, FindMeUiSettings settings) {
        if (action == FindMeSettingsAction.SAVE) {
            save(player, settings);
        } else if (action == FindMeSettingsAction.EXPORT_DEFAULTS) {
            exportDefaults(player, settings);
        } else if (action == FindMeSettingsAction.RESET_DEFAULTS) {
            resetDefaults(player);
        } else if (action == FindMeSettingsAction.RESET_BINDING_HISTORY) {
            PlayerCompanionData data = CompanionDataService.data(player);
            data.resetBindingCinematicHistory();
            CompanionDataService.save(player, data);
            send(player, true);
        } else {
            sync(player);
            FindMeModuleService.sync(player);
        }
    }

    private static void exportDefaults(ServerPlayer player, FindMeUiSettings settings) {
        if (!canManageDefaults(player)) {
            ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                    CompanionDataService.data(player).uiSettings(), false,
                    "Creative mode and operator permission are required."));
            return;
        }
        try {
            PlayerCompanionData data = CompanionDataService.data(player);
            data.setUiSettings(settings);
            data.organizeTeams();
            CompanionDataService.save(player, data);
            FindMeDefaultSettingsService.export(player.getServer(), settings);
            ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                    data.uiSettings(), true, "New-world defaults exported."));
            CompanionTeamService.syncToClient(player);
        } catch (java.io.IOException exception) {
            com.kuzhi.findme.FindMeMod.LOGGER.error("Could not export FindMe defaults", exception);
            ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                    CompanionDataService.data(player).uiSettings(), false, "Could not export defaults."));
        }
    }

    private static void resetDefaults(ServerPlayer player) {
        FindMeUiSettings reset = FindMeSettingsDefaultsPolicy.resetTarget(canManageDefaults(player),
                FindMeDefaultSettingsService.loadUiDefaults());
        PlayerCompanionData data = CompanionDataService.data(player);
        data.setUiSettings(reset);
        data.organizeTeams();
        CompanionDataService.save(player, data);
        ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                data.uiSettings(), true, "Settings reset."));
        CompanionTeamService.syncToClient(player);
    }

    static boolean canManageDefaults(ServerPlayer player) {
        return player != null && FindMeSettingsDefaultsPolicy.canManageDefaults(
                player.hasPermissions(2), player.isCreative());
    }

    public static void save(ServerPlayer player, FindMeUiSettings settings) {
        PlayerCompanionData data = CompanionDataService.data(player);
        data.setUiSettings(settings);
        data.organizeTeams();
        CompanionDataService.save(player, data);
        send(player, true);
        CompanionTeamService.syncToClient(player);
    }

    private static void send(ServerPlayer player, boolean saved) {
        PlayerCompanionData data = CompanionDataService.data(player);
        ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE, data.uiSettings(), true,
                saved ? "Settings saved." : "Settings synced."));
    }
}

