package com.kuzhi.findme.server.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** Seeds native threat/retaliation memory for the emergency combat-rescue flow. */
public final class CompanionNativeCombatIntentService {
    private static final float NATIVE_THREAT = 1_000_000.0F;
    private static final String SAINTS_DRAGON_BASE =
            "com.leon.saintsdragons.server.entity.base.DragonEntity";
    private static final String SAINTS_DRAGON_MEMORIES =
            "com.leon.saintsdragons.server.ai.dragonbrain.DragonMemories";
    private static final Map<Class<?>, Optional<ThreatAccess>> THREAT_ACCESS = new ConcurrentHashMap<>();
    private static volatile Optional<MemoryModuleType<LivingEntity>> saintsAttackTargetMemory;

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
        mob.setLastHurtByMob(threat);
        boolean brainAssigned = assignSaintsDragonBrainTarget(mob, threat);
        return brainAssigned || assignNativeThreat(mob, threat) || mob.getLastHurtByMob() == threat;
    }

    public static void clear(Mob mob, UUID expectedTargetUuid) {
        MemoryModuleType<LivingEntity> memory = saintsAttackTargetMemory(mob).orElse(null);
        if (memory == null) {
            return;
        }
        LivingEntity current = mob.getBrain().getMemory(memory).orElse(null);
        if (expectedTargetUuid == null || current == null || expectedTargetUuid.equals(current.getUUID())) {
            mob.getBrain().eraseMemory(memory);
        }
    }

    /** Keeps brain-driven optional mobs on the same target as vanilla Mob#getTarget. */
    public static boolean maintainSpecializedTarget(Mob mob, LivingEntity threat) {
        return mob != null && threat != null && assignSaintsDragonBrainTarget(mob, threat);
    }

    private static boolean assignSaintsDragonBrainTarget(Mob mob, LivingEntity threat) {
        MemoryModuleType<LivingEntity> memory = saintsAttackTargetMemory(mob).orElse(null);
        if (memory == null) {
            return false;
        }
        try {
            mob.getBrain().setMemory(memory, threat);
            return mob.getBrain().getMemory(memory).orElse(null) == threat;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<MemoryModuleType<LivingEntity>> saintsAttackTargetMemory(Mob mob) {
        if (!isSaintsDragon(mob)) {
            return Optional.empty();
        }
        Optional<MemoryModuleType<LivingEntity>> cached = saintsAttackTargetMemory;
        if (cached != null) {
            return cached;
        }
        synchronized (CompanionNativeCombatIntentService.class) {
            cached = saintsAttackTargetMemory;
            if (cached != null) {
                return cached;
            }
            try {
                Class<?> memories = Class.forName(SAINTS_DRAGON_MEMORIES, false,
                        mob.getClass().getClassLoader());
                Field field = memories.getField("ATTACK_TARGET");
                Object value = field.get(null);
                cached = value instanceof MemoryModuleType<?> type
                        ? Optional.of((MemoryModuleType<LivingEntity>) type)
                        : Optional.empty();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                cached = Optional.empty();
            }
            saintsAttackTargetMemory = cached;
            return cached;
        }
    }

    private static boolean isSaintsDragon(Mob mob) {
        for (Class<?> type = mob == null ? null : mob.getClass(); type != null; type = type.getSuperclass()) {
            if (SAINTS_DRAGON_BASE.equals(type.getName())) {
                return true;
            }
        }
        return false;
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
