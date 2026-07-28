package com.kuzhi.findme.server.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CompanionDeploymentPlanTest {
    @Test
    void tacticalDeploymentsUseOverheadArrivalExceptRescue() {
        BlockPos destination = new BlockPos(4, 70, -3);
        assertTrue(CompanionDeploymentPlan.tactical(CompanionDeploymentPlan.Intent.PROTECT,
                CompanionMoveType.WALK, destination, null).overheadArrival());
        assertTrue(CompanionDeploymentPlan.tactical(CompanionDeploymentPlan.Intent.GUARD,
                CompanionMoveType.FLY, destination, null).overheadArrival());
        assertTrue(CompanionDeploymentPlan.tactical(CompanionDeploymentPlan.Intent.FOLLOW,
                CompanionMoveType.WALK, destination, null).overheadArrival());
        assertTrue(CompanionDeploymentPlan.tactical(CompanionDeploymentPlan.Intent.ATTACK,
                CompanionMoveType.FLY, destination, null).overheadArrival());
        assertFalse(CompanionDeploymentPlan.tactical(CompanionDeploymentPlan.Intent.RESCUE,
                CompanionMoveType.FLY, destination, null).overheadArrival());
    }

    @Test
    void resolvingOriginPreservesOperationIdentityAndTiming() {
        UUID operation = UUID.randomUUID();
        CompanionDeploymentPlan plan = new CompanionDeploymentPlan(operation,
                CompanionDeploymentPlan.Intent.PROTECT, CompanionMoveType.WALK,
                BlockPos.ZERO, null, null, 9, true);
        CompanionDeploymentPlan resolved = plan.withOrigin(new Vec3(1.5, 8.0, 2.5));
        assertEquals(operation, resolved.operationUuid());
        assertEquals(9, resolved.revealOffsetTicks());
        assertEquals(new Vec3(1.5, 8.0, 2.5), resolved.origin());
    }
}
