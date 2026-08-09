package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.common.VehicleSeatOffset;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

public final class VehicleSeatService {
    private static final String ANCHOR_TAG = "find_me_vehicle_seat_anchor";
    private static final Map<UUID, SeatSession> SESSIONS = new HashMap<>();

    private VehicleSeatService() {
    }

    public static void handleEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && discardOrphan(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    static boolean bind(ServerPlayer player, Entity vehicle, Vec3 localHit) {
        Entity clickedEntity = vehicle;
        vehicle = VehicleManager.unwrapPart(vehicle);
        if (vehicle != clickedEntity) {
            localHit = clickedEntity.position().add(localHit).subtract(vehicle.position());
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!data.containsVehicle(vehicle.getUUID())) {
            player.displayClientMessage(Component.translatable("message.find_me.vehicle_seat_not_bound").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            boolean removed = data.clearVehicleSeatOffset(vehicle.getUUID());
            CompanionDataService.save(player, data);
            player.displayClientMessage(Component.translatable(removed ? "message.find_me.vehicle_seat_cleared" : "message.find_me.vehicle_seat_missing").withStyle(ChatFormatting.AQUA), true);
            return true;
        }

        Vec3 worldOffset = localHit;
        double radians = Math.toRadians(vehicle.getYRot());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double localX = worldOffset.x * cos + worldOffset.z * sin;
        double localZ = -worldOffset.x * sin + worldOffset.z * cos;
        double limit = Math.max(2.0, Math.max(vehicle.getBbWidth(), vehicle.getBbHeight()) * 2.0 + 2.0);
        VehicleSeatOffset offset = new VehicleSeatOffset(
                Mth.clamp(localX, -limit, limit),
                Mth.clamp(worldOffset.y, -1.0, vehicle.getBbHeight() + 2.0),
                Mth.clamp(localZ, -limit, limit));
        data.setVehicleSeatOffset(vehicle.getUUID(), offset);
        CompanionDataService.save(player, data);
        player.displayClientMessage(Component.translatable("message.find_me.vehicle_seat_set", vehicle.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        Entity currentRide = resolveCurrentRide(player);
        if (currentRide == null || currentRide.getUUID().equals(vehicle.getUUID())) {
            trySeat(player, vehicle, null, data);
        }
        return true;
    }

    public static Entity resolveCurrentRide(ServerPlayer player) {
        SeatSession session = SESSIONS.get(player.getUUID());
        if (session == null || player.getVehicle() != session.anchor) {
            return player.getVehicle();
        }
        return CompanionEntityLookup.findEntity(player.getServer(), session.vehicleUuid).orElse(player.getVehicle());
    }

    public static boolean trySeat(ServerPlayer player, Entity target, Entity previousRide, PlayerCompanionData data) {
        Optional<VehicleSeatOffset> configured = data.vehicleSeatOffset(target.getUUID());
        if (configured.isEmpty()) {
            return false;
        }
        SeatSession previousSession = SESSIONS.get(player.getUUID());
        ArmorStand anchor = createAnchor(player);
        anchor.addTag(ANCHOR_TAG);
        moveAnchor(anchor, target, configured.get());
        if (!player.serverLevel().addFreshEntity(anchor)) {
            return false;
        }
        if (!player.startRiding(anchor, true) || player.getVehicle() != anchor) {
            anchor.discard();
            restorePreviousSeat(player, previousSession, previousRide);
            return false;
        }
        SESSIONS.put(player.getUUID(), new SeatSession(player.getUUID(), target.getUUID(), anchor, configured.get()));
        if (previousSession != null && previousSession.anchor != anchor) {
            previousSession.anchor.discard();
        }
        return true;
    }

    public static boolean hasSeat(PlayerCompanionData data, UUID vehicleUuid) {
        return data.vehicleSeatOffset(vehicleUuid).isPresent();
    }

    public static void tick(MinecraftServer server) {
        Iterator<SeatSession> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            SeatSession session = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerUuid);
            Entity vehicle = CompanionEntityLookup.findEntity(server, session.vehicleUuid).orElse(null);
            if (player == null || vehicle == null || vehicle.isRemoved() || session.anchor.isRemoved()
                    || player.getVehicle() != session.anchor || player.level() != vehicle.level()) {
                session.anchor.discard();
                iterator.remove();
                continue;
            }
            moveAnchor(session.anchor, vehicle, session.offset);
            session.anchor.setYRot(vehicle.getYRot());
        }
    }

    public static void cleanup(ServerPlayer player) {
        SeatSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            session.anchor.discard();
        }
    }

    public static void cleanupIfSessionFor(ServerPlayer player, UUID vehicleUuid) {
        if (player == null || vehicleUuid == null) {
            return;
        }
        SeatSession session = SESSIONS.get(player.getUUID());
        if (session != null && vehicleUuid.equals(session.vehicleUuid)) {
            SESSIONS.remove(player.getUUID(), session);
            session.anchor.discard();
        }
    }

    static boolean discardOrphan(Entity entity) {
        if (isSeatAnchor(entity)) {
            entity.discard();
            return true;
        }
        return false;
    }

    public static boolean isSeatAnchor(Entity entity) {
        return entity != null && entity.getTags().contains(ANCHOR_TAG);
    }

    private static void moveAnchor(Entity anchor, Entity vehicle, VehicleSeatOffset offset) {
        double radians = Math.toRadians(vehicle.getYRot());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = offset.x() * cos - offset.z() * sin;
        double z = offset.x() * sin + offset.z() * cos;
        anchor.setPos(vehicle.getX() + x, vehicle.getY() + offset.y(), vehicle.getZ() + z);
    }

    private static ArmorStand createAnchor(ServerPlayer player) {
        ArmorStand anchor = new TransientSeatAnchor(player.serverLevel());
        anchor.setInvisible(true);
        anchor.setNoGravity(true);
        anchor.setInvulnerable(true);
        anchor.addTag(ANCHOR_TAG);
        return anchor;
    }

    private static void restorePreviousSeat(ServerPlayer player, SeatSession previousSession, Entity previousRide) {
        if (previousSession != null && !previousSession.anchor.isRemoved()) {
            player.startRiding(previousSession.anchor, true);
        } else if (previousRide != null && !previousRide.isRemoved()) {
            player.startRiding(previousRide, true);
        }
    }

    private record SeatSession(UUID playerUuid, UUID vehicleUuid, ArmorStand anchor, VehicleSeatOffset offset) {
    }

    private static final class TransientSeatAnchor extends ArmorStand {
        private TransientSeatAnchor(Level level) {
            super(EntityType.ARMOR_STAND, level);
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }
    }
}

