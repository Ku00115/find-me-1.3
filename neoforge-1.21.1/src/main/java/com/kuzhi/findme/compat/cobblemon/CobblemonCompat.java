package com.kuzhi.findme.compat.cobblemon;

import com.kuzhi.findme.common.CobblemonCommandAction;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class CobblemonCompat {
    private static final boolean AVAILABLE = ModList.get().isLoaded("cobblemon");

    private CobblemonCompat() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    public static void handleCommand(ServerPlayer player, CobblemonCommandAction action, int slot, UUID targetUuid) {
        if (!AVAILABLE) {
            return;
        }
        if (action != CobblemonCommandAction.SYNC
                && (!FindMeModuleService.require(player, FindMeModule.RIDING)
                || !FindMeModuleService.require(player, FindMeModule.COBBLEMON_INTEGRATION))) {
            return;
        }
        CobblemonHooks.handleCommand(player, action, slot, targetUuid);
    }

    public static boolean handleRosterAction(ServerPlayer player, MountRosterAction action, int slot,
                                             UUID targetUuid) {
        return AVAILABLE && FindMeModuleService.enabled(FindMeModule.RIDING)
                && FindMeModuleService.enabled(FindMeModule.COBBLEMON_INTEGRATION)
                && CobblemonHooks.handleRosterAction(player, action, slot, targetUuid);
    }

    public static boolean contains(ServerPlayer player, int slot, UUID targetUuid) {
        return AVAILABLE && CobblemonHooks.contains(player, slot, targetUuid);
    }

    public static CompanionMoveType moveType(ServerPlayer player, int slot, UUID targetUuid) {
        return AVAILABLE ? CobblemonHooks.moveType(player, slot, targetUuid) : CompanionMoveType.WALK;
    }

    public static boolean isRideReady(ServerPlayer player, UUID targetUuid) {
        return AVAILABLE && CobblemonHooks.isRideReady(player, targetUuid);
    }

    public static boolean isDeployed(ServerPlayer player, UUID targetUuid) {
        return AVAILABLE && CobblemonHooks.isDeployed(player, targetUuid);
    }

    public static void cancelRosterActivation(ServerPlayer player, UUID targetUuid) {
        if (AVAILABLE) CobblemonHooks.cancelRosterActivation(player, targetUuid);
    }

    public static boolean isPokemon(Entity entity) {
        return AVAILABLE && CobblemonHooks.isPokemon(entity);
    }

    public static Entity createPreview(Level level, CompoundTag entityTag, UUID expectedPokemonId) {
        return AVAILABLE ? CobblemonPreviewFactory.create(level, entityTag, expectedPokemonId) : null;
    }

    public static boolean isPokemonPreview(Entity entity) {
        return AVAILABLE && CobblemonPreviewFactory.isPokemonPreview(entity);
    }

    public static void tickPreview(Entity entity, float partialTicks, boolean advanceAge) {
        if (AVAILABLE) {
            CobblemonPreviewFactory.tickPreview(entity, partialTicks, advanceAge);
        }
    }

    public static Vec3 rideVelocity(Entity entity) {
        return AVAILABLE ? CobblemonHooks.rideVelocity(entity) : null;
    }

    public static void prepareRideHandoff(Entity target, com.kuzhi.findme.server.lifecycle.RideHandoffService.MotionSnapshot snapshot) {
        if (AVAILABLE) {
            CobblemonHooks.prepareRideHandoff(target, snapshot);
        }
    }

    public static boolean tryRide(ServerPlayer player, LivingEntity entity) {
        return AVAILABLE && CobblemonHooks.tryRide(player, entity);
    }

    public static boolean recallIfPokemon(Entity entity) {
        return AVAILABLE && CobblemonHooks.recallIfPokemon(entity);
    }

    public static void clearRideHandoff(Entity entity) {
        if (AVAILABLE) {
            CobblemonHooks.clearRideHandoff(entity);
        }
    }

    public static CompanionMoveType moveType(Entity entity) {
        return AVAILABLE ? CobblemonHooks.moveType(entity) : CompanionMoveType.WALK;
    }

    public static void sync(ServerPlayer player) {
        if (AVAILABLE) {
            CobblemonHooks.syncParty(player);
        }
    }

    public static long syncRevision(ServerPlayer player) {
        return AVAILABLE ? CobblemonHooks.syncRevision(player) : 0L;
    }

    public static void recallDeployed(ServerPlayer player) {
        if (AVAILABLE) {
            CobblemonHooks.recallDeployed(player);
        }
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (AVAILABLE) {
            CobblemonHooks.forgetPlayer(player);
        }
    }

    public static int deployedCount(ServerPlayer player) {
        return AVAILABLE ? CobblemonHooks.deployedCount(player) : 0;
    }
}
