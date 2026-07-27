package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class VehicleCommandHandler {
    private VehicleCommandHandler() {
    }

    public static void handle(ServerPlayer player, VehicleCommandAction action, UUID targetUuid, int position) {
        if (action == VehicleCommandAction.SELECT_SUMMON
                && !FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return;
        }
        if (isManagementAction(action)
                && !FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return;
        }
        VehicleActionCommandHandler.handle(player, action, targetUuid, position);
    }

    private static boolean isManagementAction(VehicleCommandAction action) {
        return action == VehicleCommandAction.REORDER_WHEEL || action == VehicleCommandAction.ADD_TO_WHEEL
                || action == VehicleCommandAction.REMOVE_FROM_WHEEL || action == VehicleCommandAction.REMOVE;
    }

    public static void rename(ServerPlayer player, boolean wheel, int index, String name) {
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)
                || !FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return;
        }
        VehicleActionCommandHandler.rename(player, wheel, index, name);
    }
}
