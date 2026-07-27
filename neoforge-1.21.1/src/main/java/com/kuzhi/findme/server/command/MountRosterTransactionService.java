package com.kuzhi.findme.server.command;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.MountRosterSource;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.WheelIntentPhase;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.MountRosterIntentResultPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.RideHandoffService;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;
import com.kuzhi.findme.server.lifecycle.CompanionMountCinematicFlowService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-owned transaction boundary for the merged mount roster. */
public final class MountRosterTransactionService {
    private static final long TIMEOUT_TICKS = 20L * 70L;
    private static final long DISPATCH_RETRY_WINDOW_TICKS = 40L;
    private static final long DISPATCH_RETRY_INTERVAL_TICKS = 5L;
    private static final long RESULT_CACHE_TICKS = 20L * 120L;
    private static final int REQUIRED_STABLE_TICKS = 3;
    private static final Map<ProtocolRequestKey, Transaction> ACTIVE_BY_REQUEST = new HashMap<>();
    private static final Map<UUID, ProtocolRequestKey> ACTIVE_BY_PLAYER = new HashMap<>();
    private static final TerminalResultCache<ProtocolRequestKey, MountRosterIntentResultPacket> RESULTS =
            new TerminalResultCache<>();

    private MountRosterTransactionService() {
    }

    public static void begin(ServerPlayer player, UUID requestId, MountRosterAction action,
                             MountRosterSource destinationSource, UUID targetUuid, int sourceSlot,
                             int teamIndex, long expectedRevision) {
        if (player == null || requestId == null || action == null || destinationSource == null
                || targetUuid == null) return;
        ProtocolRequestKey requestKey = new ProtocolRequestKey(player.getUUID(), requestId);
        MountRosterIntentResultPacket cached = RESULTS.get(requestKey);
        if (cached != null) {
            ModNetwork.sendToPlayer(player, cached);
            return;
        }
        Transaction duplicate = ACTIVE_BY_REQUEST.get(requestKey);
        if (duplicate != null) {
            send(player, duplicate, WheelIntentPhase.STARTED, "duplicate_request");
            return;
        }

        PlayerCompanionData data = CompanionDataService.data(player);
        long revision = CompanionDataService.revision(player);
        String rejection = validate(player, data, action, destinationSource, targetUuid, sourceSlot, teamIndex,
                expectedRevision, revision);
        if (rejection != null) {
            sendTerminalWithoutActive(player, requestId, action, destinationSource, targetUuid,
                    WheelIntentPhase.REJECTED, rejection);
            return;
        }

        ProtocolRequestKey previousRequest = ACTIVE_BY_PLAYER.get(player.getUUID());
        if (previousRequest != null) {
            Transaction previous = ACTIVE_BY_REQUEST.get(previousRequest);
            if (previous != null) {
                cancelRuntime(player, previous, "superseded");
                finish(player, previous, WheelIntentPhase.CANCELLED, "superseded");
            }
        }

        RideHandoffService.Source source = RideHandoffService.resolveSource(player, data);
        RideHandoffService.MotionSnapshot handoff = RideHandoffService.captureMotion(source, player.getVehicle());
        CompanionMoveType destinationMove = destinationMoveType(player, data, destinationSource,
                targetUuid, sourceSlot);
        long now = player.serverLevel().getGameTime();
        Transaction transaction = new Transaction(requestId, player.getUUID(), action, destinationSource,
                targetUuid, sourceSlot, teamIndex, source, destinationMove, handoff, expectedRevision, now);
        ACTIVE_BY_REQUEST.put(requestKey, transaction);
        ACTIVE_BY_PLAYER.put(player.getUUID(), requestKey);
        if (action == MountRosterAction.ACTIVATE) {
            RideHandoffService.registerTransactionMotion(player.getUUID(), targetUuid, handoff);
            RideHandoffService.retainSource(player.getUUID(), source, targetUuid);
        }
        send(player, transaction, WheelIntentPhase.STARTED, "accepted");
        FindMeDebugLogger.info("mount-roster-transaction",
                "request={} phase=STARTED player={} action={} sourceType={} source={} sourceMove={} destinationSource={} destination={} destinationMove={} velocity={} revision={}",
                requestId, player.getUUID(), action, source.type(), source.uuid(), source.moveType(),
                destinationSource, targetUuid, destinationMove, handoff.velocity(), revision);

        if (action == MountRosterAction.RECALL && !isDeployed(player, data, transaction)) {
            finish(player, transaction, WheelIntentPhase.COMPLETED, "already_recalled");
            return;
        }
        if (action == MountRosterAction.ACTIVATE && isControllerReady(player, data, transaction)) {
            finish(player, transaction, WheelIntentPhase.COMPLETED, "already_active");
            return;
        }

        if (!dispatch(player, data, transaction)) {
            // A target can still be leaving an earlier summon/retreat transaction on
            // this exact tick. Keep the roster transaction alive and retry briefly;
            // treating this as terminal used to strand a switch halfway through.
            transaction.dispatchPending = true;
            transaction.nextDispatchAttemptAt = now + DISPATCH_RETRY_INTERVAL_TICKS;
            FindMeDebugLogger.info("mount-roster-transaction",
                    "request={} phase=DISPATCH_RETRY_SCHEDULED player={} destinationSource={} destination={} windowTicks={}",
                    requestId, player.getUUID(), destinationSource, targetUuid, DISPATCH_RETRY_WINDOW_TICKS);
        } else {
            transaction.phase = OperationPhase.DESTINATION_STARTED;
            if (action == MountRosterAction.SELECT) {
                finish(player, transaction, WheelIntentPhase.COMPLETED, "selection_committed");
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long now = server.overworld().getGameTime();
        RESULTS.expire(now);
        for (Transaction transaction : new ArrayList<>(ACTIVE_BY_REQUEST.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(transaction.playerUuid);
            if (player == null) {
                remove(transaction);
                continue;
            }
            if (now - transaction.startedAt >= TIMEOUT_TICKS) {
                cancelRuntime(player, transaction, "server_timeout");
                finish(player, transaction, WheelIntentPhase.TIMED_OUT, "server_timeout");
                continue;
            }
            if (transaction.dispatchPending && now >= transaction.nextDispatchAttemptAt) {
                if (now - transaction.startedAt >= DISPATCH_RETRY_WINDOW_TICKS) {
                    transaction.dispatchPending = false;
                    cancelRuntime(player, transaction, "dispatch_timeout");
                    finish(player, transaction, WheelIntentPhase.FAILED, "dispatch_timeout");
                    continue;
                }
                PlayerCompanionData dispatchData = CompanionDataService.data(player);
                if (dispatch(player, dispatchData, transaction)) {
                    transaction.dispatchPending = false;
                    transaction.phase = OperationPhase.DESTINATION_STARTED;
                    FindMeDebugLogger.info("mount-roster-transaction",
                            "request={} phase=DISPATCH_RETRY_ACCEPTED player={} destinationSource={} destination={} ageTicks={}",
                            transaction.requestId, player.getUUID(), transaction.destinationSource,
                            transaction.targetUuid, now - transaction.startedAt);
                    if (transaction.action == MountRosterAction.SELECT) {
                        finish(player, transaction, WheelIntentPhase.COMPLETED, "selection_committed");
                        continue;
                    }
                } else {
                    transaction.nextDispatchAttemptAt = now + DISPATCH_RETRY_INTERVAL_TICKS;
                }
            }
            PlayerCompanionData data = requiresPlayerData(transaction.destinationSource, transaction.action)
                    ? CompanionDataService.data(player) : null;
            boolean satisfied = transaction.action == MountRosterAction.RECALL
                    ? !isDeployed(player, data, transaction)
                    : isControllerReady(player, data, transaction);
            transaction.stableTicks = satisfied ? transaction.stableTicks + 1 : 0;
            if (satisfied) transaction.phase = OperationPhase.VERIFYING;
            if (transaction.stableTicks >= REQUIRED_STABLE_TICKS) {
                finish(player, transaction, WheelIntentPhase.COMPLETED,
                        transaction.action == MountRosterAction.RECALL
                                ? "retirement_verified" : "controller_verified");
            }
        }
    }

    public static void cancelForPlayer(ServerPlayer player, String reason) {
        if (player == null) return;
        ProtocolRequestKey requestKey = ACTIVE_BY_PLAYER.get(player.getUUID());
        Transaction transaction = requestKey == null ? null : ACTIVE_BY_REQUEST.get(requestKey);
        if (transaction != null) {
            cancelRuntime(player, transaction, reason);
            finish(player, transaction, WheelIntentPhase.CANCELLED, reason);
        }
    }

    /** Terminates a mount-roster request when the shared summon path rejects its target. */
    public static void failLatest(ServerPlayer player, UUID targetUuid, String reason) {
        if (player == null) return;
        for (Transaction transaction : new ArrayList<>(ACTIVE_BY_REQUEST.values())) {
            if (!transaction.playerUuid.equals(player.getUUID())
                    || transaction.destinationSource != MountRosterSource.FIND_ME
                    || targetUuid != null && !transaction.targetUuid.equals(targetUuid)) {
                continue;
            }
            cancelRuntime(player, transaction, "summon_failure");
            finish(player, transaction, WheelIntentPhase.FAILED,
                    reason == null ? "summon_failed" : reason);
        }
    }

    public static void resetServerState() {
        ACTIVE_BY_REQUEST.clear();
        ACTIVE_BY_PLAYER.clear();
        RESULTS.clear();
        RideHandoffService.resetServerState();
    }

    private static void cancelRuntime(ServerPlayer player, Transaction transaction, String reason) {
        switch (transaction.destinationSource) {
            case FIND_ME -> rollbackFindMeActivation(player, transaction.targetUuid, reason);
            case VEHICLE -> VehicleManager.cancelRosterActivation(player, transaction.targetUuid, reason);
            case COBBLEMON -> {
                CompanionMountCinematicFlowService.cancelForCompanion(player, transaction.targetUuid,
                        "mount_roster:" + reason);
                CobblemonCompat.cancelRosterActivation(player, transaction.targetUuid);
            }
        }
    }

    private static void rollbackFindMeActivation(ServerPlayer player, UUID targetUuid, String reason) {
        CompanionMountCinematicFlowService.cancelForCompanion(player, targetUuid, "mount_roster:" + reason);
        if (player.getVehicle() != null && targetUuid.equals(player.getVehicle().getUUID())) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        Entity target = CompanionEntityLookup.findEntity(player.getServer(), targetUuid).orElse(null);
        boolean changed = target instanceof LivingEntity living && living.isAlive()
                && CompanionDeploymentService.collectLiving(player, data, CompanionKind.MOUNT, living);
        if (!changed && data.isDeployed(CompanionKind.MOUNT, targetUuid)
                && data.storedEntity(targetUuid).isPresent()) {
            data.clearDeployed(CompanionKind.MOUNT, targetUuid);
            changed = true;
        }
        if (changed) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        }
    }

    private static String validate(ServerPlayer player, PlayerCompanionData data, MountRosterAction action,
                                   MountRosterSource source, UUID targetUuid, int sourceSlot,
                                   int teamIndex,
                                   long expectedRevision, long serverRevision) {
        long sourceRevision = sourceRevision(source, serverRevision, CobblemonCompat.syncRevision(player));
        if (expectedRevision > sourceRevision) return "invalid_revision";
        if (!FindMeModuleService.enabled(FindMeModule.RIDING)) return "riding_module_disabled";
        if (action == MountRosterAction.ACTIVATE && source == MountRosterSource.FIND_ME
                && player.serverLevel().getGameTime() < data.readyAt(CompanionKind.MOUNT)) {
            return "cooldown";
        }
        if (source != MountRosterSource.COBBLEMON && teamIndex >= 0) {
            CompanionTeamTarget target = source == MountRosterSource.FIND_ME
                    ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.VEHICLE;
            if (teamIndex >= data.teamCount(target) || !data.team(target, teamIndex).contains(targetUuid)) {
                return "invalid_team_context";
            }
        }
        return switch (source) {
            case FIND_ME -> !data.contains(CompanionKind.MOUNT, targetUuid)
                    || data.deadList().contains(targetUuid) ? "target_not_registered"
                    : action != MountRosterAction.SELECT
                    && CompanionLifecycleFacade.isBusy(player, data, targetUuid)
                    && !canRecallActiveSwitch(player, action, targetUuid) ? "target_busy" : null;
            case VEHICLE -> data.containsVehicle(targetUuid) ? null : "target_not_registered";
            case COBBLEMON -> !FindMeModuleService.enabled(FindMeModule.COBBLEMON_INTEGRATION)
                    ? "cobblemon_module_disabled"
                    : CobblemonCompat.contains(player, sourceSlot, targetUuid)
                    ? null : "target_not_registered";
        };
    }

    private static boolean canRecallActiveSwitch(ServerPlayer player, MountRosterAction action, UUID targetUuid) {
        if (player == null || action != MountRosterAction.RECALL || targetUuid == null) {
            return false;
        }
        ProtocolRequestKey activeKey = ACTIVE_BY_PLAYER.get(player.getUUID());
        Transaction active = activeKey == null ? null : ACTIVE_BY_REQUEST.get(activeKey);
        return active != null && active.action == MountRosterAction.ACTIVATE
                && active.targetUuid.equals(targetUuid);
    }

    private static boolean dispatch(ServerPlayer player, PlayerCompanionData data, Transaction transaction) {
        if (transaction.destinationSource != MountRosterSource.COBBLEMON && transaction.teamIndex >= 0) {
            CompanionTeamTarget target = transaction.destinationSource == MountRosterSource.FIND_ME
                    ? CompanionTeamTarget.MOUNT : CompanionTeamTarget.VEHICLE;
            if (!data.applyTeamToWheel(target, transaction.teamIndex)) return false;
            CompanionDataService.save(player, data);
        }
        return switch (transaction.destinationSource) {
            case FIND_ME -> switch (transaction.action) {
                case SELECT -> CompanionWheelCommandHandler.selectUuid(player, data, CompanionKind.MOUNT,
                        transaction.targetUuid, false);
                case ACTIVATE -> transaction.source.present()
                        && !transaction.source.uuid().equals(transaction.targetUuid)
                        ? CompanionWheelCommandHandler.selectMountUuidForSwitch(player, data,
                        transaction.targetUuid)
                        : CompanionWheelCommandHandler.selectUuid(player, data, CompanionKind.MOUNT,
                        transaction.targetUuid, true);
                case RECALL -> CompanionWheelCommandHandler.recallUuid(player, data, CompanionKind.MOUNT,
                        transaction.targetUuid);
            };
            case VEHICLE -> VehicleManager.handleRosterAction(player, transaction.action,
                    transaction.targetUuid);
            case COBBLEMON -> CobblemonCompat.handleRosterAction(player, transaction.action,
                    transaction.sourceSlot, transaction.targetUuid);
        };
    }

    private static boolean isControllerReady(ServerPlayer player, PlayerCompanionData data,
                                             Transaction transaction) {
        return switch (transaction.destinationSource) {
            case FIND_ME -> player.getVehicle() != null
                    && transaction.targetUuid.equals(player.getVehicle().getUUID());
            case VEHICLE -> VehicleManager.isRideReady(player, data, transaction.targetUuid);
            case COBBLEMON -> CobblemonCompat.isRideReady(player, transaction.targetUuid);
        };
    }

    private static boolean isDeployed(ServerPlayer player, PlayerCompanionData data, Transaction transaction) {
        return switch (transaction.destinationSource) {
            case FIND_ME -> data.isDeployed(CompanionKind.MOUNT, transaction.targetUuid)
                    || player.getVehicle() != null && transaction.targetUuid.equals(player.getVehicle().getUUID())
                    || CompanionEntityLookup.findEntity(player.getServer(), transaction.targetUuid)
                    .filter(entity -> entity instanceof LivingEntity living && living.isAlive()
                            && !CompanionStorageService.isStoragePending(living))
                    .isPresent();
            case VEHICLE -> data.isVehicleDeployed(transaction.targetUuid)
                    || VehicleManager.isRideReady(player, data, transaction.targetUuid);
            case COBBLEMON -> CobblemonCompat.isDeployed(player, transaction.targetUuid);
        };
    }

    static boolean requiresPlayerData(MountRosterSource source, MountRosterAction action) {
        if (source == null || action == null) return false;
        return source == MountRosterSource.VEHICLE
                || source == MountRosterSource.FIND_ME && action == MountRosterAction.RECALL;
    }

    static long sourceRevision(MountRosterSource source, long findMeRevision, long cobblemonRevision) {
        return source == MountRosterSource.COBBLEMON ? cobblemonRevision : findMeRevision;
    }

    private static CompanionMoveType destinationMoveType(ServerPlayer player, PlayerCompanionData data,
                                                         MountRosterSource source, UUID targetUuid,
                                                         int sourceSlot) {
        if (source == MountRosterSource.COBBLEMON) {
            return CobblemonCompat.moveType(player, sourceSlot, targetUuid);
        }
        Entity live = CompanionEntityLookup.findEntity(player.getServer(), targetUuid).orElse(null);
        if (live != null) return CompanionEntityClassifier.moveType(live, CompanionKind.MOUNT);
        CompoundTag tag = data.storedEntity(targetUuid).orElse(null);
        if (tag != null && tag.contains("id")) {
            return CompanionEntityClassifier.moveType(tag.getString("id"), CompanionKind.MOUNT);
        }
        return CompanionMoveType.WALK;
    }

    private static void finish(ServerPlayer player, Transaction transaction,
                               WheelIntentPhase phase, String reason) {
        if (transaction == null || !phase.terminal()) return;
        ProtocolRequestKey requestKey = transaction.key();
        if (!ACTIVE_BY_REQUEST.remove(requestKey, transaction)
                && RESULTS.contains(requestKey)) return;
        ACTIVE_BY_PLAYER.remove(transaction.playerUuid, requestKey);
        RideHandoffService.clearTransactionMotion(transaction.playerUuid, transaction.targetUuid);
        RideHandoffService.releaseSourceRetention(transaction.playerUuid, transaction.targetUuid);
        MountRosterIntentResultPacket packet = packet(player, transaction, phase, reason);
        RESULTS.put(requestKey, packet,
                player.serverLevel().getGameTime() + RESULT_CACHE_TICKS);
        ModNetwork.sendToPlayer(player, packet);
        syncAll(player);
        FindMeDebugLogger.info("mount-roster-transaction",
                "request={} phase={} operationPhase={} player={} sourceType={} source={} destinationSource={} destination={} reason={} stableTicks={} revision={}",
                transaction.requestId, phase, transaction.phase, player.getUUID(), transaction.source.type(),
                transaction.source.uuid(), transaction.destinationSource, transaction.targetUuid, reason,
                transaction.stableTicks, packet.serverRevision());
    }

    private static void sendTerminalWithoutActive(ServerPlayer player, UUID requestId, MountRosterAction action,
                                                  MountRosterSource source, UUID targetUuid,
                                                  WheelIntentPhase phase, String reason) {
        MountRosterIntentResultPacket packet = new MountRosterIntentResultPacket(requestId, action, source,
                targetUuid, currentSourceRevision(player, source), phase, reason);
        RESULTS.put(new ProtocolRequestKey(player.getUUID(), requestId), packet,
                player.serverLevel().getGameTime() + RESULT_CACHE_TICKS);
        ModNetwork.sendToPlayer(player, packet);
    }

    private static void send(ServerPlayer player, Transaction transaction,
                             WheelIntentPhase phase, String reason) {
        ModNetwork.sendToPlayer(player, packet(player, transaction, phase, reason));
    }

    private static MountRosterIntentResultPacket packet(ServerPlayer player, Transaction transaction,
                                                        WheelIntentPhase phase, String reason) {
        return new MountRosterIntentResultPacket(transaction.requestId, transaction.action,
                transaction.destinationSource, transaction.targetUuid,
                currentSourceRevision(player, transaction.destinationSource), phase,
                reason == null ? "" : reason);
    }

    private static long currentSourceRevision(ServerPlayer player, MountRosterSource source) {
        return sourceRevision(source, CompanionDataService.revision(player),
                CobblemonCompat.syncRevision(player));
    }

    private static void syncAll(ServerPlayer player) {
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        VehicleManager.syncToClient(player);
        CobblemonCompat.sync(player);
    }

    private static void remove(Transaction transaction) {
        ProtocolRequestKey requestKey = transaction.key();
        ACTIVE_BY_REQUEST.remove(requestKey, transaction);
        ACTIVE_BY_PLAYER.remove(transaction.playerUuid, requestKey);
        RideHandoffService.clearTransactionMotion(transaction.playerUuid, transaction.targetUuid);
        RideHandoffService.releaseSourceRetention(transaction.playerUuid, transaction.targetUuid);
    }

    private enum OperationPhase {
        REQUESTED,
        DESTINATION_STARTED,
        VERIFYING
    }

    private static final class Transaction {
        private final UUID requestId;
        private final UUID playerUuid;
        private final MountRosterAction action;
        private final MountRosterSource destinationSource;
        private final UUID targetUuid;
        private final int sourceSlot;
        private final int teamIndex;
        private final RideHandoffService.Source source;
        private final CompanionMoveType destinationMoveType;
        private final RideHandoffService.MotionSnapshot handoff;
        private final long expectedRevision;
        private final long startedAt;
        private OperationPhase phase = OperationPhase.REQUESTED;
        private int stableTicks;
        private boolean dispatchPending;
        private long nextDispatchAttemptAt;

        private Transaction(UUID requestId, UUID playerUuid, MountRosterAction action,
                            MountRosterSource destinationSource, UUID targetUuid, int sourceSlot,
                            int teamIndex,
                            RideHandoffService.Source source, CompanionMoveType destinationMoveType,
                            RideHandoffService.MotionSnapshot handoff, long expectedRevision, long startedAt) {
            this.requestId = requestId;
            this.playerUuid = playerUuid;
            this.action = action;
            this.destinationSource = destinationSource;
            this.targetUuid = targetUuid;
            this.sourceSlot = sourceSlot;
            this.teamIndex = teamIndex;
            this.source = source;
            this.destinationMoveType = destinationMoveType;
            this.handoff = handoff;
            this.expectedRevision = expectedRevision;
            this.startedAt = startedAt;
        }

        private ProtocolRequestKey key() {
            return new ProtocolRequestKey(playerUuid, requestId);
        }
    }

}
