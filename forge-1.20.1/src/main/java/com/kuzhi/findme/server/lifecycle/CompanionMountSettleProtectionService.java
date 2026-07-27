package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;


import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CompanionMountSettleProtectionService {
    private static final Map<UUID, SettledMountProtection> RECENT_SETTLED_MOUNTS = new HashMap<>();
    private static final Map<UUID, AirHandoffTrace> AIR_HANDOFF_TRACES = new HashMap<>();
    private static final int MOUNT_SETTLE_PROTECTION_TICKS = 10;

    private CompanionMountSettleProtectionService() {
    }

    public static void rememberSettledMount(ServerPlayer player, LivingEntity mount) {
        rememberSettledMount(player, mount, false);
    }

    public static void rememberSettledMount(ServerPlayer player, LivingEntity mount, boolean maintainAirborne) {
        long until = player.serverLevel().getGameTime() + MOUNT_SETTLE_PROTECTION_TICKS;
        RECENT_SETTLED_MOUNTS.put(player.getUUID(),
                new SettledMountProtection(mount.getUUID(), until, maintainAirborne));
    }

    public static void rememberCompatibleHandoff(ServerPlayer player, LivingEntity mount,
                                                 PendingMountCinematic cinematic) {
        if (!cinematic.rideHandoff().compatibleWith(cinematic.moveType())) {
            return;
        }
        long startTick = player.serverLevel().getGameTime();
        AIR_HANDOFF_TRACES.put(player.getUUID(), new AirHandoffTrace(
                mount.getUUID(), startTick, cinematic.moveType(),
                cinematic.rideHandoff().velocityFor(cinematic.moveType()),
                cinematic.rideHandoff().yRot(), cinematic.rideHandoff().xRot()));
    }

    public static boolean isMountSettleProtected(ServerPlayer player) {
        SettledMountProtection protection = RECENT_SETTLED_MOUNTS.get(player.getUUID());
        if (protection == null) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        if (now > protection.untilTick()) {
            RECENT_SETTLED_MOUNTS.remove(player.getUUID());
            return false;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null || !vehicle.getUUID().equals(protection.mountUuid())) {
            return false;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        return data.deployed(CompanionKind.MOUNT).map(protection.mountUuid()::equals).orElse(false);
    }

    public static void tickSettledMountProtections(MinecraftServer server) {
        tickAirHandoffTraces(server);
        Iterator<Map.Entry<UUID, SettledMountProtection>> iterator = RECENT_SETTLED_MOUNTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SettledMountProtection> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            SettledMountProtection protection = entry.getValue();
            long now = player.serverLevel().getGameTime();
            if (now > protection.untilTick()) {
                iterator.remove();
                continue;
            }
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                if (vehicle.getUUID().equals(protection.mountUuid())) {
                    if (protection.maintainAirborne() && vehicle instanceof LivingEntity living) {
                        int age = MOUNT_SETTLE_PROTECTION_TICKS - (int)(protection.untilTick() - now);
                        if (age == 0 || age == 1 || age == 2 || age == 5 || age == 10) {
                            BookOfDragonsRescueCompatibility.logFlightState(living, age, true);
                        }
                        BookOfDragonsRescueCompatibility.forceAirborne(living);
                    }
                    player.fallDistance = 0.0f;
                    vehicle.fallDistance = 0.0f;
                    continue;
                }
                iterator.remove();
                continue;
            }
            if (player.onGround() || player.getDeltaMovement().y >= -0.08) {
                continue;
            }
            Entity mount = CompanionEntityLookup.findEntity(server, protection.mountUuid()).orElse(null);
            if (!(mount instanceof LivingEntity living) || !living.isAlive() || living.level() != player.level()) {
                iterator.remove();
                continue;
            }
            if ((!CompanionMountCinematicFlowService.isTouchingMountCollision(player, living, null) && player.distanceTo(living) > Math.max(4.0f, living.getBbWidth() + 2.0f)) || !CompanionMountCinematicFlowService.startRidingAfterContact(player, living, MountCinematicMode.NORMAL_SUMMON)) {
                continue;
            }
            player.fallDistance = 0.0f;
            living.fallDistance = 0.0f;
            player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
            living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        }
    }

    private static void tickAirHandoffTraces(MinecraftServer server) {
        Iterator<Map.Entry<UUID, AirHandoffTrace>> iterator = AIR_HANDOFF_TRACES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, AirHandoffTrace> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }
            AirHandoffTrace trace = entry.getValue();
            int age = (int)(player.serverLevel().getGameTime() - trace.startTick());
            Entity entity = CompanionEntityLookup.findEntity(server, trace.mountUuid()).orElse(null);
            if (!(entity instanceof LivingEntity mount) || !mount.isAlive() || age > 40) {
                iterator.remove();
                continue;
            }
            if (trace.moveType() == com.kuzhi.findme.common.CompanionMoveType.FLY
                    && age <= 2 && player.getVehicle() == mount) {
                CompanionMountSwitchService.applyAirToAirFlightState(mount, Vec3.ZERO, 0.0f, 0.0f, false);
            }
            if (age == 0 || age == 1 || age == 2 || age == 5 || age == 10 || age == 20 || age == 40) {
                FindMeDebugLogger.info("air-handoff",
                        "tick={} moveType={} player={} vehicle={} vehicleType={} target={} targetType={} riding={} passenger={} pos={} velocity={} inheritedVelocity={} yaw={} pitch={} onGround={} noGravity={} pose={}",
                        age, trace.moveType(), player.getUUID(), player.getVehicle() == null ? "null" : player.getVehicle().getUUID(),
                        player.getVehicle() == null ? "null" : EntityType.getKey(player.getVehicle().getType()),
                        mount.getUUID(), EntityType.getKey(mount.getType()), player.getVehicle() == mount,
                        mount.hasPassenger(player), mount.position(), mount.getDeltaMovement(), trace.velocity(),
                        mount.getYRot(), mount.getXRot(), mount.onGround(), mount.isNoGravity(), mount.getPose());
            }
            if (age >= 40) {
                iterator.remove();
            }
        }
    }

    private record AirHandoffTrace(UUID mountUuid, long startTick,
                                   com.kuzhi.findme.common.CompanionMoveType moveType,
                                   net.minecraft.world.phys.Vec3 velocity, float yRot, float xRot) {
    }
}

