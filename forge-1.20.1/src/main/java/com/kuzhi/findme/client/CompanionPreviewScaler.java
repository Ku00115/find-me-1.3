package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.network.CompanionListPacket;
import java.util.Collections;
import java.util.Map;
import java.util.Locale;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

final class CompanionPreviewScaler {
    private static final Map<Entity, PreviewDimensions> STABLE_DIMENSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CompanionPreviewScaler() {
    }

    static float wheelScale(CompanionListPacket.Entry entry, Entity entity) {
        return wheelScale(entry, entity, CompanionWheelLayout.SLOT_RADIUS - 3);
    }

    static float wheelScale(CompanionListPacket.Entry entry, Entity entity, float radius) {
        PreviewDimensions dimensions = stableDimensions(entry, entity);
        double horizontal = Math.max(dimensions.width(), dimensions.depth()) * 1.12;
        double height = dimensions.height();
        double usableDiameter = Math.max(8.0, radius * 2.0 - 5.0);
        double diagonal = Math.sqrt(horizontal * horizontal + height * height);
        double scale = usableDiameter / Math.max(0.75, diagonal);
        scale = Math.min(scale, usableDiameter * 0.94 / Math.max(0.75, height));
        scale = Math.min(scale, usableDiameter * 0.94 / Math.max(0.75, horizontal));
        return applyOverride(entry, scale, 0.18, 42.0);
    }

    static int wheelBottomOffset(CompanionListPacket.Entry entry, Entity entity, float scale, float radius) {
        double heightPx = stableDimensions(entry, entity).height() * scale;
        double maxOffset = Math.max(8.0, radius - 3.0);
        return (int)Math.round(Math.min(maxOffset, heightPx * 0.5));
    }

    static float detailScale(CompanionListPacket.Entry entry, Entity entity, float boxSize, float maxScale) {
        PreviewDimensions dimensions = stableDimensions(entry, entity);
        double width = dimensions.width();
        double height = dimensions.height();
        double depth = dimensions.depth();
        double maxSize = Math.max(Math.max(width, depth) * 1.15, height);
        double scale = (double)boxSize / Math.max(0.75, maxSize);
        return applyOverride(entry, scale, 0.12, maxScale);
    }

    static float fittedScale(CompanionListPacket.Entry entry, Entity entity, float availableWidth, float availableHeight) {
        PreviewDimensions dimensions = stableDimensions(entry, entity);
        double projectedWidth = Math.max(dimensions.width(), dimensions.depth()) * 1.16;
        double projectedHeight = dimensions.height() * 1.08;
        double scale = Math.min(Math.max(4.0, availableWidth) / Math.max(0.75, projectedWidth),
                Math.max(4.0, availableHeight) / Math.max(0.75, projectedHeight));
        return applyOverride(entry, scale, 0.12, 42.0);
    }

    static float silhouetteScale(CompanionListPacket.Entry entry, Entity entity) {
        PreviewDimensions dimensions = stableDimensions(entry, entity);
        double horizontal = Math.max(dimensions.width(), dimensions.depth());
        double footprint = Math.sqrt(Math.max(0.10, horizontal * dimensions.height()));
        double scale = 0.72 + Math.log1p(footprint) * 0.25;
        return (float)Math.max(0.72, Math.min(1.16, scale));
    }

    private static double previewWidth(CompanionListPacket.Entry entry, Entity entity) {
        return CompanionPreviewScaler.previewDimension(entry, "CompanionPreviewWidth", entity.getBbWidth(), CompanionPreviewScaler.iceAndFireDragonMinimum(entry, 7.5));
    }

    private static double previewHeight(CompanionListPacket.Entry entry, Entity entity) {
        return CompanionPreviewScaler.previewDimension(entry, "CompanionPreviewHeight", entity.getBbHeight(), CompanionPreviewScaler.iceAndFireDragonMinimum(entry, 5.5));
    }

    private static double previewDepth(CompanionListPacket.Entry entry, Entity entity) {
        return CompanionPreviewScaler.previewDimension(entry, "CompanionPreviewDepth", entity.getBbWidth(), CompanionPreviewScaler.iceAndFireDragonMinimum(entry, 7.5));
    }

    private static PreviewDimensions stableDimensions(CompanionListPacket.Entry entry, Entity entity) {
        PreviewDimensions cached = STABLE_DIMENSIONS.get(entity);
        if (cached != null) {
            return cached;
        }
        PreviewDimensions measured = new PreviewDimensions(
                Math.max(0.75, CompanionPreviewScaler.previewWidth(entry, entity)),
                Math.max(0.75, CompanionPreviewScaler.previewHeight(entry, entity)),
                Math.max(0.75, CompanionPreviewScaler.previewDepth(entry, entity))
        );
        STABLE_DIMENSIONS.put(entity, measured);
        return measured;
    }

    static void remember(Entity entity, CompanionListPacket.Entry entry, CompoundTag previewTag) {
        if (entity == null) {
            return;
        }
        STABLE_DIMENSIONS.put(entity, new PreviewDimensions(
                Math.max(0.75, previewDimension(previewTag, "CompanionPreviewWidth", entity.getBbWidth(), iceAndFireDragonMinimum(entry, 7.5))),
                Math.max(0.75, previewDimension(previewTag, "CompanionPreviewHeight", entity.getBbHeight(), iceAndFireDragonMinimum(entry, 5.5))),
                Math.max(0.75, previewDimension(previewTag, "CompanionPreviewDepth", entity.getBbWidth(), iceAndFireDragonMinimum(entry, 7.5)))
        ));
    }

    static void forget(Entity entity) {
        if (entity != null) {
            STABLE_DIMENSIONS.remove(entity);
        }
    }

    private static double previewDimension(CompanionListPacket.Entry entry, String key, double fallback, double minimum) {
        return previewDimension(entry.previewTag(), key, fallback, minimum);
    }

    private static double previewDimension(CompoundTag tag, String key, double fallback, double minimum) {
        if (tag != null && tag.contains(key)) {
            float value = tag.getFloat(key);
            if (Float.isFinite(value) && value > 0.0f) {
                return Math.max((double)value, minimum);
            }
        }
        return Math.max(fallback, minimum);
    }

    private static double iceAndFireDragonMinimum(CompanionListPacket.Entry entry, double minimum) {
        String key = entry.entityType() == null ? "" : entry.entityType().toLowerCase(Locale.ROOT);
        return key.equals("iceandfire:fire_dragon") || key.equals("iceandfire:ice_dragon") || key.equals("iceandfire:lightning_dragon") ? minimum : 0.0;
    }

    private static float applyOverride(CompanionListPacket.Entry entry, double scale, double minimum, double maximum) {
        Double override = override(entry);
        double multiplier = override == null || !Double.isFinite(override) ? 1.0 : Math.max(0.1, Math.min(4.0, override));
        return (float)Math.max(minimum, Math.min(maximum, scale * multiplier));
    }

    private static Double override(CompanionListPacket.Entry entry) {
        String key = entry.entityType() == null ? "" : entry.entityType().toLowerCase(Locale.ROOT);
        return Config.previewScaleOverrides.get(key);
    }

    private record PreviewDimensions(double width, double height, double depth) {
    }

}
