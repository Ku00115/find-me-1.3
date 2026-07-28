package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.CompanionTacticalFormationPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.animation.CompanionMagicAudioService;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared companion-only deployment presentation. Gameplay state remains owned by the caller. */
final class CompanionDeploymentPresentationService {
    private static final int PRESENTATION_TICKS = 52;

    private CompanionDeploymentPresentationService() {
    }

    static void startOrdinary(ServerPlayer player, LivingEntity companion, CompanionMoveType moveType) {
        if (player == null || companion == null || moveType == null) return;
        BlockPos destination = companion.blockPosition();
        CompanionEntityVisualBoundsService.VisualDimensions dimensions =
                CompanionEntityVisualBoundsService.effectDimensions(companion);
        double radius = Math.max(dimensions.width(), dimensions.depth()) * 0.5;
        Vec3 origin = Vec3.atBottomCenterOf(destination)
                .add(0.0, Math.max(3.0, radius * 1.65), 0.0);
        companion.moveTo(origin.x, origin.y, origin.z, companion.getYRot(), 0.0f);
        companion.setPos(origin.x, origin.y, origin.z);
        CompanionDeploymentPlan plan = new CompanionDeploymentPlan(UUID.randomUUID(),
                CompanionDeploymentPlan.Intent.ORDINARY, moveType, destination, origin, null, 0, true);
        sendSingle(player, companion, plan);
        CompanionSummonApproachService.start(player, companion, plan, true);
    }

    static void sendSingle(ServerPlayer player, LivingEntity companion, CompanionDeploymentPlan plan) {
        if (player == null || companion == null || plan == null || plan.destination() == null) return;
        CompanionEntityVisualBoundsService.VisualDimensions dimensions =
                CompanionEntityVisualBoundsService.effectDimensions(companion);
        float radius = (float)Math.max(0.75, Math.max(dimensions.width(), dimensions.depth()) * 0.5);
        Vec3 center = plan.intent() == CompanionDeploymentPlan.Intent.GUARD
                ? Vec3.atBottomCenterOf(plan.destination()) : player.position();
        Vec3 point = Vec3.atBottomCenterOf(plan.destination());
        float centerRadius = (float)Math.min(48.0, Math.max(2.0, center.distanceTo(point) + radius));
        CompanionTacticalFormationPacket.Member member = new CompanionTacticalFormationPacket.Member(
                point.x, point.y, point.z, radius, plan.revealOffsetTicks(),
                plan.moveType() == null ? CompanionMoveType.WALK : plan.moveType());
        ModNetwork.sendToPlayersNear(player.serverLevel(), center, 96.0,
                new CompanionTacticalFormationPacket(plan.operationUuid(), plan.intent(), center.x, center.y,
                        center.z, centerRadius, PRESENTATION_TICKS, List.of(member)));
        CompanionMagicAudioService.playCircleOpen(player, center, Math.min(centerRadius, 4.0f), false);
    }
}
