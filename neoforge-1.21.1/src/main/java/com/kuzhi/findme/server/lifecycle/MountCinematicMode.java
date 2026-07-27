package com.kuzhi.findme.server.lifecycle;

public enum MountCinematicMode {
    NORMAL_SUMMON,
    RIDE_HOME,
    MOUNT_SWITCH,
    FALL_RESCUE_FLY,
    FALL_RESCUE_WALK,
    FALL_RESCUE_SWIM,
    AIR_TO_FLY_SWITCH,
    AIR_TO_WALK_SWITCH,
    AIR_TO_SWIM_SWITCH;

    public boolean isRescue() {
        return this == FALL_RESCUE_FLY || this == FALL_RESCUE_WALK || this == FALL_RESCUE_SWIM
                || this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
    }

    public boolean isFlyingRescue() {
        return this == FALL_RESCUE_FLY;
    }

    public boolean isGroundOrWaterRescue() {
        return this == FALL_RESCUE_WALK || this == FALL_RESCUE_SWIM || this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
    }

    public boolean isAirToGroundSwitch() {
        return this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
    }

    public boolean isAirToAirSwitch() {
        return this == AIR_TO_FLY_SWITCH;
    }

    public boolean isMountSwitch() {
        return this == MOUNT_SWITCH || this == AIR_TO_FLY_SWITCH || this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
    }

    public boolean isQuickFlyingMount() {
        return this == NORMAL_SUMMON || this == RIDE_HOME || this == MOUNT_SWITCH || this == AIR_TO_FLY_SWITCH;
    }

    public boolean requiresExactRideContact() {
        return this == RIDE_HOME;
    }
}
