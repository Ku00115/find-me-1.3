package com.kuzhi.findme.server.compat;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Reversibly suspends native owner-follow movement while FindMe owns a fixed post. */
public final class CompanionFixedPostService {
    public enum Reason {
        HOME,
        GUARD
    }

    private static final Map<UUID, Suspension> SUSPENDED = new HashMap<>();

    private CompanionFixedPostService() {
    }

    public static void acquire(LivingEntity entity, Reason reason) {
        if (!(entity instanceof Mob mob) || reason == null) {
            return;
        }
        Suspension existing = SUSPENDED.get(entity.getUUID());
        if (existing != null) {
            existing.reasons().add(reason);
            mob.getNavigation().stop();
            return;
        }

        List<GoalEntry> removed = new ArrayList<>();
        for (WrappedGoal wrapped : List.copyOf(mob.goalSelector.getAvailableGoals())) {
            Goal goal = wrapped.getGoal();
            if (!isOwnerFollowGoal(goal)) {
                continue;
            }
            boolean running = wrapped.isRunning();
            if (running) {
                wrapped.stop();
            }
            removed.add(new GoalEntry(wrapped.getPriority(), goal));
            mob.goalSelector.removeGoal(goal);
            FindMeDebugLogger.info("fixed-post",
                    "suspended entity={} reason={} goal={} priority={} wasRunning={}",
                    FindMeDebugLogger.entity(entity), reason, goal.getClass().getName(),
                    wrapped.getPriority(), running);
        }
        mob.getNavigation().stop();
        SUSPENDED.put(entity.getUUID(), new Suspension(mob, EnumSet.of(reason), List.copyOf(removed)));
    }

    public static void release(LivingEntity entity, Reason reason) {
        if (entity != null) {
            release(entity.getUUID(), reason);
        }
    }

    public static void release(UUID entityUuid, Reason reason) {
        Suspension suspension = entityUuid == null ? null : SUSPENDED.get(entityUuid);
        if (suspension == null || reason == null) {
            return;
        }
        suspension.reasons().remove(reason);
        if (!suspension.reasons().isEmpty()) {
            return;
        }
        SUSPENDED.remove(entityUuid);
        if (!suspension.mob().isRemoved()) {
            for (GoalEntry entry : suspension.goals()) {
                suspension.mob().goalSelector.addGoal(entry.priority(), entry.goal());
            }
        }
        FindMeDebugLogger.info("fixed-post", "restored entity={} goals={}",
                FindMeDebugLogger.entity(suspension.mob()), suspension.goals().size());
    }

    public static void reset() {
        SUSPENDED.clear();
    }

    private static boolean isOwnerFollowGoal(Goal goal) {
        String name = goal.getClass().getName().toLowerCase(Locale.ROOT)
                .replace("_", "");
        return name.contains("followowner")
                || name.contains("ownerfollow")
                || name.contains("dragonescort")
                || name.contains("aiescort");
    }

    private record GoalEntry(int priority, Goal goal) {
    }

    private record Suspension(Mob mob, EnumSet<Reason> reasons, List<GoalEntry> goals) {
    }
}
