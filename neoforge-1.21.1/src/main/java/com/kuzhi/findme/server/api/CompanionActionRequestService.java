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
import java.util.HashMap;
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
    private static final Map<UUID, Entry> REQUESTS = new HashMap<>();

    private CompanionActionRequestService() {
    }

    public static CompanionActionRequest submit(ServerPlayer owner, UUID companionUuid, UUID requestId,
                                                 Action action, int timeoutTicks) {
        if (owner == null) return invalid(requestId, null, companionUuid, action, 0L);
        long now = owner.serverLevel().getGameTime();
        if (requestId == null || companionUuid == null || action == null) {
            return invalid(requestId, owner.getUUID(), companionUuid, action, now);
        }
        Entry existing = REQUESTS.get(requestId);
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
            Entry entry = Entry.terminal(owner.getServer(), requestId, owner.getUUID(), companionUuid,
                    action, now, State.SUCCEEDED, Reason.ALREADY_SATISFIED);
            REQUESTS.put(requestId, entry);
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
        REQUESTS.put(requestId, entry);
        PlayerCompanionData data = CompanionDataService.data(owner);
        if (!start(owner, data, descriptor, action)) {
            entry.finish(State.REJECTED, Reason.UNAVAILABLE, now);
        } else {
            CompanionDescriptor current = CompanionDescriptorService
                    .describe(owner.getServer(), owner.getUUID(), companionUuid).orElse(null);
            if (current != null && satisfied(current, action)) {
                entry.finish(State.SUCCEEDED, Reason.NONE, now);
            }
        }
        return entry.snapshot();
    }

    public static Optional<CompanionActionRequest> status(MinecraftServer server, UUID requestId) {
        Entry entry = requestId == null ? null : REQUESTS.get(requestId);
        return entry == null || server == null || entry.server != server
                ? Optional.empty() : Optional.of(entry.snapshot());
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
                entry.finish(State.REJECTED, Reason.OWNER_OFFLINE, now);
                continue;
            }
            CompanionDescriptor descriptor = CompanionDescriptorService
                    .describe(server, entry.ownerUuid, entry.companionUuid).orElse(null);
            if (descriptor == null) {
                entry.finish(State.REJECTED, Reason.RELEASED, now);
            } else if (descriptor.lifecycle() == CompanionLifecycleState.DEAD) {
                entry.finish(State.REJECTED, Reason.DEAD, now);
            } else if (satisfied(descriptor, entry.action)) {
                entry.finish(State.SUCCEEDED, Reason.NONE, now);
            } else if (entry.expiresAt < now) {
                entry.finish(State.TIMED_OUT, Reason.TIMEOUT, now);
            }
        }
    }

    public static void resetServerState(MinecraftServer server) {
        REQUESTS.values().removeIf(entry -> server == null || entry.server == server);
    }

    private static boolean start(ServerPlayer owner, PlayerCompanionData data,
                                 CompanionDescriptor descriptor, Action action) {
        if (action == Action.STORE) {
            return CompanionCollectionService.collectUuid(owner, data, descriptor.kind(), descriptor.companionUuid());
        }
        CompanionDeployRequestService.Result result = CompanionDeployRequestService.request(owner, data,
                descriptor.kind(), descriptor.companionUuid(), CompanionDeployRequestService.Mode.AUTONOMOUS,
                "api:external_deploy");
        return result.state() != CompanionDeployRequestService.State.REJECTED;
    }

    static boolean satisfied(CompanionDescriptor descriptor, Action action) {
        if (descriptor == null) return false;
        if (action == Action.DEPLOY) {
            return descriptor.deployed() && descriptor.live()
                    && descriptor.lifecycle() == CompanionLifecycleState.DEPLOYED;
        }
        return descriptor.stored() && !descriptor.live() && !descriptor.deployed()
                && (descriptor.lifecycle() == CompanionLifecycleState.STORED
                || descriptor.lifecycle() == CompanionLifecycleState.HOME_STORED
                || descriptor.lifecycle() == CompanionLifecycleState.SHOULDER);
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
        REQUESTS.put(requestId, entry);
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
}
