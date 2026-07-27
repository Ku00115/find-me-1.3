package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

public final class VehicleActionCommandHandler {
    private VehicleActionCommandHandler() {
    }

    public static void handle(ServerPlayer player, VehicleCommandAction action, UUID targetUuid, int position) {
        VehicleManager.handle(player, action, targetUuid, position);
    }

    public static void rename(ServerPlayer player, boolean wheel, int index, String name) {
        VehicleManager.rename(player, wheel, index, name);
    }
}
