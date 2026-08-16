package com.kuzhi.findme.server.compat;

import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Narrow, optional rescue handoff for Ice and Fire dragons. */
public final class IceAndFireRescueCompatibility {
    private static final String DRAGON_CLASS_PREFIX = "com.github.alexthe666.iceandfire.entity.Entity";
    private static final Map<Class<?>, Optional<DragonAccess>> ACCESS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PREVIOUS_COMMANDS = new ConcurrentHashMap<>();

    private IceAndFireRescueCompatibility() {
    }

    public static boolean isDragon(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key != null && "iceandfire".equals(key.getNamespace())
                && ("fire_dragon".equals(key.getPath()) || "ice_dragon".equals(key.getPath())
                || "lightning_dragon".equals(key.getPath()))) {
            return true;
        }
        return entity.getClass().getName().startsWith(DRAGON_CLASS_PREFIX)
                && entity.getClass().getSimpleName().endsWith("Dragon");
    }

    /** Restores the stored entity to a neutral state before a failed rescue is stored. */
    public static void clearRescueState(LivingEntity entity) {
        if (!isDragon(entity)) {
            return;
        }
        invoke(entity, access(entity).map(DragonAccess::setFlying), false);
        invoke(entity, access(entity).map(DragonAccess::setHovering), false);
    }

    /** Leaves the native flight state enabled after the player has actually boarded. */
    public static void finishMountedRide(LivingEntity entity) {
        if (!isDragon(entity)) {
            return;
        }
        invoke(entity, access(entity).map(DragonAccess::setFlying), true);
        invoke(entity, access(entity).map(DragonAccess::setHovering), false);
    }

    /** Keeps the native dragon controller in moving flight while FindMe owns escort motion. */
    public static void forceEscortFlight(LivingEntity entity) {
        if (!isDragon(entity)) return;
        invoke(entity, access(entity).map(DragonAccess::setFlying), true);
        invoke(entity, access(entity).map(DragonAccess::setHovering), false);
    }

    /** Allows forced boarding only for the registered, owned Ice and Fire mount. */
    public static boolean canForceRescueMount(ServerPlayer player, LivingEntity entity) {
        boolean allowed = isDragon(entity) && player != null
                && FindMeApi.isRegistered(player, entity, CompanionKind.MOUNT)
                && CompanionEntityClassifier.isOwnedBy(player, entity);
        if (allowed) {
            FindMeDebugLogger.info("iceandfire-rescue", "force mount allowed player={} entity={}",
                    player.getUUID(), FindMeDebugLogger.entity(entity));
        }
        return allowed;
    }

    /** Disables Ice and Fire's owner escort while FindMe owns a fixed post. */
    public static void applyFixedPostCommand(LivingEntity entity, String reason) {
        if (!isDragon(entity)) {
            return;
        }
        access(entity).ifPresent(access -> {
            Integer current = access.getCommand(entity);
            if (current == null) {
                return;
            }
            PREVIOUS_COMMANDS.putIfAbsent(entity.getUUID(), current);
            if (current != 0 && access.setCommand(entity, 0)) {
                FindMeDebugLogger.info("iceandfire-command",
                        "entity={} previous={} applied=0 reason={}",
                        FindMeDebugLogger.entity(entity), current, reason);
            }
        });
    }

    public static void applyHomeCommand(LivingEntity entity, HouseResidentMode mode) {
        if (!isDragon(entity)) {
            return;
        }
        access(entity).ifPresent(access -> {
            Integer current = access.getCommand(entity);
            if (current == null) {
                return;
            }
            PREVIOUS_COMMANDS.putIfAbsent(entity.getUUID(), current);
            int target = mode == HouseResidentMode.REST ? 1 : 0;
            if (current != target && access.setCommand(entity, target)) {
                FindMeDebugLogger.info("iceandfire-command",
                        "entity={} previous={} applied={} reason=HOME/{}",
                        FindMeDebugLogger.entity(entity), current, target, mode);
            }
        });
    }

    public static void clearFixedPostCommand(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        Integer previous = PREVIOUS_COMMANDS.remove(entity.getUUID());
        if (previous == null || !isDragon(entity)) {
            return;
        }
        access(entity).ifPresent(access -> {
            Integer current = access.getCommand(entity);
            if (current != null && current != previous && access.setCommand(entity, previous)) {
                FindMeDebugLogger.info("iceandfire-command",
                        "entity={} restored={}", FindMeDebugLogger.entity(entity), previous);
            }
        });
    }

    public static void resetServerState() {
        PREVIOUS_COMMANDS.clear();
    }

    private static Optional<DragonAccess> access(LivingEntity entity) {
        return ACCESS.computeIfAbsent(entity.getClass(), IceAndFireRescueCompatibility::resolveAccess);
    }

    private static Optional<DragonAccess> resolveAccess(Class<?> type) {
        try {
            return Optional.of(new DragonAccess(type.getMethod("setFlying", boolean.class),
                    type.getMethod("setHovering", boolean.class),
                    type.getMethod("getCommand"), type.getMethod("setCommand", int.class)));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FindMeDebugLogger.info("iceandfire-rescue", "native dragon flight API unavailable class={} reason={}",
                    type.getName(), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static void invoke(LivingEntity entity, Optional<Method> method, boolean value) {
        method.ifPresent(candidate -> {
            try {
                candidate.invoke(entity, value);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FindMeDebugLogger.info("iceandfire-rescue", "native dragon state update failed entity={} method={} reason={}",
                        FindMeDebugLogger.entity(entity), candidate.getName(), exception.getClass().getSimpleName());
            }
        });
    }

    private record DragonAccess(Method setFlying, Method setHovering, Method getCommand, Method setCommand) {
        private Integer getCommand(LivingEntity entity) {
            try {
                return ((Number) getCommand.invoke(entity)).intValue();
            } catch (ReflectiveOperationException | ClassCastException exception) {
                FindMeDebugLogger.info("iceandfire-command", "command read failed entity={} reason={}",
                        FindMeDebugLogger.entity(entity), exception.getClass().getSimpleName());
                return null;
            }
        }

        private boolean setCommand(LivingEntity entity, int command) {
            try {
                setCommand.invoke(entity, command);
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FindMeDebugLogger.info("iceandfire-command", "command write failed entity={} command={} reason={}",
                        FindMeDebugLogger.entity(entity), command, exception.getClass().getSimpleName());
                return false;
            }
        }
    }
}
