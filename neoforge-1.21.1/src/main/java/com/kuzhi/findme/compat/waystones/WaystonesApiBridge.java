package com.kuzhi.findme.compat.waystones;

import com.mojang.datafixers.util.Either;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class WaystonesApiBridge {
    private WaystonesApiBridge() {
    }

    static List<WaystoneDestination> destinations(ServerPlayer player) {
        return WaystonesAPI.getActivatedWaystones(player).stream()
                .filter(Waystone::isValid)
                .map(value -> new WaystoneDestination(value.getWaystoneUid(), value.getEffectiveName().getString(),
                        value.getDimension(), value.getPos()))
                .sorted(Comparator.comparing(WaystoneDestination::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static boolean isActivated(ServerPlayer player, UUID uuid) {
        Waystone waystone = WaystonesAPI.getWaystone(player.getServer(), uuid).orElse(null);
        return waystone != null && waystone.isValid() && WaystonesAPI.isWaystoneActivated(player, waystone);
    }

    static void teleport(ServerPlayer player, LivingEntity mount, UUID uuid,
                         Consumer<List<Entity>> success, Consumer<Component> failure) {
        Waystone waystone = WaystonesAPI.getWaystone(player.getServer(), uuid).orElse(null);
        if (waystone == null || !waystone.isValid() || !WaystonesAPI.isWaystoneActivated(player, waystone)) {
            failure.accept(Component.translatable("message.find_me.waystone_not_activated"));
            return;
        }
        Either<WaystoneTeleportContext, WaystoneTeleportError> created = WaystonesAPI.createDefaultTeleportContext(
                player, waystone, context -> {
                    // Waystones' batch restores passenger relationships after moving all attached entities.
                    if (mount != null && mount.isAlive() && player.getVehicle() == mount) {
                        context.addAdditionalEntity(mount);
                    }
                    context.setPlaysEffect(false);
                });
        created.ifRight(error -> failure.accept(error.getComponent()));
        created.ifLeft(context -> WaystonesAPI.tryTeleportAsync(context).whenComplete((result, throwable) ->
                player.getServer().execute(() -> {
                    if (throwable != null) {
                        failure.accept(Component.literal(throwable.getMessage() == null
                                ? "Waystone teleport failed." : throwable.getMessage()));
                    } else {
                        result.ifLeft(success);
                        result.ifRight(error -> failure.accept(error.getComponent()));
                    }
                })));
    }

    static void syncMountedState(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null || player.getVehicle() != mount) return;
        player.connection.send(new ClientboundSetPassengersPacket(mount));
        player.connection.send(new ClientboundMoveVehiclePacket(mount));
    }
}
