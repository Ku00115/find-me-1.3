package com.kuzhi.findme.server.compat;

import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/** Bridges Saints & Dragons' custom command state without a compile-time dependency. */
public final class CompanionSaintsDragonsCompat {
    private static final String DRAGON_BASE_CLASS =
            "com.leon.saintsdragons.server.entity.base.DragonEntity";
    private static final Map<Class<?>, Accessor> ACCESSORS = new HashMap<>();
    private static final Set<Class<?>> UNSUPPORTED = new HashSet<>();
    private static final Map<UUID, Integer> PREVIOUS_COMMANDS = new HashMap<>();
    private static final Map<UUID, Integer> PREVIOUS_ACTIVE_COMMANDS = new HashMap<>();

    private CompanionSaintsDragonsCompat() {
    }

    public static void applyHomeCommand(LivingEntity entity, HouseResidentMode mode) {
        Accessor accessor = accessor(entity);
        if (accessor == null || entity == null) {
            return;
        }

        UUID uuid = entity.getUUID();
        Integer currentCommand = accessor.getCommand(entity);
        if (currentCommand == null) {
            return;
        }
        PREVIOUS_COMMANDS.putIfAbsent(uuid, currentCommand);
        int targetCommand = mode == HouseResidentMode.REST ? 1 : 2;
        if (currentCommand != targetCommand && accessor.setCommand(entity, targetCommand)) {
            FindMeDebugLogger.info("saints-dragons-compat",
                    "home command entity={} command={} mode={}",
                    FindMeDebugLogger.entity(entity), targetCommand, mode);
        }
    }

    public static void clearHomeCommand(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        Integer previous = PREVIOUS_COMMANDS.remove(entity.getUUID());
        if (previous == null) {
            return;
        }
        Accessor accessor = accessor(entity);
        Integer currentCommand = accessor == null ? null : accessor.getCommand(entity);
        if (accessor != null && currentCommand != null
                && currentCommand.intValue() != previous.intValue()
                && accessor.setCommand(entity, previous)) {
            FindMeDebugLogger.info("saints-dragons-compat",
                    "restored command entity={} command={}",
                    FindMeDebugLogger.entity(entity), previous);
        }
    }

    /** Lets Saints & Dragons own follow locomotion while Find Me owns threat selection. */
    public static void applyActiveCommand(LivingEntity entity) {
        Accessor accessor = accessor(entity);
        if (accessor == null || entity == null) {
            return;
        }
        Integer currentCommand = accessor.getCommand(entity);
        if (currentCommand == null) {
            return;
        }
        PREVIOUS_ACTIVE_COMMANDS.putIfAbsent(entity.getUUID(), currentCommand);
        if (currentCommand != 0 && accessor.setCommand(entity, 0)) {
            FindMeDebugLogger.info("saints-dragons-compat",
                    "active follow command entity={} command=0", FindMeDebugLogger.entity(entity));
        }
    }

    public static void clearActiveCommand(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        Integer previous = PREVIOUS_ACTIVE_COMMANDS.remove(entity.getUUID());
        if (previous == null) {
            return;
        }
        Accessor accessor = accessor(entity);
        Integer currentCommand = accessor == null ? null : accessor.getCommand(entity);
        if (accessor != null && currentCommand != null
                && currentCommand.intValue() != previous.intValue()
                && accessor.setCommand(entity, previous)) {
            FindMeDebugLogger.info("saints-dragons-compat",
                    "restored active command entity={} command={}",
                    FindMeDebugLogger.entity(entity), previous);
        }
    }

    /** Returns the dragon brain's actual flight state, not its mount category. */
    public static boolean isAirborne(LivingEntity entity) {
        if (!isSaintsDragon(entity)) {
            return entity != null && (!entity.onGround() || entity.isNoGravity());
        }
        Accessor accessor = accessor(entity);
        Boolean flying = accessor == null ? null : accessor.isFlying(entity);
        return Boolean.TRUE.equals(flying) || !entity.onGround() || entity.isNoGravity();
    }

    public static void resetServerState() {
        PREVIOUS_COMMANDS.clear();
        PREVIOUS_ACTIVE_COMMANDS.clear();
    }

    private static Accessor accessor(LivingEntity entity) {
        if (entity == null || !isSaintsDragon(entity)) {
            return null;
        }
        Class<?> entityClass = entity.getClass();
        Accessor cached = ACCESSORS.get(entityClass);
        if (cached != null) {
            return cached;
        }
        if (UNSUPPORTED.contains(entityClass)) {
            return null;
        }
        try {
            Accessor resolved = new Accessor(
                    entityClass.getMethod("getCommand"),
                    entityClass.getMethod("setCommand", int.class)
            );
            ACCESSORS.put(entityClass, resolved);
            return resolved;
        } catch (ReflectiveOperationException exception) {
            UNSUPPORTED.add(entityClass);
            FindMeDebugLogger.info("saints-dragons-compat",
                    "unsupported dragon class={} reason={}", entityClass.getName(), exception.getClass().getSimpleName());
            return null;
        }
    }

    /** Returns true for entities whose locomotion is owned by Saints & Dragons' brain. */
    public static boolean isSaintsDragon(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (DRAGON_BASE_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private record Accessor(Method getCommand, Method setCommand) {
        private Boolean isFlying(LivingEntity entity) {
            try {
                Method method = entity.getClass().getMethod("isFlying");
                return (Boolean) method.invoke(entity);
            } catch (ReflectiveOperationException | ClassCastException exception) {
                return null;
            }
        }

        private Integer getCommand(LivingEntity entity) {
            try {
                return ((Number) getCommand.invoke(entity)).intValue();
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException exception) {
                FindMeDebugLogger.info("saints-dragons-compat",
                        "command read failed entity={} reason={}",
                        FindMeDebugLogger.entity(entity), exception.getClass().getSimpleName());
                return null;
            }
        }

        private boolean setCommand(LivingEntity entity, int command) {
            try {
                setCommand.invoke(entity, command);
                return true;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                FindMeDebugLogger.info("saints-dragons-compat",
                        "command write failed entity={} command={} reason={}",
                        FindMeDebugLogger.entity(entity), command, exception.getClass().getSimpleName());
                return false;
            }
        }
    }
}
