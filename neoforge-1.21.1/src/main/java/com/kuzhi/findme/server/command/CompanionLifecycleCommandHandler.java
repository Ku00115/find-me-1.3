package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionLifecycleCommandHandler {
    private CompanionLifecycleCommandHandler() {
    }

    public static boolean summonActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, String source) {
        FindMeModule module = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!FindMeModuleService.require(player, module)) {
            return false;
        }
        return CompanionLifecycleFacade.summonActive(player, data, kind, source);
    }

    public static boolean summonActive(ServerPlayer player, CompanionKind kind, String source) {
        PlayerCompanionData data = CompanionDataService.data(player);
        return summonActive(player, data, kind, source);
    }

    public static void collectActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, String source) {
        CompanionLifecycleFacade.collectActive(player, data, kind, source);
    }

    public static void collectActive(ServerPlayer player, CompanionKind kind, String source) {
        PlayerCompanionData data = CompanionDataService.data(player);
        CompanionLifecycleFacade.collectActive(player, data, kind, source);
    }

    public static boolean collectWheel(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int wheelIndex, String source) {
        return CompanionLifecycleFacade.collectWheel(player, data, kind, wheelIndex, source);
    }

    public static boolean collectWheel(ServerPlayer player, CompanionKind kind, int wheelIndex, String source) {
        PlayerCompanionData data = CompanionDataService.data(player);
        return CompanionLifecycleFacade.collectWheel(player, data, kind, wheelIndex, source);
    }

    public static boolean collectCurrentRide(ServerPlayer player, PlayerCompanionData data) {
        return CompanionLifecycleFacade.collectCurrentRide(player, data, "command:store_current");
    }
}
