package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared summon-first boundary for features that need a selected creature deployed. */
public final class CompanionDeployRequestService {
    private CompanionDeployRequestService() {
    }

    public static Result request(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                 UUID uuid, Mode mode, String source) {
        return request(player, data, kind, uuid, mode, source, (CompanionDeploymentPlan)null);
    }

    public static Result request(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                 UUID uuid, Mode mode, String source, BlockPos plannedSpawn) {
        CompanionDeploymentPlan plan = plannedSpawn == null ? null
                : CompanionDeploymentPlan.tactical(CompanionDeploymentPlan.Intent.ORDINARY, null,
                plannedSpawn, null);
        return request(player, data, kind, uuid, mode, source, plan);
    }

    public static Result request(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                 UUID uuid, Mode mode, String source, CompanionDeploymentPlan plan) {
        LivingEntity ready = readyEntity(player, data, uuid, mode);
        if (ready != null && !CompanionLifecycleFacade.isBusy(player, data, uuid)) {
            return new Result(State.READY, ready);
        }
        if (CompanionSummonApproachService.isActive(uuid)) {
            return new Result(State.WAITING, null);
        }
        if (CompanionLifecycleFacade.isBusy(player, data, uuid)) {
            return new Result(State.WAITING, null);
        }
        if (mode != Mode.AUTONOMOUS && !uuid.equals(data.active(kind).orElse(null))) {
            data.setActiveUuid(kind, uuid);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, kind);
        }
        if (mode == Mode.AUTONOMOUS) {
            return deployDirect(player, data, kind, uuid, source, plan);
        }
        boolean accepted = mode == Mode.AUTONOMOUS
                ? CompanionLifecycleFacade.summonActiveForTacticalOrder(player, data, kind, source)
                : CompanionLifecycleFacade.summonActive(player, data, kind, source);
        return new Result(accepted ? State.STARTED : State.REJECTED, null);
    }

    public static LivingEntity readyEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                           Mode mode) {
        Entity found = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
        if (!(found instanceof LivingEntity living) || !living.isAlive() || living.level() != player.level()
                || CompanionHomeResidentService.isResident(uuid)
                || CompanionSummonApproachService.isActive(uuid)) {
            return null;
        }
        if (mode == Mode.AUTONOMOUS && (player.distanceToSqr(living) > 48.0 * 48.0
                || data.kindOf(uuid).map(kind -> !data.isDeployed(kind, uuid)).orElse(true))) {
            return null;
        }
        if (mode == Mode.RIDDEN && player.getVehicle() != living) {
            return null;
        }
        return living;
    }

    private static Result deployDirect(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                       UUID uuid, String source, CompanionDeploymentPlan plan) {
        Entity found = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
        if (found instanceof LivingEntity resident && CompanionHomeResidentService.isResident(uuid)) {
            if (!CompanionStorageService.snapshotAndDiscardHomeResidentForSummon(player, data, resident)) {
                return new Result(State.WAITING, null);
            }
            found = null;
        }
        if (found instanceof LivingEntity occupied && occupied.getFirstPassenger() != null
                && occupied.getFirstPassenger() != player) {
            return new Result(State.REJECTED, null);
        }

        Optional<CompoundTag> stored = data.storedEntity(uuid);
        CompanionMoveType moveType = found instanceof LivingEntity living
                ? CompanionEntityClassifier.moveType(living, kind)
                : CompanionEntityClassifier.moveType(
                        stored.map(CompanionEntitySnapshots::storedEntityType).orElse(""), kind);
        String entityType = found instanceof LivingEntity living
                ? net.minecraft.world.entity.EntityType.getKey(living.getType()).toString()
                : stored.map(CompanionEntitySnapshots::storedEntityType).orElse("");
        boolean animatedArrival = plan != null && kind == CompanionKind.COMPANION
                && data.uiSettings().companionSummonAnimations();
        CompanionEntityVisualBoundsService.VisualDimensions dimensions = found instanceof LivingEntity living
                ? CompanionEntityVisualBoundsService.effectDimensions(living)
                : stored.flatMap(CompanionEntityVisualBoundsService::storedEffectDimensions).orElse(null);
        BlockPos destination = plan != null && plan.destination() != null ? plan.destination()
                : CompanionSpawnPlacementService.findSummonSpot(player, kind, moveType);
        Vec3 origin = plan == null ? null : plan.origin();
        if (animatedArrival && plan.overheadArrival()) {
            double radius = dimensions == null ? 1.0
                    : Math.max(dimensions.width(), dimensions.depth()) * 0.5;
            origin = Vec3.atBottomCenterOf(destination).add(0.0, Math.max(3.0, radius * 1.65), 0.0);
        }
        BlockPos spawn = animatedArrival
                ? origin != null ? BlockPos.containing(origin)
                : CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, false,
                entityType, null, dimensions)
                : destination;
        LivingEntity deployed;
        if (found instanceof LivingEntity living) {
            deployed = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), spawn,
                    player.getYRot(), player.getXRot(), false);
        } else {
            Entity restored = CompanionLifecycleFacade.restoreStoredDirect(player, data, uuid, spawn,
                    player.getYRot(), player.getXRot(), source + ":direct_restore").orElse(null);
            if (!(restored instanceof LivingEntity living)) {
                return new Result(State.REJECTED, null);
            }
            deployed = living;
        }

        CompanionHomeResidentService.clearResident(deployed);
        if (moveType == CompanionMoveType.FLY) {
            if (deployed instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(false);
            }
            CompanionAnimationHelper.forceFlyingAnimationPose(deployed);
            BookOfDragonsRescueCompatibility.forceAirborne(deployed);
        }
        CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        data.setLastKnownPosition(uuid, SavedPosition.of(deployed.level(), deployed.getX(), deployed.getY(),
                deployed.getZ(), deployed.getYRot(), deployed.getXRot()));
        CompanionDataService.save(player, data);
        if (plan == null) CompanionSyncService.syncToClient(player, kind);
        if (animatedArrival) {
            CompanionDeploymentPlan resolvedPlan = new CompanionDeploymentPlan(plan.operationUuid(), plan.intent(),
                    moveType, destination, origin, plan.targetUuid(), plan.revealOffsetTicks(),
                    plan.overheadArrival());
            if (plan.revealOffsetTicks() == 0) {
                CompanionDeploymentPresentationService.sendSingle(player, deployed, resolvedPlan);
            }
            CompanionSummonApproachService.start(player, deployed, resolvedPlan, plan.revealOffsetTicks() == 0);
            return new Result(State.WAITING, null);
        }
        return new Result(State.READY, deployed);
    }

    public enum Mode {
        RIDDEN,
        AUTONOMOUS
    }

    public enum State {
        READY,
        WAITING,
        STARTED,
        REJECTED
    }

    public record Result(State state, LivingEntity entity) {
    }
}
