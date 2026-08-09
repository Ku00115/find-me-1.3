package com.kuzhi.findme.server.api;

import com.kuzhi.findme.api.CompanionActionRequest;
import com.kuzhi.findme.api.CompanionActionRequest.Action;
import com.kuzhi.findme.api.CompanionActionRequest.Reason;
import com.kuzhi.findme.api.CompanionActionRequest.State;
import com.kuzhi.findme.api.CompanionDescriptor;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionCollectionService;
import com.kuzhi.findme.server.lifecycle.CompanionDeployRequestService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.lifecycle.CompanionSummonApproachService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionActionRequestService {
    private static final int DEFAULT_TIMEOUT_TICKS = 20 * 30;
    private static final int MAX_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final int TERMINAL_RETENTION_TICKS = 20 * 60 * 5;
    private static final Map<RequestKey, Entry> REQUESTS = new HashMap<>();
    private static final Map<OwnerKey, java.util.Set<UUID>> TASK_DEPLOYMENTS = new HashMap<>();

    private CompanionActionRequestService() {
    }

    public static CompanionActionRequest submit(ServerPlayer owner, UUID companionUuid, UUID requestId,
                                                 Action action, int timeoutTicks) {
        if (owner == null) return invalid(requestId, null, companionUuid, action, 0L);
        long now = owner.serverLevel().getGameTime();
        if (requestId == null || companionUuid == null || action == null) {
            return invalid(requestId, owner.getUUID(), companionUuid, action, now);
        }
        RequestKey key = new RequestKey(owner.getServer(), owner.getUUID(), requestId);
        Entry existing = REQUESTS.get(key);
        if (existing != null) {
            if (existing.matches(owner.getServer(), owner.getUUID(), companionUuid, action)) return existing.snapshot();
            return rejected(requestId, owner.getUUID(), companionUuid, action, now, Reason.INVALID_REQUEST);
        }

        CompanionDescriptor descriptor = CompanionDescriptorService
                .describe(owner.getServer(), owner.getUUID(), companionUuid).orElse(null);
        if (descriptor == null) return storeRejected(owner.getServer(), requestId, owner.getUUID(),
                companionUuid, action, now, Reason.NOT_REGISTERED);
        if (descriptor.lifecycle() == CompanionLifecycleState.DEAD) {
            return storeRejected(owner.getServer(), requestId, owner.getUUID(), companionUuid, action, now,
                    Reason.DEAD);
        }
        if (satisfied(descriptor, action)) {
            updateTaskDeploymentOwnership(owner.getServer(), owner.getUUID(), descriptor, action);
            Entry entry = Entry.terminal(owner.getServer(), requestId, owner.getUUID(), companionUuid,
                    action, now, State.SUCCEEDED, Reason.ALREADY_SATISFIED);
            REQUESTS.put(key, entry);
            return entry.snapshot();
        }
        if (CompanionOperationLockService.get(companionUuid) != null) {
            return storeRejected(owner.getServer(), requestId, owner.getUUID(), companionUuid, action, now,
                    Reason.BUSY);
        }

        int duration = Math.min(MAX_TIMEOUT_TICKS,
                timeoutTicks <= 0 ? DEFAULT_TIMEOUT_TICKS : timeoutTicks);
        Entry entry = new Entry(owner.getServer(), requestId, owner.getUUID(), companionUuid,
                action, now, now + duration, State.PENDING, Reason.NONE, 0L);
        REQUESTS.put(key, entry);
        PlayerCompanionData data = CompanionDataService.data(owner);
        StartResult started = start(owner, data, descriptor, action, requestId);
        entry.operationOwned = started.operationOwned();
        if (!started.accepted()) {
            entry.finish(State.REJECTED, Reason.UNAVAILABLE, now);
        } else {
            CompanionDescriptor current = CompanionDescriptorService
                    .describe(owner.getServer(), owner.getUUID(), companionUuid).orElse(null);
            if (current != null && satisfied(current, action)) {
                updateTaskDeploymentOwnership(owner.getServer(), owner.getUUID(), current, action);
                entry.finish(State.SUCCEEDED, Reason.NONE, now);
            }
        }
        return entry.snapshot();
    }

    public static Optional<CompanionActionRequest> status(MinecraftServer server, UUID ownerUuid, UUID requestId) {
        if (server == null || ownerUuid == null || requestId == null) return Optional.empty();
        Entry entry = REQUESTS.get(new RequestKey(server, ownerUuid, requestId));
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
    }

    public static void tick(MinecraftServer server) {
        if (server == null || REQUESTS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<Entry> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.server != server) continue;
            if (entry.state != State.PENDING) {
                if (entry.completedAt + TERMINAL_RETENTION_TICKS < now) iterator.remove();
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.ownerUuid);
            if (owner == null) {
                cancelUnderlying(null, entry, "owner_offline");
                entry.finish(State.REJECTED, Reason.OWNER_OFFLINE, now);
                continue;
            }
            CompanionDescriptor descriptor = CompanionDescriptorService
                    .describe(server, entry.ownerUuid, entry.companionUuid).orElse(null);
            if (descriptor == null) {
                cancelUnderlying(owner, entry, "released");
                entry.finish(State.REJECTED, Reason.RELEASED, now);
            } else if (descriptor.lifecycle() == CompanionLifecycleState.DEAD) {
                cancelUnderlying(owner, entry, "dead");
                entry.finish(State.REJECTED, Reason.DEAD, now);
            } else if (satisfied(descriptor, entry.action)) {
                updateTaskDeploymentOwnership(server, entry.ownerUuid, descriptor, entry.action);
                entry.finish(State.SUCCEEDED, Reason.NONE, now);
            } else if (entry.expiresAt < now) {
                cancelUnderlying(owner, entry, "timeout");
                entry.finish(State.TIMED_OUT, Reason.TIMEOUT, now);
            }
        }
    }

    public static void resetServerState(MinecraftServer server) {
        REQUESTS.values().removeIf(entry -> server == null || entry.server == server);
        TASK_DEPLOYMENTS.keySet().removeIf(key -> server == null || key.server == server);
    }

    public static void releaseTaskDeployments(ServerPlayer owner, String reason) {
        if (owner == null) return;
        java.util.Set<UUID> companions = TASK_DEPLOYMENTS.remove(
                new OwnerKey(owner.getServer(), owner.getUUID()));
        if (companions == null || companions.isEmpty()) return;
        PlayerCompanionData data = CompanionDataService.data(owner);
        for (UUID companionUuid : java.util.Set.copyOf(companions)) {
            CompanionDescriptor descriptor = CompanionDescriptorService
                    .describe(owner.getServer(), owner.getUUID(), companionUuid).orElse(null);
            if (descriptor != null && descriptor.live()) {
                CompanionCollectionService.collectUuid(owner, data, descriptor.kind(), companionUuid);
            }
        }
    }

    private static StartResult start(ServerPlayer owner, PlayerCompanionData data,
                                     CompanionDescriptor descriptor, Action action, UUID requestId) {
        if (action == Action.STORE) {
            boolean alreadyPending = CompanionStorageService.isStoragePending(descriptor.companionUuid());
            boolean accepted = CompanionCollectionService.collectUuid(owner, data, descriptor.kind(),
                    descriptor.companionUuid());
            return new StartResult(accepted, accepted && !alreadyPending && CompanionStorageService.isStoragePending(
                    descriptor.companionUuid()));
        }
        CompanionDeployRequestService.Result result = action == Action.TASK_DEPLOY
                ? CompanionDeployRequestService.requestExternalTask(owner, data, descriptor.kind(),
                descriptor.companionUuid(), "api:task_deploy:" + requestId)
                : CompanionDeployRequestService.request(owner, data, descriptor.kind(),
                descriptor.companionUuid(), CompanionDeployRequestService.Mode.AUTONOMOUS,
                "api:external_deploy:" + requestId);
        return new StartResult(result.state() != CompanionDeployRequestService.State.REJECTED,
                result.operationStarted());
    }

    private static void cancelUnderlying(ServerPlayer owner, Entry entry, String reason) {
        if (entry == null || !entry.operationOwned) return;
        entry.operationOwned = false;
        CompanionSummonApproachService.cancel(entry.companionUuid, "api_request_" + reason);
        if (owner != null && (entry.action == Action.DEPLOY || entry.action == Action.TASK_DEPLOY)) {
            PlayerCompanionData data = CompanionDataService.data(owner);
            CompanionDescriptor current = CompanionDescriptorService.describe(entry.server, entry.ownerUuid,
                    entry.companionUuid).orElse(null);
            if (current != null && current.deployed() && current.live()) {
                CompanionCollectionService.collectUuid(owner, data, current.kind(), entry.companionUuid);
            }
        }
    }

    static boolean satisfied(CompanionDescriptor descriptor, Action action) {
        if (descriptor == null) return false;
        if (action == Action.DEPLOY) {
            return descriptor.deployed() && descriptor.live()
                    && descriptor.lifecycle() == CompanionLifecycleState.DEPLOYED;
        }
        if (action == Action.TASK_DEPLOY) {
            return descriptor.live() && descriptor.lifecycle() == CompanionLifecycleState.DEPLOYED;
        }
        return descriptor.stored() && !descriptor.live() && !descriptor.deployed()
                && (descriptor.lifecycle() == CompanionLifecycleState.STORED
                || descriptor.lifecycle() == CompanionLifecycleState.HOME_STORED
                || descriptor.lifecycle() == CompanionLifecycleState.SHOULDER);
    }

    private static void updateTaskDeploymentOwnership(MinecraftServer server, UUID ownerUuid,
                                                       CompanionDescriptor descriptor, Action action) {
        OwnerKey key = new OwnerKey(server, ownerUuid);
        if (action == Action.TASK_DEPLOY && !descriptor.deployed()) {
            TASK_DEPLOYMENTS.computeIfAbsent(key, ignored -> new HashSet<>())
                    .add(descriptor.companionUuid());
        } else if (action == Action.STORE) {
            java.util.Set<UUID> companions = TASK_DEPLOYMENTS.get(key);
            if (companions != null && companions.remove(descriptor.companionUuid()) && companions.isEmpty()) {
                TASK_DEPLOYMENTS.remove(key);
            }
        }
    }

    private static CompanionActionRequest invalid(UUID requestId, UUID ownerUuid, UUID companionUuid,
                                                   Action action, long now) {
        return rejected(requestId, ownerUuid, companionUuid, action, now, Reason.INVALID_REQUEST);
    }

    private static CompanionActionRequest rejected(UUID requestId, UUID ownerUuid, UUID companionUuid,
                                                    Action action, long now, Reason reason) {
        return new CompanionActionRequest(requestId, ownerUuid, companionUuid, action,
                State.REJECTED, reason, now, now, now);
    }

    private static CompanionActionRequest storeRejected(MinecraftServer server, UUID requestId, UUID ownerUuid,
                                                         UUID companionUuid, Action action, long now, Reason reason) {
        Entry entry = Entry.terminal(server, requestId, ownerUuid, companionUuid, action, now,
                State.REJECTED, reason);
        REQUESTS.put(new RequestKey(server, ownerUuid, requestId), entry);
        return entry.snapshot();
    }

    private static final class Entry {
        private final MinecraftServer server;
        private final UUID requestId;
        private final UUID ownerUuid;
        private final UUID companionUuid;
        private final Action action;
        private final long submittedAt;
        private final long expiresAt;
        private State state;
        private Reason reason;
        private long completedAt;
        private boolean operationOwned;

        private Entry(MinecraftServer server, UUID requestId, UUID ownerUuid, UUID companionUuid,
                      Action action, long submittedAt, long expiresAt, State state, Reason reason,
                      long completedAt) {
            this.server = server;
            this.requestId = requestId;
            this.ownerUuid = ownerUuid;
            this.companionUuid = companionUuid;
            this.action = action;
            this.submittedAt = submittedAt;
            this.expiresAt = expiresAt;
            this.state = state;
            this.reason = reason;
            this.completedAt = completedAt;
        }

        private static Entry terminal(MinecraftServer server, UUID requestId, UUID ownerUuid,
                                      UUID companionUuid, Action action, long now, State state, Reason reason) {
            return new Entry(server, requestId, ownerUuid, companionUuid, action, now, now,
                    state, reason, now);
        }

        private boolean matches(MinecraftServer server, UUID ownerUuid, UUID companionUuid, Action action) {
            return this.server == server && this.ownerUuid.equals(ownerUuid)
                    && this.companionUuid.equals(companionUuid) && this.action == action;
        }

        private void finish(State state, Reason reason, long now) {
            if (this.state != State.PENDING) return;
            this.state = state;
            this.reason = reason;
            this.completedAt = now;
        }

        private CompanionActionRequest snapshot() {
            return new CompanionActionRequest(requestId, ownerUuid, companionUuid, action, state, reason,
                    submittedAt, expiresAt, completedAt);
        }
    }

    private record RequestKey(MinecraftServer server, UUID ownerUuid, UUID requestId) {
    }

    private record OwnerKey(MinecraftServer server, UUID ownerUuid) {
    }

    private record StartResult(boolean accepted, boolean operationOwned) {
    }
}
