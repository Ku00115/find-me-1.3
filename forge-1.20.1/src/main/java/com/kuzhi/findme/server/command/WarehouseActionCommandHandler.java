package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.WarehouseEntityAction;
import com.kuzhi.findme.server.ui.WarehouseEntityService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class WarehouseActionCommandHandler {
    private WarehouseActionCommandHandler() {
    }

    public static void handle(ServerPlayer player, WarehouseEntityAction action, UUID uuid, String value) {
        WarehouseEntityService.handle(player, action, uuid, value);
    }
}
