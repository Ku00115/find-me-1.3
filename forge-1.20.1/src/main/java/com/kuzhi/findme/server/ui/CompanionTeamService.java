package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.ui.CompanionMessageService;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.network.CompanionTeamListPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionTeamService {
    private CompanionTeamService() {
    }

    public static void syncToClient(ServerPlayer player) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.organizeTeams()) {
            CompanionDataService.save(player, data);
        }
        sendSnapshot(player, data);
    }

    private static void sendSnapshot(ServerPlayer player, PlayerCompanionData data) {
        ArrayList<CompanionTeamListPacket.Entry> entries = new ArrayList<>();
        for (CompanionTeamTarget target : CompanionTeamTarget.values()) {
            for (int i = 0; i < data.teamCount(target); ++i) {
                List<UUID> visible = data.team(target, i).stream()
                        .filter(uuid -> !data.isRecovery(uuid))
                        .toList();
                entries.add(new CompanionTeamListPacket.Entry(target, i, data.teamNumber(target, i), data.teamAutoJoin(target, i), data.teamName(target, i), visible));
            }
        }
        ModNetwork.sendToPlayer(player, new CompanionTeamListPacket(CompanionDataService.revision(player), entries));
    }

    public static void createTeam(ServerPlayer player, CompanionTeamTarget target) {
        if (target == null) {
            syncToClient(player);
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        data.createTeam(target);
        CompanionDataService.save(player, data);
        // Keep a newly created empty team visible long enough to name it or add members.
        sendSnapshot(player, data);
    }

    public static void toggleAutoJoin(ServerPlayer player, CompanionTeamTarget target, int teamIndex) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.setTeamAutoJoin(target, teamIndex, !data.teamAutoJoin(target, teamIndex))) {
            CompanionDataService.save(player, data);
        }
        syncToClient(player);
    }

    public static void deleteTeam(ServerPlayer player, CompanionTeamTarget target, int teamIndex) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.deleteTeam(target, teamIndex)) {
            CompanionDataService.save(player, data);
        }
        syncToClient(player);
    }

    public static void setTeam(ServerPlayer player, CompanionTeamTarget target, int teamIndex, List<UUID> uuids) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (target == null || teamIndex < 0 || teamIndex >= data.teamCount(target)) {
            syncToClient(player);
            return;
        }
        Optional<UUID> locked = firstBusy(player, data, uuids);
        if (locked.isPresent()) {
            FindMeDebugLogger.lifecycle("TEAM_SET_REJECTED_LOCKED", player, locked.get(), null,
                    CompanionLifecycleFacade.busyReason(player, data, locked.get()), "TEAM", "team:set", data.storedEntity(locked.get()).isPresent(), false);
            CompanionMessageService.tell(player, "message.find_me.busy", ChatFormatting.YELLOW, data.displayName(locked.get()).orElse(locked.get().toString().substring(0, 8)));
            syncToClient(player);
            return;
        }
        if (data.setTeam(target, teamIndex, uuids)) {
            CompanionDataService.save(player, data);
            if (target == CompanionTeamTarget.VEHICLE) {
                VehicleManager.syncToClient(player);
            } else {
                CompanionSyncService.syncToClient(player, target == CompanionTeamTarget.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION);
            }
        }
        syncToClient(player);
    }

    public static void applyTeamToWheel(ServerPlayer player, CompanionTeamTarget target, int teamIndex) {
        PlayerCompanionData data = CompanionDataService.data(player);
        List<UUID> team = data.team(target, teamIndex);
        Optional<UUID> locked = firstBusy(player, data, team);
        if (locked.isPresent()) {
            FindMeDebugLogger.lifecycle("TEAM_APPLY_REJECTED_LOCKED", player, locked.get(), null,
                    CompanionLifecycleFacade.busyReason(player, data, locked.get()), "TEAM", "team:apply", data.storedEntity(locked.get()).isPresent(), false);
            CompanionMessageService.tell(player, "message.find_me.busy", ChatFormatting.YELLOW, data.displayName(locked.get()).orElse(locked.get().toString().substring(0, 8)));
            syncToClient(player);
            return;
        }
        if (!data.applyTeamToWheel(target, teamIndex)) {
            syncToClient(player);
            return;
        }
        CompanionDataService.save(player, data);
        if (target == CompanionTeamTarget.VEHICLE) {
            VehicleManager.syncToClient(player);
        } else {
            CompanionSyncService.syncToClient(player, target == CompanionTeamTarget.MOUNT ? CompanionKind.MOUNT : CompanionKind.COMPANION);
        }
        syncToClient(player);
    }

    public static void renameTeam(ServerPlayer player, CompanionTeamTarget target, int teamIndex, String name) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.setTeamName(target, teamIndex, name)) CompanionDataService.save(player, data);
        syncToClient(player);
    }

    public static void reorderTeam(ServerPlayer player, CompanionTeamTarget target, int from, int to) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.reorderTeam(target, from, to)) CompanionDataService.save(player, data);
        syncToClient(player);
    }

    private static Optional<UUID> firstBusy(ServerPlayer player, PlayerCompanionData data, List<UUID> uuids) {
        if (uuids == null) {
            return Optional.empty();
        }
        for (UUID uuid : uuids) {
            if (uuid != null && CompanionLifecycleFacade.isBusy(player, data, uuid)) {
                return Optional.of(uuid);
            }
        }
        return Optional.empty();
    }
}

