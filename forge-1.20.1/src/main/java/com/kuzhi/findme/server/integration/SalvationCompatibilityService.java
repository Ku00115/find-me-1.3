package com.kuzhi.findme.server.integration;

import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** FindMe-owned adapter for Salvation's stable entity markers. */
public final class SalvationCompatibilityService {
    private static final String TAMED_KEY = "SalvationTamed";
    private static final String OWNER_KEY = "SalvationOwner";
    private static final String REDEEMED_KEY = "FindMeSalvationRedeemed";
    private static final String MOVEMENT_KEY = "FindMeSalvationMovement";
    private static final String GENERIC_CONTROL_KEY = "FindMeSalvationGenericControl";
    private static boolean registered;
    private static Object rideService;
    private static Method rideSupportsMethod;
    private static Method rideTryMountMethod;
    private static boolean rideFailureLogged;

    private SalvationCompatibilityService() {
    }

    public static void bootstrap() {
        if (registered) return;
        registered = true;
        FindMeApi.registerOwnershipProvider(SalvationCompatibilityService::ownerUuid);
        FindMeApi.registerMovementProfileProvider((type, entity) -> movement(entity));
        registerSalvationResolveListener();
        resolveRideApi();
    }

    private static void resolveRideApi() {
        if (!ModList.get().isLoaded("findme_salvation")) return;
        try {
            Class<?> apiType = Class.forName("com.kuzhi.findmesalvation.api.SalvationApi");
            rideService = apiType.getMethod("riding").invoke(null);
            rideSupportsMethod = rideService.getClass().getMethod("supports", LivingEntity.class);
            rideTryMountMethod = rideService.getClass().getMethod(
                    "tryMount", ServerPlayer.class, LivingEntity.class);
            FindMeMod.LOGGER.info("Enabled explicit FindMe Salvation riding integration");
        } catch (ReflectiveOperationException | LinkageError exception) {
            rideService = null;
            rideSupportsMethod = null;
            rideTryMountMethod = null;
            FindMeMod.LOGGER.info("FindMe Salvation riding API unavailable; using legacy mount interaction");
        }
    }

    public static RideStartResult tryStartRide(ServerPlayer player, LivingEntity mount) {
        if (player == null || mount == null || !mount.getPersistentData().getBoolean(TAMED_KEY)) {
            return RideStartResult.NOT_APPLICABLE;
        }
        if (rideService == null || rideSupportsMethod == null || rideTryMountMethod == null) {
            return RideStartResult.API_UNAVAILABLE;
        }
        try {
            if (!Boolean.TRUE.equals(rideSupportsMethod.invoke(rideService, mount))) {
                return RideStartResult.NOT_APPLICABLE;
            }
            return Boolean.TRUE.equals(rideTryMountMethod.invoke(rideService, player, mount))
                    ? RideStartResult.STARTED : RideStartResult.REJECTED;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!rideFailureLogged) {
                rideFailureLogged = true;
                FindMeMod.LOGGER.warn("FindMe Salvation riding API call failed", exception);
            }
            return RideStartResult.REJECTED;
        }
    }

    public enum RideStartResult {
        NOT_APPLICABLE,
        API_UNAVAILABLE,
        STARTED,
        REJECTED
    }

    private static void registerSalvationResolveListener() {
        if (!ModList.get().isLoaded("findme_salvation")) return;
        try {
            Class<? extends Event> eventType = Class.forName(
                    "com.kuzhi.findmesalvation.api.event.RitualResolveEvent$Post")
                    .asSubclass(Event.class);
            registerSalvationResolveListener(eventType);
            FindMeMod.LOGGER.info("Enabled FindMe Salvation event integration");
        } catch (ReflectiveOperationException | LinkageError exception) {
            FindMeMod.LOGGER.warn("FindMe Salvation event integration unavailable", exception);
        }
    }

    private static <T extends Event> void registerSalvationResolveListener(Class<T> eventType) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, eventType,
                SalvationCompatibilityService::onSalvationResolve);
    }

    private static void onSalvationResolve(Event event) {
        try {
            Object result = event.getClass().getMethod("result").invoke(event);
            if (!"SALVATION_APPLIED".equals(String.valueOf(result))) return;
            Object player = event.getClass().getMethod("player").invoke(event);
            Object target = event.getClass().getMethod("target").invoke(event);
            if (player instanceof ServerPlayer serverPlayer && target instanceof LivingEntity entity) {
                onSalvationSucceeded(serverPlayer, entity.getUUID());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FindMeMod.LOGGER.warn("FindMe Salvation event integration failed", exception);
        }
    }

    public static void onSalvationSucceeded(ServerPlayer player, UUID entityUuid) {
        if (player == null || entityUuid == null || player.getServer() == null) return;
        player.getServer().execute(() -> {
            if (player.isRemoved()) return;
            if (player.serverLevel().getEntity(entityUuid) instanceof LivingEntity entity) {
                bindAndStore(player, entity);
            }
        });
    }

    static Optional<UUID> ownerUuid(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(TAMED_KEY) || !data.hasUUID(OWNER_KEY)) return Optional.empty();
        return Optional.of(data.getUUID(OWNER_KEY));
    }

    static Optional<CompanionMoveType> movement(LivingEntity entity) {
        if (entity == null || !entity.getPersistentData().getBoolean(REDEEMED_KEY)) {
            return Optional.empty();
        }
        try {
            return Optional.of(CompanionMoveType.valueOf(entity.getPersistentData().getString(MOVEMENT_KEY)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static void bindAndStore(ServerPlayer player, LivingEntity entity) {
        Optional<UUID> owner = ownerUuid(entity);
        if (!entity.isAlive() || entity.isRemoved() || entity.level() != player.level()
                || !entity.getPersistentData().getBoolean(REDEEMED_KEY)
                || owner.filter(player.getUUID()::equals).isEmpty()) {
            FindMeDebugLogger.info("integration", "salvation handoff rejected player={} entity={} reason=invalid_state",
                    player.getUUID(), entity.getUUID());
            return;
        }
        CompanionKind kind = entity.getPersistentData().getBoolean(GENERIC_CONTROL_KEY)
                ? CompanionKind.MOUNT : CompanionKind.COMPANION;
        boolean stored = FindMeApi.bindAndStore(player, entity, kind);
        FindMeDebugLogger.info("integration", "salvation handoff player={} entity={} kind={} stored={}",
                player.getUUID(), entity.getUUID(), kind, stored);
    }
}
