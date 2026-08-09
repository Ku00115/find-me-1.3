package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.api.CompanionSpellIntent;
import com.kuzhi.findme.api.CompanionSpellRole;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.network.CompanionTacticalTargetPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionRuntimeIndex;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.safety.CompanionThreatResolver;
import com.kuzhi.findme.server.compat.CompanionFixedPostService;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Summon-first tactical command pipeline followed by one active autonomous order per creature. */
public final class CompanionTacticalOrderService {
    private static final double MAX_TARGET_DISTANCE_SQR = 96.0 * 96.0;
    private static final int REQUEST_TIMEOUT_TICKS = 20 * 20;
    private static final int DEPLOY_RETRY_TICKS = 10;
    private static final int MOVE_TIMEOUT_TICKS = 20 * 30;
    private static final double GUARD_RADIUS = 32.0;
    private static final double GUARD_PURSUIT_RADIUS = CompanionGuardPostService.HARD_RADIUS;
    private static final double FOLLOW_RELOCATE_DISTANCE = 160.0;
    private static final double PROTECT_IDLE_RELOCATE_DISTANCE = 160.0;
    private static final double MAGIC_ATTACK_RETREAT_DISTANCE = 7.0;
    private static final double MAGIC_ATTACK_APPROACH_DISTANCE = 24.0;
    private static final double MAGIC_ATTACK_RETREAT_STEP = 8.0;
    private static final double PROTECT_SCAN_RADIUS = 72.0;
    private static final double PROTECT_PURSUIT_RADIUS = 128.0;
    private static final int THREAT_SCAN_INTERVAL_TICKS = 20;
    private static final int NAVIGATION_REPLAN_INTERVAL_TICKS = 10;
    private static final double NAVIGATION_TARGET_MOVE_SQR = 2.25;
    private static final Map<UUID, CommandRequest> REQUESTS = new HashMap<>();
    private static final Map<UUID, ActiveOrder> ACTIVE = new HashMap<>();
    private static final Map<UUID, Map<UUID, CompanionFormationPlanner.Offset>> FORMATION_CACHE = new HashMap<>();

    private CompanionTacticalOrderService() {
    }

    public static void handle(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                              CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId) {
        if (player != null && companionUuid != null) {
            CompanionTeamOrderService.detachMember(player.getUUID(), companionUuid);
        }
        handleInternal(player, kind, companionUuid, action, requestedPos, targetEntityId);
    }

    static void handleTeamMember(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                                 CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId) {
        handleInternal(player, kind, companionUuid, action, requestedPos, targetEntityId, null);
    }

    static void handleTeamMember(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                                 CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId,
                                 BlockPos plannedSpawn) {
        CompanionDeploymentPlan plan = plannedSpawn == null ? null
                : CompanionDeploymentPlan.tactical(intentFor(action), null, plannedSpawn, null);
        handleInternal(player, kind, companionUuid, action, requestedPos, targetEntityId, plan, true);
    }

    static void handleTeamMember(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                                 CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId,
                                 CompanionDeploymentPlan plan) {
        handleInternal(player, kind, companionUuid, action, requestedPos, targetEntityId, plan, true);
    }

    private static void handleInternal(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                                       CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId) {
        handleInternal(player, kind, companionUuid, action, requestedPos, targetEntityId, null);
    }

    private static void handleInternal(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                                       CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId,
                                       BlockPos plannedSpawn) {
        CompanionDeploymentPlan plan = plannedSpawn == null ? null
                : CompanionDeploymentPlan.tactical(intentFor(action), null, plannedSpawn, null);
        handleInternal(player, kind, companionUuid, action, requestedPos, targetEntityId, plan, false);
    }

    private static void handleInternal(ServerPlayer player, CompanionKind kind, UUID companionUuid,
                                       CompanionTacticalAction action, BlockPos requestedPos, int targetEntityId,
                                       CompanionDeploymentPlan plan, boolean teamBatch) {
        if (player == null || kind == null || action == null) {
            return;
        }
        FindMeModule module = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!FindMeModuleService.require(player, module)) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (companionUuid == null || !data.contains(kind, companionUuid)) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED,
                    CompanionMessageService.label(kind));
            return;
        }
        if (action == CompanionTacticalAction.STOP_CURRENT) {
            int cancelled = cancelTarget(player.getServer(), companionUuid, "player_stop");
            if (cancelled > 0) {
                CompanionMessageService.tell(player, "message.find_me.command_stopped", ChatFormatting.GREEN);
            }
            CompanionSyncService.syncToClient(player, kind);
            return;
        }

        UUID attackTarget = validateRequestIntent(player, companionUuid, action, requestedPos, targetEntityId);
        if ((action == CompanionTacticalAction.MOVE_TO || action == CompanionTacticalAction.GUARD_HERE)
                && !validMoveRequest(player, requestedPos)) {
            CompanionMessageService.tell(player, "message.find_me.command_invalid_position", ChatFormatting.YELLOW);
            return;
        }
        if (isAttackAction(action) && attackTarget == null) {
            CompanionMessageService.tell(player, "message.find_me.command_invalid_target", ChatFormatting.YELLOW);
            return;
        }

        if (isAttackAction(action) && targetEntityId >= 0) {
            ModNetwork.sendToPlayer(player, new CompanionTacticalTargetPacket(targetEntityId, 20 * 20));
        }

        if (plan == null) {
            BlockPos destination = action == CompanionTacticalAction.GUARD_HERE ? requestedPos : null;
            plan = CompanionDeploymentPlan.tactical(intentFor(action), null, destination, attackTarget);
        } else if (plan.targetUuid() == null && attackTarget != null) {
            plan = new CompanionDeploymentPlan(plan.operationUuid(), plan.intent(), plan.moveType(),
                    plan.destination(), plan.origin(), attackTarget, plan.revealOffsetTicks(),
                    plan.overheadArrival());
        }

        cancelActive(player.getServer(), companionUuid, "command_replaced");
        CommandRequest request = new CommandRequest(player.getUUID(), companionUuid, kind, action,
                requestedPos, attackTarget, plan, teamBatch);
        trackRequest(request);
        if (!teamBatch) CompanionSyncService.syncToClient(player, kind);
        FindMeDebugLogger.info("command", "requested player={} companion={} kind={} action={} pos={} target={}",
                player.getUUID(), companionUuid, kind, action, requestedPos, attackTarget);
        advanceRequest(player.getServer(), player, data, request);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (CommandRequest request : List.copyOf(REQUESTS.values())) {
            if (REQUESTS.get(request.companionUuid) != request) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(request.ownerUuid);
            if (owner == null) {
                REQUESTS.remove(request.companionUuid, request);
                continue;
            }
            PlayerCompanionData data = CompanionDataService.data(owner);
            if (!data.contains(request.kind, request.companionUuid)) {
                REQUESTS.remove(request.companionUuid, request);
                continue;
            }
            if (++request.age > REQUEST_TIMEOUT_TICKS) {
                terminateRequest(request, () -> {
                    CompanionSyncService.syncToClient(owner, request.kind);
                    clearTargetOutlineIfIdle(owner, request);
                    CompanionMessageService.tell(owner, "message.find_me.command_summon_failed", ChatFormatting.YELLOW);
                    FindMeMod.LOGGER.warn("[FindMe command] deployment timed out player={} companion={} action={}",
                            request.ownerUuid, request.companionUuid, request.action);
                });
                continue;
            }
            advanceRequest(server, owner, data, request);
        }

        FORMATION_CACHE.clear();
        for (ActiveOrder order : List.copyOf(ACTIVE.values())) {
            if (ACTIVE.get(order.companionUuid) != order) {
                continue;
            }
            if (!tickActive(server, order)) {
                cancelActive(server, order.companionUuid, "order_finished");
            }
        }
    }

    public static int cancelTarget(MinecraftServer server, UUID uuid, String reason) {
        return cancelTarget(server, uuid, reason, true);
    }

    static int cancelTargetWithoutSync(MinecraftServer server, UUID uuid, String reason) {
        return cancelTarget(server, uuid, reason, false);
    }

    private static int cancelTarget(MinecraftServer server, UUID uuid, String reason, boolean syncRequestOwner) {
        CommandRequest request = REQUESTS.remove(uuid);
        int cancelled = request == null ? 0 : 1;
        if (request != null && syncRequestOwner) {
            syncOwner(server, request.ownerUuid, request.kind);
        }
        if (cancelActive(server, uuid, reason)) {
            cancelled++;
        }
        return cancelled;
    }

    public static CompanionTacticalAction currentAction(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        CommandRequest request = REQUESTS.get(uuid);
        if (request != null) {
            return request.action;
        }
        ActiveOrder order = ACTIVE.get(uuid);
        return order == null ? null : order.paused ? CompanionTacticalAction.HOLD : order.action;
    }

    static boolean isTeamPaused(UUID uuid) {
        ActiveOrder order = uuid == null ? null : ACTIVE.get(uuid);
        return order != null && (order.paused || order.action == CompanionTacticalAction.HOLD);
    }

    static boolean setPaused(MinecraftServer server, UUID uuid, boolean paused) {
        ActiveOrder order = uuid == null ? null : ACTIVE.get(uuid);
        if (order == null || order.paused == paused) return false;
        Entity entity = CompanionEntityLookup.findEntity(server, uuid).orElse(null);
        if (!(entity instanceof LivingEntity living)) return false;
        order.paused = paused;
        if (paused) {
            order.holdPosition = living.position();
            clearCombatTarget(living, order);
            if (order.guardPost) {
                CompanionGuardPostService.release(uuid, "team_paused");
                CompanionFixedPostService.release(uuid, CompanionFixedPostService.Reason.GUARD);
            }
            if (order.action == CompanionTacticalAction.PROTECT_OWNER) {
                CompanionProtectIdleService.release(uuid, "team_paused");
            }
        } else {
            order.combatState.rearm();
            restoreMotionFlags(living, order);
            if (order.guardPost && order.targetPos != null && living instanceof Mob mob) {
                CompanionFixedPostService.acquire(living, CompanionFixedPostService.Reason.GUARD);
                CompanionGuardPostService.acquire(mob, order.targetPos, order.moveType);
            }
            if (order.action == CompanionTacticalAction.PROTECT_OWNER && living instanceof Mob mob) {
                CompanionFixedPostService.acquire(living, CompanionFixedPostService.Reason.PROTECT);
                CompanionProtectIdleService.acquire(mob);
            }
        }
        return true;
    }

    static boolean resumeTeamMember(MinecraftServer server, UUID uuid) {
        ActiveOrder order = uuid == null ? null : ACTIVE.get(uuid);
        if (order == null) return false;
        if (!order.paused && order.action == CompanionTacticalAction.HOLD) {
            return cancelActive(server, uuid, "team_resumed");
        }
        return setPaused(server, uuid, false);
    }

    public static boolean controls(UUID uuid) {
        return uuid != null && (REQUESTS.containsKey(uuid) || ACTIVE.containsKey(uuid));
    }

    public static Optional<UUID> currentTargetUuid(UUID uuid) {
        ActiveOrder order = uuid == null ? null : ACTIVE.get(uuid);
        return Optional.ofNullable(order == null ? null : order.attackTarget);
    }

    /** Fast AI-goal hook; guard orders are temporary fixed posts like home residency. */
    public static boolean isGuardingPost(UUID uuid) {
        ActiveOrder order = uuid == null ? null : ACTIVE.get(uuid);
        return order != null && order.action == CompanionTacticalAction.GUARD_HERE;
    }

    static boolean hasPendingDeployment(ServerPlayer player, UUID uuid) {
        if (player == null || uuid == null) {
            return false;
        }
        CommandRequest request = REQUESTS.get(uuid);
        return request != null && request.ownerUuid.equals(player.getUUID());
    }

    public static int cancelPlayer(MinecraftServer server, UUID playerUuid, String reason) {
        int cancelled = 0;
        for (CommandRequest request : List.copyOf(REQUESTS.values())) {
            if (request.ownerUuid.equals(playerUuid) && REQUESTS.remove(request.companionUuid, request)) {
                cancelled++;
            }
        }
        for (ActiveOrder order : List.copyOf(ACTIVE.values())) {
            if (order.ownerUuid.equals(playerUuid) && cancelActive(server, order.companionUuid, reason)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    public static void resetServerState() {
        REQUESTS.clear();
        ACTIVE.clear();
        FORMATION_CACHE.clear();
        CompanionSummonApproachService.reset();
        CompanionProtectIdleService.reset();
        CompanionGuardPostService.reset();
        CompanionFixedPostService.reset();
    }

    /** Gives protect orders the real attacker immediately; periodic scans remain the fallback. */
    public static void onOwnerHurt(ServerPlayer owner, LivingEntity attacker) {
        if (owner == null || attacker == null || attacker == owner || !attacker.isAlive()
                || attacker.level() != owner.level()) {
            return;
        }
        wakeOwnerProtection(owner, attacker, TargetSource.OWNER_DAMAGE, "owner hurt");
    }

    /** Reacts as soon as a nearby mob acquires the owner, before the first attack lands. */
    public static void onOwnerTargeted(ServerPlayer owner, LivingEntity threat) {
        if (owner == null || !hasOwnerProtection(owner.getUUID()) || threat == null || threat == owner
                || owner.isCreative() || owner.isSpectator()
                || !threat.isAlive() || threat.isRemoved() || threat.level() != owner.level()
                || threat.distanceToSqr(owner) > PROTECT_SCAN_RADIUS * PROTECT_SCAN_RADIUS
                || owner.isAlliedTo(threat) || CompanionEntityClassifier.isOwnedBy(owner, threat)
                || CompanionDataService.runtimeIndex(owner).contains(threat.getUUID())) {
            return;
        }
        wakeOwnerProtection(owner, threat, TargetSource.OWNER_TARGETED, "owner targeted");
    }

    private static boolean hasOwnerProtection(UUID ownerUuid) {
        for (ActiveOrder order : ACTIVE.values()) {
            if (order.ownerUuid.equals(ownerUuid) && reactsToOwnerThreat(order.action)) return true;
        }
        return false;
    }

    private static void wakeOwnerProtection(ServerPlayer owner, LivingEntity threat, TargetSource source,
                                              String reason) {
        for (ActiveOrder order : ACTIVE.values()) {
            if (reactsToOwnerThreat(order.action)
                    && order.ownerUuid.equals(owner.getUUID())) {
                order.attackTarget = threat.getUUID();
                order.targetSource = source;
                order.combatState.rearm();
                FindMeDebugLogger.info("command-target",
                        "{} companion={} owner={} threat={} threatType={} source={}",
                        reason, order.companionUuid, owner.getUUID(), threat.getUUID(), threat.getType(), source);
            }
        }
    }

    static boolean reactsToOwnerThreat(CompanionTacticalAction action) {
        return action == CompanionTacticalAction.PROTECT_OWNER || action == CompanionTacticalAction.MAGIC_PROTECT;
    }

    private static void advanceRequest(MinecraftServer server, ServerPlayer owner, PlayerCompanionData data,
                                       CommandRequest request) {
        if (REQUESTS.get(request.companionUuid) != request) {
            return;
        }
        Entity found = CompanionEntityLookup.findEntity(server, request.companionUuid).orElse(null);
        if (found instanceof LivingEntity living && !living.isAlive()) {
            terminateRequest(request, () -> CompanionSyncService.syncToClient(owner, request.kind));
            return;
        }
        LivingEntity ready = CompanionDeployRequestService.readyEntity(owner, data, request.companionUuid,
                CompanionDeployRequestService.Mode.AUTONOMOUS);
        if (ready != null && !CompanionLifecycleFacade.isBusy(owner, data, request.companionUuid)) {
            beginOrder(owner, data, request, ready);
            return;
        }
        if (CompanionLifecycleFacade.isBusy(owner, data, request.companionUuid)
                || owner.serverLevel().getGameTime() < request.nextDeployAttempt) {
            return;
        }

        CompanionDeployRequestService.Result result = CompanionDeployRequestService.request(owner, data,
                request.kind, request.companionUuid, CompanionDeployRequestService.Mode.AUTONOMOUS,
                "command:" + request.action.name().toLowerCase(), request.deploymentPlan);
        request.nextDeployAttempt = owner.serverLevel().getGameTime() + DEPLOY_RETRY_TICKS;
        if (result.state() == CompanionDeployRequestService.State.READY && result.entity() != null) {
            beginOrder(owner, data, request, result.entity());
            return;
        }
        if (result.state() == CompanionDeployRequestService.State.STARTED) {
            request.deployStarted = true;
        }
        if (result.state() == CompanionDeployRequestService.State.REJECTED) {
            terminateRequest(request, () -> {
                CompanionSyncService.syncToClient(owner, request.kind);
                clearTargetOutlineIfIdle(owner, request);
                CompanionMessageService.tell(owner, "message.find_me.command_summon_failed", ChatFormatting.YELLOW);
            });
            FindMeDebugLogger.info("command",
                    "deployment rejected player={} companion={} action={} age={} busy={} started={}",
                    owner.getUUID(), request.companionUuid, request.action, request.age,
                    CompanionLifecycleFacade.isBusy(owner, data, request.companionUuid), request.deployStarted);
            return;
        }
        FindMeDebugLogger.info("command",
                "deployment wait player={} companion={} action={} state={} age={} busy={} started={}",
                owner.getUUID(), request.companionUuid, request.action, result.state(), request.age,
                CompanionLifecycleFacade.isBusy(owner, data, request.companionUuid), request.deployStarted);
    }

    private static void beginOrder(ServerPlayer owner, PlayerCompanionData data, CommandRequest request,
                                   LivingEntity living) {
        if (REQUESTS.get(request.companionUuid) != request) {
            return;
        }
        if (living.level() != owner.level() || !living.isAlive()) {
            return;
        }
        FindMeApi.cancelTemporaryActionsForCompanion(owner.getServer(), living.getUUID(), "command_replaced");
        CompanionSummonApproachService.cancel(living.getUUID(), "tactical_order");
        if (request.action != CompanionTacticalAction.LAND
                && (owner.getVehicle() == living || living.hasPassenger(owner))) {
            owner.stopRiding();
            owner.setDeltaMovement(Vec3.ZERO);
            owner.fallDistance = 0.0f;
        }

        BlockPos targetPos = null;
        UUID attackTarget = request.attackTarget;
        if (request.action == CompanionTacticalAction.MOVE_TO
                || request.action == CompanionTacticalAction.GUARD_HERE) {
            targetPos = validateMoveTarget(owner, living, request.kind, request.requestedPos).orElse(null);
            if (targetPos == null) {
                rejectOrder(owner, request, "message.find_me.command_invalid_position");
                return;
            }
        } else if (isAttackAction(request.action)) {
            Entity target = attackTarget == null ? null
                    : CompanionEntityLookup.findEntity(owner.getServer(), attackTarget).orElse(null);
            if (request.action == CompanionTacticalAction.ATTACK_TARGET && !(living instanceof Mob)) {
                rejectOrder(owner, request, "message.find_me.command_attack_unsupported");
                return;
            }
            if (!(target instanceof LivingEntity targetLiving)
                    || !validAttackTarget(owner, data::contains, living, targetLiving, true)) {
                rejectOrder(owner, request, "message.find_me.command_invalid_target");
                return;
            }
        } else if (request.action == CompanionTacticalAction.LAND) {
            CompanionMoveType moveType = CompanionEntityClassifier.summonMoveType(living, request.kind);
            targetPos = moveType == CompanionMoveType.FLY
                    ? findLandingPosition((ServerLevel)living.level(), living).orElse(null)
                    : BlockPos.containing(living.position());
            if (targetPos == null) {
                rejectOrder(owner, request, "message.find_me.command_invalid_position");
                return;
            }
        }
        CompanionSpellRole requiredRole = requiredSpellRole(request.action);
        if (requiredRole != null && data.spellBindings(living.getUUID()).stream()
                .noneMatch(binding -> binding != null && binding.role() == requiredRole)) {
            rejectOrder(owner, request, unsupportedSpellMessage(request.action));
            return;
        }

        CompanionEscortService.cancelIfEscorting(owner, data, living.getUUID());
        ActiveOrder previous = ACTIVE.remove(living.getUUID());
        if (previous != null) {
            restore(living, previous);
        }
        int side = ((owner.getUUID().getLeastSignificantBits() ^ living.getUUID().getLeastSignificantBits()) & 1L) == 0L
                ? 1 : -1;
        CompanionMoveType moveType = CompanionEntityClassifier.summonMoveType(living, request.kind);
        if (!terminateRequest(request, () -> {})) {
            return;
        }
        ActiveOrder order = new ActiveOrder(owner.getUUID(), living.getUUID(), request.kind, request.action,
                targetPos, attackTarget, living.position(), side, moveType, living.isNoGravity(), living.noPhysics,
                living instanceof Mob mob && mob.isNoAi(), living.getBbWidth(), living.getBbHeight(),
                data.spellBindings(living.getUUID()));
        if (isAttackAction(request.action)) {
            order.targetSource = TargetSource.MANUAL;
        }
        // Threat scans are offset in tickActive. Running one full-radius scan per member here
        // made a team command perform all entity queries in the packet-handling tick.
        ACTIVE.put(living.getUUID(), order);
        if (request.action == CompanionTacticalAction.GUARD_HERE && living instanceof Mob mob) {
            CompanionFixedPostService.acquire(living, CompanionFixedPostService.Reason.GUARD);
            CompanionGuardPostService.acquire(mob, targetPos, moveType);
        } else if ((request.action == CompanionTacticalAction.PROTECT_OWNER
                || request.action == CompanionTacticalAction.MAGIC_PROTECT
                || request.action == CompanionTacticalAction.MAGIC_SUPPORT) && living instanceof Mob mob) {
            CompanionFixedPostService.acquire(living, CompanionFixedPostService.Reason.PROTECT);
            CompanionProtectIdleService.acquire(mob);
        }
        CompanionMessageService.tell(owner, messageKey(request.action), ChatFormatting.GREEN,
                living.getDisplayName().getString());
        if (!request.teamBatch) CompanionSyncService.syncToClient(owner, request.kind);
        FindMeDebugLogger.info("command", "active player={} companion={} action={} pos={} target={}",
                owner.getUUID(), living.getUUID(), request.action, targetPos, attackTarget);
    }

    private static void rejectOrder(ServerPlayer owner, CommandRequest request, String messageKey) {
        terminateRequest(request, () -> {
            CompanionMessageService.tell(owner, messageKey, ChatFormatting.YELLOW);
            CompanionSyncService.syncToClient(owner, request.kind);
            clearTargetOutlineIfIdle(owner, request);
            FindMeMod.LOGGER.warn("[FindMe command] rejected after deployment player={} companion={} action={} reason={}",
                    owner.getUUID(), request.companionUuid, request.action, messageKey);
        });
    }

    static boolean trackRequest(CommandRequest request) {
        return request != null && REQUESTS.put(request.companionUuid, request) != request;
    }

    static boolean terminateRequest(CommandRequest request, Runnable terminalAction) {
        if (request == null || !REQUESTS.remove(request.companionUuid, request)) {
            return false;
        }
        if (terminalAction != null) {
            terminalAction.run();
        }
        return true;
    }

    private static boolean tickActive(MinecraftServer server, ActiveOrder order) {
        ServerPlayer owner = server.getPlayerList().getPlayer(order.ownerUuid);
        Entity found = CompanionEntityLookup.findEntity(server, order.companionUuid).orElse(null);
        if (owner == null || !(found instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        CompanionRuntimeIndex runtime = CompanionDataService.runtimeIndex(owner);
        if (!runtime.contains(order.kind, order.companionUuid)
                || isRuntimeBusy(order.companionUuid, living)
                || living.level() != owner.level()
                || order.action != CompanionTacticalAction.LAND
                && (owner.getVehicle() == living || living.hasPassenger(owner))) {
            return false;
        }

        if (order.paused) {
            hold(living, order);
            order.age++;
            return true;
        }

        switch (order.action) {
            case FOLLOW -> {
                followOwner(owner, living, order);
            }
            case HOLD -> hold(living, order);
            case GUARD_HERE -> guard(owner, runtime, living, order);
            case PROTECT_OWNER -> protectOwner(owner, runtime, living, order);
            case MAGIC_PROTECT -> magicProtect(owner, runtime, living, order);
            case MAGIC_SUPPORT -> magicSupport(owner, living, order);
            case MOVE_TO, LAND -> {
                if (order.targetPos == null) {
                    return false;
                }
                Vec3 target = Vec3.atBottomCenterOf(order.targetPos);
                double completion = Math.max(1.1, living.getBbWidth() * 0.55);
                if (living.position().distanceToSqr(target) <= completion * completion) {
                    restore(living, order);
                    if (order.action == CompanionTacticalAction.LAND) {
                        living.moveTo(target.x, target.y, target.z, living.getYRot(), 0.0f);
                        living.setOnGround(true);
                        CompanionAnimationHelper.forceStandingPose(living);
                    }
                    order.action = CompanionTacticalAction.HOLD;
                    order.holdPosition = living.position();
                    CompanionSyncService.syncToClient(owner, order.kind);
                    return true;
                }
                if (order.action == CompanionTacticalAction.MOVE_TO && order.age >= MOVE_TIMEOUT_TICKS) {
                    return false;
                }
                if (order.action == CompanionTacticalAction.MOVE_TO && order.moveType == CompanionMoveType.WALK
                        && living instanceof Mob mob && !order.originalNoAi) {
                    restoreMotionFlags(living, order);
                    mob.setTarget(null);
                    mob.setAggressive(false);
                    navigateTo(mob, target, 1.15, order);
                } else {
                    CompanionEscortMovementService.control(living, target,
                            order.action == CompanionTacticalAction.LAND ? CompanionMoveType.FLY : order.moveType);
                }
            }
            case ATTACK_TARGET -> {
                Entity target = order.attackTarget == null ? null
                        : CompanionEntityLookup.findEntity(owner.getServer(), order.attackTarget).orElse(null);
                if (!(living instanceof Mob mob) || !(target instanceof LivingEntity targetLiving)
                        || !validAttackTarget(owner, runtime::contains, living, targetLiving, false)) {
                    return false;
                }
                restoreMotionFlags(living, order);
                engageThreat(living, mob, targetLiving, order, TargetSource.MANUAL);
            }
            case MAGIC_ATTACK -> {
                Entity target = order.attackTarget == null ? null
                        : CompanionEntityLookup.findEntity(owner.getServer(), order.attackTarget).orElse(null);
                if (!(target instanceof LivingEntity targetLiving)
                        || !validAttackTarget(owner, runtime::contains, living, targetLiving, false)) {
                    return false;
                }
                restoreMotionFlags(living, order);
                if (FindMeApi.isCastingCompanionSpell(living)) break;
                MagicAttackMovement movement = magicAttackMovement(living.distanceToSqr(targetLiving));
                if (movement == MagicAttackMovement.APPROACH) {
                    CompanionEscortMovementService.control(living, targetLiving.position(), order.moveType);
                    break;
                }
                if (movement == MagicAttackMovement.RETREAT) {
                    CompanionEscortMovementService.control(living,
                            magicAttackRetreatPosition(living, targetLiving), order.moveType);
                    break;
                }
                faceTarget(living, targetLiving);
                if (tryCastSpell(owner, living, order, CompanionSpellIntent.ATTACK, targetLiving)) {
                    order.hasCastSpell = true;
                } else if (!order.hasCastSpell && order.age >= 60) {
                    CompanionMessageService.tell(owner, "message.find_me.command_spell_unavailable", ChatFormatting.YELLOW);
                    return false;
                }
            }
            case HEAL_OWNER -> {
                restoreMotionFlags(living, order);
                if (FindMeApi.isCastingCompanionSpell(living)) break;
                if (living.distanceToSqr(owner) > 64.0) {
                    CompanionEscortMovementService.control(living, owner.position(), order.moveType);
                    break;
                }
                faceTarget(living, owner);
                if (tryCastSpell(owner, living, order, CompanionSpellIntent.HEAL_OWNER, owner)) {
                    order.action = CompanionTacticalAction.FOLLOW;
                    order.age = 0;
                    CompanionSyncService.syncToClient(owner, order.kind);
                } else if (order.age >= 100) {
                    CompanionMessageService.tell(owner, "message.find_me.command_spell_unavailable", ChatFormatting.YELLOW);
                    return false;
                }
            }
            case STOP_CURRENT -> {
                return false;
            }
        }
        order.age++;
        return true;
    }

    private static UUID validateRequestIntent(ServerPlayer player, UUID companionUuid, CompanionTacticalAction action,
                                              BlockPos requestedPos, int targetEntityId) {
        if (!isAttackAction(action)) {
            return null;
        }
        Entity target = player.level().getEntity(targetEntityId);
        if (!(target instanceof LivingEntity living) || living == player
                || living.getUUID().equals(companionUuid)
                || living.getRootVehicle() == player.getRootVehicle()) {
            FindMeDebugLogger.info("command-target",
                    "request rejected player={} companion={} targetId={} target={} reason=self_or_shared_vehicle",
                    player.getUUID(), companionUuid, targetEntityId,
                    target instanceof LivingEntity rejected ? rejected.getUUID() : null);
            return null;
        }
        return living.getUUID();
    }

    private static void followOwner(ServerPlayer owner, LivingEntity living,
                                    ActiveOrder order) {
        restoreMotionFlags(living, order);
        Entity anchor = owner.getVehicle() == null ? owner : owner.getVehicle();
        CompanionFormationPlanner.Offset slot = formationOffset(order, anchor.getBbWidth());
        Vec3 target = CompanionEscortMovementService.followPosition(owner, anchor, living, order.moveType, slot);
        if (living.position().distanceToSqr(target) > FOLLOW_RELOCATE_DISTANCE * FOLLOW_RELOCATE_DISTANCE) {
            CompanionEscortMovementService.moveNearPlayer(owner, CompanionDataService.data(owner), living, target,
                    order.moveType);
        } else {
            CompanionEscortMovementService.control(living, target, order.moveType);
        }
    }

    private static CompanionFormationPlanner.Offset formationOffset(ActiveOrder current, double anchorWidth) {
        Map<UUID, CompanionFormationPlanner.Offset> formation = FORMATION_CACHE.computeIfAbsent(
                current.ownerUuid, ignored -> {
                    List<CompanionFormationPlanner.Member> followers = ACTIVE.values().stream()
                            .filter(order -> order.ownerUuid.equals(current.ownerUuid))
                            .filter(order -> !order.paused)
                            .filter(order -> order.action == CompanionTacticalAction.FOLLOW
                                    || order.action == CompanionTacticalAction.PROTECT_OWNER
                                    && order.attackTarget == null)
                            .map(order -> new CompanionFormationPlanner.Member(order.companionUuid,
                                    order.width, order.width, order.height,
                                    order.moveType == CompanionMoveType.FLY))
                            .toList();
                    return CompanionFormationPlanner.plan(followers, anchorWidth);
                });
        return formation.getOrDefault(current.companionUuid,
                new CompanionFormationPlanner.Offset(current.side * 3.2, 3.2, 0.0));
    }

    private static void guard(ServerPlayer owner, CompanionRuntimeIndex runtime, LivingEntity living,
                              ActiveOrder order) {
        if (order.targetPos == null) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(order.targetPos);
        LivingEntity threat = resolveOrderTarget(owner, runtime, living, order, center,
                GUARD_PURSUIT_RADIUS * GUARD_PURSUIT_RADIUS);
        if ((order.age + order.threatScanOffset) % THREAT_SCAN_INTERVAL_TICKS == 0) {
            threat = CompanionThreatResolver.findAreaThreat(owner, runtime::contains, living, center,
                    living.position(), threat,
                    GUARD_RADIUS, GUARD_PURSUIT_RADIUS).orElse(null);
            order.attackTarget = threat == null ? null : threat.getUUID();
            order.targetSource = threat == null ? TargetSource.NONE : TargetSource.GUARD_SCAN;
            FindMeDebugLogger.info("guard-target",
                    "companion={} post={} selected={} responderDistance={} postDistance={} scanRadius={} hardRadius={}",
                    living.getUUID(), order.targetPos, threat == null ? null : threat.getUUID(),
                    threat == null ? -1.0 : living.distanceTo(threat),
                    threat == null ? -1.0 : Math.sqrt(threat.position().distanceToSqr(center)),
                    GUARD_RADIUS, GUARD_PURSUIT_RADIUS);
        }
        if (threat != null && living instanceof Mob mob) {
            engageThreat(living, mob, threat, order, order.targetSource);
            return;
        }
        order.attackTarget = null;
        order.targetSource = TargetSource.NONE;
        clearCombatTarget(living, order);
        restoreMotionFlags(living, order);
    }

    private static void protectOwner(ServerPlayer owner, CompanionRuntimeIndex runtime, LivingEntity living,
                                     ActiveOrder order) {
        LivingEntity threat = resolveOrderTarget(owner, runtime, living, order, owner.position(),
                PROTECT_PURSUIT_RADIUS * PROTECT_PURSUIT_RADIUS);
        if ((order.age + order.threatScanOffset) % THREAT_SCAN_INTERVAL_TICKS == 0) {
            Optional<CompanionThreatResolver.ProtectThreat> selected =
                    CompanionThreatResolver.findProtectOwnerThreat(owner, runtime::contains, living, threat,
                            PROTECT_SCAN_RADIUS, PROTECT_PURSUIT_RADIUS);
            threat = selected.map(CompanionThreatResolver.ProtectThreat::entity).orElse(null);
            if (threat != null) {
                order.targetSource = TargetSource.PROTECT_SCAN;
                order.targetTier = selected.orElseThrow().tier().name();
                FindMeDebugLogger.info("command-target",
                        "protect select companion={} target={} tier={} current={} distance={}",
                        living.getUUID(), threat.getUUID(), order.targetTier,
                        selected.orElseThrow().current(),
                        String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(selected.orElseThrow().distanceSqr())));
            }
        }
        if (threat != null && living instanceof Mob mob) {
            order.attackTarget = threat.getUUID();
            CompanionProtectIdleService.release(living.getUUID(), "threat_acquired");
            engageThreat(living, mob, threat, order, order.targetSource);
            return;
        }
        order.attackTarget = null;
        order.targetSource = TargetSource.NONE;
        order.targetTier = "none";
        clearCombatTarget(living, order);
        restoreMotionFlags(living, order);
        if (living instanceof Mob mob) {
            CompanionProtectIdleService.acquire(mob);
        }
        if (living.distanceToSqr(owner) > PROTECT_IDLE_RELOCATE_DISTANCE * PROTECT_IDLE_RELOCATE_DISTANCE) {
            Entity anchor = owner.getVehicle() == null ? owner : owner.getVehicle();
            CompanionFormationPlanner.Offset slot = formationOffset(order, anchor.getBbWidth());
            Vec3 target = CompanionEscortMovementService.followPosition(owner, anchor, living,
                    order.moveType, slot);
            CompanionEscortMovementService.moveNearPlayer(owner, CompanionDataService.data(owner), living,
                    target, order.moveType);
        }
    }

    private static void magicProtect(ServerPlayer owner, CompanionRuntimeIndex runtime, LivingEntity living,
                                     ActiveOrder order) {
        LivingEntity threat = resolveOrderTarget(owner, runtime, living, order, owner.position(),
                PROTECT_PURSUIT_RADIUS * PROTECT_PURSUIT_RADIUS);
        if ((order.age + order.threatScanOffset) % THREAT_SCAN_INTERVAL_TICKS == 0) {
            threat = CompanionThreatResolver.findProtectOwnerThreat(owner, runtime::contains, living, threat,
                    PROTECT_SCAN_RADIUS, PROTECT_PURSUIT_RADIUS)
                    .map(CompanionThreatResolver.ProtectThreat::entity).orElse(null);
        }
        magicStayNearOwner(owner, living, order);
        order.attackTarget = threat == null ? null : threat.getUUID();
        if (threat != null && !FindMeApi.isCastingCompanionSpell(living) && order.age % 20 == 0) {
            faceTarget(living, owner);
            tryCastSpell(owner, living, order, CompanionSpellIntent.DEFEND_OWNER, owner);
        }
    }

    private static void magicSupport(ServerPlayer owner, LivingEntity living, ActiveOrder order) {
        magicStayNearOwner(owner, living, order);
        if (owner.getHealth() < owner.getMaxHealth() && !FindMeApi.isCastingCompanionSpell(living)
                && order.age % 20 == 0) {
            faceTarget(living, owner);
            tryCastSpell(owner, living, order, CompanionSpellIntent.HEAL_OWNER, owner);
        }
    }

    private static void magicStayNearOwner(ServerPlayer owner, LivingEntity living, ActiveOrder order) {
        order.attackTarget = null;
        order.targetSource = TargetSource.NONE;
        order.targetTier = "none";
        clearCombatTarget(living, order);
        restoreMotionFlags(living, order);
        if (living instanceof Mob mob) CompanionProtectIdleService.acquire(mob);
        if (living.distanceToSqr(owner) > PROTECT_IDLE_RELOCATE_DISTANCE * PROTECT_IDLE_RELOCATE_DISTANCE) {
            Entity anchor = owner.getVehicle() == null ? owner : owner.getVehicle();
            CompanionFormationPlanner.Offset slot = formationOffset(order, anchor.getBbWidth());
            Vec3 target = CompanionEscortMovementService.followPosition(owner, anchor, living, order.moveType, slot);
            CompanionEscortMovementService.moveNearPlayer(owner, CompanionDataService.data(owner), living,
                    target, order.moveType);
        }
    }

    private static void engageThreat(LivingEntity living, Mob mob, LivingEntity threat,
                                     ActiveOrder order, TargetSource source) {
        living.noPhysics = order.originalNoPhysics;
        living.setNoGravity(order.originalNoGravity);
        if (FindMeApi.ownsTacticalCombat(living)) {
            CompanionTacticalCombatService.suspend(mob, order.combatState, "external_controller");
            return;
        }
        String detail = source == TargetSource.PROTECT_SCAN
                ? source.name() + ':' + order.targetTier : source.name();
        CompanionTacticalCombatService.engage(living, mob, threat, order.combatState, detail);
    }

    private static void clearCombatTarget(LivingEntity living, ActiveOrder order) {
        if (living instanceof Mob mob) {
            CompanionTacticalCombatService.disengage(mob, order.combatState);
        }
    }

    private static LivingEntity resolveOrderTarget(ServerPlayer owner, CompanionRuntimeIndex runtime,
                                                    LivingEntity living, ActiveOrder order, Vec3 center,
                                                    double maxCenterDistanceSqr) {
        Entity target = order.attackTarget == null ? null
                : CompanionEntityLookup.findEntity(owner.getServer(), order.attackTarget).orElse(null);
        if (target instanceof LivingEntity targetLiving
                && targetLiving.position().distanceToSqr(center) <= maxCenterDistanceSqr
                && validAttackTarget(owner, runtime::contains, living, targetLiving, false)) {
            return targetLiving;
        }
        return null;
    }

    private static Optional<BlockPos> validateMoveTarget(ServerPlayer player, LivingEntity living,
                                                          CompanionKind kind, BlockPos requested) {
        if (!validMoveRequest(player, requested)) {
            return Optional.empty();
        }
        CompanionMoveType moveType = CompanionEntityClassifier.summonMoveType(living, kind);
        return CompanionPlacementFinder.findTacticalEntitySpace(player.serverLevel(), living, requested, moveType);
    }

    private static boolean validMoveRequest(ServerPlayer player, BlockPos requested) {
        return requested != null && player.blockPosition().distSqr(requested) <= MAX_TARGET_DISTANCE_SQR
                && player.serverLevel().isLoaded(requested);
    }

    private static Optional<BlockPos> findLandingPosition(ServerLevel level, LivingEntity living) {
        BlockPos origin = living.blockPosition();
        int minimum = Math.max(level.getMinBuildHeight() + 1, origin.getY() - 128);
        for (int y = origin.getY(); y >= minimum; --y) {
            BlockPos candidate = new BlockPos(origin.getX(), y, origin.getZ());
            if (CompanionPlacementFinder.isSafe(level, candidate)
                    && CompanionPlacementFinder.hasOpenEntitySpace(level, living,
                    candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean validAttackTarget(ServerPlayer owner, java.util.function.Predicate<UUID> rosterContains,
                                             LivingEntity source, LivingEntity target,
                                             boolean requireOwnerAim) {
        return target.isAlive() && target != owner && target != source && target.level() == owner.level()
                && source.distanceToSqr(target) <= MAX_TARGET_DISTANCE_SQR
                && (!requireOwnerAim || owner.distanceToSqr(target) <= MAX_TARGET_DISTANCE_SQR)
                && !rosterContains.test(target.getUUID())
                && !CompanionEntityClassifier.isOwnedBy(owner, target)
                && !owner.isAlliedTo(target) && !source.isAlliedTo(target)
                && (!requireOwnerAim || owner.hasLineOfSight(target));
    }

    private static boolean isRuntimeBusy(UUID uuid, LivingEntity living) {
        return CompanionOperationLockService.get(uuid) != null
                || CompanionStorageService.isStoragePending(uuid)
                || CompanionArrivalSequenceService.isPending(living);
    }

    private static void navigateTo(Mob mob, Vec3 target, double speed, ActiveOrder order) {
        if (!shouldReplanNavigation(mob, target, order)) return;
        mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
        rememberNavigation(target, order);
    }

    private static boolean shouldReplanNavigation(Mob mob, Vec3 target, ActiveOrder order) {
        return mob.getNavigation().isDone()
                || order.lastNavigationTarget == null
                || order.age - order.lastNavigationRequestAge >= NAVIGATION_REPLAN_INTERVAL_TICKS
                || order.lastNavigationTarget.distanceToSqr(target) >= NAVIGATION_TARGET_MOVE_SQR;
    }

    private static void rememberNavigation(Vec3 target, ActiveOrder order) {
        order.lastNavigationTarget = target;
        order.lastNavigationRequestAge = order.age;
    }

    private static void hold(LivingEntity living, ActiveOrder order) {
        living.noPhysics = order.originalNoPhysics;
        living.setNoGravity(order.moveType == CompanionMoveType.FLY || order.originalNoGravity);
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            CompanionTacticalCombatService.disengage(mob, order.combatState);
            if (mob.isNoAi() != order.originalNoAi) {
                mob.setNoAi(order.originalNoAi);
            }
        }
        if (order.holdPosition != null && living.position().distanceToSqr(order.holdPosition) > 0.04) {
            living.moveTo(order.holdPosition.x, order.holdPosition.y, order.holdPosition.z,
                    living.getYRot(), living.getXRot());
        }
        living.setDeltaMovement(Vec3.ZERO);
        living.fallDistance = 0.0f;
        living.hurtMarked = true;
    }

    private static boolean cancelActive(MinecraftServer server, UUID uuid, String reason) {
        ActiveOrder order = ACTIVE.remove(uuid);
        if (order == null) {
            return false;
        }
        restore(CompanionEntityLookup.findEntity(server, uuid).orElse(null), order);
        FindMeDebugLogger.info("command", "active order cancelled companion={} action={} reason={}",
                uuid, order.action, reason);
        syncOwner(server, order.ownerUuid, order.kind);
        if (isAttackAction(order.action)) {
            ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(order.ownerUuid);
            if (owner != null && !hasAttackIntent(order.ownerUuid)) {
                ModNetwork.sendToPlayer(owner, new CompanionTacticalTargetPacket(-1, 0));
            }
        }
        return true;
    }

    private static void clearTargetOutlineIfIdle(ServerPlayer owner, CommandRequest completed) {
        if (owner == null || completed == null || !isAttackAction(completed.action)) return;
        if (!hasAttackIntent(owner.getUUID())) {
            ModNetwork.sendToPlayer(owner, new CompanionTacticalTargetPacket(-1, 0));
        }
    }

    private static boolean hasAttackIntent(UUID ownerUuid) {
        return REQUESTS.values().stream().anyMatch(request -> request.ownerUuid.equals(ownerUuid)
                && isAttackAction(request.action))
                || ACTIVE.values().stream().anyMatch(order -> order.ownerUuid.equals(ownerUuid)
                && isAttackAction(order.action));
    }

    private static void syncOwner(MinecraftServer server, UUID ownerUuid, CompanionKind kind) {
        if (server == null || ownerUuid == null || kind == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
        if (owner != null) {
            CompanionSyncService.syncToClient(owner, kind);
        }
    }

    private static void restore(Entity entity, ActiveOrder order) {
        if (order != null && order.guardPost) {
            CompanionGuardPostService.release(order.companionUuid, "order_restore");
            CompanionFixedPostService.release(order.companionUuid, CompanionFixedPostService.Reason.GUARD);
        }
        if (order != null && order.action == CompanionTacticalAction.PROTECT_OWNER) {
            CompanionProtectIdleService.release(order.companionUuid, "order_restore");
            CompanionFixedPostService.release(order.companionUuid, CompanionFixedPostService.Reason.PROTECT);
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        restoreMotionFlags(living, order);
        if (living instanceof Mob mob) {
            CompanionTacticalCombatService.disengage(mob, order.combatState);
            mob.getNavigation().stop();
        }
        living.setDeltaMovement(Vec3.ZERO);
        living.fallDistance = 0.0f;
        living.hurtMarked = true;
    }

    private static void restoreMotionFlags(LivingEntity living, ActiveOrder order) {
        living.noPhysics = order.originalNoPhysics;
        living.setNoGravity(order.originalNoGravity);
        if (living instanceof Mob mob) {
            mob.setNoAi(order.originalNoAi);
        }
    }

    private static String messageKey(CompanionTacticalAction action) {
        return switch (action) {
            case FOLLOW -> "message.find_me.command_following";
            case HOLD -> "message.find_me.command_holding";
            case MOVE_TO -> "message.find_me.command_moving";
            case ATTACK_TARGET -> "message.find_me.command_attacking";
            case MAGIC_ATTACK -> "message.find_me.command_magic_attacking";
            case LAND -> "message.find_me.command_landing";
            case GUARD_HERE -> "message.find_me.command_guarding";
            case PROTECT_OWNER -> "message.find_me.command_protecting";
            case HEAL_OWNER -> "message.find_me.command_healing";
            case MAGIC_PROTECT -> "message.find_me.command_magic_protecting";
            case MAGIC_SUPPORT -> "message.find_me.command_magic_supporting";
            case STOP_CURRENT -> "message.find_me.command_stopped";
        };
    }

    private static CompanionDeploymentPlan.Intent intentFor(CompanionTacticalAction action) {
        return switch (action) {
            case FOLLOW -> CompanionDeploymentPlan.Intent.FOLLOW;
            case PROTECT_OWNER, HEAL_OWNER, MAGIC_PROTECT, MAGIC_SUPPORT -> CompanionDeploymentPlan.Intent.PROTECT;
            case GUARD_HERE, HOLD -> CompanionDeploymentPlan.Intent.GUARD;
            case ATTACK_TARGET, MAGIC_ATTACK -> CompanionDeploymentPlan.Intent.ATTACK;
            default -> CompanionDeploymentPlan.Intent.ORDINARY;
        };
    }

    static final class CommandRequest {
        private final UUID ownerUuid;
        private final UUID companionUuid;
        private final CompanionKind kind;
        private final CompanionTacticalAction action;
        private final BlockPos requestedPos;
        private final UUID attackTarget;
        private final CompanionDeploymentPlan deploymentPlan;
        private final boolean teamBatch;
        private boolean deployStarted;
        private int age;
        private long nextDeployAttempt;

        CommandRequest(UUID ownerUuid, UUID companionUuid, CompanionKind kind,
                       CompanionTacticalAction action, BlockPos requestedPos, UUID attackTarget) {
            this(ownerUuid, companionUuid, kind, action, requestedPos, attackTarget, null);
        }

        CommandRequest(UUID ownerUuid, UUID companionUuid, CompanionKind kind,
                       CompanionTacticalAction action, BlockPos requestedPos, UUID attackTarget,
                       BlockPos plannedSpawn) {
            this(ownerUuid, companionUuid, kind, action, requestedPos, attackTarget,
                    plannedSpawn == null ? null : CompanionDeploymentPlan.tactical(intentFor(action), null,
                            plannedSpawn, attackTarget), false);
        }

        CommandRequest(UUID ownerUuid, UUID companionUuid, CompanionKind kind,
                       CompanionTacticalAction action, BlockPos requestedPos, UUID attackTarget,
                       CompanionDeploymentPlan deploymentPlan, boolean teamBatch) {
            this.ownerUuid = ownerUuid;
            this.companionUuid = companionUuid;
            this.kind = kind;
            this.action = action;
            this.requestedPos = requestedPos;
            this.attackTarget = attackTarget;
            this.deploymentPlan = deploymentPlan;
            this.teamBatch = teamBatch;
        }
    }

    private static final class ActiveOrder {
        private final UUID ownerUuid;
        private final UUID companionUuid;
        private final CompanionKind kind;
        private CompanionTacticalAction action;
        private final BlockPos targetPos;
        private UUID attackTarget;
        private TargetSource targetSource = TargetSource.NONE;
        private String targetTier = "none";
        private Vec3 holdPosition;
        private final int side;
        private final CompanionMoveType moveType;
        private final boolean originalNoGravity;
        private final boolean originalNoPhysics;
        private final boolean originalNoAi;
        private final boolean guardPost;
        private final int threatScanOffset;
        private boolean paused;
        private final double width;
        private final double height;
        private Vec3 lastNavigationTarget;
        private int lastNavigationRequestAge = Integer.MIN_VALUE / 2;
        private final CompanionTacticalCombatService.State combatState = new CompanionTacticalCombatService.State();
        private final List<CompanionSpellBinding> spellBindings;
        private int age;
        private boolean hasCastSpell;
        private int nextSpellSlot;
        private int nextSpellAttemptAge;

        private ActiveOrder(UUID ownerUuid, UUID companionUuid, CompanionKind kind,
                            CompanionTacticalAction action, BlockPos targetPos, UUID attackTarget,
                             Vec3 holdPosition, int side, CompanionMoveType moveType,
                             boolean originalNoGravity, boolean originalNoPhysics, boolean originalNoAi,
                             double width, double height, List<CompanionSpellBinding> spellBindings) {
            this.ownerUuid = ownerUuid;
            this.companionUuid = companionUuid;
            this.kind = kind;
            this.action = action;
            this.targetPos = targetPos;
            this.attackTarget = attackTarget;
            this.holdPosition = holdPosition;
            this.side = side;
            this.moveType = moveType;
            this.originalNoGravity = originalNoGravity;
            this.originalNoPhysics = originalNoPhysics;
            this.originalNoAi = originalNoAi;
            this.width = width;
            this.height = height;
            this.spellBindings = spellBindings == null ? List.of() : spellBindings;
            this.guardPost = action == CompanionTacticalAction.GUARD_HERE;
            this.threatScanOffset = Math.floorMod(companionUuid.hashCode(), THREAT_SCAN_INTERVAL_TICKS);
        }
    }

    private static boolean tryCastSpell(ServerPlayer owner, LivingEntity companion, ActiveOrder order,
                                        CompanionSpellIntent intent, LivingEntity target) {
        if (FindMeApi.isCastingCompanionSpell(companion)) return true;
        if (order.age < order.nextSpellAttemptAge) return false;
        CompanionSpellRole role = switch (intent) {
            case ATTACK -> CompanionSpellRole.ATTACK;
            case DEFEND_OWNER -> CompanionSpellRole.DEFENSE;
            case HEAL_OWNER -> CompanionSpellRole.HEAL;
        };
        int size = order.spellBindings.size();
        for (int offset = 0; offset < size; offset++) {
            int index = Math.floorMod(order.nextSpellSlot + offset, size);
            CompanionSpellBinding binding = order.spellBindings.get(index);
            if (binding != null && binding.role() == role
                    && FindMeApi.tryCastCompanionSpell(owner, companion, binding, intent, target)) {
                order.nextSpellSlot = Math.floorMod(index + 1, Math.max(1, size));
                order.nextSpellAttemptAge = order.age + 10;
                return true;
            }
        }
        order.nextSpellAttemptAge = order.age + 10;
        return false;
    }

    private static boolean isAttackAction(CompanionTacticalAction action) {
        return action == CompanionTacticalAction.ATTACK_TARGET
                || action == CompanionTacticalAction.MAGIC_ATTACK;
    }

    static MagicAttackMovement magicAttackMovement(double distanceSqr) {
        if (distanceSqr < MAGIC_ATTACK_RETREAT_DISTANCE * MAGIC_ATTACK_RETREAT_DISTANCE) {
            return MagicAttackMovement.RETREAT;
        }
        if (distanceSqr > MAGIC_ATTACK_APPROACH_DISTANCE * MAGIC_ATTACK_APPROACH_DISTANCE) {
            return MagicAttackMovement.APPROACH;
        }
        return MagicAttackMovement.CAST;
    }

    private static Vec3 magicAttackRetreatPosition(LivingEntity companion, LivingEntity target) {
        Vec3 away = companion.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 0.001D) {
            Vec3 look = target.getLookAngle();
            horizontal = new Vec3(-look.x, 0.0D, -look.z);
        }
        if (horizontal.lengthSqr() < 0.001D) horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 retreat = companion.position().add(horizontal.normalize().scale(MAGIC_ATTACK_RETREAT_STEP));
        return new Vec3(retreat.x, companion.getY(), retreat.z);
    }

    static CompanionSpellRole requiredSpellRole(CompanionTacticalAction action) {
        return switch (action) {
            case MAGIC_ATTACK -> CompanionSpellRole.ATTACK;
            case MAGIC_PROTECT -> CompanionSpellRole.DEFENSE;
            case HEAL_OWNER, MAGIC_SUPPORT -> CompanionSpellRole.HEAL;
            default -> null;
        };
    }

    private static String unsupportedSpellMessage(CompanionTacticalAction action) {
        return switch (action) {
            case MAGIC_ATTACK -> "message.find_me.command_magic_attack_unsupported";
            case MAGIC_PROTECT -> "message.find_me.command_magic_protect_unsupported";
            case HEAL_OWNER, MAGIC_SUPPORT -> "message.find_me.command_magic_support_unsupported";
            default -> "message.find_me.command_spell_unavailable";
        };
    }

    private static void faceTarget(LivingEntity companion, LivingEntity target) {
        Vec3 delta = target.getEyePosition().subtract(companion.getEyePosition());
        if (delta.lengthSqr() < 0.0001D) return;
        float yaw = (float)(Math.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float)(-(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))
                * 180.0D / Math.PI));
        companion.setYRot(yaw);
        companion.setYHeadRot(yaw);
        companion.yBodyRot = yaw;
        companion.setXRot(pitch);
    }

    private enum TargetSource {
        NONE,
        MANUAL,
        OWNER_DAMAGE,
        OWNER_TARGETED,
        PROTECT_SCAN,
        GUARD_SCAN
    }

    enum MagicAttackMovement {
        RETREAT,
        CAST,
        APPROACH
    }
}
