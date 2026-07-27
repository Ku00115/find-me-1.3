package com.kuzhi.findme.server.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Seeds native threat/retaliation memory and leaves target selection to native AI goals. */
public final class CompanionNativeCombatIntentService {
    private static final float NATIVE_THREAT = 1_000_000.0F;
    private static final Map<Class<?>, Optional<ThreatAccess>> THREAT_ACCESS = new ConcurrentHashMap<>();

    private CompanionNativeCombatIntentService() {
    }

    public static boolean assign(Mob mob, LivingEntity threat) {
        if (mob == null || threat == null || !mob.isAlive() || !threat.isAlive()
                || mob.level() != threat.level()) {
            return false;
        }
        if (mob.isNoAi()) {
            mob.setNoAi(false);
        }

        // A normal target goal consumes this retaliation timestamp on its next AI tick.
        mob.setLastHurtByMob(threat);
        return assignNativeThreat(mob, threat) || mob.getLastHurtByMob() == threat;
    }

    private static boolean assignNativeThreat(Mob mob, LivingEntity threat) {
        ThreatAccess access = THREAT_ACCESS.computeIfAbsent(mob.getClass(),
                CompanionNativeCombatIntentService::resolveThreatAccess).orElse(null);
        if (access == null) {
            return false;
        }
        try {
            if (access.removeExclusion() != null) {
                access.removeExclusion().invoke(mob, threat.getUUID());
            }
            access.addThreat().invoke(mob, threat.getUUID(), NATIVE_THREAT);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Optional<ThreatAccess> resolveThreatAccess(Class<?> type) {
        try {
            Method addThreat = type.getMethod("addThreat", UUID.class, float.class);
            Method removeExclusion;
            try {
                removeExclusion = type.getMethod("removeThreatExclusion", UUID.class);
            } catch (NoSuchMethodException ignored) {
                removeExclusion = null;
            }
            return Optional.of(new ThreatAccess(addThreat, removeExclusion));
        } catch (NoSuchMethodException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private record ThreatAccess(Method addThreat, Method removeExclusion) {
    }
}
