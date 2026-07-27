package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.WheelIntentPhase;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.CompanionWheelIntentResultPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;
import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionWheelTransactionService {
    private static final long TIMEOUT_TICKS = 20L * 70L;
    private static final long RESULT_CACHE_TICKS = 20L * 120L;
    private static final Map<ProtocolRequestKey, Transaction> ACTIVE = new HashMap<>();
    private static final Map<LaneKey, ProtocolRequestKey> ACTIVE_BY_LANE = new HashMap<>();
    private static final TerminalResultCache<ProtocolRequestKey, CompanionWheelIntentResultPacket> RESULTS =
            new TerminalResultCache<>();

    private CompanionWheelTransactionService() {
    }

    public static boolean begin(ServerPlayer player, PlayerCompanionData data, UUID requestId, CompanionKind kind,
                                CompanionAction action, UUID targetUuid, long expectedRevision) {
        if (player == null || data == null || requestId == null || kind == null || action == null
                || targetUuid == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        ProtocolRequestKey requestKey = new ProtocolRequestKey(player.getUUID(), requestId);
        CompanionWheelIntentResultPacket cached = RESULTS.get(requestKey);
        if (cached != null) {
            ModNetwork.sendToPlayer(player, cached);
            return false;
        }
        Transaction duplicate = ACTIVE.get(requestKey);
        if (duplicate != null) {
            send(player, duplicate, WheelIntentPhase.STARTED, "duplicate_request");
            return false;
        }
        LaneKey lane = new LaneKey(player.getUUID(), kind);
        ProtocolRequestKey previousKey = ACTIVE_BY_LANE.get(lane);
        Transaction previous = previousKey == null ? null : ACTIVE.get(previousKey);
        if (previous != null) {
            finish(player, previous, WheelIntentPhase.CANCELLED, "superseded");
            cancelRuntime(player, data, previous, "superseded");
        }
        Transaction transaction = new Transaction(requestId, player.getUUID(), kind, action, targetUuid,
                expectedRevision, data.isDeployed(kind, targetUuid), now);
        ACTIVE.put(requestKey, transaction);
        ACTIVE_BY_LANE.put(lane, requestKey);
        send(player, transaction, WheelIntentPhase.STARTED, "accepted");
        FindMeDebugLogger.info("wheel-transaction",
                "started request={} player={} kind={} action={} target={} expectedRevision={} serverRevision={}",
                requestId, player.getUUID(), kind, action, targetUuid, expectedRevision,
                CompanionDataService.revision(player));
        return true;
    }

    public static boolean canRecallActiveSwitch(ServerPlayer player, CompanionKind kind, UUID targetUuid) {
        if (player == null || kind == null || targetUuid == null) {
            return false;
        }
        ProtocolRequestKey activeKey = ACTIVE_BY_LANE.get(new LaneKey(player.getUUID(), kind));
        Transaction active = activeKey == null ? null : ACTIVE.get(activeKey);
        return active != null && active.action == CompanionAction.SELECT_SUMMON
                && active.targetUuid.equals(targetUuid);
    }

    public static void reject(ServerPlayer player, UUID requestId, CompanionKind kind,
                              CompanionAction action, UUID targetUuid, String reason) {
        if (player == null || requestId == null || kind == null || action == null || targetUuid == null) {
            return;
        }
        ProtocolRequestKey requestKey = new ProtocolRequestKey(player.getUUID(), requestId);
        CompanionWheelIntentResultPacket cached = RESULTS.get(requestKey);
        if (cached != null) {
            ModNetwork.sendToPlayer(player, cached);
            return;
        }
        finish(player, new Transaction(requestId, player.getUUID(), kind, action, targetUuid,
                CompanionDataService.revision(player), false, player.serverLevel().getGameTime()),
                WheelIntentPhase.REJECTED, reason);
    }

    public static void completeImmediate(ServerPlayer player, UUID requestId, String reason) {
        finishActive(player, requestId, WheelIntentPhase.COMPLETED, reason);
    }

    public static void fail(ServerPlayer player, UUID requestId, String reason) {
        finishActive(player, requestId, WheelIntentPhase.FAILED, reason);
    }

    public static void complete(ServerPlayer player, CompanionKind kind, CompanionAction action,
                                UUID targetUuid, String reason) {
        finishMatching(player, kind, action, targetUuid, WheelIntentPhase.COMPLETED, reason);
    }

    public static void failLatest(ServerPlayer player, CompanionKind kind, UUID targetUuid, String reason) {
        if (player == null || kind == null) return;
        Transaction latest = null;
        for (Transaction transaction : ACTIVE.values()) {
            if (!transaction.playerUuid.equals(player.getUUID()) || transaction.kind != kind
                    || targetUuid != null && !transaction.targetUuid.equals(targetUuid)) {
                continue;
            }
            if (latest == null || transaction.startedAt > latest.startedAt) {
                latest = transaction;
            }
        }
        if (latest != null) {
            finish(player, latest, WheelIntentPhase.FAILED, reason);
        }
    }

    public static void cancel(ServerPlayer player, CompanionKind kind, CompanionAction action,
                              UUID targetUuid, String reason) {
        finishMatching(player, kind, action, targetUuid, WheelIntentPhase.CANCELLED, reason);
    }

    public static void observeRoster(ServerPlayer player, CompanionKind kind,
                                     List<CompanionListPacket.Entry> entries) {
        if (player == null || kind == null || entries == null || ACTIVE.isEmpty()) return;
        Map<UUID, CompanionListPacket.Entry> byUuid = new HashMap<>();
        for (CompanionListPacket.Entry entry : entries) {
            byUuid.put(entry.uuid(), entry);
        }
        for (Transaction transaction : new ArrayList<>(ACTIVE.values())) {
            if (!transaction.playerUuid.equals(player.getUUID()) || transaction.kind != kind) continue;
            CompanionListPacket.Entry entry = byUuid.get(transaction.targetUuid);
            boolean completed = switch (transaction.action) {
                case SELECT_SUMMON -> entry != null && (entry.deployed() || entry.ridden());
                case RECALL -> entry != null && !entry.deployed() && !entry.ridden();
                default -> false;
            };
            if (completed) {
                finish(player, transaction, WheelIntentPhase.COMPLETED, "authoritative_roster");
            }
        }
    }

    public static void cancelForPlayer(ServerPlayer player, String reason) {
        if (player == null) return;
        for (Transaction transaction : new ArrayList<>(ACTIVE.values())) {
            if (transaction.playerUuid.equals(player.getUUID())) {
                finish(player, transaction, WheelIntentPhase.CANCELLED, reason);
            }
        }
    }

    public static void resetServerState() {
        ACTIVE.clear();
        ACTIVE_BY_LANE.clear();
        RESULTS.clear();
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long now = server.overworld().getGameTime();
        RESULTS.expire(now);
        for (Transaction transaction : new ArrayList<>(ACTIVE.values())) {
            if (now - transaction.startedAt < TIMEOUT_TICKS) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(transaction.playerUuid);
            if (player == null) {
                remove(transaction);
            } else {
                finish(player, transaction, WheelIntentPhase.TIMED_OUT, "server_timeout");
            }
        }
    }

    private static void finishMatching(ServerPlayer player, CompanionKind kind, CompanionAction action,
                                       UUID targetUuid, WheelIntentPhase phase, String reason) {
        if (player == null || kind == null || action == null || targetUuid == null) return;
        for (Transaction transaction : new ArrayList<>(ACTIVE.values())) {
            if (transaction.playerUuid.equals(player.getUUID()) && transaction.kind == kind
                    && transaction.action == action && transaction.targetUuid.equals(targetUuid)) {
                finish(player, transaction, phase, reason);
            }
        }
    }

    private static void finishActive(ServerPlayer player, UUID requestId, WheelIntentPhase phase, String reason) {
        if (player == null || requestId == null) return;
        Transaction transaction = ACTIVE.get(new ProtocolRequestKey(player.getUUID(), requestId));
        if (transaction != null && transaction.playerUuid.equals(player.getUUID())) {
            finish(player, transaction, phase, reason);
        }
    }

    private static void finish(ServerPlayer player, Transaction transaction,
                               WheelIntentPhase phase, String reason) {
        if (phase == null || !phase.terminal()) return;
        ProtocolRequestKey requestKey = transaction.key();
        if (!ACTIVE.remove(requestKey, transaction) && RESULTS.contains(requestKey)) {
            return;
        }
        ACTIVE_BY_LANE.remove(transaction.laneKey(), requestKey);
        CompanionWheelIntentResultPacket packet = packet(player, transaction, phase, reason);
        long now = player.serverLevel().getGameTime();
        RESULTS.put(requestKey, packet, now + RESULT_CACHE_TICKS);
        ModNetwork.sendToPlayer(player, packet);
        FindMeDebugLogger.info("wheel-transaction",
                "terminal request={} player={} kind={} action={} target={} phase={} reason={} revision={}",
                transaction.requestId, player.getUUID(), transaction.kind, transaction.action,
                transaction.targetUuid, phase, reason, packet.serverRevision());
    }

    private static void remove(Transaction transaction) {
        ProtocolRequestKey requestKey = transaction.key();
        ACTIVE.remove(requestKey, transaction);
        ACTIVE_BY_LANE.remove(transaction.laneKey(), requestKey);
    }

    private static void send(ServerPlayer player, Transaction transaction,
                             WheelIntentPhase phase, String reason) {
        ModNetwork.sendToPlayer(player, packet(player, transaction, phase, reason));
    }

    private static CompanionWheelIntentResultPacket packet(ServerPlayer player, Transaction transaction,
                                                            WheelIntentPhase phase, String reason) {
        return new CompanionWheelIntentResultPacket(transaction.requestId, transaction.kind,
                transaction.action, transaction.targetUuid, CompanionDataService.revision(player), phase,
                reason == null ? "" : reason);
    }

    private static void cancelRuntime(ServerPlayer player, PlayerCompanionData data,
                                      Transaction transaction, String reason) {
        if (transaction.action == CompanionAction.RIDE_HOME) {
            CompanionRideHomeJourneyService.cancelForPlayer(player, "wheel_" + reason);
            return;
        }
        if (transaction.action != CompanionAction.SELECT_SUMMON) {
            return;
        }
        CompanionTransientStateService.cancelTarget(player, data, transaction.targetUuid,
                CompanionTransientStateService.Reason.SUPERSEDED);
        if (!shouldRollbackDeployment(transaction.action, transaction.initiallyDeployed)
                || player.getVehicle() != null
                && transaction.targetUuid.equals(player.getVehicle().getUUID())) {
            return;
        }
        Entity target = CompanionEntityLookup.findEntity(player.getServer(), transaction.targetUuid).orElse(null);
        boolean changed = target instanceof LivingEntity living && living.isAlive()
                && CompanionDeploymentService.collectLiving(player, data, transaction.kind, living);
        if (!changed && data.isDeployed(transaction.kind, transaction.targetUuid)
                && data.storedEntity(transaction.targetUuid).isPresent()) {
            data.clearDeployed(transaction.kind, transaction.targetUuid);
            changed = true;
        }
        if (changed) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, transaction.kind);
        }
    }

    static boolean shouldRollbackDeployment(CompanionAction action, boolean initiallyDeployed) {
        return action == CompanionAction.SELECT_SUMMON && !initiallyDeployed;
    }

    private record Transaction(UUID requestId, UUID playerUuid, CompanionKind kind,
                               CompanionAction action, UUID targetUuid,
                               long expectedRevision, boolean initiallyDeployed, long startedAt) {
        private ProtocolRequestKey key() {
            return new ProtocolRequestKey(playerUuid, requestId);
        }

        private LaneKey laneKey() {
            return new LaneKey(playerUuid, kind);
        }
    }

    private record LaneKey(UUID playerUuid, CompanionKind kind) {
    }

}
