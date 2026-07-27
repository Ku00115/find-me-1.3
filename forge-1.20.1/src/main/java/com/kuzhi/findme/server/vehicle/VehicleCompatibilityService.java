package com.kuzhi.findme.server.vehicle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class VehicleCompatibilityService {
    private VehicleCompatibilityService() {
    }

    public static boolean tryBoardVehicle(ServerPlayer player, Entity target, Entity previousRide) {
        if (target == null || target.isRemoved()) {
            restorePreviousRide(player, previousRide);
            return false;
        }
        if (isRiding(player, target)) {
            return true;
        }
        if (tryForcedRide(player, target)) {
            return true;
        }
        if (tryInteractionRide(player, target)) {
            return true;
        }
        if (tryForcedRide(player, target)) {
            return true;
        }
        restorePreviousRide(player, previousRide);
        return false;
    }

    private static boolean tryForcedRide(ServerPlayer player, Entity target) {
        if (target.isRemoved()) {
            return false;
        }
        try {
            return player.startRiding(target, true) && isRiding(player, target);
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean tryInteractionRide(ServerPlayer player, Entity target) {
        if (target.isRemoved()) {
            return false;
        }
        try {
            target.interact(player, InteractionHand.MAIN_HAND);
            return isRiding(player, target);
        }
        catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean restorePreviousRide(ServerPlayer player, Entity previousRide) {
        if (previousRide == null || previousRide.isRemoved() || isRiding(player, previousRide)) {
            return previousRide != null && !previousRide.isRemoved();
        }
        if (MachineMaxVehicleCompatibility.findForEntity(previousRide)
                .map(handle -> MachineMaxVehicleCompatibility.tryBoard(player, handle))
                .orElse(false)) {
            return true;
        }
        return tryForcedRide(player, previousRide);
    }

    private static boolean isRiding(ServerPlayer player, Entity target) {
        Entity vehicle = player.getVehicle();
        while (vehicle != null) {
            if (vehicle == target || vehicle.getUUID().equals(target.getUUID())) {
                return true;
            }
            vehicle = vehicle.getVehicle();
        }
        return false;
    }
}
