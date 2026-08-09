package com.kuzhi.findme.compat.waystones;

import com.mojang.datafixers.util.Either;
import com.kuzhi.findme.FindMeMod;
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
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
                WaystonesAPI.createDefaultTeleportContext(player, waystone, WarpMode.CUSTOM, null);
        created.ifRight(error -> failure.accept(errorComponent(error)));
        created.ifLeft(context -> {
            UUID mountUuid = mount != null && mount.isAlive() && player.getVehicle() == mount
                    ? mount.getUUID() : null;
            context.setPlaysEffect(false);
            FindMeMod.LOGGER.info("[FindMe waystones] api request player={} mount={} target={} targetDimension={} targetPos={} sourceDimension={} sourcePos={}",
                    player.getUUID(), mountUuid, waystone.getWaystoneUid(), waystone.getDimension().location(),
                    waystone.getPos(), player.level().dimension().location(), player.position());
            Either<List<Entity>, WaystoneTeleportError> result = WaystonesAPI.tryTeleport(context);
            result.ifLeft(entities -> {
                if (mountUuid == null) {
                    syncClientPosition(player);
                    FindMeMod.LOGGER.info("[FindMe waystones] api success player={} target={} playerDimension={} playerPos={} entities={}",
                            player.getUUID(), waystone.getWaystoneUid(), player.level().dimension().location(),
                            player.position(), entities.size());
                    success.accept(entities);
                    return;
                }
                LivingEntity teleportedMount = entities.stream()
                        .filter(entity -> mountUuid.equals(entity.getUUID()))
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast)
                        .findFirst()
                        .orElse(null);
                boolean alreadyMounted = teleportedMount != null && teleportedMount.isAlive()
                        && player.getVehicle() == teleportedMount
                        && teleportedMount.hasPassenger(player);
                boolean startedRiding = !alreadyMounted && teleportedMount != null && teleportedMount.isAlive()
                        && player.startRiding(teleportedMount, true);
                boolean remounted = alreadyMounted || startedRiding && player.getVehicle() == teleportedMount
                        && teleportedMount.hasPassenger(player);
                FindMeDebugLogger.info("waystones",
                        "teleport returned player={} mount={} entities={} alreadyMounted={} startedRiding={} remounted={} playerDimension={} playerPos={} mountDimension={} mountPos={}",
                        player.getUUID(), mountUuid, entities.size(), alreadyMounted, startedRiding, remounted,
                        player.level().dimension().location(),
                        player.position(),
                        teleportedMount == null ? "missing" : teleportedMount.level().dimension().location(),
                        teleportedMount == null ? "missing" : teleportedMount.position());
                if (!remounted) {
                    FindMeMod.LOGGER.warn("[FindMe waystones] api relationship failed player={} mount={} vehicle={} returnedMount={}",
                            player.getUUID(), mountUuid,
                            player.getVehicle() == null ? "none" : player.getVehicle().getUUID(),
                            teleportedMount == null ? "missing" : teleportedMount.getUUID());
                    failure.accept(Component.literal("Waystone moved the mount but did not restore its rider."));
                    return;
                }
                syncMountedState(player, teleportedMount);
                // The passenger position changes when the final rider relationship is established. Sync only
                // after that handshake, matching the accepted ride-home transfer sequence.
                syncClientPosition(player);
                success.accept(entities);
            });
            result.ifRight(error -> failure.accept(errorComponent(error)));
        });
    }

    static void syncMountedState(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null || player.getVehicle() != mount) {
            return;
        }
        player.connection.send(new ClientboundSetPassengersPacket(mount));
        player.connection.send(new ClientboundMoveVehiclePacket(mount));
    }

    private static void syncClientPosition(ServerPlayer player) {
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.onUpdateAbilities();
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.connection.resetPosition();
        FindMeMod.LOGGER.info("[FindMe waystones] client position sync player={} dimension={} pos={} yaw={} pitch={}",
                player.getUUID(), player.level().dimension().location(), player.position(), player.getYRot(), player.getXRot());
    }

    private static Component errorComponent(WaystoneTeleportError error) {
        String key = error == null ? null : error.getTranslationKey();
        return key == null || key.isBlank()
                ? Component.literal("Waystone teleport failed.")
                : Component.translatable(key);
    }
}
