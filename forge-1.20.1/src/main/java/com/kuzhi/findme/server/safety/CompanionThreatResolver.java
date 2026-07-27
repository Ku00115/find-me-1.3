package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared bounded threat recognition and scoring for rescue and persistent tactical orders. */
public final class CompanionThreatResolver {
    private static final int RECENT_COMBAT_TICKS = 200;
    private static final double CURRENT_TARGET_BONUS = 24.0;

    private CompanionThreatResolver() {
    }

    public static Optional<LivingEntity> findOwnerThreat(ServerPlayer owner, PlayerCompanionData data,
                                                          LivingEntity responder, LivingEntity current,
                                                          double range) {
        return findOwnerThreat(owner, data, responder, current, range, range);
    }

    public static Optional<LivingEntity> findOwnerThreat(ServerPlayer owner, PlayerCompanionData data,
                                                          LivingEntity responder, LivingEntity current,
                                                          double scanRadius, double pursuitRadius) {
        return findOwnerThreat(owner, data::contains, responder, current, scanRadius, pursuitRadius);
    }

    public static Optional<LivingEntity> findOwnerThreat(ServerPlayer owner, Predicate<UUID> rosterContains,
                                                          LivingEntity responder, LivingEntity current,
                                                          double scanRadius, double pursuitRadius) {
        return findOwnerThreat(owner, rosterContains, responder, current, scanRadius, pursuitRadius, true);
    }

    public static Optional<LivingEntity> findImmediateOwnerThreat(ServerPlayer owner, PlayerCompanionData data,
                                                                   LivingEntity responder, LivingEntity current,
                                                                   double scanRadius, double pursuitRadius) {
        return findOwnerThreat(owner, data::contains, responder, current, scanRadius, pursuitRadius, false);
    }

    private static Optional<LivingEntity> findOwnerThreat(ServerPlayer owner, Predicate<UUID> rosterContains,
                                                           LivingEntity responder, LivingEntity current,
                                                           double scanRadius, double pursuitRadius,
                                                           boolean includeAreaHostiles) {
        Vec3 center = owner.position();
        Set<LivingEntity> candidates = new LinkedHashSet<>();
        addIfValid(candidates, owner, rosterContains, responder, current, center, pursuitRadius, includeAreaHostiles);
        CompanionThreatMemoryService.findRecentThreat(owner, pursuitRadius)
                .ifPresent(candidate -> addIfValid(candidates, owner, rosterContains, responder, candidate,
                        center, pursuitRadius, includeAreaHostiles));
        addIfRecent(candidates, owner, rosterContains, responder, owner.getLastHurtByMob(),
                owner.getLastHurtByMobTimestamp(), center, pursuitRadius, includeAreaHostiles);
        addIfRecent(candidates, owner, rosterContains, responder, owner.getLastHurtMob(),
                owner.getLastHurtMobTimestamp(), center, pursuitRadius, includeAreaHostiles);
        AABB area = owner.getBoundingBox().inflate(scanRadius, Math.min(scanRadius, 12.0), scanRadius);
        for (Mob candidate : owner.serverLevel().getEntitiesOfClass(Mob.class, area)) {
            addIfValid(candidates, owner, rosterContains, responder, candidate, center, scanRadius, includeAreaHostiles);
        }
        return choose(owner, rosterContains, responder, current, candidates, center);
    }

    public static Optional<LivingEntity> findAreaThreat(ServerPlayer owner, PlayerCompanionData data,
                                                         LivingEntity responder, Vec3 center,
                                                         LivingEntity current, double scanRadius,
                                                         double pursuitRadius) {
        return findAreaThreat(owner, data::contains, responder, center, current, scanRadius, pursuitRadius);
    }

    public static Optional<LivingEntity> findAreaThreat(ServerPlayer owner, Predicate<UUID> rosterContains,
                                                         LivingEntity responder, Vec3 center,
                                                         LivingEntity current, double scanRadius,
                                                         double pursuitRadius) {
        return findAreaThreat(owner, rosterContains, responder, center, center, current, scanRadius,
                pursuitRadius);
    }

    public static Optional<LivingEntity> findAreaThreat(ServerPlayer owner, Predicate<UUID> rosterContains,
                                                         LivingEntity responder, Vec3 scanCenter, Vec3 priorityCenter,
                                                         LivingEntity current, double scanRadius,
                                                         double pursuitRadius) {
        Set<LivingEntity> candidates = new LinkedHashSet<>();
        addIfValid(candidates, owner, rosterContains, responder, current, scanCenter, pursuitRadius, true);
        AABB area = new AABB(scanCenter, scanCenter).inflate(scanRadius, Math.min(scanRadius, 8.0), scanRadius);
        for (Mob candidate : owner.serverLevel().getEntitiesOfClass(Mob.class, area)) {
            addIfValid(candidates, owner, rosterContains, responder, candidate, scanCenter, scanRadius, true);
        }
        return choose(owner, rosterContains, responder, current, candidates,
                priorityCenter == null ? scanCenter : priorityCenter);
    }

    private static Optional<LivingEntity> choose(ServerPlayer owner, Predicate<UUID> rosterContains,
                                                  LivingEntity responder, LivingEntity current,
                                                  Set<LivingEntity> candidates, Vec3 center) {
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        LivingEntity remembered = CompanionThreatMemoryService.findRecentThreat(owner, 96.0).orElse(null);
        for (LivingEntity candidate : candidates) {
            double score = score(owner, rosterContains, responder, candidate, center, candidate == current,
                    candidate == remembered);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static double score(ServerPlayer owner, Predicate<UUID> rosterContains, LivingEntity responder,
                                LivingEntity candidate, Vec3 center, boolean current,
                                boolean remembered) {
        double score = 24.0 - Math.sqrt(candidate.position().distanceToSqr(center));
        if (candidate instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target == owner) {
                score += 100.0;
            } else if (target == responder || target != null && rosterContains.test(target.getUUID())) {
                score += 72.0;
            }
        }
        if (remembered) {
            score += 86.0;
        }
        if (owner.getLastHurtByMob() == candidate
                && owner.tickCount - owner.getLastHurtByMobTimestamp() <= RECENT_COMBAT_TICKS) {
            score += 82.0;
        }
        if (owner.getLastHurtMob() == candidate
                && owner.tickCount - owner.getLastHurtMobTimestamp() <= RECENT_COMBAT_TICKS) {
            score += 36.0;
        }
        if (candidate instanceof Enemy) {
            score += 20.0;
        }
        if (isLookingAt(candidate, owner)) {
            score += 14.0;
        }
        if (isApproaching(candidate, owner)) {
            score += 8.0;
        }
        return current ? score + CURRENT_TARGET_BONUS : score;
    }

    private static boolean isRecognizedThreat(ServerPlayer owner, Predicate<UUID> rosterContains,
                                               LivingEntity responder, LivingEntity candidate,
                                               boolean includeAreaHostiles) {
        if (candidate instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target == owner || target == responder || target != null && rosterContains.test(target.getUUID())) {
                return true;
            }
        }
        if ((candidate == owner.getLastHurtByMob()
                && owner.tickCount - owner.getLastHurtByMobTimestamp() <= RECENT_COMBAT_TICKS)
                || (candidate == owner.getLastHurtMob()
                && owner.tickCount - owner.getLastHurtMobTimestamp() <= RECENT_COMBAT_TICKS)) {
            return true;
        }
        boolean declaredHostile = candidate instanceof Enemy
                || candidate.getType().getCategory() == MobCategory.MONSTER;
        return declaredHostile && (includeAreaHostiles || isLookingAt(candidate, owner)
                || isApproaching(candidate, owner));
    }

    private static void addIfRecent(Set<LivingEntity> candidates, ServerPlayer owner,
                                    Predicate<UUID> rosterContains, LivingEntity responder,
                                    LivingEntity candidate, int timestamp, Vec3 center,
                                    double range, boolean includeAreaHostiles) {
        if (candidate != null && owner.tickCount - timestamp <= RECENT_COMBAT_TICKS) {
            addIfValid(candidates, owner, rosterContains, responder, candidate, center, range,
                    includeAreaHostiles);
        }
    }

    private static void addIfValid(Set<LivingEntity> candidates, ServerPlayer owner,
                                   Predicate<UUID> rosterContains, LivingEntity responder,
                                   LivingEntity candidate, Vec3 center, double range,
                                   boolean includeAreaHostiles) {
        if (valid(owner, rosterContains, responder, candidate, center, range)
                && isRecognizedThreat(owner, rosterContains, responder, candidate, includeAreaHostiles)) {
            candidates.add(candidate);
        }
    }

    private static boolean valid(ServerPlayer owner, Predicate<UUID> rosterContains, LivingEntity responder,
                                 LivingEntity candidate, Vec3 center, double range) {
        return candidate != null && candidate != owner && candidate != responder
                && !(candidate instanceof Player) && candidate.isAlive() && !candidate.isRemoved()
                && candidate.level() == owner.level()
                && candidate.position().distanceToSqr(center) <= range * range
                && !rosterContains.test(candidate.getUUID())
                && !CompanionEntityClassifier.isOwnedBy(owner, candidate)
                && !owner.isAlliedTo(candidate)
                && (responder == null || !responder.isAlliedTo(candidate));
    }

    private static boolean isLookingAt(LivingEntity candidate, ServerPlayer owner) {
        Vec3 look = candidate.getLookAngle();
        Vec3 toOwner = owner.getEyePosition().subtract(candidate.getEyePosition());
        return look.lengthSqr() > 0.001 && toOwner.lengthSqr() > 0.001
                && look.normalize().dot(toOwner.normalize()) > 0.74
                && candidate.hasLineOfSight(owner);
    }

    private static boolean isApproaching(LivingEntity candidate, ServerPlayer owner) {
        Vec3 motion = candidate.getDeltaMovement();
        Vec3 toOwner = owner.position().subtract(candidate.position());
        return motion.horizontalDistanceSqr() > 0.0025 && toOwner.horizontalDistanceSqr() > 0.01
                && motion.normalize().dot(toOwner.normalize()) > 0.45;
    }
}
