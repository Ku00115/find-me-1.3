package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.ui.CompanionListService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionListCommandHandler {
    private CompanionListCommandHandler() {
    }

    public static int list(ServerPlayer player, CompanionKind kind) {
        return CompanionListService.list(player, kind);
    }

    public static int remove(ServerPlayer player, CompanionKind kind, int index) {
        return CompanionListService.remove(player, kind, index);
    }

    public static void rename(ServerPlayer player, CompanionKind kind, int index, String name) {
        CompanionListService.rename(player, kind, index, name);
    }

    public static boolean rejectBusy(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, java.util.UUID uuid, String source) {
        return CompanionListService.rejectBusy(player, data, kind, uuid, source);
    }

    public static boolean selectWheelIndex(PlayerCompanionData data, CompanionKind kind, int index) {
        return CompanionListService.selectWheelIndex(data, kind, index);
    }

    public static boolean releaseAndRemove(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index, java.util.UUID uuid) {
        return CompanionListService.releaseAndRemove(player, data, kind, index, uuid);
    }

    public static void moveToOther(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        CompanionListService.moveToOther(player, data, kind, targetEntityId);
    }

    public static void assignWheelSlot(ServerPlayer player, CompanionKind kind, int index, int slot) {
        CompanionListService.assignWheelSlot(player, kind, index, slot);
    }
}
