package com.kuzhi.findme.client;

import java.lang.reflect.Method;
import java.util.Set;
import net.minecraft.world.entity.Entity;

/** Initializes only data-less client preview dragons without linking Ice and Fire at runtime. */
final class IceAndFirePreviewCompatibility {
    private static final Set<String> DRAGON_TYPES = Set.of(
            "iceandfire:fire_dragon",
            "iceandfire:ice_dragon",
            "iceandfire:lightning_dragon"
    );
    private static final int PREVIEW_AGE_DAYS = 50;

    private IceAndFirePreviewCompatibility() {
    }

    static void initializeSyntheticPreview(Entity entity, String requestedType) {
        if (entity == null || requestedType == null || !DRAGON_TYPES.contains(requestedType)) {
            return;
        }
        Class<?> entityClass = entity.getClass();
        if (!entityClass.getName().startsWith("com.iafenvoy.iceandfire.entity.")) {
            return;
        }
        try {
            Method getVariantName = entityClass.getMethod("getVariantName", int.class);
            Object rawVariant = getVariantName.invoke(entity, 0);
            if (rawVariant instanceof String variant && !variant.isBlank()) {
                entityClass.getMethod("setVariant", String.class).invoke(entity, trimVariantSuffix(variant));
            }
            entityClass.getMethod("setAgeInDays", int.class).invoke(entity, PREVIEW_AGE_DAYS);
            entity.refreshDimensions();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional integration: an incompatible build keeps its native preview defaults.
        }
    }

    private static String trimVariantSuffix(String variant) {
        int end = variant.length();
        while (end > 0 && variant.charAt(end - 1) == '_') {
            end--;
        }
        return end == variant.length() ? variant : variant.substring(0, end);
    }
}
