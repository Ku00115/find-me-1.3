package com.kuzhi.findme.server.core;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

public final class FindMeDebugLogger {
    private static final Map<String, Long> BOX_LOG_TICKS = new HashMap<>();
    private static final Map<String, Long> SAMPLE_LOG_TICKS = new HashMap<>();
    private static long lastBoxLogTick = Long.MIN_VALUE;

    private FindMeDebugLogger() {
    }

    public static boolean enabled() {
        return Config.enableDiagnosticLogging;
    }

    public static void info(String area, String message, Object... args) {
        if (!enabled()) {
            return;
        }
        FindMeMod.LOGGER.info("[FindMe debug/{}] " + message, prepend(area, args));
    }

    public static boolean shouldLogSample(String area, String key, long tick, int intervalTicks) {
        if (!enabled()) {
            return false;
        }
        String sampleKey = area + ':' + key;
        Long previous = SAMPLE_LOG_TICKS.get(sampleKey);
        if (previous != null && tick >= previous && tick - previous < Math.max(1, intervalTicks)) {
            return false;
        }
        if (SAMPLE_LOG_TICKS.size() > 2048) {
            SAMPLE_LOG_TICKS.clear();
        }
        SAMPLE_LOG_TICKS.put(sampleKey, tick);
        return true;
    }

    public static void lifecycle(String operation, ServerPlayer player, UUID companionId, Entity entity, String fromState,
                          String toState, String reason, boolean snapshotPresent, boolean entityPresent) {
        if (!enabled()) {
            return;
        }
        String dimension = entity != null && entity.level() != null ? entity.level().dimension().location().toString()
                : player != null && player.level() != null ? player.level().dimension().location().toString() : "unknown";
        String position = entity != null ? round(entity.getX()) + "," + round(entity.getY()) + "," + round(entity.getZ())
                : player != null ? round(player.getX()) + "," + round(player.getY()) + "," + round(player.getZ()) : "unknown";
        long tick = player != null && player.level() != null ? player.level().getGameTime() : -1L;
        info("lifecycle",
                "operation={} player={} companion={} entity={} from={} to={} reason={} dimension={} pos={} snapshotPresent={} entityPresent={} tick={}",
                value(operation), player == null ? "null" : player.getUUID(), companionId, entity(entity), value(fromState),
                value(toState), value(reason), dimension, position, snapshotPresent, entityPresent, tick);
    }

    public static void contactBoxes(Entity entity, double inflate, List<AABB> boxes, int officialParts, int officialPartsFound, int ignoredOfficialParts, boolean reflectedUsed, int reflectedParts, boolean segmentsUsed) {
        if (!enabled() || entity == null) {
            return;
        }
        long tick = entity.level() == null ? -1L : entity.level().getGameTime();
        if (lastBoxLogTick == Long.MIN_VALUE || tick < lastBoxLogTick || tick - lastBoxLogTick >= 1200L) {
            BOX_LOG_TICKS.clear();
            lastBoxLogTick = tick;
        }
        String key = entity.getId() + ":" + inflate;
        Long previousTick = BOX_LOG_TICKS.get(key);
        if (previousTick != null && tick >= previousTick && tick - previousTick < 20L) {
            return;
        }
        BOX_LOG_TICKS.put(key, tick);
        info("contact-boxes", "{} inflate={} main={} boxes={} officialParts={}/{} ignoredOfficialParts={} reflectedUsed={} reflectedParts={} customSegments={}",
                entity(entity), round(inflate), box(entity.getBoundingBox()), boxesSummary(boxes), officialParts, officialPartsFound, ignoredOfficialParts, reflectedUsed, reflectedParts, segmentsUsed);
    }

    public static String entity(Entity entity) {
        if (!enabled()) {
            return "";
        }
        if (entity == null) {
            return "null";
        }
        ResourceLocation key = EntityType.getKey(entity.getType());
        return key + "#" + entity.getId() + "/" + entity.getUUID();
    }

    public static String box(AABB box) {
        if (!enabled()) {
            return "";
        }
        if (box == null) {
            return "null";
        }
        return "[" + round(box.minX) + "," + round(box.minY) + "," + round(box.minZ)
                + " -> " + round(box.maxX) + "," + round(box.maxY) + "," + round(box.maxZ)
                + " size=" + round(box.getXsize()) + "x" + round(box.getYsize()) + "x" + round(box.getZsize()) + "]";
    }

    private static String boxesSummary(List<AABB> boxes) {
        if (!enabled()) {
            return "";
        }
        if (boxes == null || boxes.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(boxes.size(), 6);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(i).append('=').append(box(boxes.get(i)));
        }
        if (boxes.size() > limit) {
            builder.append("; +").append(boxes.size() - limit).append(" more");
        }
        return builder.append(']').toString();
    }

    private static Object[] prepend(String first, Object[] rest) {
        Object[] combined = new Object[rest.length + 1];
        combined[0] = first;
        System.arraycopy(rest, 0, combined, 1, rest.length);
        return combined;
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
