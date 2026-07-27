package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.lifecycle.CompanionRetreatService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class VehicleCinematicService {
    private static final double VEHICLE_CINEMATIC_SPEED = 0.72;
    private static final int VEHICLE_STUCK_SPEED_BOOST_TICKS = 100;
    private static final double VEHICLE_STUCK_SPEED_MULTIPLIER = 3.0;
    private static final int VEHICLE_RETREAT_TICKS = 55;
    private static final double VEHICLE_RETREAT_SPEED = 0.55;
    private static final List<PendingVehicleCinematic> PENDING_VEHICLE_CINEMATICS = new ArrayList<PendingVehicleCinematic>();
    private static final List<PendingVehicleRetreat> PENDING_VEHICLE_RETREATS = new ArrayList<PendingVehicleRetreat>();

    private VehicleCinematicService() {
    }

    static void schedule(ServerPlayer player, Entity entity, Entity oldVehicle, UUID uuid, long now, boolean inCombat) {
        UUID playerUuid = player.getUUID();
        UUID oldUuid = oldVehicle == null || oldVehicle == entity ? null : oldVehicle.getUUID();
        PENDING_VEHICLE_CINEMATICS.removeIf(pending -> pending.playerUuid().equals(playerUuid) || pending.vehicleUuid().equals(uuid));
        PENDING_VEHICLE_RETREATS.removeIf(retreat -> retreat.entityUuid().equals(uuid));
        entity.noPhysics = true;
        PENDING_VEHICLE_CINEMATICS.add(new PendingVehicleCinematic(playerUuid, uuid, oldUuid, now, inCombat, entity.position()));
    }

    static void tick(MinecraftServer server) {
        tickVehicleApproaches(server);
        tickVehicleRetreats(server);
    }

    static int cancelForPlayer(ServerPlayer player, String reason) {
        if (player == null) {
            return 0;
        }
        UUID playerUuid = player.getUUID();
        int before = PENDING_VEHICLE_CINEMATICS.size() + PENDING_VEHICLE_RETREATS.size();
        PENDING_VEHICLE_CINEMATICS.removeIf(pending -> {
            if (!pending.playerUuid().equals(playerUuid)) {
                return false;
            }
            CompanionEntityLookup.findEntity(player.getServer(), pending.vehicleUuid())
                    .ifPresent(entity -> entity.noPhysics = false);
            return true;
        });
        PENDING_VEHICLE_RETREATS.removeIf(retreat -> {
            if (!retreat.playerUuid().equals(playerUuid)) {
                return false;
            }
            CompanionEntityLookup.findEntity(player.getServer(), retreat.entityUuid())
                    .ifPresent(entity -> entity.noPhysics = false);
            return true;
        });
        int removed = before - PENDING_VEHICLE_CINEMATICS.size() - PENDING_VEHICLE_RETREATS.size();
        if (removed > 0) {
            FindMeDebugLogger.info("transient", "cancelled vehicle cinematics player={} reason={} count={}",
                    playerUuid, reason, removed);
        }
        return removed;
    }

    static Vec3 cinematicStart(ServerPlayer player, Entity entity, boolean switching) {
        return cinematicStart(player, entity.getBbHeight(), switching);
    }

    static Vec3 cinematicStart(ServerPlayer player, double entityHeight, boolean switching) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        horizontal = horizontal.normalize();
        double distance = switching ? 9.0 : 11.0;
        double height = Math.max(1.2, Math.min(4.0, entityHeight * 0.45 + 1.0));
        return player.position().subtract(horizontal.scale(distance)).add(0.0, height, 0.0);
    }

    private static void tickVehicleApproaches(MinecraftServer server) {
        Iterator<PendingVehicleCinematic> iterator = PENDING_VEHICLE_CINEMATICS.iterator();
        while (iterator.hasNext()) {
            PendingVehicleCinematic pending = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid());
            if (player == null || player.isRemoved()) {
                CompanionEntityLookup.findEntity(server, pending.vehicleUuid()).ifPresent(entity -> entity.noPhysics = false);
                iterator.remove();
                continue;
            }
            PlayerCompanionData data = CompanionDataService.data(player);
            Entity entity = VehicleManager.locateEntity(server, data, pending.vehicleUuid()).orElse(null);
            if (entity == null || entity.isRemoved()) {
                data.clearDeployed(CompanionKind.MOUNT, pending.vehicleUuid());
                FindMeDebugLogger.lifecycle("VEHICLE_CINEMATIC_MISSING_KEEP_RECORD", player, pending.vehicleUuid(), null,
                        "DEPLOYED", "UNKNOWN", "vehicle:cinematic", data.storedEntity(pending.vehicleUuid()).isPresent(), false);
                CompanionDataService.save(player, data);
                CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
                iterator.remove();
                continue;
            }
            pending.incrementAge();
            entity.noPhysics = true;
            Vec3 target = vehicleCatchTarget(player, entity);
            Vec3 delta = target.subtract(entity.position());
            double distance = delta.length();
            double speed = vehicleCinematicSpeed(player, distance, pending.age());
            Vec3 step = delta.normalize().scale(Math.min(distance, speed));
            Vec3 previousPosition = entity.position();
            entity.setDeltaMovement(step);
            entity.move(MoverType.SELF, step);
            entity.setYRot(yawTo(step));
            entity.fallDistance = 0.0f;
            entity.hurtMarked = true;
            pending.setLastPosition(entity.position());
            if (isVehicleCatchContact(server, data, pending, player, entity, previousPosition) || pending.age() >= 100) {
                finishVehicleCinematic(iterator, pending, player, data, entity);
            }
        }
    }

    private static void finishVehicleCinematic(Iterator<PendingVehicleCinematic> iterator, PendingVehicleCinematic pending, ServerPlayer player, PlayerCompanionData data, Entity entity) {
        entity.noPhysics = false;
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0f;
        player.fallDistance = 0.0f;
        player.invulnerableTime = Math.max(player.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        player.startRiding(entity, true);
        data.setOrigin(pending.vehicleUuid(), SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
        data.setLastKnownPosition(pending.vehicleUuid(), SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
        data.setDeployed(CompanionKind.MOUNT, pending.vehicleUuid());
        data.markVehicleMount(pending.vehicleUuid());
        data.setReadyAt(CompanionKind.MOUNT, pending.now() + (long)Config.summonCooldownTicks);
        if (pending.oldVehicleUuid() != null && data.contains(CompanionKind.MOUNT, pending.oldVehicleUuid())) {
            data.rememberPrevious(CompanionKind.MOUNT, pending.oldVehicleUuid());
            VehicleManager.locateEntity(player.getServer(), data, pending.oldVehicleUuid()).ifPresent(old -> retreatOldVehicle(player, data, old));
            data.clearDeployed(CompanionKind.MOUNT, pending.oldVehicleUuid());
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        iterator.remove();
    }

    private static void retreatOldVehicle(ServerPlayer player, PlayerCompanionData data, Entity old) {
        if (old instanceof LivingEntity) {
            CompanionRetreatService.sendAway(old, player, data);
            return;
        }
        if (!VehicleManager.isVehicleEntry(data, old.getUUID())) {
            return;
        }
        if (player.getVehicle() == old) {
            player.stopRiding();
        }
        Vec3 direction = old.position().subtract(player.position());
        if (direction.horizontalDistanceSqr() < 0.001) {
            direction = player.getLookAngle().reverse();
        }
        direction = new Vec3(direction.x, 0.15, direction.z).normalize();
        old.noPhysics = true;
        PENDING_VEHICLE_RETREATS.removeIf(retreat -> retreat.entityUuid().equals(old.getUUID()));
        PENDING_VEHICLE_RETREATS.add(new PendingVehicleRetreat(old.getUUID(), player.getUUID(), direction));
    }

    private static void tickVehicleRetreats(MinecraftServer server) {
        Iterator<PendingVehicleRetreat> iterator = PENDING_VEHICLE_RETREATS.iterator();
        while (iterator.hasNext()) {
            PendingVehicleRetreat retreat = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(retreat.playerUuid());
            if (player == null) {
                iterator.remove();
                continue;
            }
            PlayerCompanionData data = CompanionDataService.data(player);
            Entity entity = VehicleManager.locateEntity(server, data, retreat.entityUuid()).orElse(null);
            if (entity == null || entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            retreat.incrementAge();
            Vec3 step = retreat.direction().scale(VEHICLE_RETREAT_SPEED);
            entity.noPhysics = true;
            entity.setDeltaMovement(step);
            entity.move(MoverType.SELF, step);
            entity.fallDistance = 0.0f;
            entity.hurtMarked = true;
            if (retreat.age() < VEHICLE_RETREAT_TICKS) {
                continue;
            }
            entity.noPhysics = false;
            VehicleManager.storeMountedEntity(player, data, entity);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            iterator.remove();
        }
    }

    private static Vec3 vehicleCatchTarget(ServerPlayer player, Entity entity) {
        double y = player.getY() + Math.min(1.0, Math.max(0.0, entity.getBbHeight() * 0.15));
        return new Vec3(player.getX(), y, player.getZ());
    }

    private static boolean isVehicleCatchContact(MinecraftServer server, PlayerCompanionData data, PendingVehicleCinematic pending, ServerPlayer player, Entity entity, Vec3 previousPosition) {
        Vec3 currentPosition = entity.position();
        if (pointEnteredBox(previousPosition, currentPosition, player.getBoundingBox())) {
            return true;
        }
        UUID oldUuid = pending.oldVehicleUuid();
        if (oldUuid == null) {
            return false;
        }
        Entity oldVehicle = VehicleManager.locateEntity(server, data, oldUuid).orElse(null);
        return oldVehicle != null && oldVehicle != entity && sweptPointHits(previousPosition, currentPosition, oldVehicle.getBoundingBox());
    }

    private static boolean pointEnteredBox(Vec3 from, Vec3 to, AABB targetBox) {
        return !targetBox.contains(from) && targetBox.contains(to);
    }

    private static boolean sweptPointHits(Vec3 from, Vec3 to, AABB targetBox) {
        return targetBox.contains(to) || targetBox.clip(from, to).isPresent();
    }

    private static double vehicleCinematicSpeed(ServerPlayer player, double distance, int age) {
        double riderSpeed = 0.0;
        Entity current = player.getVehicle();
        if (current != null) {
            riderSpeed = current.getDeltaMovement().horizontalDistance();
        }
        riderSpeed = Math.max(riderSpeed, player.getDeltaMovement().horizontalDistance());
        double speed = VEHICLE_CINEMATIC_SPEED + Math.min(0.6, riderSpeed * 0.8);
        if (age >= VEHICLE_STUCK_SPEED_BOOST_TICKS) {
            speed *= VEHICLE_STUCK_SPEED_MULTIPLIER;
        }
        return Math.min(distance, speed);
    }

    private static float yawTo(Vec3 step) {
        if (step.horizontalDistanceSqr() < 1.0E-4) {
            return 0.0f;
        }
        return (float)(Mth.atan2(step.z, step.x) * 57.2957763671875) - 90.0f;
    }

    private static final class PendingVehicleCinematic {
        private final UUID playerUuid;
        private final UUID vehicleUuid;
        private final UUID oldVehicleUuid;
        private final long now;
        private final boolean inCombat;
        private Vec3 lastPosition;
        private int age;

        private PendingVehicleCinematic(UUID playerUuid, UUID vehicleUuid, UUID oldVehicleUuid, long now, boolean inCombat, Vec3 lastPosition) {
            this.playerUuid = playerUuid;
            this.vehicleUuid = vehicleUuid;
            this.oldVehicleUuid = oldVehicleUuid;
            this.now = now;
            this.inCombat = inCombat;
            this.lastPosition = lastPosition;
        }

        private UUID playerUuid() {
            return this.playerUuid;
        }

        private UUID vehicleUuid() {
            return this.vehicleUuid;
        }

        private UUID oldVehicleUuid() {
            return this.oldVehicleUuid;
        }

        private long now() {
            return this.now;
        }

        private boolean inCombat() {
            return this.inCombat;
        }

        private int age() {
            return this.age;
        }

        private void incrementAge() {
            ++this.age;
        }

        private void setLastPosition(Vec3 lastPosition) {
            this.lastPosition = lastPosition;
        }
    }

    private static final class PendingVehicleRetreat {
        private final UUID entityUuid;
        private final UUID playerUuid;
        private final Vec3 direction;
        private int age;

        private PendingVehicleRetreat(UUID entityUuid, UUID playerUuid, Vec3 direction) {
            this.entityUuid = entityUuid;
            this.playerUuid = playerUuid;
            this.direction = direction;
        }

        private UUID entityUuid() {
            return this.entityUuid;
        }

        private UUID playerUuid() {
            return this.playerUuid;
        }

        private Vec3 direction() {
            return this.direction;
        }

        private int age() {
            return this.age;
        }

        private void incrementAge() {
            ++this.age;
        }
    }
}

