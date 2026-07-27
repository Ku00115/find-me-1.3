package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionTeamAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.ui.CompanionTeamService;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionTeamCommandHandler {
    private CompanionTeamCommandHandler() {
    }

    public static void handle(ServerPlayer player, CompanionTeamAction action, CompanionTeamTarget target, int teamIndex, int secondaryIndex, String value, List<UUID> uuids) {
        if (player == null || action == null) {
            return;
        }
        if (!FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return;
        }
        switch (action) {
            case SYNC -> CompanionTeamService.syncToClient(player);
            case CREATE -> CompanionTeamService.createTeam(player, target);
            case TOGGLE_AUTO_JOIN -> CompanionTeamService.toggleAutoJoin(player, target, teamIndex);
            case DELETE -> CompanionTeamService.deleteTeam(player, target, teamIndex);
            case SET -> CompanionTeamService.setTeam(player, target, teamIndex, uuids);
            case APPLY -> CompanionTeamService.applyTeamToWheel(player, target, teamIndex);
            case RENAME -> CompanionTeamService.renameTeam(player, target, teamIndex, value);
            case REORDER -> CompanionTeamService.reorderTeam(player, target, teamIndex, secondaryIndex);
        }
    }
}
