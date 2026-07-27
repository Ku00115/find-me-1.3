package com.kuzhi.findme.compat.waystones;

import com.mojang.datafixers.util.Either;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.IWaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystoneTeleportError;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WarpMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class WaystonesApiBridge {
    private WaystonesApiBridge() {
    }

    static List<WaystoneDestination> destinations(ServerPlayer player) {
        return WaystonesAPI.getActivatedWaystones(player).stream()
                .filter(IWaystone::isValid)
                .map(value -> new WaystoneDestination(value.getWaystoneUid(), value.getName(),
                        value.getDimension(), value.getPos()))
                .sorted(Comparator.comparing(WaystoneDestination::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static boolean isActivated(ServerPlayer player, UUID uuid) {
        IWaystone waystone = WaystonesAPI.getWaystone(player.getServer(), uuid).orElse(null);
        return waystone != null && waystone.isValid() && WaystonesAPI.isWaystoneActivated(player, waystone);
    }

    static void teleport(ServerPlayer player, LivingEntity mount, UUID uuid,
                         Consumer<List<Entity>> success, Consumer<Component> failure) {
        IWaystone waystone = WaystonesAPI.getWaystone(player.getServer(), uuid).orElse(null);
        if (waystone == null || !waystone.isValid() || !WaystonesAPI.isWaystoneActivated(player, waystone)) {
            failure.accept(Component.translatable("message.find_me.waystone_not_activated"));
            return;
        }
        Either<IWaystoneTeleportContext, WaystoneTeleportError> created =
                WaystonesAPI.createDefaultTeleportContext(player, waystone, WarpMode.INVENTORY_BUTTON, null);
        created.ifRight(error -> failure.accept(errorComponent(error)));
        created.ifLeft(context -> {
            UUID mountUuid = mount != null && mount.isAlive() && player.getVehicle() == mount
                    ? mount.getUUID() : null;
            context.setPlaysEffect(false);
            Either<List<Entity>, WaystoneTeleportError> result = WaystonesAPI.tryTeleport(context);
            result.ifLeft(entities -> {
                if (mountUuid == null) {
                    success.accept(entities);
                    return;
                }
                LivingEntity teleportedMount = entities.stream()
                        .filter(entity -> mountUuid.equals(entity.getUUID()))
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast)
                        .findFirst()
                        .orElse(null);
                boolean remounted = teleportedMount != null && teleportedMount.isAlive()
                        && player.startRiding(teleportedMount, true)
                        && player.getVehicle() == teleportedMount
                        && teleportedMount.hasPassenger(player);
                FindMeDebugLogger.info("waystones",
                        "teleport returned player={} mount={} entities={} remounted={} playerDimension={} mountDimension={}",
                        player.getUUID(), mountUuid, entities.size(), remounted,
                        player.level().dimension().location(),
                        teleportedMount == null ? "missing" : teleportedMount.level().dimension().location());
                if (!remounted) {
                    failure.accept(Component.literal("Waystone moved the mount but did not restore its rider."));
                    return;
                }
                success.accept(entities);
            });
            result.ifRight(error -> failure.accept(errorComponent(error)));
        });
    }

    private static Component errorComponent(WaystoneTeleportError error) {
        String key = error == null ? null : error.getTranslationKey();
        return key == null || key.isBlank()
                ? Component.literal("Waystone teleport failed.")
                : Component.translatable(key);
    }
}
