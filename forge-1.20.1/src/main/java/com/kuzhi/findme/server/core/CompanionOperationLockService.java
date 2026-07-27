package com.kuzhi.findme.server.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionOperationLockService {
    private static final int DEFAULT_TIMEOUT_TICKS = 20 * 30;
    private static final Map<UUID, ActiveOperation> ACTIVE = new HashMap<>();

    private CompanionOperationLockService() {
    }

    public static boolean tryBegin(ServerPlayer player, UUID companionId, Operation operation, String source, int timeoutTicks) {
        if (player == null || companionId == null || operation == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        BeginResult result = begin(player.getUUID(), companionId, operation, source, now, timeoutTicks);
        if (!result.accepted()) {
            ActiveOperation current = result.previous();
            FindMeDebugLogger.lifecycle("OPERATION_REJECTED", player, companionId, null,
                    current.operation.name(), operation.name(), "locked_by_" + current.source, false, false);
            return false;
        }
        if (result.previous() != null) {
            ActiveOperation current = result.previous();
            FindMeDebugLogger.lifecycle("OPERATION_LOCK_EXPIRED", player, companionId, null,
                    current.operation.name(), operation.name(), current.source, false, false);
        }
        FindMeDebugLogger.lifecycle("OPERATION_STARTED", player, companionId, null,
                "-", operation.name(), source, false, false);
        return true;
    }

    static BeginResult begin(UUID playerUuid, UUID companionId, Operation operation, String source,
                             long now, int timeoutTicks) {
        ActiveOperation current = ACTIVE.get(companionId);
        if (current != null && current.expiresAt >= now) {
            return new BeginResult(false, current);
        }
        ACTIVE.put(companionId, new ActiveOperation(playerUuid, operation, source, now,
                now + Math.max(1, timeoutTicks)));
        return new BeginResult(true, current);
    }

    public static boolean tryBegin(ServerPlayer player, UUID companionId, Operation operation, String source) {
        return tryBegin(player, companionId, operation, source, DEFAULT_TIMEOUT_TICKS);
    }

    public static void end(ServerPlayer player, UUID companionId, Operation operation, String reason) {
        if (companionId == null) {
            return;
        }
        ActiveOperation current = ACTIVE.get(companionId);
        if (current == null || current.operation != operation) {
            return;
        }
        ACTIVE.remove(companionId);
        FindMeDebugLogger.lifecycle("OPERATION_FINISHED", player, companionId, null,
                operation.name(), "-", reason, false, false);
    }

    public static ActiveOperation get(UUID companionId) {
        if (companionId == null) {
            return null;
        }
        return ACTIVE.get(companionId);
    }

    public static boolean heldBy(ServerPlayer player, UUID companionId, Operation operation) {
        ActiveOperation active = get(companionId);
        return player != null && active != null && active.operation == operation
                && player.getUUID().equals(active.playerUuid);
    }

    public static boolean clear(ServerPlayer player, UUID companionId, String reason) {
        if (companionId == null) {
            return false;
        }
        ActiveOperation current = ACTIVE.remove(companionId);
        if (current == null) {
            return false;
        }
        FindMeDebugLogger.lifecycle("OPERATION_CLEARED", player, companionId, null,
                current.operation.name(), "-", reason, false, false);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, ActiveOperation>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveOperation> entry = iterator.next();
            ActiveOperation operation = entry.getValue();
            if (operation.expiresAt >= now) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(operation.playerUuid);
            FindMeDebugLogger.lifecycle("OPERATION_TIMEOUT", player, entry.getKey(), null,
                    operation.operation.name(), "-", operation.source, false, false);
            iterator.remove();
        }
    }

    public static Map<UUID, ActiveOperation> snapshot() {
        return Map.copyOf(ACTIVE);
    }

    public static boolean hasActiveForPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        return ACTIVE.values().stream().anyMatch(operation -> playerUuid.equals(operation.playerUuid));
    }

    public static int clearForPlayer(ServerPlayer player, String reason, Predicate<UUID> preserve) {
        if (player == null) {
            return 0;
        }
        Map<UUID, ActiveOperation> cleared = clearForOwner(player.getUUID(), preserve);
        for (Map.Entry<UUID, ActiveOperation> entry : cleared.entrySet()) {
            ActiveOperation operation = entry.getValue();
            FindMeDebugLogger.lifecycle("OPERATION_CLEARED", player, entry.getKey(), null,
                    operation.operation.name(), "-", reason, false, false);
        }
        return cleared.size();
    }

    public static void resetServerState() {
        ACTIVE.clear();
    }

    static Map<UUID, ActiveOperation> clearForOwner(UUID playerUuid, Predicate<UUID> preserve) {
        if (playerUuid == null) {
            return Map.of();
        }
        Map<UUID, ActiveOperation> cleared = new HashMap<>();
        Iterator<Map.Entry<UUID, ActiveOperation>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveOperation> entry = iterator.next();
            ActiveOperation operation = entry.getValue();
            if (!playerUuid.equals(operation.playerUuid)
                    || preserve != null && preserve.test(entry.getKey())) {
                continue;
            }
            iterator.remove();
            cleared.put(entry.getKey(), operation);
        }
        return Map.copyOf(cleared);
    }

    public enum Operation {
        DEPLOY,
        STORE,
        SWITCH,
        RECOVER,
        JOURNEY
    }

    public record ActiveOperation(UUID playerUuid, Operation operation, String source, long startedAt, long expiresAt) {
    }

    record BeginResult(boolean accepted, ActiveOperation previous) {
    }
}
