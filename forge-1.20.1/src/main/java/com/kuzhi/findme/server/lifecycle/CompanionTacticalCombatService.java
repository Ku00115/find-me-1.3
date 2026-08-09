package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.compat.CompanionNativeCombatIntentService;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Owns one precise command target while native combat goals own movement and attacks. */
final class CompanionTacticalCombatService {
    private static final int REASSERT_INTERVAL_TICKS = 20;
    private static final int MAX_FAST_REPAIRS = 3;

    private CompanionTacticalCombatService() {
    }

    static void engage(LivingEntity living, Mob mob, LivingEntity threat, State state, String source) {
        int tick = living.tickCount;
        boolean acquired = !threat.getUUID().equals(state.targetUuid);
        if (acquired) {
            state.acquire(threat, tick, source);
        }

        LivingEntity nativeTarget = mob.getTarget();
        boolean retained = nativeTarget == threat && nativeTarget.isAlive();
        boolean fastRepair = !acquired && state.fastRepairs < MAX_FAST_REPAIRS && tick - state.targetAcquiredTick <= 5;
        if (!retained && (acquired || fastRepair || tick - state.lastAssignmentTick >= REASSERT_INTERVAL_TICKS)) {
            UUID displaced = nativeTarget == null ? null : nativeTarget.getUUID();
            boolean nativeIntent = CompanionNativeCombatIntentService.assign(mob, threat);
            mob.setTarget(threat);
            state.lastAssignmentTick = tick;
            state.assignments++;
            if (fastRepair) state.fastRepairs++;
            FindMeDebugLogger.info("command-target",
                    "assign companion={} type={} target={} targetType={} source={} phase={} assignment={} displaced={} retainedNow={} canAttack={} distance={}",
                    living.getUUID(), living.getType(), threat.getUUID(), threat.getType(), state.source,
                    acquired ? "acquire" : "repair", state.assignments, displaced, mob.getTarget() == threat,
                    mob.canAttack(threat), String.format(java.util.Locale.ROOT, "%.2f", living.distanceTo(threat)));
        }

        state.traceTargetPersistence(living, mob, threat, tick);
    }

    static void disengage(Mob mob, State state) {
        if (state.targetUuid == null) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && state.targetUuid.equals(target.getUUID())) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            FindMeDebugLogger.info("command-target", "clear companion={} target={} source={} reason=order_disengaged",
                    mob.getUUID(), state.targetUuid, state.source);
        }
        state.reset();
    }

    static void suspend(Mob mob, State state, String reason) {
        LivingEntity target = mob.getTarget();
        if (target != null) {
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
            FindMeDebugLogger.info("command-target",
                    "suspend companion={} target={} source={} reason={}",
                    mob.getUUID(), target.getUUID(), state.source, reason);
        }
        state.reset();
    }

    static final class State {
        private UUID targetUuid;
        private int targetAcquiredTick;
        private int lastAssignmentTick;
        private int assignments;
        private int fastRepairs;
        private String source = "unknown";

        private void acquire(LivingEntity target, int tick, String source) {
            targetUuid = target.getUUID();
            targetAcquiredTick = tick;
            lastAssignmentTick = Integer.MIN_VALUE / 2;
            assignments = 0;
            fastRepairs = 0;
            this.source = source == null ? "unknown" : source;
        }

        private void traceTargetPersistence(LivingEntity living, Mob mob, LivingEntity target, int tick) {
            int age = tick - targetAcquiredTick;
            if (age != 1 && age != 2 && age != 5 && age != 10) {
                return;
            }
            LivingEntity nativeTarget = mob.getTarget();
            FindMeDebugLogger.info("command",
                    "combat target check companion={} target={} age={} retained={} nativeTarget={} aggressive={} navigationDone={} distance={}",
                    living.getUUID(), target.getUUID(), age, nativeTarget == target,
                    nativeTarget == null ? null : nativeTarget.getUUID(), mob.isAggressive(),
                    mob.getNavigation().isDone(), String.format(java.util.Locale.ROOT, "%.2f", living.distanceTo(target)));
        }

        private void reset() {
            targetUuid = null;
            targetAcquiredTick = 0;
            lastAssignmentTick = 0;
            assignments = 0;
            fastRepairs = 0;
            source = "unknown";
        }

        void rearm() {
            lastAssignmentTick = Integer.MIN_VALUE / 2;
        }
    }
}
