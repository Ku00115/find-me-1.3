package com.kuzhi.findme.server.animation;

import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionAnimationStyleService {
    private CompanionAnimationStyleService() {
    }

    public static void setAnimationStyle(ServerPlayer player, UUID uuid,
                                         CompanionAnimationPurpose purpose, CompanionAnimationStyle style) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!data.contains(uuid) && !data.containsVehicle(uuid)) {
            return;
        }
        data.setAnimationStyle(uuid, purpose, style);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        VehicleManager.syncToClient(player);
    }
}
