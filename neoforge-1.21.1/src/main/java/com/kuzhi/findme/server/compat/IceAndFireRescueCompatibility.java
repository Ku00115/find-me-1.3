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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Optional rescue handoff for IAFEnvoy's Ice And Fire Community Edition on NeoForge 1.21.1. */
public final class IceAndFireRescueCompatibility {
    private static final String DRAGON_BASE_CLASS = "com.iafenvoy.iceandfire.entity.DragonBaseEntity";
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
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
            if (DRAGON_BASE_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
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
        access(entity).ifPresent(access -> {
            // IceAndFire-CE treats an idle rider as hovering. Its updateRider()
            // turns flying off when zza == 0, and a stale down/dismount bit makes
            // the next tick land the dragon immediately.
            access.setControlState(entity, access.getControlState(entity) & ~0x12);
            invoke(entity, Optional.of(access.setFlying()), false);
            invoke(entity, Optional.of(access.setHovering()), true);
            invoke(entity, Optional.of(access.setNoGravity()), true);
            invokeInt(entity, Optional.of(access.switchNavigator()), 2);
        });
    }

    /** Applies the server state and mirrors it to the CE riding client. */
    public static void finishMountedRide(ServerPlayer player, LivingEntity entity) {
        finishMountedRide(entity);
        syncMountedRide(player, entity);
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

    /** Mirrors CE's successful adult-dragon interaction on the riding client's side. */
    public static void syncMountedRide(ServerPlayer player, LivingEntity entity) {
        if (!isDragon(entity) || player == null || player.getVehicle() != entity) {
            return;
        }
        try {
            Class<?> payloadType = Class.forName(
                    "com.iafenvoy.iceandfire.network.payload.StartRidingMobPayload");
            Object payload = payloadType.getConstructor(int.class, boolean.class, boolean.class)
                    .newInstance(entity.getId(), true, false);
            if (payload instanceof CustomPacketPayload customPayload) {
                PacketDistributor.sendToPlayer(player, customPayload);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            FindMeDebugLogger.info("iceandfire-rescue",
                    "native ride sync unavailable entity={} reason={}",
                    FindMeDebugLogger.entity(entity), exception.getClass().getSimpleName());
        }
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

    public static void clearHomeCommand(LivingEntity entity) {
        clearFixedPostCommand(entity);
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
                    type.getMethod("getCommand"), type.getMethod("setCommand", int.class),
                    type.getMethod("getControlState"), type.getMethod("setControlState", byte.class),
                    type.getMethod("setNoGravity", boolean.class), type.getMethod("switchNavigator", int.class)));
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

    private static void invokeInt(LivingEntity entity, Optional<Method> method, int value) {
        method.ifPresent(candidate -> {
            try {
                candidate.invoke(entity, value);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FindMeDebugLogger.info("iceandfire-rescue", "native dragon int state update failed entity={} method={} reason={}",
                        FindMeDebugLogger.entity(entity), candidate.getName(), exception.getClass().getSimpleName());
            }
        });
    }

    private record DragonAccess(Method setFlying, Method setHovering, Method getCommand, Method setCommand,
                                Method getControlState, Method setControlState,
                                Method setNoGravity, Method switchNavigator) {
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

        private byte getControlState(LivingEntity entity) {
            try {
                return ((Number) getControlState.invoke(entity)).byteValue();
            } catch (ReflectiveOperationException | ClassCastException exception) {
                return 0;
            }
        }

        private void setControlState(LivingEntity entity, int state) {
            try {
                setControlState.invoke(entity, (byte) state);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FindMeDebugLogger.info("iceandfire-rescue", "control state write failed entity={} reason={}",
                        FindMeDebugLogger.entity(entity), exception.getClass().getSimpleName());
            }
        }
    }
}
