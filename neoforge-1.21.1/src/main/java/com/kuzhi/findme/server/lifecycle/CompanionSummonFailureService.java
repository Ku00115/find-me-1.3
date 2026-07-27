package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.command.MountRosterTransactionService;

public final class CompanionSummonFailureService {
    private CompanionSummonFailureService() {
    }

    public static void unavailable(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, boolean saveBeforeSync) {
        if (saveBeforeSync) {
            CompanionDataService.save(player, data);
        }
        CompanionSyncService.syncToClient(player, kind);
        CompanionWheelTransactionService.failLatest(player, kind, data.active(kind).orElse(null),
                "summon_unavailable");
        if (kind == CompanionKind.MOUNT) {
            MountRosterTransactionService.failLatest(player, data.active(kind).orElse(null),
                    "summon_unavailable");
        }
        CompanionSummonLineService.showUnavailable(player, data, kind);
    }

    public static void restoredEntityFailed(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living, Reason reason) {
        if (CompanionLifecycleFacade.storeLiving(player, data, living, CompanionTransientStateService.Reason.FAILURE_RECOVERY, "summon_failure:" + reason.name().toLowerCase())) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, kind);
        }
        if (reason == Reason.TOO_HIGH) {
            CompanionSummonLineService.showTooHigh(player, data, kind, living);
        } else {
            CompanionSummonLineService.showNeedOpenSpace(player, data, kind, living);
        }
        CompanionWheelTransactionService.failLatest(player, kind, living.getUUID(),
                "summon_" + reason.name().toLowerCase());
        if (kind == CompanionKind.MOUNT) {
            MountRosterTransactionService.failLatest(player, living.getUUID(),
                    "summon_" + reason.name().toLowerCase());
        }
    }

    public enum Reason {
        NEED_OPEN_SPACE,
        TOO_HIGH
    }
}


