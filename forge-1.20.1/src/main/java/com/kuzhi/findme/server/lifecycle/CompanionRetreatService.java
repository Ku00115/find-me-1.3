package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class CompanionRetreatService {
    private static final List<PendingRetreat> PENDING_RETREATS = new ArrayList<>();

    private CompanionRetreatService() {
    }

    public static void tickRetreats(MinecraftServer server) {
        // Storage/home callbacks may cancel or replace retreats. Iterate a stable frame snapshot so
        // those re-entrant mutations cannot invalidate the active collection traversal.
        for (PendingRetreat retreat : List.copyOf(PENDING_RETREATS)) {
            if (!PENDING_RETREATS.contains(retreat)) continue;
            Entity entity = CompanionEntityLookup.findEntity(server, retreat.entityUuid()).orElse(null);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                PENDING_RETREATS.remove(retreat);
                continue;
            }
            PlayerCompanionData retirementData = null;
            Vec3 direction = retreat.direction();
            if (living instanceof Mob mob) {
                if (retreat.switchRetreat()) {
                    // A switch departure remains a real mob. Give native
                    // navigation one random departure target instead of freezing
                    // AI and teleporting the entity every tick.
                    mob.setNoAi(false);
                    mob.setTarget(null);
                    mob.setAggressive(false);
                    if (!retreat.navigationStarted()) {
                        mob.getNavigation().moveTo(retreat.destination().x, retreat.destination().y,
                                retreat.destination().z, 1.0);
                        retreat.markNavigationStarted();
                    }
                } else {
                    mob.setNoAi(false);
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                    mob.setAggressive(false);
                }
            }
            if (retreat.switchRetreat() && retreat.moveType() == CompanionMoveType.FLY) {
                living.noPhysics = false;
                living.setNoGravity(true);
                if (retreat.age() == 0) {
                    living.setYRot(retreat.capturedYRot());
                    living.setXRot(retreat.capturedXRot());
                    if (retreat.capturedVelocity().lengthSqr() > 1.0E-4) {
                        living.setDeltaMovement(retreat.capturedVelocity());
                    }
                }
                CompanionAnimationHelper.forceFlyingAnimationPose(living);
            }
            if (!retreat.switchRetreat() && retreat.moveType() == CompanionMoveType.FLY) {
                living.noPhysics = true;
                living.setNoGravity(true);
                CompanionAnimationHelper.forceFlyingAnimationPose(living);
            } else if (!retreat.switchRetreat()) {
                CompanionAnimationHelper.restoreAnimationControl(living);
            }
            if (!retreat.switchRetreat()) {
                double remainingDistance = Math.max(0.0, 44.0 - living.position().distanceTo(retreat.origin()));
                double speed = CompanionCinematicSpeedService.retreatSpeed(retreat.moveType(), remainingDistance);
                Vec3 next = living.position().add(direction.scale(speed));
                float lookYaw = CompanionCinematicOrientationHelper.yawToward(direction);
                float lookPitch = retreat.moveType() == CompanionMoveType.FLY
                        ? CompanionCinematicOrientationHelper.pitchToward(direction, 18.0f) : living.getXRot();
                living.moveTo(next.x, next.y, next.z, lookYaw, lookPitch);
                living.setPos(next.x, next.y, next.z);
                CompanionCinematicOrientationHelper.faceYaw(living, lookYaw);
                living.setXRot(lookPitch);
                living.xRotO = lookPitch;
                living.setDeltaMovement(direction.scale(speed));
            }
            living.fallDistance = 0.0f;
            living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
            living.hurtMarked = true;
            retreat.incrementAge();
            ServerPlayer player = server.getPlayerList().getPlayer(retreat.playerUuid());
            boolean pulledBackToPlayer = !retreat.switchRetreat() && player != null && retreat.age() > 8 && living.distanceTo(player) < 8.0f;
            boolean pulledBackToOrigin = !retreat.switchRetreat() && retreat.age() > 8 && living.position().distanceTo(retreat.origin()) < 8.0;
            int maximumAge = retreat.switchRetreat() ? 62 : 82;
            double distanceToDestination = living.position().distanceTo(retreat.destination());
            double distanceFromOrigin = living.position().distanceTo(retreat.origin());
            boolean stillTraveling = retreat.switchRetreat()
                    ? distanceToDestination > 1.5
                    : distanceFromOrigin < 44.0;
            if (stillTraveling && retreat.age() <= maximumAge
                    && !pulledBackToPlayer && !pulledBackToOrigin) {
                continue;
            }
            boolean safeToRemove = false;
            if (player != null) {
                PlayerCompanionData data = CompanionDataService.data(player);
                retirementData = data;
                if (retreat.kind() != null && data.homeNestBlock(living.getUUID()).isPresent()
                        && CompanionHomeResidentService.sendHomeIfPossible(player, data, retreat.kind(), living)) {
                    data.clearDeployed(retreat.kind(), living.getUUID());
                    CompanionDataService.save(player, data);
                    CompanionSyncService.syncToClient(player, retreat.kind());
                    PENDING_RETREATS.remove(retreat);
                    continue;
                }
                if (!CompanionOperationLockService.tryBegin(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "retreat:store")) {
                    FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, living.getUUID(), living, "RETREATING", "ACTIVE", "retreat_lock_active", data.storedEntity(living.getUUID()).isPresent(), true);
                    PENDING_RETREATS.remove(retreat);
                    continue;
                }
                safeToRemove = CompanionStorageService.storeEntity(player, data, living);
                if (!safeToRemove) {
                    FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, living.getUUID(), living, "RETREATING", "ACTIVE", "retreat_snapshot_failed", false, true);
                    CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "retreat_snapshot_failed");
                    PENDING_RETREATS.remove(retreat);
                    continue;
                }
                if (retreat.kind() != null) {
                    data.clearDeployed(retreat.kind(), living.getUUID());
                }
                CompanionDataService.save(player, data);
                if (retreat.kind() != null) {
                    CompanionSyncService.syncToClient(player, retreat.kind());
                }
            }
            if (!safeToRemove) {
                FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, living.getUUID(), living, "RETREATING", "ACTIVE", "retreat_owner_unavailable", false, true);
                PENDING_RETREATS.remove(retreat);
                continue;
            }
            boolean presentationFinished = retreat.switchRetreat()
                    ? CompanionStorageService.finishMovingRetreat(player, retirementData, living)
                    : CompanionStorageService.finishRetreatWithStoragePresentation(player, living);
            if (!presentationFinished) {
                FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, living.getUUID(), living,
                        "RETREATING", "ACTIVE", "retreat_presentation_failed", true, true);
                CompanionOperationLockService.end(player, living.getUUID(),
                        CompanionOperationLockService.Operation.STORE, "retreat_presentation_failed");
                PENDING_RETREATS.remove(retreat);
                continue;
            }
            PENDING_RETREATS.remove(retreat);
        }
    }

    public static void cancelRetreat(LivingEntity living) {
        PENDING_RETREATS.removeIf(retreat -> retreat.entityUuid().equals(living.getUUID()));
    }

    public static void sendAway(Entity entity, ServerPlayer player, PlayerCompanionData data) {
        sendAway(entity, player, data, false);
    }

    public static void sendAwayForSwitch(Entity entity, ServerPlayer player, PlayerCompanionData data) {
        sendAway(entity, player, data, true);
    }

    private static void sendAway(Entity entity, ServerPlayer player, PlayerCompanionData data,
                                 boolean switchRetreat) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        Vec3 capturedVelocity = living.getDeltaMovement();
        float capturedYRot = living.getYRot();
        float capturedXRot = living.getXRot();
        living.stopRiding();
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        cancelRetreat(living);
        CompanionKind kind = kindOf(data, living);
        CompanionMoveType moveType = CompanionEntityClassifier.moveType(living,
                kind == null ? CompanionKind.MOUNT : kind);
        Vec3 origin = living.position();
        Vec3 direction = switchRetreat
                ? randomSwitchDepartureDirection(player)
                : randomRetreatDirection(player, living, moveType);
        double departureDistance = switchRetreat
                ? 8.0 + player.getRandom().nextDouble() * 10.0
                : 44.0;
        Vec3 destination = origin.add(direction.scale(departureDistance));
        if (switchRetreat && moveType == CompanionMoveType.WALK) {
            destination = new Vec3(destination.x,
                    CompanionCinematicLandingService.walkGroundY(living.level(), destination.x,
                            origin.y, destination.z, origin.y), destination.z);
        }
        PendingRetreat pending = new PendingRetreat(living.getUUID(), origin, direction, destination,
                player.getUUID(), kind, moveType, switchRetreat, capturedVelocity,
                capturedYRot, capturedXRot);
        if (switchRetreat) {
            CompanionStorageService.beginMovingRetreatPresentation(player, data, living);
            pending.markPresentationStarted();
        }
        PENDING_RETREATS.add(pending);
        if (kind != null) {
            CompanionSyncService.syncToClient(player, kind);
        }
    }

    private static CompanionKind kindOf(PlayerCompanionData data, LivingEntity living) {
        if (data == null || living == null) {
            return null;
        }
        if (data.contains(CompanionKind.MOUNT, living.getUUID())) {
            return CompanionKind.MOUNT;
        }
        if (data.contains(CompanionKind.COMPANION, living.getUUID())) {
            return CompanionKind.COMPANION;
        }
        return null;
    }

    private static Vec3 randomRetreatDirection(ServerPlayer player, LivingEntity living,
                                               CompanionMoveType moveType) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.001) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        horizontal = horizontal.normalize();
        int choice = player.getRandom().nextInt(3);
        Vec3 direction = choice == 1 ? new Vec3(-horizontal.z, 0.0, horizontal.x).normalize()
                : choice == 2 ? new Vec3(horizontal.z, 0.0, -horizontal.x).normalize()
                : horizontal.reverse();
        if (moveType != CompanionMoveType.FLY) {
            return direction;
        }
        double currentY = living.getDeltaMovement().y;
        double vertical = Math.max(0.035, Math.min(0.16, currentY));
        return new Vec3(direction.x, vertical, direction.z).normalize();
    }

    private static Vec3 randomSwitchDepartureDirection(ServerPlayer player) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
        return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
    }
}

