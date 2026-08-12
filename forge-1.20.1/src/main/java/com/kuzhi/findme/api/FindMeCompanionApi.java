package com.kuzhi.findme.api;

import com.kuzhi.findme.server.api.CompanionDescriptorService;
import com.kuzhi.findme.server.api.CompanionActionRequestService;
import com.kuzhi.findme.server.api.ExternalActionLeaseService;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentPlan;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import com.kuzhi.findme.common.SmallHouseBlockEntity;

/** Stable companion-task API for FindMe addons. */
public final class FindMeCompanionApi {
    private FindMeCompanionApi() {
    }

    public static Optional<CompanionDescriptor> descriptor(MinecraftServer server, UUID ownerUuid,
                                                            UUID companionUuid) {
        return CompanionDescriptorService.describe(server, ownerUuid, companionUuid);
    }

    public static List<CompanionDescriptor> companions(MinecraftServer server, UUID ownerUuid) {
        return CompanionDescriptorService.describeAll(server, ownerUuid);
    }

    public static Optional<UUID> ownerUuid(MinecraftServer server, UUID companionUuid) {
        return CompanionDescriptorService.ownerUuid(server, companionUuid);
    }

    public static Optional<CompanionHouseDescriptor> house(MinecraftServer server, UUID houseId) {
        if (server == null || houseId == null) return Optional.empty();
        return FindMeWorldSavedData.get(server).house(houseId)
                .map(house -> new CompanionHouseDescriptor(house.houseId(), house.owner(), house.position(),
                        house.displayName(), house.residents()));
    }

    public static Optional<LivingEntity> liveEntity(MinecraftServer server, UUID ownerUuid, UUID companionUuid) {
        if (server == null || ownerUuid == null || companionUuid == null
                || !FindMeWorldSavedData.get(server).companionRuntimeIndex(ownerUuid).contains(companionUuid)) {
            return Optional.empty();
        }
        return CompanionEntityLookup.findEntity(server, companionUuid)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(LivingEntity::isAlive);
    }

    /** Claims a freshly placed FindMe house without exposing its block entity implementation to addons. */
    public static Optional<CompanionHouseDescriptor> claimHouseAt(ServerPlayer owner, BlockPos pos) {
        if (owner == null || pos == null || !owner.serverLevel().hasChunkAt(pos)) return Optional.empty();
        if (!(owner.serverLevel().getBlockEntity(pos) instanceof SmallHouseBlockEntity house)
                || house.owner() != null && !owner.getUUID().equals(house.owner())) return Optional.empty();
        house.initializeOwner(owner);
        return house(owner.getServer(), house.houseId());
    }

    public static Optional<ExternalActionLease> tryAcquireExternalAction(ServerPlayer owner,
                                                                         UUID companionUuid,
                                                                         ResourceLocation actionId,
                                                                         int priority,
                                                                         int timeoutTicks) {
        return ExternalActionLeaseService.acquire(owner, companionUuid, actionId, priority, timeoutTicks);
    }

    /**
     * Claims one bounded action inside FindMe's active tactical order without replacing that order.
     * The lease remains valid only while the exact tactical target is unchanged.
     */
    public static Optional<ExternalActionLease> tryAcquireTacticalAction(ServerPlayer owner,
                                                                         UUID companionUuid,
                                                                         UUID targetUuid,
                                                                         ResourceLocation actionId,
                                                                         int priority,
                                                                         int timeoutTicks) {
        return ExternalActionLeaseService.acquireTactical(owner, companionUuid, targetUuid, actionId,
                priority, timeoutTicks);
    }

    public static CompanionActionRequest requestDeploy(ServerPlayer owner, UUID companionUuid,
                                                        UUID requestId, int timeoutTicks) {
        return CompanionActionRequestService.submit(owner, companionUuid, requestId,
                CompanionActionRequest.Action.DEPLOY, timeoutTicks);
    }

    /**
     * Deploys a companion for a bounded addon task without consuming the player's normal deployment limit.
     * The task owner must release companions it temporarily deployed through {@link #requestStore}.
     */
    public static CompanionActionRequest requestTaskDeploy(ServerPlayer owner, UUID companionUuid,
                                                            UUID requestId, int timeoutTicks) {
        return CompanionActionRequestService.submit(owner, companionUuid, requestId,
                CompanionActionRequest.Action.TASK_DEPLOY, timeoutTicks);
    }

    /**
     * Deploys a temporary task companion at an addon-provided tactical point.
     * FindMe still owns the lifecycle and may adjust the point to a safe space.
     */
    public static CompanionActionRequest requestTaskDeploy(ServerPlayer owner, UUID companionUuid,
                                                            UUID requestId, int timeoutTicks,
                                                            CompanionDeploymentPlan plan) {
        return CompanionActionRequestService.submit(owner, companionUuid, requestId,
                CompanionActionRequest.Action.TASK_DEPLOY, timeoutTicks, plan);
    }

    public static CompanionActionRequest requestStore(ServerPlayer owner, UUID companionUuid,
                                                       UUID requestId, int timeoutTicks) {
        return CompanionActionRequestService.submit(owner, companionUuid, requestId,
                CompanionActionRequest.Action.STORE, timeoutTicks);
    }

    public static Optional<CompanionActionRequest> requestStatus(MinecraftServer server, UUID ownerUuid,
                                                                  UUID requestId) {
        return CompanionActionRequestService.status(server, ownerUuid, requestId);
    }
}
