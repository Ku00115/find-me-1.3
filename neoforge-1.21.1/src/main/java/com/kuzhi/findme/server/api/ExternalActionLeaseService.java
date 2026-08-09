package com.kuzhi.findme.server.api;

import com.kuzhi.findme.api.CompanionDescriptor;
import com.kuzhi.findme.api.ExternalActionLease;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class ExternalActionLeaseService {
    private static final int MAX_TIMEOUT_TICKS = 20 * 60 * 10;
    private static final Map<UUID, LeaseImpl> BY_ID = new HashMap<>();
    private static final Map<UUID, UUID> BY_COMPANION = new HashMap<>();

    private ExternalActionLeaseService() {
    }

    public static Optional<ExternalActionLease> acquire(ServerPlayer owner, UUID companionUuid,
                                                        ResourceLocation actionId, int priority,
                                                        int timeoutTicks) {
        if (owner == null || companionUuid == null || actionId == null || timeoutTicks <= 0) {
            return Optional.empty();
        }
        CompanionDescriptor descriptor = CompanionDescriptorService.describe(owner.getServer(), owner.getUUID(),
                companionUuid).orElse(null);
        if (descriptor == null || !descriptor.deployed() || !descriptor.live()) return Optional.empty();
        LivingEntity living = CompanionEntityLookup.findEntity(owner.getServer(), companionUuid)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).orElse(null);
        if (living == null || FindMeApi.isMovementControlled(living)) return Optional.empty();

        long now = owner.serverLevel().getGameTime();
        int duration = Math.min(MAX_TIMEOUT_TICKS, timeoutTicks);
        LeaseImpl lease = acquire(owner.getServer(), owner.getUUID(), companionUuid, actionId,
                priority, now, duration);
        return Optional.ofNullable(lease);
    }

    public static Optional<ExternalActionLease> acquireTactical(ServerPlayer owner, UUID companionUuid,
                                                                UUID targetUuid, ResourceLocation actionId,
                                                                int priority, int timeoutTicks) {
        if (owner == null || companionUuid == null || targetUuid == null || actionId == null
                || timeoutTicks <= 0) return Optional.empty();
        long now = owner.serverLevel().getGameTime();
        CompanionDescriptor descriptor = CompanionDescriptorService.describe(owner.getServer(), owner.getUUID(),
                companionUuid).orElse(null);
        if (descriptor == null || !descriptor.deployed() || !descriptor.live()
                || hasActiveOperation(companionUuid, now)) return Optional.empty();
        LivingEntity living = CompanionEntityLookup.findEntity(owner.getServer(), companionUuid)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).orElse(null);
        if (living == null) return Optional.empty();

        int duration = Math.min(MAX_TIMEOUT_TICKS, timeoutTicks);
        Supplier<UUID> targetSupplier = () -> CompanionTacticalOrderService.currentTargetUuid(companionUuid)
                .orElse(null);
        return Optional.ofNullable(acquireTactical(owner.getServer(), owner.getUUID(), companionUuid, targetUuid,
                actionId, priority, now, duration, targetSupplier));
    }

    static LeaseImpl acquire(MinecraftServer server, UUID ownerUuid, UUID companionUuid,
                             ResourceLocation actionId, int priority, long now, int timeoutTicks) {
        if (ownerUuid == null || companionUuid == null || actionId == null || timeoutTicks <= 0) return null;
        UUID currentId = BY_COMPANION.get(companionUuid);
        LeaseImpl current = currentId == null ? null : BY_ID.get(currentId);
        if (current != null && current.isValidAt(now) && priority <= current.priority) return null;

        CompanionOperationLockService.ActiveOperation operation = CompanionOperationLockService.get(companionUuid);
        if (operation != null && operation.expiresAt() >= now
                && (current == null || !current.source.equals(operation.source()))) {
            return null;
        }
        if (current != null) current.invalidate(true);

        UUID leaseId = UUID.randomUUID();
        String source = "external:" + leaseId + ":" + actionId;
        if (!CompanionOperationLockService.tryBeginExternal(ownerUuid, companionUuid,
                source, now, timeoutTicks)) {
            return null;
        }
        LeaseImpl lease = new LeaseImpl(server, leaseId, ownerUuid, companionUuid, actionId,
                priority, source, now + timeoutTicks);
        BY_ID.put(leaseId, lease);
        BY_COMPANION.put(companionUuid, leaseId);
        return lease;
    }

    static LeaseImpl acquireTactical(MinecraftServer server, UUID ownerUuid, UUID companionUuid, UUID targetUuid,
                                     ResourceLocation actionId, int priority, long now, int timeoutTicks,
                                     Supplier<UUID> targetSupplier) {
        if (ownerUuid == null || companionUuid == null || targetUuid == null || actionId == null
                || timeoutTicks <= 0 || targetSupplier == null || !targetUuid.equals(targetSupplier.get())) {
            return null;
        }
        UUID currentId = BY_COMPANION.get(companionUuid);
        LeaseImpl current = currentId == null ? null : BY_ID.get(currentId);
        if (current != null && current.isValidAt(now) && priority <= current.priority) return null;
        if (hasActiveOperation(companionUuid, now)) return null;
        if (current != null) current.invalidate(true);

        UUID leaseId = UUID.randomUUID();
        String source = "tactical:" + leaseId + ":" + actionId;
        LeaseImpl lease = new LeaseImpl(server, leaseId, ownerUuid, companionUuid, actionId,
                priority, source, now + timeoutTicks, targetUuid, targetSupplier);
        BY_ID.put(leaseId, lease);
        BY_COMPANION.put(companionUuid, leaseId);
        return lease;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || BY_ID.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Iterator<LeaseImpl> iterator = BY_ID.values().iterator();
        while (iterator.hasNext()) {
            LeaseImpl lease = iterator.next();
            if (lease.server != server || lease.isValidAt(now)) continue;
            iterator.remove();
            BY_COMPANION.remove(lease.companionUuid, lease.leaseId);
            lease.valid = false;
        }
    }

    private static boolean hasActiveOperation(UUID companionUuid, long now) {
        CompanionOperationLockService.ActiveOperation operation = CompanionOperationLockService.get(companionUuid);
        return operation != null && operation.expiresAt() >= now;
    }

    public static void resetServerState(MinecraftServer server) {
        Iterator<LeaseImpl> iterator = BY_ID.values().iterator();
        while (iterator.hasNext()) {
            LeaseImpl lease = iterator.next();
            if (server != null && lease.server != server) continue;
            lease.valid = false;
            BY_COMPANION.remove(lease.companionUuid, lease.leaseId);
            iterator.remove();
        }
    }

    static void resetForTests() {
        resetServerState(null);
    }

    static final class LeaseImpl implements ExternalActionLease {
        private final MinecraftServer server;
        private final UUID leaseId;
        private final UUID ownerUuid;
        private final UUID companionUuid;
        private final ResourceLocation actionId;
        private final int priority;
        private final String source;
        private final UUID tacticalTargetUuid;
        private final Supplier<UUID> tacticalTargetSupplier;
        private long expiresAt;
        private boolean valid = true;

        private LeaseImpl(MinecraftServer server, UUID leaseId, UUID ownerUuid, UUID companionUuid,
                          ResourceLocation actionId, int priority, String source, long expiresAt) {
            this(server, leaseId, ownerUuid, companionUuid, actionId, priority, source, expiresAt, null, null);
        }

        private LeaseImpl(MinecraftServer server, UUID leaseId, UUID ownerUuid, UUID companionUuid,
                          ResourceLocation actionId, int priority, String source, long expiresAt,
                          UUID tacticalTargetUuid, Supplier<UUID> tacticalTargetSupplier) {
            this.server = server;
            this.leaseId = leaseId;
            this.ownerUuid = ownerUuid;
            this.companionUuid = companionUuid;
            this.actionId = actionId;
            this.priority = priority;
            this.source = source;
            this.expiresAt = expiresAt;
            this.tacticalTargetUuid = tacticalTargetUuid;
            this.tacticalTargetSupplier = tacticalTargetSupplier;
        }

        @Override public UUID leaseId() { return leaseId; }
        @Override public UUID ownerUuid() { return ownerUuid; }
        @Override public UUID companionUuid() { return companionUuid; }
        @Override public ResourceLocation actionId() { return actionId; }
        @Override public int priority() { return priority; }
        @Override public long expiresAt() { return expiresAt; }

        @Override
        public boolean renew(int timeoutTicks) {
            if (server == null || timeoutTicks <= 0 || !isValid()) return false;
            long now = server.overworld().getGameTime();
            int duration = Math.min(MAX_TIMEOUT_TICKS, timeoutTicks);
            if (tacticalTargetUuid != null) {
                if (!isValidAt(now)) {
                    invalidate(false);
                    return false;
                }
                expiresAt = now + duration;
                return true;
            }
            if (!CompanionOperationLockService.renew(companionUuid,
                    CompanionOperationLockService.Operation.EXTERNAL, source, now, duration)) {
                invalidate(false);
                return false;
            }
            expiresAt = now + duration;
            return true;
        }

        @Override
        public boolean isValid() {
            if (!valid || server == null) return false;
            return isValidAt(server.overworld().getGameTime());
        }

        boolean isValidAt(long now) {
            if (!valid || expiresAt < now) return false;
            if (tacticalTargetUuid != null) {
                return tacticalTargetSupplier != null && tacticalTargetUuid.equals(tacticalTargetSupplier.get())
                        && !hasActiveOperation(companionUuid, now);
            }
            CompanionOperationLockService.ActiveOperation operation =
                    CompanionOperationLockService.get(companionUuid);
            return operation != null && operation.operation() == CompanionOperationLockService.Operation.EXTERNAL
                    && source.equals(operation.source());
        }

        @Override
        public void close() {
            invalidate(true);
        }

        private void invalidate(boolean releaseLock) {
            if (!valid) return;
            valid = false;
            BY_ID.remove(leaseId, this);
            BY_COMPANION.remove(companionUuid, leaseId);
            if (releaseLock && tacticalTargetUuid == null) {
                CompanionOperationLockService.endIfSource(companionUuid,
                        CompanionOperationLockService.Operation.EXTERNAL, source);
            }
        }
    }
}
