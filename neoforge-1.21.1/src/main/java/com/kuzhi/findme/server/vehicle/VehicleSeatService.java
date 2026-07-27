package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.common.VehicleSeatOffset;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class VehicleSeatService {
    private static final String ANCHOR_TAG = "find_me_vehicle_seat_anchor";
    private static final Map<UUID, SeatSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, PendingSableTeleport> SABLE_TELEPORTS = new HashMap<>();

    private VehicleSeatService() {
    }

    public static boolean handleSeatCushionInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getItemStack().is(ModItems.VEHICLE_SEAT_CUSHION.get())) {
            return false;
        }
        Player player = event.getEntity();
        event.setCanceled(true);
        if (player.level().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        boolean bound = player instanceof ServerPlayer serverPlayer
                && bind(serverPlayer, event.getTarget(), event.getLocalPos());
        event.setCancellationResult(bound ? InteractionResult.CONSUME : InteractionResult.FAIL);
        return true;
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

    public static boolean bindSable(ServerPlayer player, SableVehicleCompatibility.Handle handle, BlockPos clickedPos) {
        UUID uuid = handle.uuid();
        PlayerCompanionData data = CompanionDataService.data(player);
        if (uuid == null || !data.containsVehicle(uuid)) {
            player.displayClientMessage(Component.translatable("message.find_me.vehicle_seat_not_bound").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        if (player.isShiftKeyDown()) {
            boolean removed = data.clearVehicleSeatOffset(uuid);
            CompanionDataService.save(player, data);
            player.displayClientMessage(Component.translatable(removed ? "message.find_me.vehicle_seat_cleared" : "message.find_me.vehicle_seat_missing").withStyle(ChatFormatting.AQUA), true);
            return true;
        }
        Vec3 plotFeet = new Vec3(clickedPos.getX() + 0.5, clickedPos.getY() + 1.01, clickedPos.getZ() + 0.5);
        Optional<Vec3> stableOffset = SableVehicleCompatibility.stableSeatOffset(handle, plotFeet);
        if (stableOffset.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.find_me.vehicle_switch_failed").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        Vec3 stable = stableOffset.get();
        VehicleSeatOffset offset = new VehicleSeatOffset(stable.x, stable.y, stable.z);
        data.setVehicleSeatOffset(uuid, offset);
        CompanionDataService.save(player, data);
        com.kuzhi.findme.FindMeMod.LOGGER.info(
                "FindMe Sable teleport point bound: player={}, sable={}, localBlock={}, stableOffset={}",
                player.getGameProfile().getName(), uuid, clickedPos, offset);
        player.displayClientMessage(Component.translatable("message.find_me.vehicle_seat_set", Component.literal(handle.name())).withStyle(ChatFormatting.AQUA), true);
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

    public static boolean teleportToSableSeat(ServerPlayer player, SableVehicleCompatibility.Handle handle, PlayerCompanionData data) {
        Optional<VehicleSeatOffset> configured = data.vehicleSeatOffset(handle.uuid());
        if (configured.isEmpty()) {
            return false;
        }
        cleanup(player);
        PendingSableTeleport pending = new PendingSableTeleport(player.getUUID(), handle.uuid(), configured.get(), 80, 20);
        SABLE_TELEPORTS.put(player.getUUID(), pending);
        tryPendingSableTeleport(player, handle, pending);
        return true;
    }

    public static boolean hasSeat(PlayerCompanionData data, UUID vehicleUuid) {
        return data.vehicleSeatOffset(vehicleUuid).isPresent();
    }

    public static boolean isSeatedOn(ServerPlayer player, UUID vehicleUuid) {
        if (player == null || vehicleUuid == null) {
            return false;
        }
        SeatSession session = SESSIONS.get(player.getUUID());
        return session != null && vehicleUuid.equals(session.vehicleUuid)
                && !session.anchor.isRemoved() && player.getVehicle() == session.anchor;
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
        tickSableTeleports(server);
    }

    public static void cleanup(ServerPlayer player) {
        SeatSession session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            session.anchor.discard();
        }
        SABLE_TELEPORTS.remove(player.getUUID());
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

    private static void tickSableTeleports(MinecraftServer server) {
        Iterator<PendingSableTeleport> iterator = SABLE_TELEPORTS.values().iterator();
        while (iterator.hasNext()) {
            PendingSableTeleport pending = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
            if (player == null) {
                iterator.remove();
                continue;
            }
            SableVehicleCompatibility.Handle handle = SableVehicleCompatibility.find(player, pending.vehicleUuid).orElse(null);
            if (handle == null) {
                iterator.remove();
                continue;
            }
            if (tryPendingSableTeleport(player, handle, pending)) {
                pending.successfulCorrections++;
                if (SableVehicleCompatibility.isPlayerTracking(player, handle)) {
                    pending.consecutiveTrackedTicks++;
                } else {
                    pending.consecutiveTrackedTicks = 0;
                }
                if (pending.consecutiveTrackedTicks >= 3 || pending.successfulCorrections >= pending.maxCorrections) {
                    com.kuzhi.findme.FindMeMod.LOGGER.info(
                            "FindMe Sable moving-seat handoff complete: player={}, sable={}, corrections={}, trackedTicks={}",
                            player.getGameProfile().getName(), pending.vehicleUuid,
                            pending.successfulCorrections, pending.consecutiveTrackedTicks);
                    iterator.remove();
                }
            } else if (--pending.attemptsRemaining <= 0) {
                com.kuzhi.findme.FindMeMod.LOGGER.warn(
                        "FindMe canceled unsafe Sable fixed-point teleport: player={}, sable={}, localFeet={}",
                        player.getGameProfile().getName(), pending.vehicleUuid, pending.localFeet);
                iterator.remove();
            }
        }
    }

    private static boolean tryPendingSableTeleport(ServerPlayer player, SableVehicleCompatibility.Handle handle, PendingSableTeleport pending) {
        VehicleSeatOffset localFeet = pending.localFeet;
        Vec3 saved = new Vec3(localFeet.x(), localFeet.y(), localFeet.z());
        boolean legacyAbsolutePlotPoint = Math.abs(saved.x) > 1_000_000.0 || Math.abs(saved.z) > 1_000_000.0;
        Vec3 world = legacyAbsolutePlotPoint
                ? SableVehicleCompatibility.projectOut(player.serverLevel(), saved)
                : SableVehicleCompatibility.projectStableSeat(player.serverLevel(), handle, saved).orElse(null);
        if (!isSafeProjectedPoint(handle, world)) {
            return false;
        }
        player.stopRiding();
        player.teleportTo(world.x, world.y, world.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        SableVehicleCompatibility.attachPlayerToProjectedPoint(player, handle, world);
        if (!pending.teleported) {
            pending.teleported = true;
            com.kuzhi.findme.FindMeMod.LOGGER.info(
                    "FindMe Sable fixed-point teleport: player={}, sable={}, stableOffset={}, legacy={}, worldFeet={}",
                    player.getGameProfile().getName(), handle.uuid(), localFeet, legacyAbsolutePlotPoint, world);
        }
        return true;
    }

    private static boolean isSafeProjectedPoint(SableVehicleCompatibility.Handle handle, Vec3 world) {
        return world != null
                && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)
                && handle.box().inflate(4.0, 3.0, 4.0).contains(world);
    }

    private static ArmorStand createAnchor(ServerPlayer player) {
        ArmorStand anchor = new TransientSeatAnchor(player.serverLevel());
        anchor.setInvisible(true);
        anchor.setNoGravity(true);
        anchor.setInvulnerable(true);
        anchor.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.VEHICLE_SEAT_CUSHION.get()));
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

    private static final class PendingSableTeleport {
        private final UUID playerUuid;
        private final UUID vehicleUuid;
        private final VehicleSeatOffset localFeet;
        private int attemptsRemaining;
        private final int maxCorrections;
        private int successfulCorrections;
        private int consecutiveTrackedTicks;
        private boolean teleported;

        private PendingSableTeleport(UUID playerUuid, UUID vehicleUuid, VehicleSeatOffset localFeet, int attemptsRemaining, int maxCorrections) {
            this.playerUuid = playerUuid;
            this.vehicleUuid = vehicleUuid;
            this.localFeet = localFeet;
            this.attemptsRemaining = attemptsRemaining;
            this.maxCorrections = maxCorrections;
        }
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

