package com.kuzhi.findme.server.home;

import com.kuzhi.findme.FindMeMod;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

final class CompanionHomePerformanceTrace {
    static final int DATA = 0;
    static final int ROSTER = 1;
    static final int RESOLVE = 2;
    static final int RECONCILE = 3;
    static final int COLLECT = 4;
    static final int ELIGIBILITY = 5;
    static final int RESTORE = 6;
    static final int SAVE_SYNC = 7;

    private static final String[] NAMES = {
            "data", "roster", "resolve", "reconcile", "collect", "eligibility", "restore", "save_sync"
    };
    private static final long SLOW_NANOS = 5_000_000L;
    private static final long REPORT_INTERVAL_TICKS = 200L;
    private static final long[] TOTALS = new long[NAMES.length];
    private static final long[] MAXIMUMS = new long[NAMES.length];
    private static final long[] CALLS = new long[NAMES.length];
    private static final Map<UUID, Long> LAST_SLOW_LOG = new ConcurrentHashMap<>();
    private static long lastReportTick = Long.MIN_VALUE;

    private final ServerPlayer player;
    private final long startedAt = System.nanoTime();
    private final long[] stages = new long[NAMES.length];
    private UUID companionUuid;

    CompanionHomePerformanceTrace(ServerPlayer player) {
        this.player = player;
    }

    void companion(UUID uuid) {
        companionUuid = uuid;
    }

    void record(int stage, long stageStartedAt) {
        long elapsed = System.nanoTime() - stageStartedAt;
        stages[stage] += elapsed;
        TOTALS[stage] += elapsed;
        MAXIMUMS[stage] = Math.max(MAXIMUMS[stage], elapsed);
        CALLS[stage]++;
    }

    void finish() {
        long elapsed = System.nanoTime() - startedAt;
        long gameTime = player.serverLevel().getGameTime();
        if (elapsed >= SLOW_NANOS
                && gameTime - LAST_SLOW_LOG.getOrDefault(player.getUUID(), Long.MIN_VALUE) >= 20L) {
            LAST_SLOW_LOG.put(player.getUUID(), gameTime);
            FindMeMod.LOGGER.warn("[FindMe perf/home] slow reconciliation player={} companion={} totalMs={} stages={}",
                    player.getUUID(), companionUuid, milliseconds(elapsed), snapshot(stages));
        }
        if (lastReportTick == Long.MIN_VALUE) {
            lastReportTick = gameTime;
        } else if (gameTime - lastReportTick >= REPORT_INTERVAL_TICKS) {
            FindMeMod.LOGGER.info("[FindMe perf/home] 10s breakdown stages={}", aggregate());
            Arrays.fill(TOTALS, 0L);
            Arrays.fill(MAXIMUMS, 0L);
            Arrays.fill(CALLS, 0L);
            lastReportTick = gameTime;
        }
    }

    static void forgetPlayer(UUID playerUuid) {
        if (playerUuid != null) {
            LAST_SLOW_LOG.remove(playerUuid);
        }
    }

    private static String snapshot(long[] values) {
        StringBuilder result = new StringBuilder(160);
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0L) continue;
            if (!result.isEmpty()) result.append(',');
            result.append(NAMES[i]).append('=').append(milliseconds(values[i]));
        }
        return result.toString();
    }

    private static String aggregate() {
        StringBuilder result = new StringBuilder(220);
        for (int i = 0; i < NAMES.length; i++) {
            if (CALLS[i] <= 0L) continue;
            if (!result.isEmpty()) result.append(',');
            result.append(NAMES[i]).append("(avg/max=")
                    .append(milliseconds(TOTALS[i] / CALLS[i])).append('/')
                    .append(milliseconds(MAXIMUMS[i])).append("ms,calls=")
                    .append(CALLS[i]).append(')');
        }
        return result.toString();
    }

    private static String milliseconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
