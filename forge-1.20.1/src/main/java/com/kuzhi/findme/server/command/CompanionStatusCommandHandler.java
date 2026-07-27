package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionEscortService;
import com.kuzhi.findme.server.lifecycle.CompanionRegistrationService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionStatusCommandHandler {
    private CompanionStatusCommandHandler() {
    }

    public static void toggleEscortWheel(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        CompanionEscortService.toggleWheelIndex(player, data, kind, index);
    }

    public static void toggleEscortList(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        CompanionEscortService.toggleListIndex(player, data, kind, index);
    }

    public static void syncDead(ServerPlayer player, PlayerCompanionData data) {
        CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, CompanionKind.MOUNT);
        CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, CompanionKind.COMPANION);
        CompanionSyncService.syncDeadToClient(player);
    }

    public static void syncList(ServerPlayer player, CompanionKind kind) {
        CompanionSyncService.syncToClient(player, kind);
    }
}
