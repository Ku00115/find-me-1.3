package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionWheelCommandHandler {
    private CompanionWheelCommandHandler() {
    }

    public static void selectNext(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        Optional<UUID> target = cycleTarget(data, kind, 1);
        if (target.isPresent() && CompanionListCommandHandler.rejectBusy(player, data, kind, target.get(), "wheel:next")) {
            return;
        }
        data.cycle(kind, 1);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        data.active(kind).ifPresentOrElse(
                uuid -> CompanionMessageService.tell(player, "message.find_me.selected_next", ChatFormatting.GRAY, CompanionMessageService.label(kind)),
                () -> CompanionMessageService.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionMessageService.label(kind)));
    }

    public static void selectPrevious(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        data.previous(kind).ifPresentOrElse(uuid -> {
            if (!data.contains(uuid)) {
                CompanionMessageService.tell(player, "message.find_me.no_previous", ChatFormatting.YELLOW, CompanionMessageService.label(kind));
                return;
            }
            if (CompanionListCommandHandler.rejectBusy(player, data, kind, uuid, "wheel:previous")) {
                return;
            }
            data.add(kind, uuid);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, kind);
            CompanionMessageService.tell(player, "message.find_me.previous_selected", ChatFormatting.GRAY, CompanionMessageService.label(kind));
        }, () -> CompanionMessageService.tell(player, "message.find_me.no_previous", ChatFormatting.YELLOW, CompanionMessageService.label(kind)));
    }

    public static void select(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId, boolean summon) {
        Optional<UUID> uuid = data.wheelUuidAt(kind, targetEntityId);
        if (uuid.isPresent() && CompanionListCommandHandler.rejectBusy(player, data, kind, uuid.get(), summon ? "wheel:select_summon" : "wheel:select")) {
            return;
        }
        if (!CompanionListCommandHandler.selectWheelIndex(data, kind, targetEntityId)) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return;
        }
        CompanionDataService.save(player, data);
        if (summon) {
            CompanionLifecycleCommandHandler.summonActive(player, data, kind, "command:select_summon");
        }
        CompanionSyncService.syncToClient(player, kind);
    }

    public static boolean selectUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                     UUID uuid, boolean summon) {
        if (uuid == null || !data.setActiveUuid(kind, uuid)) {
            return false;
        }
        CompanionDataService.save(player, data);
        boolean accepted = true;
        if (summon) {
            accepted = CompanionLifecycleCommandHandler.summonActive(
                    player, data, kind, "wheel:intent_select_summon");
        }
        CompanionSyncService.syncToClient(player, kind);
        return accepted;
    }

    public static boolean selectMountUuidForSwitch(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (uuid == null || !data.setActiveUuid(CompanionKind.MOUNT, uuid)) {
            return false;
        }
        CompanionDataService.save(player, data);
        boolean accepted = com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade.summonActiveForMountSwitch(
                player, data, "wheel:intent_mount_switch");
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        return accepted;
    }

    public static void remove(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        Optional<UUID> uuid = data.wheelUuidAt(kind, targetEntityId);
        if (uuid.isPresent()) {
            CompanionListCommandHandler.releaseAndRemove(player, data, kind, targetEntityId, uuid.get());
        } else {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
        }
    }

    public static void recall(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        if (!CompanionLifecycleCommandHandler.collectWheel(player, data, kind, targetEntityId, "command:recall")) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
        }
    }

    public static boolean recallUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        return com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade.collectUuid(
                player, data, kind, uuid, "wheel:intent_recall");
    }

    public static void reorder(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int targetEntityId) {
        int from = targetEntityId % 1000;
        int to = targetEntityId / 1000;
        Optional<UUID> uuid = data.wheelUuidAt(kind, from);
        if (uuid.isPresent() && CompanionListCommandHandler.rejectBusy(player, data, kind, uuid.get(), "wheel:reorder")) {
            return;
        }
        if (!data.reorder(kind, from, to)) {
            return;
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
    }

    private static Optional<UUID> cycleTarget(PlayerCompanionData data, CompanionKind kind, int delta) {
        java.util.List<UUID> list = data.list(kind);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        int current = data.activeIndex(kind);
        int next = Math.floorMod(current + delta, list.size());
        return Optional.of(list.get(next));
    }
}
