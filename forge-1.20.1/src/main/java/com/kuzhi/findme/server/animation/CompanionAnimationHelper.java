package com.kuzhi.findme.server.animation;


import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public final class CompanionAnimationHelper {
    private static final Map<MethodKey, Optional<Method>> BOOLEAN_METHODS = new HashMap<>();
    private static final Map<MethodKey, Optional<Method>> INT_METHODS = new HashMap<>();
    private static final Map<MethodKey, Optional<Method>> INT_INT_METHODS = new HashMap<>();
    private static final Map<MethodKey, Optional<Method>> ACCESSOR_METHODS = new HashMap<>();

    private CompanionAnimationHelper() {
    }

    public static void restoreAnimationControl(LivingEntity living) {
        restoreMovementAnimationControl(living);
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
    }

    /** Restores movement flags without interrupting an active combat target or path. */
    public static void restoreMovementAnimationControl(LivingEntity living) {
        living.noPhysics = false;
        living.setNoGravity(false);
        restoreGenericFlightInput(living);
    }

    public static void forceFlyingAnimationPose(LivingEntity living) {
        living.setPose(Pose.FALL_FLYING);
        living.setNoGravity(true);
        living.setOnGround(false);
        living.fallDistance = 0.0f;
        living.hurtMarked = true;
        forceGenericMovingFlight(living);
    }

    public static boolean keepsFlyingAnimationWithActiveAi(LivingEntity living) {
        String key = EntityType.getKey(living.getType()).toString().toLowerCase(Locale.ROOT);
        return key.startsWith("iceandfire:");
    }

    public static boolean shouldSuppressOwnerFollowDuringCinematic(LivingEntity living) {
        String key = EntityType.getKey(living.getType()).toString().toLowerCase(Locale.ROOT);
        return CompanionEntityClassifier.isDragonMountsLegacyKey(key);
    }

    public static void forceStandingPose(LivingEntity living) {
        callBooleanMethod(living, "setOrderedToSit", false);
        callBooleanMethod(living, "setInSittingPose", false);
        callBooleanMethod(living, "setSitting", false);
        callBooleanMethod(living, "setSittingPose", false);
        callBooleanMethod(living, "setIsSitting", false);
    }

    public static void forceGroundMovingPose(LivingEntity living) {
        forceStandingPose(living);
        living.setSprinting(true);
        callBooleanMethod(living, "setRunning", true);
        callBooleanMethod(living, "setIsRunning", true);
        callBooleanMethod(living, "setMoving", true);
        callBooleanMethod(living, "setIsMoving", true);
    }

    private static void restoreGenericFlightInput(LivingEntity living) {
        callBooleanMethod(living, "setGoingUp", false);
        callBooleanMethod(living, "setGoingDown", false);
        callBooleanMethod(living, "setAccelerating", false);
        callBooleanMethod(living, "setSprinting", false);
    }

    private static void forceGenericMovingFlight(LivingEntity living) {
        Vec3 motion = living.getDeltaMovement();
        double horizontalSpeedSqr = motion.horizontalDistanceSqr();
        if (horizontalSpeedSqr < 0.0016 && Math.abs(motion.y) < 0.03) {
            Vec3 look = living.getLookAngle();
            motion = new Vec3(look.x, 0.0, look.z);
            motion = motion.horizontalDistanceSqr() < 0.0016 ? new Vec3(0.0, 0.0, 0.28) : motion.normalize().scale(0.28);
            living.setDeltaMovement(motion);
        }
        callBooleanMethod(living, "setFlying", true);
        callBooleanMethod(living, "setInAir", true);
        callBooleanMethod(living, "setAirborne", true);
        callBooleanMethod(living, "setHovering", false);
        callBooleanMethod(living, "setLanding", false);
        callBooleanMethod(living, "setTakeoff", false);
        callBooleanMethod(living, "setGoingUp", false);
        callBooleanMethod(living, "setGoingDown", false);
        callBooleanMethod(living, "setAccelerating", true);
        callBooleanMethod(living, "setSprinting", true);
        int flightMode = 4;
        callIntMethod(living, "setFlightMode", flightMode);
        callIntMethod(living, "setSyncedFlightMode", flightMode);
        callIntMethod(living, "setMoveState", 2);
        callIntMethod(living, "setMovementState", 2);
        setSyncedIntAccessor(living, "getFlightModeAccessor", flightMode);
        setSyncedIntAccessor(living, "getMoveStateAccessor", 2);
        setSyncedIntAccessor(living, "getMovementStateAccessor", 2);
        callIntIntMethod(living, "syncAnimState", 0, flightMode);
    }

    private static void setSyncedIntAccessor(LivingEntity living, String accessorMethod, int value) {
        try {
            Method method = ACCESSOR_METHODS.computeIfAbsent(new MethodKey(living.getClass(), accessorMethod),
                    ignored -> Optional.ofNullable(findNoArgMethod(living.getClass(), accessorMethod))).orElse(null);
            if (method == null) {
                return;
            }
            method.setAccessible(true);
            Object accessor = method.invoke(living);
            if (accessor instanceof EntityDataAccessor<?> dataAccessor) {
                living.getEntityData().set((EntityDataAccessor<Integer>)dataAccessor, value);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
        }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (method.getParameterCount() != 0) continue;
                return method;
            } catch (NoSuchMethodException noSuchMethodException) {
            }
        }
        return null;
    }

    private static void callBooleanMethod(Object target, String name, boolean value) {
        cachedBooleanMethod(target.getClass(), name)
                .ifPresent(method -> invoke(method, target, value));
    }

    private static Optional<Method> cachedBooleanMethod(Class<?> type, String name) {
        MethodKey key = new MethodKey(type, name);
        return BOOLEAN_METHODS.computeIfAbsent(key, ignored -> {
            try {
                return Optional.of(type.getMethod(name, Boolean.TYPE));
            } catch (NoSuchMethodException primitiveMissing) {
                try {
                    return Optional.of(type.getMethod(name, Boolean.class));
                } catch (NoSuchMethodException boxedMissing) {
                    return Optional.empty();
                }
            }
        });
    }

    private static void callIntMethod(Object target, String name, int value) {
        cachedMethod(INT_METHODS, target.getClass(), name, Integer.TYPE)
                .ifPresent(method -> invoke(method, target, value));
    }

    private static void callIntIntMethod(Object target, String name, int first, int second) {
        cachedMethod(INT_INT_METHODS, target.getClass(), name, Integer.TYPE, Integer.TYPE)
                .ifPresent(method -> invoke(method, target, first, second));
    }

    private static Optional<Method> cachedMethod(Map<MethodKey, Optional<Method>> cache, Class<?> type,
                                                 String name, Class<?>... parameters) {
        MethodKey key = new MethodKey(type, name);
        return cache.computeIfAbsent(key, ignored -> {
            try {
                return Optional.of(type.getMethod(name, parameters));
            } catch (NoSuchMethodException exception) {
                return Optional.empty();
            }
        });
    }

    private static void invoke(Method method, Object target, Object... values) {
        try {
            method.invoke(target, values);
        } catch (ReflectiveOperationException | RuntimeException exception) {
        }
    }

    private record MethodKey(Class<?> type, String name) {
    }
}
