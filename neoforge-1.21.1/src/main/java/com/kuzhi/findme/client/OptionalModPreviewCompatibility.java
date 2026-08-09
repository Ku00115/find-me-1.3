package com.kuzhi.findme.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.Entity;

/** Applies optional-mod flags required by stable detached inventory previews. */
final class OptionalModPreviewCompatibility {
    private static final String ERS_ENTITY_PREFIX = "cn.aurorian.ers.";
    private static final String OASIS_ENTITY_PREFIX = "cn.aurorian.oasis.";
    private static final Map<Class<?>, Method> ERS_ANIMATOR_METHODS = new HashMap<>();
    private static final Set<Class<?>> NO_ERS_ANIMATOR = Collections.newSetFromMap(new IdentityHashMap<>());

    private OptionalModPreviewCompatibility() {
    }

    static void prepareForRender(Entity entity) {
        Object animator = ersAnimator(entity);
        if (animator != null) {
            setBooleanField(animator, "isInScreen", true);
        }
    }

    private static Object ersAnimator(Entity entity) {
        if (entity == null) return null;
        String className = entity.getClass().getName();
        if (!className.startsWith(ERS_ENTITY_PREFIX) && !className.startsWith(OASIS_ENTITY_PREFIX)) return null;
        Class<?> type = entity.getClass();
        if (NO_ERS_ANIMATOR.contains(type)) return null;
        try {
            Method method = ERS_ANIMATOR_METHODS.get(type);
            if (method == null) {
                method = type.getMethod("getAnimator");
                method.setAccessible(true);
                ERS_ANIMATOR_METHODS.put(type, method);
            }
            return method.invoke(entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            NO_ERS_ANIMATOR.add(type);
            return null;
        }
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        for (Class<?> cursor = target.getClass(); cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                field.setBoolean(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Continue through the optional mod's animator hierarchy.
            } catch (IllegalAccessException | RuntimeException ignored) {
                return;
            }
        }
    }
}
