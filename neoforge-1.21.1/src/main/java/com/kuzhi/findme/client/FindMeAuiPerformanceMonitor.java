package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Low-overhead client timings for the AUI lifecycle and render pipeline. */
final class FindMeAuiPerformanceMonitor {
    private static final long REPORT_INTERVAL_NANOS = 10_000_000_000L;
    private static final long WARNING_INTERVAL_NANOS = 2_000_000_000L;
    private static final Map<String, Sample> SAMPLES = new LinkedHashMap<>();
    private static final Map<String, Long> LAST_WARNINGS = new LinkedHashMap<>();
    private static long windowStartedAt = System.nanoTime();

    private FindMeAuiPerformanceMonitor() {
    }

    static long start() {
        return Config.enableDiagnosticLogging ? System.nanoTime() : 0L;
    }

    static void record(Object screen, String phase, long startedAt, double warningMs) {
        if (!Config.enableDiagnosticLogging) {
            return;
        }
        long elapsed = Math.max(0L, System.nanoTime() - startedAt);
        String key = screen.getClass().getSimpleName() + '.' + phase;
        Sample sample = SAMPLES.computeIfAbsent(key, ignored -> new Sample());
        sample.total += elapsed;
        sample.max = Math.max(sample.max, elapsed);
        sample.calls++;
        if (elapsed >= warningMs * 1_000_000.0) {
            long now = System.nanoTime();
            long previous = LAST_WARNINGS.getOrDefault(key, Long.MIN_VALUE);
            if (previous == Long.MIN_VALUE || now - previous >= WARNING_INTERVAL_NANOS) {
                LAST_WARNINGS.put(key, now);
                FindMeMod.LOGGER.warn("[FindMe AUI perf] slow phase={} elapsedMs={}", key, ms(elapsed));
            }
        }
        reportIfDue();
    }

    private static void reportIfDue() {
        long now = System.nanoTime();
        if (now - windowStartedAt < REPORT_INTERVAL_NANOS || SAMPLES.isEmpty()) {
            return;
        }
        StringBuilder summary = new StringBuilder(512);
        for (Map.Entry<String, Sample> entry : SAMPLES.entrySet()) {
            if (!summary.isEmpty()) summary.append(',');
            Sample sample = entry.getValue();
            summary.append(entry.getKey()).append("(avg/max=")
                    .append(ms(sample.total / Math.max(1L, sample.calls))).append('/')
                    .append(ms(sample.max)).append("ms,calls=").append(sample.calls).append(')');
        }
        FindMeMod.LOGGER.info("[FindMe AUI perf] 10s summary phases={}", summary);
        SAMPLES.clear();
        windowStartedAt = now;
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static final class Sample {
        private long total;
        private long max;
        private long calls;
    }
}
