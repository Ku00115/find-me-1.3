package com.kuzhi.findme.server.home;

import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.HouseResidentMode;
import com.kuzhi.findme.server.compat.CompanionFixedPostService;
import com.kuzhi.findme.server.compat.IceAndFireRescueCompatibility;
import com.kuzhi.findme.server.lifecycle.CompanionGuardPostService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;

final class CompanionHomeBehaviorService {
    static final String HOME_RESIDENT_TAG = "FindMeHomeResident";
    private static final String HOME_CENTER_X_TAG = "FindMeHomeCenterX";
    private static final String HOME_CENTER_Y_TAG = "FindMeHomeCenterY";
    private static final String HOME_CENTER_Z_TAG = "FindMeHomeCenterZ";
    private static final String HOME_MOVE_TYPE_TAG = "FindMeHomeMoveType";
    private static final String HOME_AIRBORNE_TAG = "FindMeHomeAirborne";
    private static final String HOME_MODE_TAG = "FindMeHomeMode";
    private static final Map<UUID, Boolean> PREVIOUS_ORDERED_SIT = new HashMap<>();

    private CompanionHomeBehaviorService() {
    }

    static void applyResidentBehavior(LivingEntity living) {
        applyResidentBehavior(living, residentMode(living));
    }

    static void applyResidentBehavior(LivingEntity living, HouseResidentMode mode) {
        HouseResidentMode residentMode = mode == null ? HouseResidentMode.WANDER : mode;
        living.getPersistentData().putBoolean(HOME_RESIDENT_TAG, true);
        living.getPersistentData().putString(HOME_MODE_TAG, residentMode.name());
        setTemporarySit(living, residentMode == HouseResidentMode.REST);
        living.fallDistance = 0.0f;
        if (living instanceof Mob mob) {
            CompanionFixedPostService.acquire(living, CompanionFixedPostService.Reason.HOME);
            IceAndFireRescueCompatibility.applyHomeCommand(living, residentMode);
            CompanionGuardPostService.acquireHome(mob, homeCenter(living), homeMoveType(living),
                    living.getPersistentData().getBoolean(HOME_AIRBORNE_TAG), residentMode);
        }
    }

    static void applyResidentBehavior(LivingEntity living, BlockPos center, CompanionMoveType moveType,
                                      boolean airborne) {
        configureResident(living, center, moveType, airborne);
        applyResidentBehavior(living);
    }

    static void applyResidentBehavior(LivingEntity living, BlockPos center, CompanionMoveType moveType,
                                      boolean airborne, HouseResidentMode mode) {
        configureResident(living, center, moveType, airborne, mode);
        applyResidentBehavior(living, mode);
    }

    static void configureResident(LivingEntity living, BlockPos center, CompanionMoveType moveType,
                                  boolean airborne) {
        configureResident(living, center, moveType, airborne, HouseResidentMode.WANDER);
    }

    static void configureResident(LivingEntity living, BlockPos center, CompanionMoveType moveType,
                                  boolean airborne, HouseResidentMode mode) {
        if (living == null || center == null || moveType == null) {
            return;
        }
        HouseResidentMode residentMode = mode == null ? HouseResidentMode.WANDER : mode;
        living.getPersistentData().putInt(HOME_CENTER_X_TAG, center.getX());
        living.getPersistentData().putInt(HOME_CENTER_Y_TAG, center.getY());
        living.getPersistentData().putInt(HOME_CENTER_Z_TAG, center.getZ());
        living.getPersistentData().putString(HOME_MOVE_TYPE_TAG, moveType.name());
        living.getPersistentData().putBoolean(HOME_AIRBORNE_TAG, airborne);
        living.getPersistentData().putString(HOME_MODE_TAG, residentMode.name());
    }

    static boolean isAirborneResident(LivingEntity living) {
        return living != null && living.getPersistentData().getBoolean(HOME_AIRBORNE_TAG);
    }

    static void clearResidentBehavior(LivingEntity living) {
        living.getPersistentData().remove(HOME_RESIDENT_TAG);
        living.getPersistentData().remove(HOME_MODE_TAG);
        CompanionGuardPostService.releaseHome(living.getUUID(), "home_released");
        CompanionFixedPostService.release(living, CompanionFixedPostService.Reason.HOME);
        IceAndFireRescueCompatibility.clearHomeCommand(living);
        restoreTemporarySit(living);
    }

    static void resetServerState() {
        PREVIOUS_ORDERED_SIT.clear();
        IceAndFireRescueCompatibility.resetServerState();
    }

    private static BlockPos homeCenter(LivingEntity living) {
        if (!living.getPersistentData().contains(HOME_CENTER_X_TAG)) {
            return living.blockPosition();
        }
        return new BlockPos(living.getPersistentData().getInt(HOME_CENTER_X_TAG),
                living.getPersistentData().getInt(HOME_CENTER_Y_TAG),
                living.getPersistentData().getInt(HOME_CENTER_Z_TAG));
    }

    private static CompanionMoveType homeMoveType(LivingEntity living) {
        String value = living.getPersistentData().getString(HOME_MOVE_TYPE_TAG);
        try {
            return CompanionMoveType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return living.getPersistentData().getBoolean(HOME_AIRBORNE_TAG)
                    ? CompanionMoveType.FLY : CompanionMoveType.WALK;
        }
    }

    private static HouseResidentMode residentMode(LivingEntity living) {
        return living == null
                ? HouseResidentMode.WANDER
                : HouseResidentMode.parse(living.getPersistentData().getString(HOME_MODE_TAG));
    }

    private static void setTemporarySit(LivingEntity living, boolean sitting) {
        if (!(living instanceof TamableAnimal tamable)) {
            return;
        }
        UUID uuid = tamable.getUUID();
        PREVIOUS_ORDERED_SIT.putIfAbsent(uuid, tamable.isOrderedToSit());
        tamable.setOrderedToSit(sitting);
        tamable.setInSittingPose(sitting);
    }

    private static void restoreTemporarySit(LivingEntity living) {
        if (!(living instanceof TamableAnimal tamable)) {
            return;
        }
        Boolean previous = PREVIOUS_ORDERED_SIT.remove(tamable.getUUID());
        if (previous == null) {
            return;
        }
        tamable.setOrderedToSit(previous);
        tamable.setInSittingPose(previous);
    }
}
