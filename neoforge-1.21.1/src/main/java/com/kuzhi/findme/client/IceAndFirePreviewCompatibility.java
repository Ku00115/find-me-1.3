package com.kuzhi.findme.client;

import java.lang.reflect.Method;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Initializes data-less previews from IAFEnvoy's Ice And Fire Community Edition. */
final class IceAndFirePreviewCompatibility {
    private static final Set<String> DRAGON_TYPES = Set.of(
            "iceandfire:fire_dragon",
            "iceandfire:ice_dragon",
            "iceandfire:lightning_dragon"
    );
    private static final int PREVIEW_AGE_DAYS = 50;

    private IceAndFirePreviewCompatibility() {
    }

    static boolean isDragon(Entity entity) {
        return entity != null && isDragon(EntityType.getKey(entity.getType()).toString());
    }

    static boolean isDragon(String entityType) {
        return entityType != null && DRAGON_TYPES.contains(entityType);
    }

    static void stabilizeDetachedPreview(Entity entity) {
        if (!isDragon(entity)) {
            return;
        }
        try {
            entity.getClass().getMethod("setDragonPitch", float.class).invoke(entity, 0.0f);
            var previousPitch = findField(entity.getClass(), "prevDragonPitch");
            if (previousPitch != null) {
                previousPitch.setAccessible(true);
                previousPitch.setFloat(entity, 0.0f);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional integration: retain the values supplied by another CE build.
        }
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
            stabilizeDetachedPreview(entity);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional integration: an incompatible build keeps its native preview defaults.
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the CE entity hierarchy.
            }
        }
        return null;
    }

    private static String trimVariantSuffix(String variant) {
        int end = variant.length();
        while (end > 0 && variant.charAt(end - 1) == '_') {
            end--;
        }
        return end == variant.length() ? variant : variant.substring(0, end);
    }
}
