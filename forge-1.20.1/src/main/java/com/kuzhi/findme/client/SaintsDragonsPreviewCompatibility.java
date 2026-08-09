package com.kuzhi.findme.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Freezes Saints & Dragons' custom pose inputs for detached wheel previews. */
final class SaintsDragonsPreviewCompatibility {
    private static final String DRAGON_BASE =
            "com.leon.saintsdragons.server.entity.base.DragonEntity";
    private static final Map<Class<?>, Accessors> ACCESSORS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_MANAGER_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_CONTROLLER_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_SPEED_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_RESET_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_CURRENT_ANIMATION_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_ANIMATION_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_NAME_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_LENGTH_METHODS = new HashMap<>();
    private static final Map<Class<?>, Method> GECKO_LOOP_TYPE_METHODS = new HashMap<>();
    private static final Map<Class<?>, Constructor<?>> GECKO_QUEUED_ANIMATION_CONSTRUCTORS = new HashMap<>();
    private static final Map<Class<?>, Field> GECKO_TICK_OFFSET_FIELDS = new HashMap<>();
    private static final Map<Class<?>, Field> GECKO_RESET_TICK_FIELDS = new HashMap<>();
    private static final Map<Class<?>, Field> GECKO_LAST_POLL_FIELDS = new HashMap<>();
    private static final Map<Class<?>, Field> GECKO_CURRENT_ANIMATION_FIELDS = new HashMap<>();
    private static final Map<Class<?>, Field> GECKO_HOLD_LOOP_FIELDS = new HashMap<>();
    private static final Set<Class<?>> UNSUPPORTED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Entity> INITIALIZED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private SaintsDragonsPreviewCompatibility() {
    }

    static void prepareForRender(Entity entity) {
        if (entity == null || !isSaintsDragon(entity)) {
            return;
        }
        Accessors accessors = accessors(entity.getClass());
        if (accessors == null) {
            return;
        }
        boolean initialize = INITIALIZED.add(entity);
        if (initialize) {
            invoke(accessors.resetAnimationState(), entity);
        }

        entity.setDeltaMovement(Vec3.ZERO);
        entity.hasImpulse = false;
        entity.setOnGround(true);
        invokeBoolean(accessors.setFlying(), entity, false);
        invokeBoolean(accessors.setHovering(), entity, false);
        invokeBoolean(accessors.setTakeoff(), entity, false);
        invokeBoolean(accessors.setLanding(), entity, false);
        invokeBoolean(accessors.setGoingUp(), entity, false);
        invokeBoolean(accessors.setGoingDown(), entity, false);
        invokeBoolean(accessors.setAccelerating(), entity, false);
        invokeBoolean(accessors.setRunning(), entity, false);
        invoke(accessors.resetRiderFlightThrottle(), entity);
        invokeInt(accessors.setGroundMoveStateFromAi(), entity, 0);
        invokeFloat(accessors.setAccumulatedRoll(), entity, 0.0f);
        invokeFloat(accessors.setLastRiderForward(), entity, 0.0f);
        invokeFloat(accessors.setLastRiderStrafe(), entity, 0.0f);
        invokeDouble(accessors.setRiderFlightThrottle(), entity, 0.0d);
        setField(accessors.clientTailDragVelocity(), entity, 0.0f);
        zeroSmoothValue(invokeResult(accessors.getBodyRotDeviation(), entity));
        zeroSmoothValue(invokeResult(accessors.getPitchDeviation(), entity));
        zeroSmoothValue(invokeResult(accessors.getYawVelocity(), entity));
        freezeGeckoLibAnimation(entity, accessors, initialize);
    }

    private static boolean isSaintsDragon(Entity entity) {
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (DRAGON_BASE.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Accessors accessors(Class<?> type) {
        Accessors cached = ACCESSORS.get(type);
        if (cached != null) {
            return cached;
        }
        if (UNSUPPORTED.contains(type)) {
            return null;
        }
        Accessors resolved = new Accessors(
                findMethod(type, "resetAnimationState"),
                findMethod(type, "setFlying", boolean.class),
                findMethod(type, "setHovering", boolean.class),
                findMethod(type, "setTakeoff", boolean.class),
                findMethod(type, "setLanding", boolean.class),
                findMethod(type, "setGoingUp", boolean.class),
                findMethod(type, "setGoingDown", boolean.class),
                findMethod(type, "setAccelerating", boolean.class),
                findMethod(type, "setRunning", boolean.class),
                findMethod(type, "resetRiderFlightThrottle"),
                findMethod(type, "setGroundMoveStateFromAI", int.class),
                findMethod(type, "setAccumulatedRoll", float.class),
                findMethod(type, "setLastRiderForward", float.class),
                findMethod(type, "setLastRiderStrafe", float.class),
                findMethod(type, "setRiderFlightThrottle", double.class),
                findMethod(type, "getBodyRotDeviation"),
                findMethod(type, "getPitchDeviation"),
                findMethod(type, "getYawVelocity"),
                findField(type, "clientTailDragVelocity"),
                findMethod(type, "getAnimatableInstanceCache")
        );
        ACCESSORS.put(type, resolved);
        return resolved;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method cachedMethod(Map<Class<?>, Method> cache, Class<?> type, String name,
                                       Class<?>... parameters) {
        Method cached = cache.get(type);
        if (cached != null) {
            return cached;
        }
        Method resolved = findMethod(type, name, parameters);
        if (resolved != null) {
            cache.put(type, resolved);
        }
        return resolved;
    }

    private static Field cachedField(Map<Class<?>, Field> cache, Class<?> type, String name) {
        Field cached = cache.get(type);
        if (cached != null) {
            return cached;
        }
        Field resolved = findField(type, name);
        if (resolved != null) {
            cache.put(type, resolved);
        }
        return resolved;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through the optional mod's entity hierarchy.
            }
        }
        return null;
    }

    private static Object invokeResult(Method method, Object target) {
        return invokeResult(method, target, new Object[0]);
    }

    private static Object invokeResult(Method method, Object target, Object... arguments) {
        try {
            return method == null ? null : method.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void invoke(Method method, Object target) {
        invokeResult(method, target);
    }

    private static void invokeBoolean(Method method, Object target, boolean value) {
        try {
            if (method != null) method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void invokeInt(Method method, Object target, int value) {
        try {
            if (method != null) method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void invokeFloat(Method method, Object target, float value) {
        try {
            if (method != null) method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void invokeDouble(Method method, Object target, double value) {
        try {
            if (method != null) method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void freezeGeckoLibAnimation(Entity entity, Accessors accessors, boolean initialize) {
        Object cache = invokeResult(accessors.getAnimatableInstanceCache(), entity);
        if (cache == null) {
            return;
        }
        Method managerMethod = cachedMethod(GECKO_MANAGER_METHODS, cache.getClass(), "getManagerForId", long.class);
        Object manager = invokeResult(managerMethod, cache, (long)entity.hashCode());
        if (manager == null) {
            return;
        }
        Method controllersMethod = cachedMethod(GECKO_CONTROLLER_METHODS, manager.getClass(), "getAnimationControllers");
        Object controllers = invokeResult(controllersMethod, manager);
        if (!(controllers instanceof Map<?, ?> controllerMap)) {
            return;
        }
        for (Object controller : controllerMap.values()) {
            if (controller == null) {
                continue;
            }
            Method speedMethod = cachedMethod(GECKO_SPEED_METHODS, controller.getClass(), "setAnimationSpeed", double.class);
            invokeDouble(speedMethod, controller, 0.0d);
            if (initialize) {
                Method resetMethod = cachedMethod(GECKO_RESET_METHODS, controller.getClass(), "forceAnimationReset");
                invoke(resetMethod, controller);
            }
            holdIdleAnimationAtEnd(controller);
        }
    }

    private static void holdIdleAnimationAtEnd(Object controller) {
        Method currentMethod = cachedMethod(GECKO_CURRENT_ANIMATION_METHODS, controller.getClass(), "getCurrentAnimation");
        Object queuedAnimation = invokeResult(currentMethod, controller);
        if (queuedAnimation == null) {
            return;
        }
        Method animationMethod = cachedMethod(GECKO_ANIMATION_METHODS, queuedAnimation.getClass(), "animation");
        Object animation = invokeResult(animationMethod, queuedAnimation);
        if (animation == null) {
            return;
        }
        Method nameMethod = cachedMethod(GECKO_NAME_METHODS, animation.getClass(), "name");
        Object name = invokeResult(nameMethod, animation);
        if (!(name instanceof String animationName)
                || (!animationName.endsWith(".idle") && !animationName.contains("_idle"))) {
            return;
        }

        // Saints' idle clips are authored as loops. Replace only this detached controller's
        // queue entry with GeckoLib's native hold mode so the last keyframe is preserved.
        holdAnimationAtEnd(controller, queuedAnimation, animationMethod);
        Method lengthMethod = cachedMethod(GECKO_LENGTH_METHODS, animation.getClass(), "length");
        Object lengthValue = invokeResult(lengthMethod, animation);
        if (!(lengthValue instanceof Number lengthNumber)) {
            return;
        }
        double length = Math.max(0.001d, lengthNumber.doubleValue());
        Field lastPollField = cachedField(GECKO_LAST_POLL_FIELDS, controller.getClass(), "lastPollTime");
        double anchor = readDoubleField(lastPollField, controller, 0.0d);
        if (anchor < 0.0d) {
            anchor = 0.0d;
        }
        Field tickOffsetField = cachedField(GECKO_TICK_OFFSET_FIELDS, controller.getClass(), "tickOffset");
        setDoubleField(tickOffsetField, controller, anchor - Math.max(0.0d, length - 0.001d));
        Field resetTickField = cachedField(GECKO_RESET_TICK_FIELDS, controller.getClass(), "shouldResetTick");
        setBooleanField(resetTickField, controller, false);
        Method speedMethod = cachedMethod(GECKO_SPEED_METHODS, controller.getClass(), "setAnimationSpeed", double.class);
        invokeDouble(speedMethod, controller, 1.0d);
    }

    private static void holdAnimationAtEnd(Object controller, Object queuedAnimation, Method animationMethod) {
        Method loopTypeMethod = cachedMethod(GECKO_LOOP_TYPE_METHODS, queuedAnimation.getClass(), "loopType");
        if (loopTypeMethod == null) {
            return;
        }
        Class<?> loopTypeClass = loopTypeMethod.getReturnType();
        Field holdLoopField = cachedField(GECKO_HOLD_LOOP_FIELDS, loopTypeClass, "HOLD_ON_LAST_FRAME");
        Object holdLoop = readStaticField(holdLoopField);
        if (holdLoop == null) {
            return;
        }
        Object heldAnimation = newQueuedAnimation(
                queuedAnimation,
                animationMethod == null ? null : animationMethod.getReturnType(),
                loopTypeClass,
                invokeResult(animationMethod, queuedAnimation),
                holdLoop
        );
        if (heldAnimation == null) {
            return;
        }
        Field currentAnimationField = cachedField(
                GECKO_CURRENT_ANIMATION_FIELDS,
                controller.getClass(),
                "currentAnimation"
        );
        setObjectField(currentAnimationField, controller, heldAnimation);
    }

    private static Object newQueuedAnimation(Object queuedAnimation, Class<?> animationType, Class<?> loopType,
                                              Object animation, Object holdLoop) {
        if (animationType == null || animation == null || loopType == null) {
            return null;
        }
        Constructor<?> constructor = cachedConstructor(
                GECKO_QUEUED_ANIMATION_CONSTRUCTORS,
                queuedAnimation.getClass(),
                animationType,
                loopType
        );
        try {
            return constructor == null ? null : constructor.newInstance(animation, holdLoop);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Constructor<?> cachedConstructor(Map<Class<?>, Constructor<?>> cache, Class<?> type,
                                                     Class<?>... parameters) {
        Constructor<?> cached = cache.get(type);
        if (cached != null) {
            return cached;
        }
        try {
            Constructor<?> resolved = type.getConstructor(parameters);
            resolved.setAccessible(true);
            cache.put(type, resolved);
            return resolved;
        } catch (NoSuchMethodException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object readStaticField(Field field) {
        try {
            return field == null ? null : field.get(null);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static void setField(Field field, Object target, float value) {
        try {
            if (field != null) field.setFloat(target, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static double readDoubleField(Field field, Object target, double fallback) {
        try {
            return field == null ? fallback : field.getDouble(target);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static void setDoubleField(Field field, Object target, double value) {
        try {
            if (field != null) field.setDouble(target, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static void setObjectField(Field field, Object target, Object value) {
        try {
            if (field != null) field.set(target, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static void setBooleanField(Field field, Object target, boolean value) {
        try {
            if (field != null) field.setBoolean(target, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static void zeroSmoothValue(Object smoothValue) {
        if (smoothValue == null) {
            return;
        }
        try {
            Method setValue = smoothValue.getClass().getMethod("setValue", double.class);
            Method setTo = smoothValue.getClass().getMethod("setTo", double.class);
            setValue.invoke(smoothValue, 0.0d);
            setTo.invoke(smoothValue, 0.0d);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private record Accessors(
            Method resetAnimationState,
            Method setFlying,
            Method setHovering,
            Method setTakeoff,
            Method setLanding,
            Method setGoingUp,
            Method setGoingDown,
            Method setAccelerating,
            Method setRunning,
            Method resetRiderFlightThrottle,
            Method setGroundMoveStateFromAi,
            Method setAccumulatedRoll,
            Method setLastRiderForward,
            Method setLastRiderStrafe,
            Method setRiderFlightThrottle,
            Method getBodyRotDeviation,
            Method getPitchDeviation,
            Method getYawVelocity,
            Field clientTailDragVelocity,
            Method getAnimatableInstanceCache
    ) {
    }
}
