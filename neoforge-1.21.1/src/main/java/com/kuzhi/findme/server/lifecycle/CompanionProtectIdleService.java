package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Prevents native follow movement while an idle protector waits for a threat. */
final class CompanionProtectIdleService {
    private static final int GOAL_PRIORITY = 1;
    private static final Map<UUID, IdleLease> ACTIVE = new HashMap<>();

    private CompanionProtectIdleService() {
    }

    static void acquire(Mob mob) {
        if (mob == null || ACTIVE.containsKey(mob.getUUID())) {
            return;
        }
        IdleLease lease = new IdleLease(mob);
        ACTIVE.put(mob.getUUID(), lease);
        mob.goalSelector.addGoal(GOAL_PRIORITY, lease.goal);
        FindMeDebugLogger.info("protect-idle", "installed companion={} type={}",
                mob.getUUID(), mob.getType());
    }

    static void release(UUID uuid, String reason) {
        IdleLease lease = uuid == null ? null : ACTIVE.remove(uuid);
        if (lease == null) {
            return;
        }
        lease.goal.stop();
        if (!lease.mob.isRemoved()) {
            lease.mob.goalSelector.removeGoal(lease.goal);
        }
        FindMeDebugLogger.info("protect-idle", "removed companion={} type={} reason={}",
                uuid, lease.mob.getType(), reason);
    }

    static void reset() {
        for (UUID uuid : List.copyOf(ACTIVE.keySet())) {
            release(uuid, "server_reset");
        }
    }

    private static final class IdleLease {
        private final Mob mob;
        private final Goal goal;

        private IdleLease(Mob mob) {
            this.mob = mob;
            this.goal = new Goal() {
                {
                    setFlags(EnumSet.of(Flag.MOVE));
                }

                @Override
                public boolean canUse() {
                    return ownsIdleMovement();
                }

                @Override
                public boolean canContinueToUse() {
                    return ownsIdleMovement();
                }

                @Override
                public void start() {
                    mob.getNavigation().stop();
                }

                private boolean ownsIdleMovement() {
                    return ACTIVE.get(mob.getUUID()) == IdleLease.this
                            && mob.isAlive() && !mob.isVehicle()
                            && (mob.getTarget() == null || !mob.getTarget().isAlive());
                }
            };
        }
    }
}
