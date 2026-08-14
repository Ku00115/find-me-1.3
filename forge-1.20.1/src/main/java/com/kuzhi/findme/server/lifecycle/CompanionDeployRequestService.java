package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
        return request(player, data, kind, uuid, mode, source, plan, true);
    }

    public static Result requestExternalTask(ServerPlayer player, PlayerCompanionData data,
                                             CompanionKind kind, UUID uuid, String source) {
        return request(player, data, kind, uuid, Mode.AUTONOMOUS, source, null, false);
    }

    public static Result requestExternalTask(ServerPlayer player, PlayerCompanionData data,
                                             CompanionKind kind, UUID uuid, String source,
                                             CompanionDeploymentPlan plan) {
        return request(player, data, kind, uuid, Mode.AUTONOMOUS, source, plan, false, true);
    }

    public static Result requestExternalTaskSilent(ServerPlayer player, PlayerCompanionData data,
                                                   CompanionKind kind, UUID uuid, String source,
                                                   CompanionDeploymentPlan plan) {
        return request(player, data, kind, uuid, Mode.AUTONOMOUS, source, plan, false, false);
    }

    public static Result requestExternalDeploySilent(ServerPlayer player, PlayerCompanionData data,
                                                     CompanionKind kind, UUID uuid, String source) {
        return request(player, data, kind, uuid, Mode.AUTONOMOUS, source, null, true, false);
    }

    private static Result request(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                  UUID uuid, Mode mode, String source, CompanionDeploymentPlan plan,
                                  boolean countTowardDeploymentLimit) {
        return request(player, data, kind, uuid, mode, source, plan, countTowardDeploymentLimit, true);
    }

    private static Result request(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                  UUID uuid, Mode mode, String source, CompanionDeploymentPlan plan,
                                  boolean countTowardDeploymentLimit, boolean presentationEnabled) {
        if (data.isRecovery(uuid)) {
            return new Result(State.REJECTED, null);
        }
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
            return deployDirect(player, data, kind, uuid, source, plan, countTowardDeploymentLimit,
                    presentationEnabled);
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
                                       UUID uuid, String source, CompanionDeploymentPlan plan,
                                       boolean countTowardDeploymentLimit, boolean presentationEnabled) {
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
        // Autonomous skill casts need the creature's actual movement type.
        // moveType() intentionally collapses ordinary companions to COMPANION,
        // which would place flying dragons on ground spots.
        CompanionMoveType moveType = found instanceof LivingEntity living
                ? CompanionEntityClassifier.summonMoveType(living, kind)
                : CompanionEntityClassifier.summonMoveType(
                        stored.map(CompanionEntitySnapshots::storedEntityType).orElse(""), kind);
        double width = widthFor(found, stored);
        double height = heightFor(found, stored);
        double depth = depthFor(found, stored);
        BlockPos destination = plan != null && plan.destination() != null ? plan.destination()
                : CompanionSpawnPlacementService.findSummonSpot(player, kind, moveType);
        if (plan != null && plan.destination() != null) {
            Optional<BlockPos> safe = moveType == CompanionMoveType.FLY
                    ? CompanionPlacementFinder.findOpenAirSpace(player.serverLevel(), destination,
                    Math.max(width, depth), height)
                    : CompanionPlacementFinder.findOpenDimensionsSpace(player.serverLevel(), width, height,
                    depth, destination);
            if (safe.isEmpty()) {
                FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, found,
                        "STORED", "STORED", "planned_destination_unsafe", true, false);
                return new Result(State.REJECTED, null);
            }
            destination = safe.get();
        }
        // The default spot can also be stale or too small for a stored entity.
        // Validate it with the same entity-sized collision box before moving or
        // restoring anything, so a failed search never falls back underground.
        if (plan == null) {
            Optional<BlockPos> safe = moveType == CompanionMoveType.FLY
                    ? CompanionPlacementFinder.findOpenAirSpace(player.serverLevel(), destination,
                    Math.max(width, depth), height)
                    : CompanionPlacementFinder.findOpenDimensionsSpace(player.serverLevel(), width, height,
                    depth, destination);
            if (safe.isEmpty()) {
                FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, found,
                        "STORED", "STORED", "summon_destination_unsafe", true, false);
                return new Result(State.REJECTED, null);
            }
            destination = safe.get();
        }
        BlockPos spawn = destination;
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
        if (countTowardDeploymentLimit) {
            CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, uuid);
        } else {
            data.clearDeployed(kind, uuid);
        }
        data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        data.setLastKnownPosition(uuid, SavedPosition.of(deployed.level(), deployed.getX(), deployed.getY(),
                deployed.getZ(), deployed.getYRot(), deployed.getXRot()));
        CompanionDataService.save(player, data);
        if (plan == null) CompanionSyncService.syncToClient(player, kind);
        if (presentationEnabled && kind == CompanionKind.COMPANION) {
            CompanionArrivalMagicService.openEffectAt(player, deployed, deployed.position(), player.position(), 42,
                    RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON);
        }
        return new Result(State.READY, deployed);
    }

    private static double storedWidth(CompoundTag tag) {
        return positive(tag, "CompanionPreviewBodyWidth", positive(tag, "CompanionPreviewWidth", 1.0D));
    }

    private static double storedHeight(CompoundTag tag) {
        return positive(tag, "CompanionPreviewBodyHeight", positive(tag, "CompanionPreviewHeight", 1.8D));
    }

    private static double storedDepth(CompoundTag tag) {
        return positive(tag, "CompanionPreviewBodyDepth", positive(tag, "CompanionPreviewDepth", storedWidth(tag)));
    }

    private static double widthFor(Entity found, Optional<CompoundTag> stored) {
        return found instanceof LivingEntity living ? Math.max(1.0D, living.getBbWidth())
                : stored.map(CompanionDeployRequestService::storedWidth).orElse(1.0D);
    }

    private static double heightFor(Entity found, Optional<CompoundTag> stored) {
        return found instanceof LivingEntity living ? Math.max(1.8D, living.getBbHeight())
                : stored.map(CompanionDeployRequestService::storedHeight).orElse(1.8D);
    }

    private static double depthFor(Entity found, Optional<CompoundTag> stored) {
        return found instanceof LivingEntity living ? Math.max(1.0D, living.getBbWidth())
                : stored.map(CompanionDeployRequestService::storedDepth).orElse(widthFor(found, stored));
    }

    private static double positive(CompoundTag tag, String key, double fallback) {
        if (tag == null || !tag.contains(key)) return fallback;
        float value = tag.getFloat(key);
        return Float.isFinite(value) && value > 0.0F ? value : fallback;
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

    public record Result(State state, LivingEntity entity, boolean operationStarted) {
        public Result(State state, LivingEntity entity) {
            this(state, entity, state == State.STARTED);
        }
    }
}
