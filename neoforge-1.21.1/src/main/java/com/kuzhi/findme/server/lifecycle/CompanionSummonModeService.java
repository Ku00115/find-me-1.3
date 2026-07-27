package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class CompanionSummonModeService {
    private CompanionSummonModeService() {
    }

    public static MountCinematicMode modeFor(CompanionKind kind, CompanionMoveType moveType, boolean fallingRescue, boolean switchingMount, boolean transitionFromFlyingMount) {
        if (kind != CompanionKind.MOUNT) {
            return MountCinematicMode.NORMAL_SUMMON;
        }
        CompanionMoveType cinematicMoveType = landPresentationMoveType(moveType);
        if (transitionFromFlyingMount) {
            return switch (cinematicMoveType) {
                case FLY -> MountCinematicMode.AIR_TO_FLY_SWITCH;
                case WALK, COMPANION -> MountCinematicMode.AIR_TO_WALK_SWITCH;
                case SWIM -> MountCinematicMode.AIR_TO_SWIM_SWITCH;
            };
        }
        if (fallingRescue) {
            return switch (cinematicMoveType) {
                case FLY -> MountCinematicMode.FALL_RESCUE_FLY;
                case WALK, COMPANION -> MountCinematicMode.FALL_RESCUE_WALK;
                case SWIM -> MountCinematicMode.FALL_RESCUE_SWIM;
            };
        }
        if (switchingMount) {
            return MountCinematicMode.MOUNT_SWITCH;
        }
        return MountCinematicMode.NORMAL_SUMMON;
    }

    public static CompanionMoveType landPresentationMoveType(CompanionMoveType moveType) {
        return moveType;
    }

    public static boolean isAirborneGroundRescue(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
        return kind == CompanionKind.MOUNT && player.getVehicle() == null && isFallingFar(player) && isGroundOrWater(moveType);
    }

    public static boolean isAirborneGroundVehicleRescue(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, CompanionMoveType moveType) {
        Entity vehicle = player.getVehicle();
        return kind == CompanionKind.MOUNT
                && vehicle != null
                && !isRegisteredBiologicalMount(data, vehicle)
                && isFallingFar(player)
                && isGroundOrWater(moveType);
    }

    public static boolean isRegisteredBiologicalMount(PlayerCompanionData data, Entity entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity && data.contains(CompanionKind.MOUNT, entity.getUUID());
    }

    public static boolean isAirborneGroundMountSwitch(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
        Entity vehicle = player.getVehicle();
        return kind == CompanionKind.MOUNT && vehicle != null && isFallingFar(player) && CompanionEntityClassifier.moveType(vehicle, CompanionKind.MOUNT) == CompanionMoveType.FLY && isGroundOrWater(moveType);
    }

    public static boolean isAirborneGroundCompanionSummon(ServerPlayer player, CompanionKind kind, CompanionMoveType moveType) {
        Entity vehicle = player.getVehicle();
        return kind == CompanionKind.COMPANION && vehicle != null && isFallingFar(player) && CompanionEntityClassifier.moveType(vehicle, CompanionKind.MOUNT) == CompanionMoveType.FLY && isGroundOrWater(moveType);
    }

    private static boolean isFallingFar(ServerPlayer player) {
        return !player.onGround() && CompanionCinematicLandingService.distanceToGround(player) > 6.0;
    }

    private static boolean isGroundOrWater(CompanionMoveType moveType) {
        return moveType == CompanionMoveType.WALK || moveType == CompanionMoveType.SWIM;
    }
}

