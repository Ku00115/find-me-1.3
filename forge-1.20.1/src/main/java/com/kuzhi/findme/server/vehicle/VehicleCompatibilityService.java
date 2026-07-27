package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
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
        if (tryForcedRide(player, target, "forced_initial")) {
            return true;
        }
        if (tryInteractionRide(player, target)) {
            return true;
        }
        if (tryForcedRide(player, target, "forced_after_interaction")) {
            return true;
        }
        FindMeDebugLogger.info("vehicle-handoff",
                "phase=BOARD_REJECTED player={} target={} targetType={} removed={} passengers={} previous={} current={}",
                player.getUUID(), target.getUUID(), target.getType(), target.isRemoved(), target.getPassengers().size(),
                previousRide == null ? "none" : previousRide.getUUID(),
                player.getVehicle() == null ? "none" : player.getVehicle().getUUID());
        restorePreviousRide(player, previousRide);
        return false;
    }

    private static boolean tryForcedRide(ServerPlayer player, Entity target, String attempt) {
        if (target.isRemoved()) {
            return false;
        }
        try {
            boolean accepted = player.startRiding(target, true);
            boolean riding = isRiding(player, target);
            FindMeDebugLogger.info("vehicle-handoff",
                    "phase=BOARD_ATTEMPT attempt={} player={} target={} targetType={} accepted={} riding={} passengers={}",
                    attempt, player.getUUID(), target.getUUID(), target.getType(), accepted, riding,
                    target.getPassengers().size());
            return accepted && riding;
        }
        catch (RuntimeException exception) {
            FindMeDebugLogger.info("vehicle-handoff",
                    "phase=BOARD_EXCEPTION attempt={} player={} target={} targetType={} exception={}",
                    attempt, player.getUUID(), target.getUUID(), target.getType(),
                    exception.getClass().getName());
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
        return tryForcedRide(player, previousRide, "restore_previous");
    }

    public static boolean isRiding(ServerPlayer player, Entity target) {
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
