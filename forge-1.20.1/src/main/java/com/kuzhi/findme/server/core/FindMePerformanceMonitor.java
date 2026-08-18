package com.kuzhi.findme.server.core;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class FindMePerformanceMonitor {
    static final int MODULES = 0;
    static final int UNLOAD_SNAPSHOTS = 1;
    static final int OPERATION_LOCKS = 2;
    static final int MOUNT_CINEMATICS = 3;
    static final int RETREATS = 4;
    static final int RIDE_HOME = 5;
    static final int MOUNT_SETTLE = 6;
    static final int CONTRACTS = 7;
    static final int ARRIVALS = 8;
    static final int STORAGE_EFFECTS = 9;
    static final int TACTICAL_ORDERS = 10;
    static final int TEMPORARY_ACTIONS = 11;
    static final int VEHICLE_SUMMONS = 12;
    static final int PLAYER_REGISTRATION = 13;
    static final int PLAYER_SAFETY = 14;
    static final int HOME_RESIDENTS = 15;
    static final int ESCORTS = 16;
    private static final int DATA_DECODE = 17;
    private static final int DATA_SAVE = 18;
    private static final int ROSTER_SYNC = 19;
    private static final int PREVIEW_BUILD = 20;
    private static final int THREAT_SCAN = 21;
    private static final int RESTORE_SCAN = 22;
    private static final int CHUNK_LOAD = 23;
    private static final int AUTO_BACKUP = 24;
    static final int ROSTER_TRANSACTIONS = 25;
    private static final int ENTITY_LOOKUPS = 26;
    private static final int ENTITY_INDEX_HITS = 27;
    private static final int ENTITY_LEVEL_FALLBACKS = 28;
    private static final int ENTITY_RESTORE_GLOBAL_SCANS = 29;

    private static final String[] NAMES = {
            "modules", "unload_snapshots", "operation_locks", "mount_cinematics", "retreats",
            "ride_home", "mount_settle", "contracts", "arrivals", "storage_effects",
            "tactical_orders", "temporary_actions", "vehicle_summons",
            "player_registration", "player_safety", "home_residents", "escorts",
            "data_decode", "data_save", "roster_sync", "preview_build", "threat_scan",
            "restore_scan", "chunk_load", "auto_backup", "roster_transactions",
            "entity_lookups", "entity_index_hits", "entity_level_fallbacks", "entity_restore_global_scans"
    };
    private static final long[] TOTAL_NANOS = new long[NAMES.length];
    private static final long[] MAX_NANOS = new long[NAMES.length];
    private static final long[] CURRENT_NANOS = new long[NAMES.length];
    private static final long[] CALLS = new long[NAMES.length];
    private static final int REPORT_INTERVAL_TICKS = 200;
    private static final long SLOW_SERVER_TICK_NANOS = 40_000_000L;
    private static final long SLOW_PLAYER_TICK_NANOS = 20_000_000L;
    private static long windowServerNanos;
    private static long maxServerNanos;
    private static int windowServerTicks;

    private FindMePerformanceMonitor() {
    }

    public static long start() {
        return Config.enableDiagnosticLogging ? System.nanoTime() : 0L;
    }

    public static void recordDataDecode(long startedAt) { record(DATA_DECODE, startedAt); }
    public static void recordDataSave(long startedAt) { record(DATA_SAVE, startedAt); }
    public static void recordRosterSync(long startedAt) { record(ROSTER_SYNC, startedAt); }
    public static void recordPreviewBuild(long startedAt) { record(PREVIEW_BUILD, startedAt); }
    public static void recordThreatScan(long startedAt) { record(THREAT_SCAN, startedAt); }
    public static void recordRestoreScan(long startedAt) { record(RESTORE_SCAN, startedAt); }
    public static void recordChunkLoad(long startedAt) { record(CHUNK_LOAD, startedAt); }
    public static void recordAutoBackup(long startedAt) { record(AUTO_BACKUP, startedAt); }
    static void recordEntityLookup(long startedAt) { record(ENTITY_LOOKUPS, startedAt); }
    static void recordEntityIndexHit() { increment(ENTITY_INDEX_HITS); }
    static void recordEntityLevelFallback() { increment(ENTITY_LEVEL_FALLBACKS); }
    static void recordEntityRestoreGlobalScan() { increment(ENTITY_RESTORE_GLOBAL_SCANS); }

    static void record(int stage, long startedAt) {
        if (!Config.enableDiagnosticLogging) {
            return;
        }
        long elapsed = System.nanoTime() - startedAt;
        TOTAL_NANOS[stage] += elapsed;
        MAX_NANOS[stage] = Math.max(MAX_NANOS[stage], elapsed);
        CURRENT_NANOS[stage] += elapsed;
        CALLS[stage]++;
    }

    static void increment(int stage) {
        if (!Config.enableDiagnosticLogging) {
            return;
        }
        CALLS[stage]++;
    }

    static void finishPlayerTick(ServerPlayer player, long startedAt) {
        if (!Config.enableDiagnosticLogging) {
            return;
        }
        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= SLOW_PLAYER_TICK_NANOS) {
            FindMeMod.LOGGER.warn("[FindMe perf] slow player tick player={} elapsedMs={} stages={}",
                    player.getUUID(), milliseconds(elapsed), currentStageSnapshot());
        }
    }

    static void finishServerTick(MinecraftServer server, long startedAt) {
        if (!Config.enableDiagnosticLogging) {
            return;
        }
        long elapsed = System.nanoTime() - startedAt;
        windowServerNanos += elapsed;
        maxServerNanos = Math.max(maxServerNanos, elapsed);
        windowServerTicks++;
        if (elapsed >= SLOW_SERVER_TICK_NANOS) {
            FindMeMod.LOGGER.warn("[FindMe perf] slow server tick tick={} elapsedMs={} players={} stages={}",
                    server.getTickCount(), milliseconds(elapsed), server.getPlayerCount(), currentStageSnapshot());
        }
        if (windowServerTicks >= REPORT_INTERVAL_TICKS) {
            FindMeMod.LOGGER.info("[FindMe perf] 10s summary ticks={} avgServerMs={} maxServerMs={} players={} stages={}",
                    windowServerTicks, milliseconds(windowServerNanos / windowServerTicks),
                    milliseconds(maxServerNanos), server.getPlayerCount(), stageSummary());
            resetWindow();
        }
        java.util.Arrays.fill(CURRENT_NANOS, 0L);
    }

    private static String currentStageSnapshot() {
        StringBuilder result = new StringBuilder(256);
        for (int i = 0; i < NAMES.length; i++) {
            if (CURRENT_NANOS[i] == 0L) {
                continue;
            }
            appendSeparator(result);
            result.append(NAMES[i]).append('=').append(milliseconds(CURRENT_NANOS[i]));
        }
        return result.toString();
    }

    private static String stageSummary() {
        StringBuilder result = new StringBuilder(384);
        for (int i = 0; i < NAMES.length; i++) {
            if (CALLS[i] == 0L) {
                continue;
            }
            appendSeparator(result);
            result.append(NAMES[i])
                    .append("(avg/max=")
                    .append(milliseconds(TOTAL_NANOS[i] / CALLS[i]))
                    .append('/')
                    .append(milliseconds(MAX_NANOS[i]))
                    .append("ms,calls=")
                    .append(CALLS[i])
                    .append(')');
        }
        return result.toString();
    }

    private static void appendSeparator(StringBuilder result) {
        if (!result.isEmpty()) {
            result.append(',');
        }
    }

    private static String milliseconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static void resetWindow() {
        java.util.Arrays.fill(TOTAL_NANOS, 0L);
        java.util.Arrays.fill(MAX_NANOS, 0L);
        java.util.Arrays.fill(CALLS, 0L);
        windowServerNanos = 0L;
        maxServerNanos = 0L;
        windowServerTicks = 0;
    }

    static void resetServerState() {
        resetWindow();
        java.util.Arrays.fill(CURRENT_NANOS, 0L);
    }
}
