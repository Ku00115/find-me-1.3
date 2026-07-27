package com.kuzhi.findme.server.lifecycle;

import java.util.UUID;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import net.minecraft.world.phys.Vec3;

final class PendingRetreat {
    private final UUID entityUuid;
    private final Vec3 origin;
    private final Vec3 direction;
    private final Vec3 destination;
    private final UUID playerUuid;
    private final CompanionKind kind;
    private final CompanionMoveType moveType;
    private final boolean switchRetreat;
    private final Vec3 capturedVelocity;
    private final float capturedYRot;
    private final float capturedXRot;
    private boolean presentationStarted;
    private boolean navigationStarted;
    private int age;

    PendingRetreat(UUID entityUuid, Vec3 origin, Vec3 direction, Vec3 destination, UUID playerUuid,
                   CompanionKind kind, CompanionMoveType moveType, boolean switchRetreat,
                   Vec3 capturedVelocity, float capturedYRot, float capturedXRot) {
        this.entityUuid = entityUuid;
        this.origin = origin;
        this.direction = direction;
        this.destination = destination;
        this.playerUuid = playerUuid;
        this.kind = kind;
        this.moveType = moveType;
        this.switchRetreat = switchRetreat;
        this.capturedVelocity = capturedVelocity == null ? Vec3.ZERO : capturedVelocity;
        this.capturedYRot = capturedYRot;
        this.capturedXRot = capturedXRot;
    }

    UUID entityUuid() {
        return this.entityUuid;
    }

    Vec3 origin() {
        return this.origin;
    }

    Vec3 direction() {
        return this.direction;
    }

    Vec3 destination() {
        return this.destination;
    }

    UUID playerUuid() {
        return this.playerUuid;
    }

    CompanionKind kind() {
        return this.kind;
    }

    CompanionMoveType moveType() {
        return this.moveType;
    }

    boolean switchRetreat() {
        return this.switchRetreat;
    }

    Vec3 capturedVelocity() {
        return this.capturedVelocity;
    }

    float capturedYRot() {
        return this.capturedYRot;
    }

    float capturedXRot() {
        return this.capturedXRot;
    }

    boolean navigationStarted() {
        return this.navigationStarted;
    }

    void markNavigationStarted() {
        this.navigationStarted = true;
    }

    boolean presentationStarted() {
        return this.presentationStarted;
    }

    void markPresentationStarted() {
        this.presentationStarted = true;
    }

    int age() {
        return this.age;
    }

    void incrementAge() {
        ++this.age;
    }
}
