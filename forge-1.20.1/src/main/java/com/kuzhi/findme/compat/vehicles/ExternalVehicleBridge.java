package com.kuzhi.findme.compat.vehicles;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.network.VehicleListPacket;
import com.kuzhi.findme.server.data.CompanionDataService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Reflection-only bridge. FindMe remains loadable when findme_vehicle is absent. */
public final class ExternalVehicleBridge {
    private static final String MOD_ID = "findme_vehicle";
    private static final String API = "com.kuzhi.findmevehicle.api.VehicleApi";
    private static volatile Boolean apiCompatible;

    private ExternalVehicleBridge() {
    }

    public static boolean available() {
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        Boolean cached = apiCompatible;
        if (cached != null) return cached;
        synchronized (ExternalVehicleBridge.class) {
            if (apiCompatible != null) return apiCompatible;
            try {
                Class<?> api = Class.forName(API);
                api.getMethod("command", ServerPlayer.class, String.class, UUID.class, int.class);
                api.getMethod("roster", ServerPlayer.class);
                apiCompatible = true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                apiCompatible = false;
                FindMeMod.LOGGER.warn("[FindMe vehicle] loaded module has no compatible VehicleApi; native vehicle handling will remain enabled", exception);
            }
            return apiCompatible;
        }
    }

    public static boolean command(ServerPlayer player, VehicleCommandAction action, UUID target, int position) {
        if (!available()) {
            return false;
        }
        try {
            Class<?> api = Class.forName(API);
            Method method = api.getMethod("command", ServerPlayer.class, String.class, UUID.class, int.class);
            return Boolean.TRUE.equals(method.invoke(null, player, action.name(), target, position));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disableApi("command", exception);
            return false;
        }
    }

    public static boolean sendRoster(ServerPlayer player) {
        if (!available()) {
            return false;
        }
        try {
            Class<?> api = Class.forName(API);
            Object roster = api.getMethod("roster", ServerPlayer.class).invoke(null, player);
            List<?> wheel = (List<?>) roster.getClass().getMethod("wheel").invoke(roster);
            List<?> all = (List<?>) roster.getClass().getMethod("all").invoke(roster);
            int activeIndex = (int) roster.getClass().getMethod("activeIndex").invoke(roster);
            com.kuzhi.findme.network.ModNetwork.sendToPlayer(player,
                    new VehicleListPacket(CompanionDataService.revision(player), activeIndex,
                            convert(wheel), convert(all)));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disableApi("roster", exception);
            return false;
        }
    }

    private static void disableApi(String operation, Throwable exception) {
        apiCompatible = false;
        FindMeMod.LOGGER.warn("[FindMe vehicle] VehicleApi failed during {}; native vehicle handling will remain enabled",
                operation, exception);
    }

    private static List<VehicleListPacket.Entry> convert(List<?> source) {
        return source.stream().map(snapshot -> {
            try {
                Class<?> type = snapshot.getClass();
                UUID uuid = (UUID) type.getMethod("uuid").invoke(snapshot);
                int entityId = (int) type.getMethod("entityId").invoke(snapshot);
                String entityType = (String) type.getMethod("entityType").invoke(snapshot);
                String name = (String) type.getMethod("name").invoke(snapshot);
                boolean loaded = (boolean) type.getMethod("loaded").invoke(snapshot);
                boolean ridden = (boolean) type.getMethod("ridden").invoke(snapshot);
                boolean alive = optionalBoolean(type, snapshot, "alive", loaded);
                boolean deployed = optionalBoolean(type, snapshot, "deployed", ridden);
                return new VehicleListPacket.Entry(uuid, entityId, entityType, name, loaded, alive,
                        deployed, ridden, CompanionAnimationStyle.STANDARD, CompanionAnimationStyle.STANDARD,
                        CompanionAnimationStyle.STANDARD, CompanionAnimationStyle.STANDARD,
                        CompanionEffectStyle.DEFAULT, CompanionEffectStyle.DEFAULT,
                        CompanionEffectStyle.DEFAULT, (CompoundTag) null);
            } catch (ReflectiveOperationException exception) {
                throw new BridgeConversionException(exception);
            }
        }).toList();
    }

    private static boolean optionalBoolean(Class<?> type, Object snapshot, String methodName, boolean fallback)
            throws ReflectiveOperationException {
        try {
            return (boolean) type.getMethod(methodName).invoke(snapshot);
        } catch (NoSuchMethodException ignored) {
            return fallback;
        }
    }

    private static final class BridgeConversionException extends RuntimeException {
        private BridgeConversionException(Throwable cause) {
            super(cause);
        }
    }
}
