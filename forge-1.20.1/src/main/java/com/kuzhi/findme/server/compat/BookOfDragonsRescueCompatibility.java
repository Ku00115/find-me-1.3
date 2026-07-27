package com.kuzhi.findme.server.compat;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;

/** Prevents Book of Dragons' own rescue goal from competing with a FindMe rescue. */
public final class BookOfDragonsRescueCompatibility {
    private static final String ENTITY_PREFIX = "net.magister.bookofdragons.entity.";
    private static final String RESCUE_GOAL = "net.magister.bookofdragons.entity.ai.goal.DragonRescueGoal";
    private static final String FOLLOW_OWNER_GOAL = "net.magister.bookofdragons.entity.ai.goal.DragonFollowOwnerGoal";
    private static final String TRANSPORT_MODE = "net.magister.bookofdragons.entity.state.TransportMode";
    private static final Map<UUID, SuspendedGoals> SUSPENDED = new HashMap<>();
    private static final Map<Class<?>, Optional<FlightAccess>> FLIGHT_ACCESS = new HashMap<>();

    private BookOfDragonsRescueCompatibility() {
    }

    public static void suspendConflictingGoals(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !entity.getClass().getName().startsWith(ENTITY_PREFIX)
                || SUSPENDED.containsKey(entity.getUUID())) {
            return;
        }
        List<GoalEntry> removed = new ArrayList<>();
        for (WrappedGoal wrapped : List.copyOf(mob.goalSelector.getAvailableGoals())) {
            Goal goal = wrapped.getGoal();
            String goalClass = goal.getClass().getName();
            if (goalClass.equals(RESCUE_GOAL) || goalClass.equals(FOLLOW_OWNER_GOAL)) {
                boolean running = wrapped.isRunning();
                if (running) {
                    wrapped.stop();
                }
                removed.add(new GoalEntry(wrapped.getPriority(), goal));
                mob.goalSelector.removeGoal(goal);
                FindMeDebugLogger.info("bookofdragons-goal",
                        "suspended entity={} goal={} priority={} wasRunning={}",
                        FindMeDebugLogger.entity(entity), goalClass, wrapped.getPriority(), running);
            }
        }
        if (!removed.isEmpty()) {
            SUSPENDED.put(entity.getUUID(), new SuspendedGoals(mob, List.copyOf(removed)));
        } else {
            FindMeDebugLogger.info("bookofdragons-goal",
                    "no conflicting goals found entity={} availableGoals={}",
                    FindMeDebugLogger.entity(entity), mob.goalSelector.getAvailableGoals().size());
        }
    }

    public static void restoreConflictingGoals(UUID entityUuid) {
        SuspendedGoals suspended = SUSPENDED.remove(entityUuid);
        if (suspended == null) {
            return;
        }
        for (GoalEntry entry : suspended.goals()) {
            suspended.mob().goalSelector.addGoal(entry.priority(), entry.goal());
        }
        FindMeDebugLogger.info("bookofdragons-goal", "restored entity={} goals={}",
                FindMeDebugLogger.entity(suspended.mob()), suspended.goals().size());
    }

    /** Keeps FindMe's bounded flight handoff inside Book of Dragons' native state machine. */
    public static boolean forceAirborne(LivingEntity entity) {
        if (!isBookOfDragonsDragon(entity)) {
            return false;
        }
        FlightAccess access = FLIGHT_ACCESS.computeIfAbsent(entity.getClass(),
                BookOfDragonsRescueCompatibility::resolveFlightAccess).orElse(null);
        if (access == null) {
            return false;
        }
        try {
            Object currentMode = access.getTransportMode().invoke(entity);
            if (currentMode != access.airborneMode()) {
                access.setTransportMode().invoke(entity, access.airborneMode());
            }
            access.setSolidGround().ifPresent(method -> invokeBoolean(method, entity, false));
            entity.setOnGround(false);
            entity.setNoGravity(true);
            entity.fallDistance = 0.0f;
            Vec3 velocity = entity.getDeltaMovement();
            if (velocity.lengthSqr() < 1.0E-4) {
                entity.setDeltaMovement(0.0, 0.01, 0.0);
            }
            entity.hurtMarked = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FindMeDebugLogger.info("bookofdragons-flight",
                    "failed to force airborne entity={} reason={}",
                    FindMeDebugLogger.entity(entity), exception.getClass().getSimpleName());
            return false;
        }
    }

    public static boolean isBookOfDragonsDragon(LivingEntity entity) {
        return entity != null && entity.getClass().getName().startsWith(ENTITY_PREFIX);
    }

    public static void logFlightState(LivingEntity entity, int age, boolean riding) {
        if (!FindMeDebugLogger.enabled() || !isBookOfDragonsDragon(entity)) {
            return;
        }
        FlightAccess access = FLIGHT_ACCESS.computeIfAbsent(entity.getClass(),
                BookOfDragonsRescueCompatibility::resolveFlightAccess).orElse(null);
        Object transportMode = null;
        Object flying = null;
        if (access != null) {
            try {
                transportMode = access.getTransportMode().invoke(entity);
                flying = access.isFlying().invoke(entity);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        FindMeDebugLogger.info("bookofdragons-flight",
                "tick={} entity={} transportMode={} isFlying={} onGround={} noGravity={} velocity={} riding={} passengers={}",
                age, FindMeDebugLogger.entity(entity), transportMode, flying, entity.onGround(),
                entity.isNoGravity(), entity.getDeltaMovement(), riding, entity.getPassengers().size());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<FlightAccess> resolveFlightAccess(Class<?> entityClass) {
        try {
            ClassLoader loader = entityClass.getClassLoader();
            Class<?> transportModeClass = Class.forName(TRANSPORT_MODE, false, loader);
            Object airborne = Enum.valueOf((Class<? extends Enum>) transportModeClass.asSubclass(Enum.class), "AIRBORNE");
            Method setTransportMode = entityClass.getMethod("setTransportMode", transportModeClass);
            Method getTransportMode = entityClass.getMethod("getTransportMode");
            Method isFlying = entityClass.getMethod("isFlying");
            Method setSolidGround;
            try {
                setSolidGround = entityClass.getMethod("setIsOnSolidGround", boolean.class);
            } catch (NoSuchMethodException ignored) {
                setSolidGround = null;
            }
            return Optional.of(new FlightAccess(setTransportMode, getTransportMode, isFlying,
                    Optional.ofNullable(setSolidGround), airborne));
        } catch (ReflectiveOperationException | LinkageError exception) {
            FindMeDebugLogger.info("bookofdragons-flight",
                    "native flight API unavailable entityClass={} reason={}",
                    entityClass.getName(), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static void invokeBoolean(Method method, Object target, boolean value) {
        try {
            method.invoke(target, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private record GoalEntry(int priority, Goal goal) {
    }

    private record SuspendedGoals(Mob mob, List<GoalEntry> goals) {
    }

    private record FlightAccess(Method setTransportMode, Method getTransportMode, Method isFlying,
                                Optional<Method> setSolidGround, Object airborneMode) {
    }
}
