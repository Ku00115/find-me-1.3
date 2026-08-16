package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.api.CompanionSpellRole;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.common.CompanionTeamCommandAction;
import com.kuzhi.findme.common.CompanionTeamTarget;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.network.CompanionTacticalFormationPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.animation.CompanionMagicAudioService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Coordinates current-companion-team commands without changing individual command semantics. */
public final class CompanionTeamOrderService {
    private static final int MEMBER_INTERVAL_TICKS = 4;
    private static final int MEMBER_FAILURE_GRACE_TICKS = 12;
    private static final int MEMBER_TIMEOUT_TICKS = 20 * 20;
    private static final int OPERATION_TIMEOUT_TICKS = 20 * 60;
    private static final Map<UUID, TeamOperation> OPERATIONS = new ConcurrentHashMap<>();

    private CompanionTeamOrderService() {
    }

    public static void handle(ServerPlayer player, CompanionTeamTarget target, int teamIndex,
                              CompanionTeamCommandAction action, BlockPos requestedPos, int targetEntityId) {
        if (player == null || target != CompanionTeamTarget.COMPANION || action == null
                || !FindMeModuleService.require(player, FindMeModule.COMPANIONS)) return;
        PlayerCompanionData data = CompanionDataService.data(player);
        TeamOperation previous = OPERATIONS.remove(player.getUUID());
        if (action == CompanionTeamCommandAction.RECALL_ALL) {
            LinkedHashSet<UUID> recall = new LinkedHashSet<>(data.deployedList(CompanionKind.COMPANION));
            if (previous != null) recall.addAll(previous.members());
            recall.removeIf(uuid -> data.lifecycleState(uuid) == CompanionLifecycleState.HOME_ACTIVE
                    || CompanionHomeResidentService.isResident(uuid));
            for (UUID uuid : recall) {
                CompanionTacticalOrderService.cancelTarget(player.getServer(), uuid, "team_recall_requested");
                CompanionTransientStateService.cancelTarget(player, data, uuid,
                        CompanionTransientStateService.Reason.MANUAL_STORE);
            }
            if (recall.isEmpty()) {
                CompanionMessageService.tell(player, "message.find_me.command_no_deployed_team_members",
                        ChatFormatting.YELLOW);
                CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
                return;
            }
            TeamOperation operation = TeamOperation.recall(player.getUUID(), teamIndex, List.copyOf(recall),
                    player.serverLevel().getGameTime());
            OPERATIONS.put(player.getUUID(), operation);
            tickOperation(player.getServer(), player, operation);
            return;
        }
        if (teamIndex < 0 || teamIndex >= data.teamCount(target)) {
            CompanionMessageService.tell(player, "message.find_me.command_no_team_members", ChatFormatting.YELLOW);
            return;
        }
        List<UUID> team = data.team(target, teamIndex).stream()
                .filter(uuid -> data.contains(CompanionKind.COMPANION, uuid))
                .filter(uuid -> data.lifecycleState(uuid) != CompanionLifecycleState.DEAD)
                .toList();
        if (team.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.command_no_team_members", ChatFormatting.YELLOW);
            return;
        }

        if (action.cancelsTacticalAction()) {
            int changed = 0;
            for (UUID uuid : team) {
                if (CompanionTacticalOrderService.currentAction(uuid) == action.tacticalAction()) {
                    changed += CompanionTacticalOrderService.cancelTargetWithoutSync(player.getServer(), uuid,
                            cancellationReason(action));
                }
            }
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            FindMeDebugLogger.info("command-team", "cancel player={} team={} action={} changed={}",
                    player.getUUID(), teamIndex, action, changed);
            return;
        }

        if (previous != null) previous.members().forEach(uuid ->
                CompanionTacticalOrderService.cancelTarget(player.getServer(), uuid, "team_command_replaced"));

        List<UUID> deployed = data.deployedList(CompanionKind.COMPANION);
        if (action == CompanionTeamCommandAction.PAUSE_RESUME) {
            List<UUID> recipients = selectRecipients(team, deployed,
                    Math.min(Config.companionDeploymentLimit, data.companionDeploymentLimit()), false);
            if (recipients.isEmpty()) {
                CompanionMessageService.tell(player, "message.find_me.command_no_deployed_team_members",
                        ChatFormatting.YELLOW);
                return;
            }
            boolean resume = recipients.stream().anyMatch(CompanionTacticalOrderService::isTeamPaused);
            int changed = 0;
            for (UUID uuid : team) {
                if (!deployed.contains(uuid)) continue;
                if (resume) {
                    if (CompanionTacticalOrderService.resumeTeamMember(player.getServer(), uuid)) changed++;
                } else if (CompanionTacticalOrderService.setPaused(player.getServer(), uuid, true)) {
                    changed++;
                } else if (CompanionTacticalOrderService.currentAction(uuid) == null) {
                    CompanionTacticalOrderService.handleTeamMember(player, CompanionKind.COMPANION, uuid,
                            CompanionTacticalAction.HOLD, null, -1);
                    changed++;
                }
            }
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            FindMeDebugLogger.info("command-team", "pause-toggle player={} team={} mode={} recipients={} changed={}",
                    player.getUUID(), teamIndex, resume ? "resume" : "pause", recipients.size(), changed);
            return;
        }

        CompanionTacticalAction tactical = action.tacticalAction();
        if (tactical == null) return;
        CompanionSpellRole requiredRole = CompanionTacticalOrderService.requiredSpellRole(tactical);
        List<UUID> eligibleTeam = requiredRole == null ? team : team.stream()
                .filter(uuid -> data.spellBindings(uuid).stream()
                        .anyMatch(binding -> binding != null && binding.role() == requiredRole))
                .toList();
        int deploymentLimit = Math.min(Config.companionDeploymentLimit, data.companionDeploymentLimit());
        List<UUID> recipients = selectRecipients(eligibleTeam, deployed, deploymentLimit,
                action.deploysTeam());
        if (recipients.isEmpty()) {
            CompanionMessageService.tell(player, requiredRole == null
                            ? "message.find_me.command_no_deployed_team_members"
                            : "message.find_me.command_no_eligible_spell_team_members",
                    ChatFormatting.YELLOW);
            return;
        }
        Map<UUID, BlockPos> guardPosts = action == CompanionTeamCommandAction.GUARD_HERE
                ? guardPosts(player, data, recipients, requestedPos) : Map.of();
        int missingGuardPosts = action == CompanionTeamCommandAction.GUARD_HERE
                ? recipients.size() - guardPosts.size() : 0;
        if (missingGuardPosts > 0) {
            CompanionMessageService.tell(player, "message.find_me.command_invalid_position",
                    ChatFormatting.YELLOW);
            FindMeDebugLogger.info("command-team",
                    "guard slots unavailable player={} team={} missing={} planned={}",
                    player.getUUID(), teamIndex, missingGuardPosts, guardPosts.size());
        }

        ArrayDeque<UUID> queued = new ArrayDeque<>();
        for (UUID uuid : recipients) {
            if (action == CompanionTeamCommandAction.GUARD_HERE && !guardPosts.containsKey(uuid)) {
                continue;
            }
            LivingEntity living = liveMember(player, uuid);
            if (action == CompanionTeamCommandAction.LAND && living != null
                    && CompanionEntityClassifier.summonMoveType(living, CompanionKind.COMPANION)
                    != CompanionMoveType.FLY) continue;
            queued.add(uuid);
        }
        if (!queued.isEmpty()) {
            List<UUID> storedMembers = queued.stream().filter(uuid -> !deployed.contains(uuid)).toList();
            long placementStartedAt = System.nanoTime();
            List<CompanionFormationPlanner.Member> storedFormation = storedMembers.stream()
                    .map(uuid -> formationMember(player, data, uuid)).toList();
            BlockPos deploymentCenter = player.blockPosition();
            if (action == CompanionTeamCommandAction.GUARD_HERE && requestedPos != null) {
                deploymentCenter = requestedPos;
            } else if ((action == CompanionTeamCommandAction.ATTACK_TARGET
                    || action == CompanionTeamCommandAction.MAGIC_ATTACK)
                    && player.level().getEntity(targetEntityId) != null) {
                deploymentCenter = player.level().getEntity(targetEntityId).blockPosition();
            }
            Map<UUID, BlockPos> deploymentPosts = action == CompanionTeamCommandAction.GUARD_HERE
                    ? storedMembers.stream().filter(guardPosts::containsKey)
                    .collect(java.util.stream.Collectors.toMap(uuid -> uuid, guardPosts::get,
                            (left, right) -> left, java.util.LinkedHashMap::new))
                    : CompanionSpawnPlacementService.planTeamSummonSpotsAt(player, deploymentCenter,
                    storedFormation);
            double placementMs = (System.nanoTime() - placementStartedAt) / 1_000_000.0;
            if (action == CompanionTeamCommandAction.GUARD_HERE) {
                queued.removeIf(uuid -> !guardPosts.containsKey(uuid));
            }
            int unplaced = storedMembers.size() - deploymentPosts.size();
            queued.removeIf(uuid -> !deployed.contains(uuid) && !deploymentPosts.containsKey(uuid));
            if (unplaced > 0) {
                CompanionMessageService.tell(player, "message.find_me.command_invalid_position",
                        ChatFormatting.YELLOW);
                FindMeDebugLogger.info("command-team",
                        "deployment slots unavailable player={} team={} action={} unplaced={} planned={}",
                        player.getUUID(), teamIndex, action, unplaced, deploymentPosts.size());
            }
            if (queued.isEmpty()) {
                CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
                return;
            }
            UUID operationUuid = UUID.randomUUID();
            Map<UUID, CompanionDeploymentPlan> deploymentPlans = deploymentPlans(player, data, action,
                    targetEntityId, storedMembers, deploymentPosts, operationUuid);
            TeamOperation operation = TeamOperation.commands(player.getUUID(), teamIndex, action, requestedPos,
                    targetEntityId, queued, guardPosts, deploymentPlans, player.serverLevel().getGameTime());
            OPERATIONS.put(player.getUUID(), operation);
            sendFormationPresentation(player, data, action, requestedPos, operationUuid, deploymentPlans);
            tickOperation(player.getServer(), player, operation);
            FindMeDebugLogger.info("command-team-perf",
                    "planned player={} team={} action={} recipients={} stored={} placementMs={}",
                    player.getUUID(), teamIndex, action, recipients.size(), storedMembers.size(), placementMs);
        }
        FindMeDebugLogger.info("command-team",
                "requested player={} team={} action={} selected={} queued={} limit={}",
                player.getUUID(), teamIndex, action, recipients.size(), queued.size(),
                deploymentLimit);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        for (TeamOperation operation : List.copyOf(OPERATIONS.values())) {
            if (OPERATIONS.get(operation.ownerUuid) != operation) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(operation.ownerUuid);
            if (player == null || server.overworld().getGameTime() - operation.startedAt > OPERATION_TIMEOUT_TICKS) {
                OPERATIONS.remove(operation.ownerUuid, operation);
                if (player != null) CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
                continue;
            }
            tickOperation(server, player, operation);
        }
    }

    static List<UUID> selectRecipients(List<UUID> team, List<UUID> deployed, int limit, boolean fillTeam) {
        if (team == null || team.isEmpty()) return List.of();
        Set<UUID> teamSet = new LinkedHashSet<>(team);
        Set<UUID> deployedSet = new LinkedHashSet<>(deployed == null ? List.of() : deployed);
        ArrayList<UUID> selected = new ArrayList<>();
        for (UUID uuid : teamSet) if (deployedSet.contains(uuid)) selected.add(uuid);
        if (!fillTeam) return List.copyOf(selected);
        int outsideTeam = 0;
        for (UUID uuid : deployedSet) if (!teamSet.contains(uuid)) outsideTeam++;
        int availableForTeam = Math.max(0, Math.max(1, limit) - outsideTeam);
        int targetSize = Math.max(selected.size(), Math.min(teamSet.size(), availableForTeam));
        for (UUID uuid : teamSet) {
            if (selected.size() >= targetSize) break;
            if (!selected.contains(uuid)) selected.add(uuid);
        }
        return List.copyOf(selected);
    }

    static String cancellationReason(CompanionTeamCommandAction action) {
        return switch (action) {
            case CANCEL_PROTECT -> "team_cancel_protect";
            case CANCEL_MAGIC_PROTECT -> "team_cancel_magic_protect";
            case CANCEL_MAGIC_SUPPORT -> "team_cancel_magic_support";
            case CANCEL_GUARD -> "team_cancel_guard";
            default -> "team_cancel";
        };
    }

    static Map<UUID, CompanionFormationPlanner.Offset> formationOffsets(List<CompanionFormationPlanner.Member> members) {
        return CompanionFormationPlanner.planGuardPosts(members);
    }

    private static Map<UUID, BlockPos> guardPosts(ServerPlayer player, PlayerCompanionData data,
                                                   List<UUID> recipients, BlockPos center) {
        if (center == null || recipients.isEmpty()) return Map.of();
        List<CompanionFormationPlanner.Member> members = recipients.stream()
                .map(uuid -> formationMember(player, data, uuid))
                .toList();
        return CompanionSpawnPlacementService.planGuardPosts(player, center, members);
    }

    private static CompanionFormationPlanner.Member formationMember(ServerPlayer player, PlayerCompanionData data,
                                                                      UUID uuid) {
        LivingEntity living = liveMember(player, uuid);
        if (living != null) {
            CompanionMoveType moveType = CompanionEntityClassifier.summonMoveType(living, CompanionKind.COMPANION);
            return new CompanionFormationPlanner.Member(uuid, living.getBbWidth(), living.getBbWidth(),
                    living.getBbHeight(), moveType);
        }
        CompoundTag tag = data.storedEntity(uuid).orElse(null);
        double width = tag != null && tag.contains("CompanionPreviewWidth")
                ? tag.getFloat("CompanionPreviewWidth") : 1.0;
        double depth = tag != null && tag.contains("CompanionPreviewDepth")
                ? tag.getFloat("CompanionPreviewDepth") : width;
        double height = tag != null && tag.contains("CompanionPreviewHeight")
                ? tag.getFloat("CompanionPreviewHeight") : 1.8;
        String type = tag == null ? "" : CompanionEntitySnapshots.storedEntityType(tag);
        CompanionMoveType moveType = CompanionEntityClassifier.summonMoveType(player.getServer(), type,
                CompanionKind.COMPANION);
        return new CompanionFormationPlanner.Member(uuid, width, depth, height, moveType);
    }

    public static void detachMember(UUID ownerUuid, UUID companionUuid) {
        if (ownerUuid == null || companionUuid == null) return;
        TeamOperation operation = OPERATIONS.get(ownerUuid);
        if (operation == null) return;
        operation.queue.remove(companionUuid);
        operation.pending.remove(companionUuid);
        operation.pendingSince.remove(companionUuid);
        if (operation.queue.isEmpty() && operation.pending.isEmpty()) OPERATIONS.remove(ownerUuid, operation);
    }

    public static void cancelPlayer(UUID ownerUuid) {
        if (ownerUuid != null) OPERATIONS.remove(ownerUuid);
    }

    public static void resetServerState() {
        OPERATIONS.clear();
    }

    private static void tickOperation(MinecraftServer server, ServerPlayer player, TeamOperation operation) {
        if (OPERATIONS.get(operation.ownerUuid) != operation) return;
        long now = player.serverLevel().getGameTime();
        PlayerCompanionData data = CompanionDataService.data(player);
        if (operation.action == CompanionTeamCommandAction.RECALL_ALL) {
            processRecall(player, data, operation, now);
            return;
        }
        CompanionTacticalAction tactical = operation.action.tacticalAction();
        operation.pending.removeIf(uuid -> {
            boolean ready = data.isDeployed(CompanionKind.COMPANION, uuid)
                    && CompanionTacticalOrderService.currentAction(uuid) == tactical;
            boolean terminalFailure = now - operation.pendingSince.getOrDefault(uuid, now)
                    >= MEMBER_FAILURE_GRACE_TICKS
                    && CompanionTacticalOrderService.currentAction(uuid) == null
                    && !CompanionTacticalOrderService.hasPendingDeployment(player, uuid)
                    && !CompanionLifecycleFacade.isBusy(player, data, uuid);
            boolean timedOut = now - operation.pendingSince.getOrDefault(uuid, now) >= MEMBER_TIMEOUT_TICKS;
            if (ready || terminalFailure || timedOut) operation.pendingSince.remove(uuid);
            return ready || terminalFailure || timedOut;
        });
        if (operation.queue.isEmpty() && operation.pending.isEmpty()) {
            OPERATIONS.remove(operation.ownerUuid, operation);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            FindMeDebugLogger.info("command-team-perf",
                    "finished player={} team={} action={} elapsedTicks={} processed={}",
                    player.getUUID(), operation.teamIndex, operation.action,
                    now - operation.startedAt, operation.processed);
            return;
        }
        if (now < operation.nextActionAt) return;
        UUID uuid = operation.queue.poll();
        if (uuid == null) {
            operation.nextActionAt = now + MEMBER_INTERVAL_TICKS;
            return;
        }
        if (!data.contains(CompanionKind.COMPANION, uuid)
                || data.lifecycleState(uuid) == CompanionLifecycleState.DEAD) {
            operation.nextActionAt = now;
            return;
        }
        long memberStartedAt = System.nanoTime();
        CompanionTacticalOrderService.handleTeamMember(player, CompanionKind.COMPANION, uuid, tactical,
                operation.guardPosts.getOrDefault(uuid, operation.targetPos), operation.targetEntityId,
                operation.deploymentPlans.get(uuid));
        FindMeDebugLogger.info("command-team-perf",
                "member player={} team={} action={} companion={} elapsedMs={} remaining={}",
                player.getUUID(), operation.teamIndex, operation.action, uuid,
                (System.nanoTime() - memberStartedAt) / 1_000_000.0, operation.queue.size());
        operation.processed++;
        operation.pending.add(uuid);
        operation.pendingSince.put(uuid, now);
        operation.nextActionAt = now + MEMBER_INTERVAL_TICKS;
    }

    private static void processRecall(ServerPlayer player, PlayerCompanionData data,
                                      TeamOperation operation, long now) {
        UUID uuid = operation.queue.peek();
        if (uuid == null) {
            OPERATIONS.remove(operation.ownerUuid, operation);
            return;
        }
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (!data.isDeployed(CompanionKind.COMPANION, uuid) && !(entity instanceof LivingEntity)) {
            operation.queue.poll();
            operation.nextActionAt = now;
            return;
        }
        if (CompanionLifecycleFacade.isBusy(player, data, uuid)) {
            operation.nextActionAt = now + MEMBER_INTERVAL_TICKS;
            return;
        }
        CompanionTacticalOrderService.cancelTarget(player.getServer(), uuid, "team_recall");
        CompanionLifecycleFacade.collectUuid(player, data, CompanionKind.COMPANION, uuid, "team:recall_all");
        operation.queue.poll();
        operation.nextActionAt = now + MEMBER_INTERVAL_TICKS;
    }

    private static LivingEntity liveMember(ServerPlayer player, UUID uuid) {
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        return entity instanceof LivingEntity living && living.isAlive() && living.level() == player.level()
                ? living : null;
    }

    private static final class TeamOperation {
        private final UUID ownerUuid;
        private final int teamIndex;
        private final CompanionTeamCommandAction action;
        private final BlockPos targetPos;
        private final int targetEntityId;
        private final Map<UUID, BlockPos> guardPosts;
        private final Map<UUID, CompanionDeploymentPlan> deploymentPlans;
        private final ArrayDeque<UUID> queue;
        private final Set<UUID> pending = new LinkedHashSet<>();
        private final Map<UUID, Long> pendingSince = new ConcurrentHashMap<>();
        private final long startedAt;
        private long nextActionAt;
        private int processed;

        private TeamOperation(UUID ownerUuid, int teamIndex, CompanionTeamCommandAction action,
                              BlockPos targetPos, int targetEntityId, ArrayDeque<UUID> queue,
                              Map<UUID, BlockPos> guardPosts,
                              Map<UUID, CompanionDeploymentPlan> deploymentPlans,
                              long startedAt) {
            this.ownerUuid = ownerUuid;
            this.teamIndex = teamIndex;
            this.action = action;
            this.targetPos = targetPos;
            this.targetEntityId = targetEntityId;
            this.guardPosts = guardPosts == null ? Map.of() : Map.copyOf(guardPosts);
            this.deploymentPlans = deploymentPlans == null ? Map.of() : Map.copyOf(deploymentPlans);
            this.queue = queue;
            this.startedAt = startedAt;
            this.nextActionAt = action == CompanionTeamCommandAction.RECALL_ALL
                    ? startedAt : startedAt + 8;
        }

        private static TeamOperation commands(UUID ownerUuid, int teamIndex, CompanionTeamCommandAction action,
                                              BlockPos targetPos, int targetEntityId,
                                              ArrayDeque<UUID> queue, Map<UUID, BlockPos> guardPosts,
                                              Map<UUID, CompanionDeploymentPlan> deploymentPlans,
                                              long startedAt) {
            return new TeamOperation(ownerUuid, teamIndex, action, targetPos, targetEntityId, queue,
                    guardPosts, deploymentPlans, startedAt);
        }

        private static TeamOperation recall(UUID ownerUuid, int teamIndex, List<UUID> members, long startedAt) {
            return new TeamOperation(ownerUuid, teamIndex, CompanionTeamCommandAction.RECALL_ALL,
                    null, -1, new ArrayDeque<>(members), Map.of(), Map.of(), startedAt);
        }

        private List<UUID> members() {
            ArrayList<UUID> members = new ArrayList<>(queue);
            for (UUID uuid : pending) if (!members.contains(uuid)) members.add(uuid);
            return members;
        }
    }

    private static Map<UUID, CompanionDeploymentPlan> deploymentPlans(ServerPlayer player,
                                                                      PlayerCompanionData data,
                                                                      CompanionTeamCommandAction action,
                                                                      int targetEntityId,
                                                                      List<UUID> storedMembers,
                                                                      Map<UUID, BlockPos> posts,
                                                                      UUID operationUuid) {
        java.util.LinkedHashMap<UUID, CompanionDeploymentPlan> plans = new java.util.LinkedHashMap<>();
        UUID targetUuid = targetEntityId < 0 || player.level().getEntity(targetEntityId) == null ? null
                : player.level().getEntity(targetEntityId).getUUID();
        CompanionDeploymentPlan.Intent intent = switch (action) {
            case FOLLOW -> CompanionDeploymentPlan.Intent.FOLLOW;
            case PROTECT_OWNER, HEAL_OWNER, MAGIC_PROTECT, MAGIC_SUPPORT -> CompanionDeploymentPlan.Intent.PROTECT;
            case GUARD_HERE -> CompanionDeploymentPlan.Intent.GUARD;
            case ATTACK_TARGET, MAGIC_ATTACK -> CompanionDeploymentPlan.Intent.ATTACK;
            default -> CompanionDeploymentPlan.Intent.ORDINARY;
        };
        int offset = 1;
        for (UUID uuid : storedMembers) {
            BlockPos post = posts.get(uuid);
            if (post == null) continue;
            CompanionFormationPlanner.Member member = formationMember(player, data, uuid);
            boolean overhead = member.moveType() == CompanionMoveType.FLY;
            plans.put(uuid, new CompanionDeploymentPlan(operationUuid, intent, member.moveType(), post,
                    null, targetUuid, offset, overhead));
            offset += MEMBER_INTERVAL_TICKS;
        }
        return Map.copyOf(plans);
    }

    private static void sendFormationPresentation(ServerPlayer player, PlayerCompanionData data,
                                                  CompanionTeamCommandAction action, BlockPos requestedPos,
                                                  UUID operationUuid,
                                                  Map<UUID, CompanionDeploymentPlan> plans) {
        if (plans.isEmpty() || action != CompanionTeamCommandAction.PROTECT_OWNER
                && action != CompanionTeamCommandAction.MAGIC_PROTECT
                && action != CompanionTeamCommandAction.MAGIC_SUPPORT
                && action != CompanionTeamCommandAction.GUARD_HERE
                && action != CompanionTeamCommandAction.FOLLOW
                && action != CompanionTeamCommandAction.ATTACK_TARGET
                && action != CompanionTeamCommandAction.MAGIC_ATTACK) return;
        Vec3 center = action == CompanionTeamCommandAction.GUARD_HERE && requestedPos != null
                ? Vec3.atBottomCenterOf(requestedPos) : player.position();
        ArrayList<CompanionTacticalFormationPacket.Member> members = new ArrayList<>();
        double farthest = 2.0;
        for (Map.Entry<UUID, CompanionDeploymentPlan> entry : plans.entrySet()) {
            CompanionFormationPlanner.Member member = formationMember(player, data, entry.getKey());
            BlockPos destination = entry.getValue().destination();
            float radius = (float)Math.max(0.75, Math.max(member.width(), member.depth()) * 0.5);
            Vec3 point = Vec3.atBottomCenterOf(destination);
            farthest = Math.max(farthest, center.distanceTo(point) + radius);
            members.add(new CompanionTacticalFormationPacket.Member(point.x, point.y, point.z, radius,
                    entry.getValue().revealOffsetTicks(), member.moveType()));
        }
        CompanionDeploymentPlan.Intent intent = plans.values().iterator().next().intent();
        CompanionTacticalFormationPacket packet = new CompanionTacticalFormationPacket(operationUuid, intent,
                center.x, center.y, center.z, (float)Math.min(48.0, farthest), 52, members);
        ModNetwork.sendToPlayersNear(player.serverLevel(), center, 96.0, packet);
        CompanionMagicAudioService.playCircleOpen(player, center, (float)Math.min(4.0, farthest), false);
    }
}
