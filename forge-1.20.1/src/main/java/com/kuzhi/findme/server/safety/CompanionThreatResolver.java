package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.kuzhi.findme.server.core.FindMePerformanceMonitor;

/** Shared bounded threat recognition and scoring for rescue and persistent tactical orders. */
public final class CompanionThreatResolver {
    private static final int RECENT_COMBAT_TICKS = 200;
    private static final double CURRENT_TARGET_BONUS = 24.0;
    private static final Map<ServerLevel, ScanWindow> SCAN_WINDOWS = new WeakHashMap<>();

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

    public static Optional<LivingEntity> findCombatRescueThreat(ServerPlayer owner, PlayerCompanionData data,
                                                                 double scanRadius, double pursuitRadius) {
        if (owner == null || owner.isCreative() || owner.isSpectator()) return Optional.empty();
        Set<LivingEntity> candidates = new LinkedHashSet<>();
        Vec3 center = owner.position();
        CompanionThreatMemoryService.findRecentThreat(owner, pursuitRadius)
                .ifPresent(candidate -> addCombatRescueCandidate(candidates, owner, data::contains, candidate, center, pursuitRadius));
        addRecentCombatRescueCandidate(candidates, owner, data::contains, owner.getLastHurtByMob(),
                owner.getLastHurtByMobTimestamp(), center, pursuitRadius);
        AABB area = owner.getBoundingBox().inflate(scanRadius, Math.min(scanRadius, 12.0), scanRadius);
        for (Mob candidate : scanMobs(owner.serverLevel(), area)) {
            if (candidate.getTarget() == owner) addCombatRescueCandidate(candidates, owner, data::contains, candidate, center, scanRadius);
        }
        return candidates.stream().min(java.util.Comparator.comparingDouble(owner::distanceToSqr));
    }

    public static Optional<ProtectThreat> findProtectOwnerThreat(ServerPlayer owner, Predicate<UUID> rosterContains,
                                                                 LivingEntity responder, LivingEntity current,
                                                                 double scanRadius, double pursuitRadius) {
        Vec3 center = owner.position();
        Set<LivingEntity> candidates = new LinkedHashSet<>();
        addIfValid(candidates, owner, rosterContains, responder, current, center, pursuitRadius, true);
        CompanionThreatMemoryService.findRecentThreat(owner, pursuitRadius)
                .ifPresent(candidate -> addIfValid(candidates, owner, rosterContains, responder, candidate, center, pursuitRadius, true));
        addIfRecent(candidates, owner, rosterContains, responder, owner.getLastHurtByMob(),
                owner.getLastHurtByMobTimestamp(), center, pursuitRadius, true);
        AABB area = owner.getBoundingBox().inflate(scanRadius, Math.min(scanRadius, 12.0), scanRadius);
        for (Mob candidate : scanMobs(owner.serverLevel(), area))
            addIfValid(candidates, owner, rosterContains, responder, candidate, center, scanRadius, true);
        ProtectThreat best = null;
        LivingEntity remembered = CompanionThreatMemoryService.findRecentThreat(owner, pursuitRadius).orElse(null);
        for (LivingEntity candidate : candidates) {
            ProtectTier tier = protectTier(owner, rosterContains, responder, candidate, remembered);
            if (tier == null) continue;
            ProtectThreat next = new ProtectThreat(candidate, tier, candidate.distanceToSqr(owner), candidate == current);
            if (best == null || next.betterThan(best)) best = next;
        }
        return Optional.ofNullable(best);
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
        for (Mob candidate : scanMobs(owner.serverLevel(), area)) {
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
        for (Mob candidate : scanMobs(owner.serverLevel(), area)) {
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

    private static List<Mob> scanMobs(ServerLevel level, AABB area) {
        long gameTime = level.getGameTime();
        ScanWindow window = SCAN_WINDOWS.get(level);
        if (window == null || window.gameTime != gameTime) {
            window = new ScanWindow(gameTime);
            SCAN_WINDOWS.put(level, window);
        }
        AreaKey key = new AreaKey(area.minX, area.minY, area.minZ, area.maxX, area.maxY, area.maxZ);
        List<Mob> cached = window.areas.get(key);
        if (cached != null) return cached;
        long startedAt = FindMePerformanceMonitor.start();
        List<Mob> scanned = level.getEntitiesOfClass(Mob.class, area);
        window.areas.put(key, scanned);
        FindMePerformanceMonitor.recordThreatScan(startedAt);
        return scanned;
    }

    public static void finishServerTick(MinecraftServer server) {
        if (server == null) {
            SCAN_WINDOWS.clear();
            return;
        }
        for (ServerLevel level : server.getAllLevels()) SCAN_WINDOWS.remove(level);
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

    private static void addRecentCombatRescueCandidate(Set<LivingEntity> candidates, ServerPlayer owner,
                                                        Predicate<UUID> rosterContains, LivingEntity candidate,
                                                        int timestamp, Vec3 center, double range) {
        if (candidate != null && owner.tickCount - timestamp <= RECENT_COMBAT_TICKS)
            addCombatRescueCandidate(candidates, owner, rosterContains, candidate, center, range);
    }
    private static void addCombatRescueCandidate(Set<LivingEntity> candidates, ServerPlayer owner,
                                                  Predicate<UUID> rosterContains, LivingEntity candidate,
                                                  Vec3 center, double range) {
        if (valid(owner, rosterContains, null, candidate, center, range)) candidates.add(candidate);
    }
    private static ProtectTier protectTier(ServerPlayer owner, Predicate<UUID> rosterContains,
                                            LivingEntity responder, LivingEntity candidate, LivingEntity remembered) {
        if (candidate == owner.getLastHurtByMob() && owner.tickCount - owner.getLastHurtByMobTimestamp() <= RECENT_COMBAT_TICKS
                || candidate == remembered) return ProtectTier.RECENT_ATTACKER;
        if (candidate instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target == owner) return candidate.distanceToSqr(owner) <= 32.0 * 32.0
                    ? ProtectTier.NEAR_OWNER_TARGET : ProtectTier.FAR_OWNER_TARGET;
            if (target == responder || target != null && rosterContains.test(target.getUUID())) return ProtectTier.TEAM_TARGET;
        }
        boolean hostile = candidate instanceof Enemy || candidate.getType().getCategory() == MobCategory.MONSTER;
        if (!hostile) return null;
        if (candidate.distanceToSqr(owner) <= 20.0 * 20.0 && isApproaching(candidate, owner)) {
            return ProtectTier.APPROACHING_HOSTILE;
        }
        return candidate.distanceToSqr(owner) <= 32.0 * 32.0
                ? ProtectTier.HOSTILE_IN_PROTECTION_ZONE : null;
    }
    public static boolean isActiveCombatRescueThreat(ServerPlayer owner, LivingEntity threat) {
        if (owner == null || threat == null || owner.isCreative() || owner.isSpectator()
                || !threat.isAlive() || threat.level() != owner.level()) return false;
        if (threat instanceof Mob mob && mob.getTarget() == owner) return true;
        if (threat == owner.getLastHurtByMob()
                && owner.tickCount - owner.getLastHurtByMobTimestamp() <= RECENT_COMBAT_TICKS) return true;
        return CompanionThreatMemoryService.findRecentThreat(owner, 48.0).orElse(null) == threat;
    }
    public enum ProtectTier {
        RECENT_ATTACKER,
        NEAR_OWNER_TARGET,
        FAR_OWNER_TARGET,
        TEAM_TARGET,
        APPROACHING_HOSTILE,
        HOSTILE_IN_PROTECTION_ZONE
    }
    public record ProtectThreat(LivingEntity entity, ProtectTier tier, double distanceSqr, boolean current) {
        private boolean betterThan(ProtectThreat other) {
            if (this.tier.ordinal() != other.tier.ordinal()) return this.tier.ordinal() < other.tier.ordinal();
            if (this.current != other.current) return this.current;
            return this.distanceSqr < other.distanceSqr;
        }
    }

    private static final class ScanWindow {
        private final long gameTime;
        private final Map<AreaKey, List<Mob>> areas = new HashMap<>();

        private ScanWindow(long gameTime) {
            this.gameTime = gameTime;
        }
    }

    private record AreaKey(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
    }
}
