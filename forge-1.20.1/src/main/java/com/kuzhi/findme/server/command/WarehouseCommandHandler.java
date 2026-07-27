package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.WarehouseEntityAction;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class WarehouseCommandHandler {
    private WarehouseCommandHandler() {
    }

    public static void handle(ServerPlayer player, WarehouseEntityAction action, UUID uuid, String value) {
        if (!FindMeModuleService.require(player, FindMeModule.MANAGEMENT)) {
            return;
        }
        WarehouseActionCommandHandler.handle(player, action, uuid, value);
    }
}
