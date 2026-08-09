package com.kuzhi.findme.api;

import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
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
        if (owner == null || companionUuid == null || actionId == null
                || FindMeCompanionApi.descriptor(owner.getServer(), owner.getUUID(), companionUuid).isEmpty()) {
            return false;
        }
        String reason = "external_action:" + actionId;
        FindMeApi.cancelTemporaryActionsForCompanion(owner.getServer(), companionUuid, reason);
        CompanionTacticalOrderService.cancelTarget(owner.getServer(), companionUuid, reason);
        return true;
    }
}
