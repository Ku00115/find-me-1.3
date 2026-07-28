package com.kuzhi.findme.server.safety;


import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.lifecycle.CompanionArrivalMagicService;
import com.kuzhi.findme.server.lifecycle.CompanionArrivalSequenceService;
import com.kuzhi.findme.server.lifecycle.CompanionPlacementFinder;
import com.kuzhi.findme.server.lifecycle.CompanionSpawnPlacementService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.CompanionOwnershipGuardService;
import com.kuzhi.findme.server.compat.CompanionNativeCombatIntentService;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

public final class CompanionCombatRescueService {
    private static final int COMBAT_RESCUE_CONTROL_TICKS = 80;
    private static final int MAX_COMBAT_INTENT_ATTEMPTS = 2;
    private static final double SCAN_THREAT_RANGE = 32.0;
    private static final double RECENT_THREAT_RANGE = 40.0;
    private static final double LOST_TARGET_DISTANCE_SQR = 48.0 * 48.0;
    private static final Map<UUID, PendingCombatRescue> PENDING = new HashMap<>();

    private CompanionCombatRescueService() {
    }

    public static Optional<LivingEntity> findThreat(ServerPlayer player) {
        return findThreat(player, CompanionDataService.data(player));
    }

    public static Optional<LivingEntity> findThreat(ServerPlayer player, PlayerCompanionData data) {
        return CompanionThreatResolver.findCombatRescueThreat(player, data,
                SCAN_THREAT_RANGE, RECENT_THREAT_RANGE);
    }

    public static BlockPos findSpawn(ServerPlayer player, LivingEntity companion, CompanionMoveType moveType, LivingEntity threat) {
        if (threat == null || !threat.isAlive() || threat.level() != player.level()) {
            return CompanionSpawnPlacementService.findSummonSpot(player, CompanionKind.COMPANION, moveType);
        }
        if (moveType == CompanionMoveType.SWIM) {
            BlockPos origin = BlockPos.containing(threat.position());
            return CompanionPlacementFinder.findWaterWithOpenSurface(player.serverLevel(), origin).orElseGet(() -> CompanionSpawnPlacementService.findSummonSpot(player, CompanionKind.COMPANION, moveType));
        }
        Vec3 awayFromPlayer = threat.position().subtract(player.position());
        awayFromPlayer = new Vec3(awayFromPlayer.x, 0.0, awayFromPlayer.z);
        if (awayFromPlayer.lengthSqr() < 0.01) {
            awayFromPlayer = player.getLookAngle();
            awayFromPlayer = new Vec3(awayFromPlayer.x, 0.0, awayFromPlayer.z);
        }
        if (awayFromPlayer.lengthSqr() < 0.01) {
            awayFromPlayer = new Vec3(1.0, 0.0, 0.0);
        }
        awayFromPlayer = awayFromPlayer.normalize();
        BlockPos base = BlockPos.containing(threat.position().add(awayFromPlayer.scale(1.8)));
        BlockPos surface = CompanionSpawnPlacementService.walkSurfacePos(player.serverLevel(), base, threat.getY());
        if (companion != null) {
            Optional<BlockPos> open = CompanionSpawnPlacementService.findOpenEntitySpace(player.serverLevel(), companion, surface);
            if (open.isPresent()) {
                return open.get();
            }
        }
        return CompanionPlacementFinder.findSafe(player.serverLevel(), surface).orElse(surface);
    }

    public static void applyArrival(ServerPlayer player, LivingEntity companion, LivingEntity threat, boolean sendArrivalMagic) {
        CompanionRescueProtectionService.protectPlayer(player);
        if (threat != null && threat.isAlive()) {
            enlistDeployedCompanions(player, companion, threat);
            staggerThreat(threat);
            Vec3 away = threat.position().subtract(player.position());
            if (away.lengthSqr() > 0.01) {
                Vec3 push = away.normalize().scale(0.28).add(0.0, 0.05, 0.0);
                threat.setDeltaMovement(threat.getDeltaMovement().add(push));
                threat.hurtMarked = true;
            }
            CompanionArrivalMagicService.openPulse(player, player.position().add(0.0, 0.02, 0.0), 1.85f, 22, RescueMagicPacket.Purpose.RESCUE);
        }
        if (sendArrivalMagic) {
            CompanionArrivalMagicService.sendGround(player, companion, threat == null ? player.position() : threat.position(), 48, RescueMagicPacket.Purpose.RESCUE);
        }
    }

    static void tick(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingCombatRescue>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCombatRescue> entry = iterator.next();
            PendingCombatRescue pending = entry.getValue();
            Entity companionEntity = CompanionEntityLookup.findEntity(server, pending.companionUuid()).orElse(null);
            Entity threatEntity = CompanionEntityLookup.findEntity(server, pending.threatUuid()).orElse(null);
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid());
            if (!(companionEntity instanceof LivingEntity companion) || !(threatEntity instanceof LivingEntity threat) || player == null || !companion.isAlive() || !threat.isAlive()) {
                iterator.remove();
                continue;
            }
            if (CompanionStorageService.isStoragePending(companion) || CompanionHomeResidentService.isResident(companion.getUUID())) {
                cancelRescueControl(companion);
                iterator.remove();
                continue;
            }
            if (!CompanionThreatResolver.isActiveCombatRescueThreat(player, threat)) {
                cancelRescueControl(companion); iterator.remove();
                FindMeDebugLogger.info("rescue-combat", "cancel player={} companion={} threat={} reason=threat_invalid",
                        player.getUUID(), companion.getUUID(), threat.getUUID());
                continue;
            }
            if (CompanionArrivalSequenceService.isPending(companion)) {
                entry.setValue(pending.tickDown());
                continue;
            }
            PendingCombatRescue next = pending;
            if (companion instanceof Mob mob && mob.getTarget() != threat
                    && pending.intentAttempts() < MAX_COMBAT_INTENT_ATTEMPTS) {
                CompanionNativeCombatIntentService.assign(mob, threat);
                next = next.withIntentAttempt();
            }
            next = next.tickDown();
            if (next.remainingTicks() <= 0 || companion.distanceToSqr(threat) > LOST_TARGET_DISTANCE_SQR && threat.distanceToSqr(player) > LOST_TARGET_DISTANCE_SQR) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    public static int cancelForCompanion(ServerPlayer player, UUID companionUuid, String reason) {
        if (companionUuid == null) {
            return 0;
        }
        PendingCombatRescue removed = PENDING.remove(companionUuid);
        if (removed == null) {
            return 0;
        }
        if (player != null) {
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), companionUuid).orElse(null);
            if (entity instanceof LivingEntity living) {
                cancelRescueControl(living);
            }
        }
        FindMeDebugLogger.info("transient", "cancelled combat rescue companion={} reason={}", companionUuid, reason);
        return 1;
    }

    public static int cancelForPlayer(ServerPlayer player, String reason) {
        if (player == null || PENDING.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<UUID, PendingCombatRescue>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingCombatRescue> entry = iterator.next();
            if (!player.getUUID().equals(entry.getValue().playerUuid())) {
                continue;
            }
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), entry.getKey()).orElse(null);
            if (entity instanceof LivingEntity living) {
                cancelRescueControl(living);
            }
            iterator.remove();
            removed++;
        }
        if (removed > 0) {
            FindMeDebugLogger.info("transient", "cancelled player combat rescues player={} reason={} count={}", player.getUUID(), reason, removed);
        }
        return removed;
    }

    private static void enlistDeployedCompanions(ServerPlayer player, LivingEntity arrivingCompanion, LivingEntity threat) {
        PlayerCompanionData data = CompanionDataService.data(player);
        java.util.LinkedHashSet<UUID> responders = new java.util.LinkedHashSet<>(data.deployedList(CompanionKind.COMPANION));
        responders.add(arrivingCompanion.getUUID());
        for (UUID uuid : responders) {
            Entity entity = uuid.equals(arrivingCompanion.getUUID())
                    ? arrivingCompanion
                    : CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
            if (!(entity instanceof LivingEntity responder) || !responder.isAlive() || responder.level() != player.level()
                    || CompanionStorageService.isStoragePending(responder) || CompanionHomeResidentService.isResident(uuid)
                    || !data.contains(CompanionKind.COMPANION, uuid)) {
                continue;
            }
            CompanionRescueProtectionService.protectCompanion(responder);
            int attempts = reinforceRescueIntent(player, responder, threat) ? 1 : 0;
            PENDING.put(uuid, new PendingCombatRescue(player.getUUID(), uuid, threat.getUUID(),
                    COMBAT_RESCUE_CONTROL_TICKS, attempts));
        }
    }

    private static void cancelRescueControl(LivingEntity companion) {
        if (companion instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
        }
    }

    private static void staggerThreat(LivingEntity threat) {
        threat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 34, 1, false, false, true));
        threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 42, 0, false, false, true));
    }

    private static boolean reinforceRescueIntent(ServerPlayer player, LivingEntity companion, LivingEntity threat) {
        if (!CompanionOwnershipGuardService.isRegisteredFor(player, companion)) {
            return false;
        }
        CompanionAnimationHelper.forceStandingPose(companion);
        if (companion instanceof TamableAnimal tamable) {
            tamable.setOrderedToSit(false);
            tamable.setInSittingPose(false);
        }
        faceToward(companion, threat.position());
        if (companion instanceof Mob mob) {
            return CompanionNativeCombatIntentService.assign(mob, threat);
        }
        return false;
    }

    private static void faceToward(LivingEntity living, Vec3 target) {
        double dx = target.x - living.getX();
        double dz = target.z - living.getZ();
        float yaw = Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 57.2957763671875) - 90.0f);
        living.setYRot(yaw);
        living.setYHeadRot(yaw);
        living.yRotO = yaw;
        living.yHeadRot = yaw;
        living.yHeadRotO = yaw;
    }

    private record PendingCombatRescue(UUID playerUuid, UUID companionUuid, UUID threatUuid,
                                       int remainingTicks, int intentAttempts) {
        PendingCombatRescue tickDown() {
            return new PendingCombatRescue(playerUuid, companionUuid, threatUuid,
                    remainingTicks - 1, intentAttempts);
        }

        PendingCombatRescue withIntentAttempt() {
            return new PendingCombatRescue(playerUuid, companionUuid, threatUuid,
                    remainingTicks, intentAttempts + 1);
        }
    }
}

