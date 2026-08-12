package com.kuzhi.findme.api;

import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.compat.CompanionFixedPostService;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Public handoff boundary for explicit addon tasks that supersede earlier companion actions. */
public final class ExternalActionControlApi {
    private ExternalActionControlApi() {
    }

    /**
     * Cancels temporary and tactical actions owned by the same player before an addon requests a lease.
     * Call this only for a user-confirmed task, immediately before {@link FindMeCompanionApi#tryAcquireExternalAction}.
     */
    public static boolean prepare(ServerPlayer owner, UUID companionUuid, ResourceLocation actionId) {
        if (owner == null || companionUuid == null || actionId == null) {
            return false;
        }
        CompanionDescriptor descriptor = FindMeCompanionApi
                .descriptor(owner.getServer(), owner.getUUID(), companionUuid).orElse(null);
        if (descriptor == null) {
            return false;
        }
        String reason = "external_action:" + actionId;
        FindMeApi.cancelTemporaryActionsForCompanion(owner.getServer(), companionUuid, reason);
        CompanionTacticalOrderService.cancelTarget(owner.getServer(), companionUuid, reason);

        // A previous FindMe deploy/store transaction can leave a lock behind after the
        // entity has already reached the stable DEPLOYED state. That lock must be
        // handed off before an addon can acquire its bounded external-action lease.
        CompanionOperationLockService.ActiveOperation operation =
                CompanionOperationLockService.get(companionUuid);
        if (descriptor.live() && descriptor.deployed()
                && descriptor.lifecycle() == CompanionLifecycleState.DEPLOYED
                && operation != null
                && operation.operation() != CompanionOperationLockService.Operation.EXTERNAL
                && owner.getUUID().equals(operation.playerUuid())) {
            CompanionOperationLockService.clear(owner, companionUuid, reason + ":stale_findme_lock");
        }

        // Do not let a failed or superseded external lease keep the entity in a
        // fixed-post suspension after the public handoff point has been reached.
        CompanionFixedPostService.release(companionUuid, CompanionFixedPostService.Reason.EXTERNAL_ACTION);
        return true;
    }
}
