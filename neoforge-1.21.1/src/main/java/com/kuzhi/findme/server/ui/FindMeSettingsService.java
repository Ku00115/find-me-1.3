package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.common.FindMeSettingsAction;
import com.kuzhi.findme.network.FindMeSettingsPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.CompanionDataService;
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
        } else if (action == FindMeSettingsAction.RESET_BINDING_HISTORY) {
            PlayerCompanionData data = CompanionDataService.data(player);
            data.resetBindingCinematicHistory();
            CompanionDataService.save(player, data);
            send(player, true);
        } else {
            sync(player);
        }
    }

    private static void exportDefaults(ServerPlayer player, FindMeUiSettings settings) {
        if (!player.hasPermissions(2)) {
            ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                    CompanionDataService.data(player).uiSettings(), false, "Operator permission is required."));
            return;
        }
        try {
            FindMeDefaultSettingsService.export(player.getServer(), settings);
            ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                    CompanionDataService.data(player).uiSettings(), true, "New-world defaults exported."));
        } catch (java.io.IOException exception) {
            com.kuzhi.findme.FindMeMod.LOGGER.error("Could not export FindMe defaults", exception);
            ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE,
                    CompanionDataService.data(player).uiSettings(), false, "Could not export defaults."));
        }
    }

    public static void save(ServerPlayer player, FindMeUiSettings settings) {
        PlayerCompanionData data = CompanionDataService.data(player);
        data.setUiSettings(settings);
        CompanionDataService.save(player, data);
        send(player, true);
    }

    private static void send(ServerPlayer player, boolean saved) {
        PlayerCompanionData data = CompanionDataService.data(player);
        ModNetwork.sendToPlayer(player, new FindMeSettingsPacket(FindMeSettingsAction.UPDATE, data.uiSettings(), true,
                saved ? "Settings saved." : "Settings synced."));
    }
}

