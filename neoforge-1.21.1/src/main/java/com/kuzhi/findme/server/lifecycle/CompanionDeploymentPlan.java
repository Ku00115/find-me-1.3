package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Immutable presentation and movement intent for one autonomous companion deployment. */
public record CompanionDeploymentPlan(UUID operationUuid, Intent intent, CompanionMoveType moveType,
                                      BlockPos destination, Vec3 origin, UUID targetUuid,
                                      int revealOffsetTicks, boolean overheadArrival) {
    public CompanionDeploymentPlan {
        intent = intent == null ? Intent.ORDINARY : intent;
        revealOffsetTicks = Math.max(0, revealOffsetTicks);
    }

    public static CompanionDeploymentPlan tactical(Intent intent, CompanionMoveType moveType,
                                                    BlockPos destination, UUID targetUuid) {
        return new CompanionDeploymentPlan(UUID.randomUUID(), intent, moveType, destination, null,
                targetUuid, 0, intent != Intent.RESCUE);
    }

    public CompanionDeploymentPlan withOrigin(Vec3 value) {
        return new CompanionDeploymentPlan(operationUuid, intent, moveType, destination, value,
                targetUuid, revealOffsetTicks, overheadArrival);
    }

    public enum Intent { ORDINARY, FOLLOW, PROTECT, GUARD, ATTACK, RESCUE }
}
