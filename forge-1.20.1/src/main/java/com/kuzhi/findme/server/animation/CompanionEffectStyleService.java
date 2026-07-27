package com.kuzhi.findme.server.animation;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionEffectStyleService {
    private CompanionEffectStyleService() {
    }

    public static void setEffectStyle(ServerPlayer player, UUID uuid, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!data.contains(uuid) && !data.containsVehicle(uuid)) {
            return;
        }
        data.setEffectStyle(uuid, purpose, style);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        VehicleManager.syncToClient(player);
    }
}

