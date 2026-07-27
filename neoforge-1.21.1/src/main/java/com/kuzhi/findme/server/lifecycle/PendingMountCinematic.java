package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.MountCinematicStage;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PendingMountCinematic {
    private final UUID playerUuid;
    private final UUID mountUuid;
    private final CompanionMoveType moveType;
    private final MountCinematicMode mode;
    private final double catchY;
    private final boolean physicalCatchOnly;
    private final boolean originalNoGravity;
    private final boolean originalNoAi;
    private final boolean externalMount;
    private final RideHandoffService.Source rideSource;
    private RescueFlightMode rescueFlightMode = RescueFlightMode.HOVER;
    private Vec3 rescueLandingPosition;
    private Vec3 rescueHoverPosition;
    private int rescueHoldAge;
    private Vec3 waitPosition;
    private Vec3 lastCinematicPosition;
    private MountCinematicStage stage = MountCinematicStage.APPROACH;
    private int age;
    private int switchAge;
    private int warmupTicks;
    private boolean switched;
    private boolean flyingRescueStaged;
    private boolean sourceRetirementStarted;
    private boolean contactLatched;
    private RideHandoffService.MotionSnapshot rideHandoff = RideHandoffService.MotionSnapshot.none();

    public PendingMountCinematic(UUID playerUuid, UUID mountUuid, CompanionMoveType moveType, MountCinematicMode mode, double catchY, boolean flyingRescueStaged, boolean physicalCatchOnly, int warmupTicks, Vec3 lastCinematicPosition, boolean originalNoGravity, boolean originalNoAi) {
        this(playerUuid, mountUuid, moveType, mode, catchY, flyingRescueStaged,
                physicalCatchOnly, warmupTicks, lastCinematicPosition, originalNoGravity, originalNoAi,
                false, RideHandoffService.Source.none());
    }

    public PendingMountCinematic(UUID playerUuid, UUID mountUuid, CompanionMoveType moveType, MountCinematicMode mode, double catchY, boolean flyingRescueStaged, boolean physicalCatchOnly, int warmupTicks, Vec3 lastCinematicPosition, boolean originalNoGravity, boolean originalNoAi, boolean externalMount) {
        this(playerUuid, mountUuid, moveType, mode, catchY, flyingRescueStaged,
                physicalCatchOnly, warmupTicks, lastCinematicPosition, originalNoGravity, originalNoAi,
                externalMount, RideHandoffService.Source.none());
    }

    public PendingMountCinematic(UUID playerUuid, UUID mountUuid, CompanionMoveType moveType,
                                 MountCinematicMode mode, double catchY, boolean flyingRescueStaged,
                                 boolean physicalCatchOnly, int warmupTicks, Vec3 lastCinematicPosition,
                                 boolean originalNoGravity, boolean originalNoAi, boolean externalMount,
                                 RideHandoffService.Source rideSource) {
        this.playerUuid = playerUuid;
        this.mountUuid = mountUuid;
        this.moveType = moveType;
        this.mode = mode;
        this.catchY = catchY;
        this.flyingRescueStaged = flyingRescueStaged;
        this.physicalCatchOnly = physicalCatchOnly;
        this.warmupTicks = warmupTicks;
        this.lastCinematicPosition = lastCinematicPosition;
        this.originalNoGravity = originalNoGravity;
        this.originalNoAi = originalNoAi;
        this.externalMount = externalMount;
        this.rideSource = rideSource == null ? RideHandoffService.Source.none() : rideSource;
    }

    public UUID playerUuid() {
        return this.playerUuid;
    }

    public UUID mountUuid() {
        return this.mountUuid;
    }

    public CompanionMoveType moveType() {
        return this.moveType;
    }

    public MountCinematicMode mode() {
        return this.mode;
    }

    public double catchY() {
        return this.catchY;
    }

    public boolean physicalCatchOnly() {
        return this.physicalCatchOnly;
    }

    public boolean originalNoGravity() {
        return this.originalNoGravity;
    }

    public boolean originalNoAi() {
        return this.originalNoAi;
    }

    public boolean externalMount() {
        return this.externalMount;
    }

    public RideHandoffService.Source rideSource() {
        return this.rideSource;
    }

    public RescueFlightMode rescueFlightMode() {
        return this.rescueFlightMode;
    }

    public void setRescueFlightMode(RescueFlightMode rescueFlightMode) {
        this.rescueFlightMode = rescueFlightMode == null ? RescueFlightMode.HOVER : rescueFlightMode;
    }

    public Vec3 rescueLandingPosition() {
        return this.rescueLandingPosition;
    }

    public void setRescueLandingPosition(Vec3 rescueLandingPosition) {
        this.rescueLandingPosition = rescueLandingPosition;
    }

    public Vec3 rescueHoverPosition() {
        return this.rescueHoverPosition;
    }

    public void setRescueHoverPosition(Vec3 rescueHoverPosition) {
        this.rescueHoverPosition = rescueHoverPosition;
    }

    public int rescueHoldAge() {
        return this.rescueHoldAge;
    }

    public void incrementRescueHoldAge() {
        ++this.rescueHoldAge;
    }

    public boolean sourceRetirementStarted() {
        return this.sourceRetirementStarted;
    }

    public void markSourceRetirementStarted() {
        this.sourceRetirementStarted = true;
    }

    public void captureRideHandoff(Entity currentVehicle) {
        if (this.rideHandoff.captured()) {
            return;
        }
        this.rideHandoff = RideHandoffService.consumeTransactionMotion(this.playerUuid, this.mountUuid)
                .orElseGet(() -> RideHandoffService.captureMotion(this.rideSource, currentVehicle));
    }

    public RideHandoffService.MotionSnapshot rideHandoff() {
        return this.rideHandoff;
    }

    public Vec3 airHandoffVelocity() {
        return this.rideHandoff.velocityFor(CompanionMoveType.FLY);
    }

    public float airHandoffYRot() {
        return this.rideHandoff.yRot();
    }

    public float airHandoffXRot() {
        return this.rideHandoff.xRot();
    }

    public boolean airHandoffCaptured() {
        return this.rideHandoff.compatibleWith(CompanionMoveType.FLY);
    }

    public boolean waitLocked() {
        return this.waitPosition != null;
    }

    public Vec3 waitPosition() {
        return this.waitPosition;
    }

    public void lockWait(Vec3 waitPosition) {
        this.waitPosition = waitPosition;
    }

    public Vec3 lastCinematicPosition() {
        return this.lastCinematicPosition;
    }

    public void rememberPosition(Vec3 position) {
        this.lastCinematicPosition = position;
    }

    public int warmupTicks() {
        return this.warmupTicks;
    }

    public void decrementWarmup() {
        this.warmupTicks = Math.max(0, this.warmupTicks - 1);
    }

    public boolean flyingRescueStaged() {
        return this.flyingRescueStaged;
    }

    public void setFlyingRescueStaged() {
        this.flyingRescueStaged = true;
    }

    public MountCinematicStage stage() {
        return this.stage;
    }

    public void beginSwitch() {
        this.stage = MountCinematicStage.SWITCH;
        this.switchAge = 0;
    }

    public boolean contactLatched() {
        return this.contactLatched;
    }

    public void latchContact() {
        this.contactLatched = true;
    }

    public int age() {
        return this.age;
    }

    public void incrementAge() {
        ++this.age;
    }

    public int switchAge() {
        return this.switchAge;
    }

    public void incrementSwitchAge() {
        ++this.switchAge;
    }

    public boolean switched() {
        return this.switched;
    }

    public void setSwitched() {
        this.switched = true;
    }

}
