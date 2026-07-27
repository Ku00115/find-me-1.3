package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.home.CompanionHomeService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionHomeCommandHandler {
    private CompanionHomeCommandHandler() {
    }

    public static int returnActiveHome(ServerPlayer player, CompanionKind kind) {
        return CompanionHomeService.returnActive(player, CompanionDataService.data(player), kind);
    }

    public static int setActiveHome(ServerPlayer player, CompanionKind kind) {
        return CompanionHomeService.setActiveHome(player, CompanionDataService.data(player), kind);
    }

    public static int sendActiveHome(ServerPlayer player, CompanionKind kind) {
        return CompanionHomeService.sendActiveHome(player, CompanionDataService.data(player), kind);
    }

    public static int clearActiveHome(ServerPlayer player, CompanionKind kind) {
        return CompanionHomeService.clearActiveHome(player, CompanionDataService.data(player), kind);
    }

    public static void setIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        CompanionHomeService.setIndexHome(player, data, kind, index);
    }

    public static void sendIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        CompanionHomeService.sendIndexHome(player, data, kind, index);
    }

    public static void clearIndexHome(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        CompanionHomeService.clearIndexHome(player, data, kind, index);
    }

    public static void handleHouseHome(ServerPlayer player, CompanionKind kind, CompanionAction action, int index, BlockPos housePos, ResourceLocation dimensionId) {
        CompanionHomeService.handleHouseHome(player, kind, action, index, housePos, dimensionId);
    }
}
