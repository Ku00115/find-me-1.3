package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicPositionService;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CompanionSummonService {

    private CompanionSummonService() {
    }

    public static boolean summonActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        return summonActive(player, data, kind, false, false);
    }

    public static boolean summonActiveForTacticalOrder(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        return summonActive(player, data, kind, true, false);
    }

    public static boolean summonActiveForMountSwitch(ServerPlayer player, PlayerCompanionData data) {
        return summonActive(player, data, CompanionKind.MOUNT, false, true);
    }

    private static boolean summonActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                        boolean tacticalDeploy, boolean bypassCooldown) {
        Optional<UUID> selected = data.active(kind);
        if (selected.isPresent() && isRecoveringAtHome(data, selected.get())) {
            CompanionMessageService.tell(player, "message.find_me.home_recovering", ChatFormatting.YELLOW);
            return false;
        }
        boolean alreadyPlacedForArrival;
        MountCinematicMode mode;
        boolean transitionFromFlyingMount;
        boolean switchingMount;
        CompanionMoveType summonMoveType;
        CompanionMoveType moveType;
        LivingEntity living;
        LivingEntity companionRescueTarget;
        boolean restoredFromStorage;
        boolean freshHomeResidentRestore;
        boolean arrivalStarted = false;
        boolean preSpawnPresentationCompleted = false;
        boolean rescueCinematicEnabled = true;
        Entity entity;
        boolean inCombat;
        long now;
        RideHandoffService.Source rideSource = RideHandoffService.Source.none();
        block35: {
            LivingEntity restoredLiving;
            Entity restored;
            boolean rescueSummon;
            block37: {
                block36: {
                    boolean fallingRescue;
                    block34: {
                        block33: {
                            Optional<CompanionSummonPreparationService.Preparation> maybePreparation =
                                    CompanionSummonPreparationService.prepare(player, data, kind, bypassCooldown);
                            if (maybePreparation.isEmpty()) {
                                return false;
                            }
                            CompanionSummonPreparationService.Preparation preparation = maybePreparation.get();
                            now = preparation.now();
                            inCombat = preparation.inCombat();
                            fallingRescue = !tacticalDeploy && preparation.fallingRescue();
                            if (kind == CompanionKind.MOUNT && !tacticalDeploy) {
                                rideSource = RideHandoffService.resolveSource(player, data);
                                if (rideSource.present()) {
                                    fallingRescue = false;
                                }
                            }
                            companionRescueTarget = tacticalDeploy ? null : preparation.companionRescueTarget();
                            boolean companionRescue = companionRescueTarget != null;
                            UUID uuid = preparation.uuid();
                            entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
                            freshHomeResidentRestore = false;
                            if (entity instanceof LivingEntity resident && CompanionHomeResidentService.isResident(uuid)
                                    && data.homeNestBlock(uuid).isPresent()) {
                                if (fallingRescue) {
                                    // Rescue has a hard fall deadline. A loaded home resident is
                                    // already the authoritative entity, so release it in place
                                    // instead of serializing and recreating it before movement.
                                    CompanionHomeResidentService.clearResident(resident);
                                } else if (CompanionStorageService.snapshotAndDiscardHomeResidentForSummon(player, data, resident)) {
                                    entity = null;
                                    freshHomeResidentRestore = true;
                                }
                            }
                            Optional<CompoundTag> storedTag = entity == null ? data.storedEntity(preparation.uuid()) : Optional.empty();
                            CompanionMoveType plannedMoveType = plannedMoveType(player, entity, storedTag, kind);
                            CompanionAnimationPurpose plannedAnimationPurpose = plannedAnimationPurpose(kind,
                                    rideSource, preparation.uuid(), fallingRescue, companionRescue,
                                    plannedMoveType, player, data);
                            rescueCinematicEnabled = plannedAnimationPurpose != CompanionAnimationPurpose.RESCUE
                                    || CompanionRescuePlanner.plan(player).playsCinematic();
                            boolean landingSummonRescue = fallingRescue
                                    && plannedMoveType == CompanionMoveType.FLY
                                    && !rescueCinematicEnabled;
                            if (isVoidSummon(player)) {
                                if (kind != CompanionKind.MOUNT || plannedMoveType != CompanionMoveType.FLY) {
                                    CompanionSummonLineService.showVoidUnsafe(player, data, kind);
                                    return false;
                                }
                                if (completeDirectVoidFlyingMount(player, data, preparation.uuid(), entity, inCombat, now,
                                        freshHomeResidentRestore, rideSource)) {
                                    return true;
                                }
                                CompanionSummonFailureService.unavailable(player, data, kind, false);
                                return false;
                            }
                            restoredFromStorage = false;
                            if (entity == null) {
                                Optional<CompanionPreSpawnPresentationService.Presentation> readyPresentation =
                                        CompanionPreSpawnPresentationService.ready(player, preparation.uuid());
                                if (readyPresentation.isPresent()) {
                                    CompanionPreSpawnPresentationService.Presentation presentation = readyPresentation.get();
                                    entity = CompanionLifecycleFacade.restoreStoredAfterPresentation(player.serverLevel(),
                                            player, data, preparation.uuid(), presentation.spawn(), player.getYRot(),
                                            player.getXRot(), presentation.freshRestore(), "summon:after_presentation")
                                            .orElse(null);
                                    restoredFromStorage = entity != null;
                                    preSpawnPresentationCompleted = restoredFromStorage;
                                    arrivalStarted = false;
                                } else {
                                    CompanionSummonPlacementService.StoredRestore restoreSpawn =
                                            CompanionSummonPlacementService.findStoredRestoreSpawn(player, data,
                                                    preparation.uuid(), kind, storedTag, fallingRescue,
                                                    companionRescueTarget, tacticalDeploy);
                                    if (restoreSpawn.preloadChunkArea()) {
                                        CompanionCinematicLandingService.preloadChunkArea(player.serverLevel(),
                                                restoreSpawn.pos(), 1);
                                    }
                                    Vec3 focus = arrivalFocus(player, kind, companionRescueTarget);
                                    int duration = arrivalDuration(kind, restoreSpawn.preloadChunkArea(), companionRescue);
                                    if (landingSummonRescue) {
                                        entity = (freshHomeResidentRestore
                                                ? CompanionLifecycleFacade.restoreStoredFresh(player.serverLevel(),
                                                player, data, preparation.uuid(), restoreSpawn.pos(),
                                                player.getYRot(), player.getXRot(), "summon:landing_rescue_fresh")
                                                : CompanionLifecycleFacade.restoreStoredDirect(player, data,
                                                preparation.uuid(), restoreSpawn.pos(), player.getYRot(),
                                                player.getXRot(), "summon:landing_rescue_direct"))
                                                .orElse(null);
                                        restoredFromStorage = entity != null;
                                        preSpawnPresentationCompleted = restoredFromStorage;
                                        arrivalStarted = false;
                                    } else {
                                        boolean presentationEnabled = CompanionSummonPresentationPolicy.enabled(
                                                data, kind, plannedAnimationPurpose, tacticalDeploy)
                                                && rescueCinematicEnabled;
                                        if (presentationEnabled && storedTag.isPresent()
                                                && plannedAnimationPurpose != CompanionAnimationPurpose.RESCUE) {
                                            if (CompanionPreSpawnPresentationService.schedule(player, data, kind,
                                                    preparation.uuid(), storedTag.get(), restoreSpawn.pos(), focus,
                                                    duration, arrivalStyle(kind), plannedAnimationPurpose,
                                                    freshHomeResidentRestore, tacticalDeploy)) {
                                                return true;
                                            }
                                            entity = CompanionLifecycleFacade.restoreStoredAfterPresentation(
                                                    player.serverLevel(), player, data, preparation.uuid(),
                                                    restoreSpawn.pos(), player.getYRot(), player.getXRot(),
                                                    freshHomeResidentRestore, "summon:no_presentation").orElse(null);
                                            restoredFromStorage = entity != null;
                                            preSpawnPresentationCompleted = restoredFromStorage;
                                            arrivalStarted = false;
                                        } else if (presentationEnabled) {
                                            RescueMagicPacket.Purpose purpose = arrivalPurpose(
                                                    restoreSpawn.preloadChunkArea(), companionRescue);
                                            restoredFromStorage = (entity = (freshHomeResidentRestore
                                                    ? CompanionLifecycleFacade.restoreStoredFreshForArrival(player.serverLevel(), player, data, preparation.uuid(), restoreSpawn.pos(), player.getYRot(), player.getXRot(), focus, duration, arrivalStyle(kind), purpose, plannedAnimationPurpose, "summon:arrival_fresh")
                                                    : CompanionLifecycleFacade.restoreStoredForArrival(player, data, preparation.uuid(), restoreSpawn.pos(), player.getYRot(), player.getXRot(), focus, duration, arrivalStyle(kind), purpose, plannedAnimationPurpose, "summon:arrival")).orElse(null)) != null;
                                            arrivalStarted = restoredFromStorage;
                                        } else {
                                            entity = CompanionLifecycleFacade.restoreStoredAfterPresentation(
                                                    player.serverLevel(), player, data, preparation.uuid(),
                                                    restoreSpawn.pos(), player.getYRot(), player.getXRot(),
                                                    freshHomeResidentRestore, "summon:presentation_disabled")
                                                    .orElse(null);
                                            restoredFromStorage = entity != null;
                                            preSpawnPresentationCompleted = restoredFromStorage;
                                            arrivalStarted = false;
                                        }
                                    }
                                }
                            }
                            if (!(entity instanceof LivingEntity)) {
                                break block33;
                            }
                            living = (LivingEntity)entity;
                            if (!entity.isRemoved() && entity.isAlive()) {
                                break block34;
                            }
                        }
                        CompanionSummonFailureService.unavailable(player, data, kind, false);
                        return false;
                    }
                    if (kind == CompanionKind.MOUNT && player.getVehicle() != null && player.getVehicle().getUUID().equals(entity.getUUID())) {
                        CompanionSummonLineService.showAlreadyRiding(player, living);
                        return true;
                    }
                    if (entity.isPassenger()) {
                        entity.stopRiding();
                    }
                    if (entity.getFirstPassenger() != null && entity.getFirstPassenger() != player) {
                        CompanionSummonLineService.showOccupied(player, living, kind);
                        return false;
                    }
                    data.setOrigin(entity.getUUID(), SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
                    moveType = CompanionEntityClassifier.moveType(entity, kind);
                    summonMoveType = CompanionEntityClassifier.summonMoveType(living, kind);
                    boolean vehicleRescue = CompanionSummonModeService.isAirborneGroundVehicleRescue(player, data, kind, moveType);
                    boolean crossRideAirRescue = kind == CompanionKind.MOUNT && rideSource.airborne()
                            && moveType != CompanionMoveType.FLY;
                    rescueSummon = fallingRescue || CompanionSummonModeService.isAirborneGroundRescue(player, kind, moveType)
                            || vehicleRescue || crossRideAirRescue;
                    switchingMount = kind == CompanionKind.MOUNT && rideSource.present()
                            && !rideSource.uuid().equals(entity.getUUID());
                    transitionFromFlyingMount = switchingMount && rideSource.airborne();
                    mode = kind == CompanionKind.MOUNT
                            ? RideHandoffService.modeForMountTarget(rideSource, moveType, fallingRescue)
                            : CompanionSummonModeService.modeFor(kind, moveType, rescueSummon, false, false);
                    if (kind != CompanionKind.MOUNT || !mode.isRescue() || restoredFromStorage) {
                        break block35;
                    }
                    BlockPos restorePos = CompanionSummonPlacementService.findMountRescueRestoreSpawn(player, moveType,
                            EntityType.getKey(living.getType()).toString());
                    CompanionCinematicLandingService.preloadChunkArea(player.serverLevel(), restorePos, 1);
                    restoredLiving = rescueCinematicEnabled
                            ? CompanionEntityTransferService.moveEntityForArrival(player, living,
                            player.serverLevel(), restorePos, player.getYRot(), player.getXRot(), player.position(),
                            arrivalDuration(kind, true, false), arrivalStyle(kind),
                            RescueMagicPacket.Purpose.RESCUE, CompanionAnimationPurpose.RESCUE)
                            : CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), restorePos,
                            player.getYRot(), player.getXRot(), false);
                    restored = restoredLiving;
                    arrivalStarted = rescueCinematicEnabled;
                    preSpawnPresentationCompleted = !rescueCinematicEnabled;
                    if (!restoredLiving.isRemoved() && restoredLiving.isAlive()) {
                        break block37;
                    }
                }
                CompanionSummonFailureService.unavailable(player, data, kind, true);
                return false;
            }
            entity = restored;
            living = restoredLiving;
            restoredFromStorage = true;
            moveType = CompanionEntityClassifier.moveType(living, kind);
            summonMoveType = CompanionEntityClassifier.summonMoveType(living, kind);
            mode = kind == CompanionKind.MOUNT
                    ? RideHandoffService.modeForMountTarget(rideSource, moveType, rescueSummon)
                    : CompanionSummonModeService.modeFor(kind, moveType, rescueSummon, switchingMount, transitionFromFlyingMount);
        }
        boolean airborneGroundCompanion = CompanionSummonModeService.isAirborneGroundCompanionSummon(player, kind, summonMoveType);
        boolean companionRescue = companionRescueTarget != null;
        boolean voicePlayed = false;
        if (kind == CompanionKind.COMPANION && !companionRescue && !airborneGroundCompanion && isUnsafeHighCompanionSummon(player, summonMoveType)) {
            if (restoredFromStorage) {
                CompanionSummonFailureService.restoredEntityFailed(player, data, kind, living, CompanionSummonFailureService.Reason.TOO_HIGH);
            } else {
                CompanionSummonLineService.showTooHigh(player, data, kind, living);
            }
            return false;
        }
        boolean nearbyMount = kind == CompanionKind.MOUNT && !mode.isRescue() && !switchingMount && !restoredFromStorage && living.level() == player.level() && living.distanceTo(player) <= 16.0;
        alreadyPlacedForArrival = restoredFromStorage && (arrivalStarted || preSpawnPresentationCompleted
                || kind == CompanionKind.MOUNT && mode.isRescue());
        if (!nearbyMount && !alreadyPlacedForArrival) {
            Optional<CompanionPreSpawnPresentationService.Presentation> readyPresentation =
                    CompanionPreSpawnPresentationService.ready(player, living.getUUID());
            BlockPos target = readyPresentation.isPresent() ? readyPresentation.get().spawn()
                    : CompanionSummonPlacementService.findLiveMoveTarget(player, data, kind, living,
                            moveType, summonMoveType, mode, airborneGroundCompanion,
                            companionRescueTarget, tacticalDeploy);
            Optional<BlockPos> openTarget = CompanionSummonPlacementService.adjustForOpenLiveSpace(player, living, target, mode);
            if (openTarget.isPresent()) {
                target = openTarget.get();
            } else {
                if (restoredFromStorage) {
                    CompanionSummonFailureService.restoredEntityFailed(player, data, kind, living, CompanionSummonFailureService.Reason.NEED_OPEN_SPACE);
                } else {
                    CompanionSummonLineService.showNeedOpenSpace(player, data, kind, living);
                }
                return false;
            }
            if (mode.isRescue()) {
                CompanionCinematicLandingService.preloadChunkArea(player.serverLevel(), target, 1);
            } else if (companionRescue) {
                CompanionCinematicLandingService.preloadChunkArea(player.serverLevel(), target, 1);
            }
            CompanionArrivalSoundService.playCommandVoice(player, living);
            voicePlayed = true;
            Vec3 focus = arrivalFocus(player, kind, companionRescueTarget);
            int duration = arrivalDuration(kind, mode.isRescue(), companionRescue);
            if (readyPresentation.isPresent()) {
                living = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), target,
                        player.getYRot(), player.getXRot(), false);
                preSpawnPresentationCompleted = true;
                arrivalStarted = false;
            } else {
                CompanionAnimationPurpose activeAnimationPurpose = animationPurpose(mode, companionRescue);
                boolean presentationEnabled = CompanionSummonPresentationPolicy.enabled(data, kind,
                        activeAnimationPurpose, tacticalDeploy) && rescueCinematicEnabled;
                if (!presentationEnabled) {
                    living = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), target,
                            player.getYRot(), player.getXRot(), false);
                    preSpawnPresentationCompleted = true;
                    arrivalStarted = false;
                } else if (activeAnimationPurpose != CompanionAnimationPurpose.RESCUE) {
                    if (CompanionPreSpawnPresentationService.scheduleLive(player, data, kind, living, target,
                            focus, duration, arrivalStyle(kind), activeAnimationPurpose, tacticalDeploy)) {
                        return true;
                    }
                    living = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), target,
                            player.getYRot(), player.getXRot(), false);
                    preSpawnPresentationCompleted = true;
                    arrivalStarted = false;
                } else if (mode.isFlyingRescue()
                        && CompanionRescuePlanner.plan(player).flightMode()
                        == RescueFlightMode.LANDING_SUMMON) {
                    living = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), target,
                            player.getYRot(), player.getXRot(), false);
                    preSpawnPresentationCompleted = true;
                    arrivalStarted = false;
                } else {
                    living = CompanionEntityTransferService.moveEntityForArrival(player, living, player.serverLevel(),
                            target, player.getYRot(), player.getXRot(), focus, duration, arrivalStyle(kind),
                            arrivalPurpose(mode.isRescue(), companionRescue), activeAnimationPurpose);
                    arrivalStarted = true;
                }
            }
        }
        if (!voicePlayed) {
            CompanionArrivalSoundService.playCommandVoice(player, living);
        }
        CompanionSummonCompletionService.complete(player, data, kind, living,
                kind == CompanionKind.COMPANION ? summonMoveType : moveType, mode,
                restoredFromStorage, inCombat, companionRescue, companionRescueTarget, arrivalStarted,
                preSpawnPresentationCompleted, rescueCinematicEnabled, now, tacticalDeploy);
        return true;
    }

    static boolean isRecoveringAtHome(PlayerCompanionData data, UUID uuid) {
        return data != null && uuid != null && data.isCritical(uuid)
                && data.lifecycleState(uuid) == CompanionLifecycleState.HOME_STORED
                && data.homeHouseId(uuid).isPresent();
    }

    private static CompanionMoveType plannedMoveType(ServerPlayer player, Entity entity,
                                                      Optional<CompoundTag> storedTag, CompanionKind kind) {
        if (entity instanceof LivingEntity living) {
            return kind == CompanionKind.MOUNT ? CompanionEntityClassifier.moveType(living, kind) : CompanionEntityClassifier.summonMoveType(living, kind);
        }
        if (storedTag.isPresent()) {
            String entityType = CompanionEntitySnapshots.storedEntityType(storedTag.get());
            return kind == CompanionKind.MOUNT
                    ? CompanionEntityClassifier.moveType(player.getServer(), entityType, kind)
                    : CompanionEntityClassifier.summonMoveType(player.getServer(), entityType, kind);
        }
        return CompanionMoveType.WALK;
    }

    private static CompanionAnimationPurpose plannedAnimationPurpose(CompanionKind kind,
            RideHandoffService.Source rideSource, UUID targetUuid, boolean fallingRescue,
            boolean companionRescue, CompanionMoveType moveType, ServerPlayer player,
            PlayerCompanionData data) {
        if (kind == CompanionKind.MOUNT && rideSource.present() && !rideSource.uuid().equals(targetUuid)) {
            return CompanionAnimationPurpose.SWITCH;
        }
        boolean rescue = fallingRescue || companionRescue;
        if (kind == CompanionKind.MOUNT) {
            rescue = rescue || CompanionSummonModeService.isAirborneGroundRescue(player, kind, moveType)
                    || CompanionSummonModeService.isAirborneGroundVehicleRescue(player, data, kind, moveType)
                    || rideSource.airborne() && moveType != CompanionMoveType.FLY;
        }
        return rescue ? CompanionAnimationPurpose.RESCUE : CompanionAnimationPurpose.SUMMON;
    }

    private static CompanionAnimationPurpose animationPurpose(MountCinematicMode mode,
                                                               boolean companionRescue) {
        if (mode.isMountSwitch()) return CompanionAnimationPurpose.SWITCH;
        return mode.isRescue() || companionRescue
                ? CompanionAnimationPurpose.RESCUE : CompanionAnimationPurpose.SUMMON;
    }

    private static boolean isVoidSummon(ServerPlayer player) {
        return !CompanionCinematicLandingService.hasReliableLandingBelow(player.serverLevel(), player);
    }

    private static boolean completeDirectVoidFlyingMount(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                                          Entity entity, boolean inCombat, long now,
                                                          boolean freshRestore, RideHandoffService.Source rideSource) {
        if (entity != null && entity.getUUID().equals(player.getVehicle() == null ? null : player.getVehicle().getUUID())) {
            if (entity instanceof LivingEntity riding) {
                CompanionSummonLineService.showAlreadyRiding(player, riding);
            }
            return true;
        }
        if (entity != null && entity.getFirstPassenger() != null && entity.getFirstPassenger() != player) {
            if (entity instanceof LivingEntity occupied) {
                CompanionSummonLineService.showOccupied(player, occupied, CompanionKind.MOUNT);
            }
            return true;
        }
        if (entity != null && entity.isPassenger()) {
            entity.stopRiding();
        }
        BlockPos target = BlockPos.containing(player.getX(), CompanionCinematicPositionService.directFlyingCatchY(player), player.getZ());
        LivingEntity living = null;
        if (entity instanceof LivingEntity liveEntity) {
            data.setOrigin(uuid, SavedPosition.of(liveEntity.level(), liveEntity.getX(), liveEntity.getY(), liveEntity.getZ(), liveEntity.getYRot(), liveEntity.getXRot()));
            living = CompanionEntityTransferService.moveEntityTo(liveEntity, player.serverLevel(), target, player.getYRot(), player.getXRot());
        } else {
            Entity restored = freshRestore
                    ? CompanionLifecycleFacade.restoreStoredFresh(player.serverLevel(), player, data, uuid, target, player.getYRot(), player.getXRot(), "summon:void_flying_fresh").orElse(null)
                    : CompanionLifecycleFacade.restoreStored(player, data, uuid, target, player.getYRot(), player.getXRot(), "summon:void_flying").orElse(null);
            if (restored instanceof LivingEntity restoredLiving) {
                living = restoredLiving;
            }
        }
        if (living == null || living.isRemoved() || !living.isAlive()) {
            return false;
        }
        CompanionArrivalSoundService.playCommandVoice(player, living);
        RideHandoffService.MotionSnapshot handoff = RideHandoffService.consumeTransactionMotion(
                player.getUUID(), living.getUUID()).orElseGet(
                () -> RideHandoffService.captureMotion(rideSource, player.getVehicle()));
        if (rideSource.present() && !rideSource.uuid().equals(living.getUUID())) {
            RideHandoffService.beginRetirement(player, data, rideSource, living.getUUID(), "findme:void_flying_mount");
        }
        CompanionSummonCompletionService.completeVoidFlyingMount(player, data, living, inCombat, now, handoff);
        return true;
    }

    private static boolean isUnsafeHighCompanionSummon(ServerPlayer player, CompanionMoveType summonMoveType) {
        if (summonMoveType == CompanionMoveType.FLY) {
            return false;
        }
        if (player.onGround()) {
            return false;
        }
        return CompanionCinematicLandingService.distanceToGround(player) > 4.0;
    }

    private static Vec3 arrivalFocus(ServerPlayer player, CompanionKind kind, LivingEntity companionRescueTarget) {
        if (kind == CompanionKind.COMPANION && companionRescueTarget != null) {
            return companionRescueTarget.position();
        }
        return player.position();
    }

    private static int arrivalDuration(CompanionKind kind, boolean rescue, boolean companionRescue) {
        return rescue || companionRescue ? 48 : 42;
    }

    private static RescueMagicPacket.Style arrivalStyle(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? RescueMagicPacket.Style.VERTICAL_PORTAL : RescueMagicPacket.Style.GROUND_CIRCLE;
    }

    private static RescueMagicPacket.Purpose arrivalPurpose(boolean rescue, boolean companionRescue) {
        return rescue || companionRescue ? RescueMagicPacket.Purpose.RESCUE : RescueMagicPacket.Purpose.SUMMON;
    }
}


