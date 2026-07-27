package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionRegistrationService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionDeadCommandHandler {
    private CompanionDeadCommandHandler() {
    }

    public static void syncDead(ServerPlayer player, PlayerCompanionData data) {
        CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, CompanionKind.MOUNT);
        CompanionRegistrationService.sweepDeadEntriesAndSync(player, data, CompanionKind.COMPANION);
        CompanionSyncService.syncDeadToClient(player);
    }

    public static void removeDead(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        if (targetEntityId < 0 || targetEntityId >= data.deadList().size()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return;
        }
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_delete_dead_record")) {
            CompanionMessageService.tell(player, "message.find_me.backup_required_failed", ChatFormatting.RED, new Object[0]);
            return;
        }
        if (data.removeDeadAt(targetEntityId)) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            CompanionSyncService.syncDeadToClient(player);
        } else {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
        }
    }
}
