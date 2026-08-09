package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.safety.CompanionCombatRescueService;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionSummonPreparationService {
    private static final int RECENT_COMBAT_TICKS = 200;
    private CompanionSummonPreparationService() {
    }

    public static Optional<Preparation> prepare(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        return prepare(player, data, kind, false);
    }

    public static Optional<Preparation> prepare(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                                boolean bypassCooldown) {
        long now = player.serverLevel().getGameTime();
        long readyAt = data.readyAt(kind);
        if (!bypassCooldown && now < readyAt) {
            CompanionSummonLineService.showCooldown(player, data, kind, (readyAt - now + 19L) / 20L);
            return Optional.empty();
        }
        boolean inCombat = isInCombat(player);
        Optional<UUID> maybeUuid = data.active(kind);
        if (maybeUuid.isEmpty()) {
            CompanionSummonLineService.showNoRegistered(player, kind);
            return Optional.empty();
        }
        UUID uuid = maybeUuid.get();
        if (data.isRecovery(uuid)) {
            CompanionSummonLineService.showUnavailable(player, data, kind);
            return Optional.empty();
        }
        if (CompanionLifecycleFacade.isBusy(player, data, uuid)) {
            CompanionSummonLineService.showBusy(player, data, kind, uuid);
            return Optional.empty();
        }
        if (kind == CompanionKind.COMPANION && CompanionCollectionService.collectActiveShoulderCompanion(player, data, uuid)) {
            return Optional.empty();
        }
        CompanionRescuePlanner.Plan rescuePlan = kind == CompanionKind.MOUNT && player.getVehicle() == null
                ? CompanionRescuePlanner.plan(player) : CompanionRescuePlanner.Plan.none();
        boolean fallingRescue = rescuePlan.shouldRescue();
        LivingEntity companionRescueTarget = kind == CompanionKind.COMPANION
                ? CompanionCombatRescueService.findThreat(player, data).orElse(null) : null;
        if (fallingRescue) {
            com.kuzhi.findme.server.core.FindMeDebugLogger.info("rescue-plan",
                    "player={} urgency={} groundDistance={} impactTicks={} catchTicks={} verticalVelocity={} expectedDamage={} reliable={} water={}",
                    player.getUUID(), rescuePlan.urgency(), rescuePlan.groundDistance(),
                    rescuePlan.ticksToImpact(), rescuePlan.ticksToCatch(), CompanionRescuePlanner.estimatedVerticalVelocity(player), rescuePlan.expectedDamage(),
                    rescuePlan.reliableLanding(), rescuePlan.waterLanding());
            player.invulnerableTime = Math.max(player.invulnerableTime,
                    Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        }
        return Optional.of(new Preparation(now, uuid, inCombat, fallingRescue, companionRescueTarget));
    }

    private static boolean isInCombat(ServerPlayer player) {
        return player.getLastHurtByMob() != null
                && player.tickCount - player.getLastHurtByMobTimestamp() <= RECENT_COMBAT_TICKS
                || player.getLastHurtMob() != null
                && player.tickCount - player.getLastHurtMobTimestamp() <= RECENT_COMBAT_TICKS;
    }

    public record Preparation(long now, UUID uuid, boolean inCombat, boolean fallingRescue, LivingEntity companionRescueTarget) {
    }
}


