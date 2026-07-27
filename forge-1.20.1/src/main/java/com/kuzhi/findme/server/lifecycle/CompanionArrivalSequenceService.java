package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.compat.BookOfDragonsRescueCompatibility;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class CompanionArrivalSequenceService {
    private static final int DEFAULT_SUMMON_REVEAL_DELAY_TICKS = 2;
    private static final int DEFAULT_RESCUE_REVEAL_DELAY_TICKS = 1;
    private static final Map<UUID, PendingArrival> PENDING = new HashMap<>();

    private CompanionArrivalSequenceService() {
    }

    public static void start(ServerPlayer player, LivingEntity living, Vec3 focus) {
        start(player, living, focus, RescueMagicPacket.Purpose.SUMMON);
    }

    public static void start(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose) {
        boolean noAi;
        if (living == null || living.level().isClientSide()) {
            return;
        }
        noAi = living instanceof Mob mob && mob.isNoAi();
        startWithCapturedState(player, living, focus, purpose, noAi);
    }

    public static void startWithCapturedState(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi) {
        if (living == null || living.level().isClientSide()) {
            return;
        }
        UUID uuid = living.getUUID();
        PendingArrival previous = PENDING.remove(uuid);
        if (previous != null) {
            previous.restore(living);
        }
        PendingArrival pending = PendingArrival.capture(player, focus, purpose, noAi).prepare(living);
        PENDING.put(uuid, pending);
    }

    public static void startWithDelay(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose, int revealDelayTicks) {
        boolean noAi;
        if (living == null || living.level().isClientSide()) {
            return;
        }
        noAi = living instanceof Mob mob && mob.isNoAi();
        startWithCapturedStateAndDelay(player, living, focus, purpose, noAi, revealDelayTicks);
    }

    public static void startWithCapturedStateAndDelay(ServerPlayer player, LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi, int revealDelayTicks) {
        startWithCapturedStateAndDelay(player, living, focus, purpose, noAi, revealDelayTicks,
                living != null && living.isInvisible());
    }

    public static void startWithCapturedStateAndDelay(ServerPlayer player, LivingEntity living, Vec3 focus,
            RescueMagicPacket.Purpose purpose, boolean noAi, int revealDelayTicks, boolean originalInvisible) {
        if (living == null || living.level().isClientSide()) {
            return;
        }
        UUID uuid = living.getUUID();
        PendingArrival previous = PENDING.remove(uuid);
        if (previous != null) {
            previous.restore(living);
        }
        PendingArrival pending = PendingArrival.capture(player, focus, purpose, noAi, revealDelayTicks,
                originalInvisible).prepare(living);
        PENDING.put(uuid, pending);
    }

    static int delayFor(RescueMagicPacket.Purpose purpose) {
        return purpose == RescueMagicPacket.Purpose.RESCUE ? DEFAULT_RESCUE_REVEAL_DELAY_TICKS : DEFAULT_SUMMON_REVEAL_DELAY_TICKS;
    }

    public static boolean isPending(LivingEntity living) {
        return living != null && PENDING.containsKey(living.getUUID());
    }

    public static boolean blocksCinematicMovement(LivingEntity living) {
        return isPending(living) && BookOfDragonsRescueCompatibility.isBookOfDragonsDragon(living);
    }

    public static Optional<Boolean> originalNoAi(LivingEntity living) {
        if (living == null) {
            return Optional.empty();
        }
        PendingArrival pending = PENDING.get(living.getUUID());
        return pending == null ? Optional.empty() : Optional.of(pending.noAi());
    }

    public static void tick(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingArrival>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingArrival> entry = iterator.next();
            PendingArrival pending = entry.getValue();
            Entity entity = CompanionEntityLookup.findEntity(server, entry.getKey()).orElse(null);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                CompanionSummonLineService.cancelScheduled(entry.getKey());
                iterator.remove();
                continue;
            }
            PendingArrival next = pending.tick(living);
            if (next == null) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    public static void cancel(LivingEntity living) {
        if (living == null) {
            return;
        }
        PendingArrival pending = PENDING.remove(living.getUUID());
        if (pending != null) {
            CompanionSummonLineService.cancelScheduled(living);
            pending.restore(living);
        }
    }

    private record PendingArrival(
            int remainingTicks,
            int hideTicks,
            boolean noAi,
            boolean invisible,
            UUID playerUuid,
            Vec3 focus,
            RescueMagicPacket.Purpose purpose,
            Vec3 anchor,
            float yRot,
            float xRot
    ) {
        static PendingArrival capture(ServerPlayer player, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi) {
            return capture(player, focus, purpose, noAi, delayFor(purpose));
        }

        static PendingArrival capture(ServerPlayer player, Vec3 focus, RescueMagicPacket.Purpose purpose, boolean noAi, int revealDelayTicks) {
            return capture(player, focus, purpose, noAi, revealDelayTicks, false);
        }

        static PendingArrival capture(ServerPlayer player, Vec3 focus, RescueMagicPacket.Purpose purpose,
                boolean noAi, int revealDelayTicks, boolean originalInvisible) {
            UUID playerUuid = player == null ? null : player.getUUID();
            RescueMagicPacket.Purpose safePurpose = purpose == null ? RescueMagicPacket.Purpose.SUMMON : purpose;
            int delay = Math.max(1, revealDelayTicks);
            // This sequence intentionally holds the entity at its spawn anchor while
            // optional-mod state and the opening effect settle. Never expose those
            // stationary frames: reveal and launch happen on the same server tick.
            int hideTicks = delay;
            return new PendingArrival(delay, hideTicks, noAi, originalInvisible, playerUuid, focus, safePurpose, null, 0.0f, 0.0f);
        }

        PendingArrival prepare(LivingEntity living) {
            if (hideTicks > 0) {
                living.setInvisible(true);
            }
            living.fallDistance = 0.0f;
            return new PendingArrival(remainingTicks, hideTicks, noAi, invisible, playerUuid, focus, purpose, living.position(), living.getYRot(), living.getXRot());
        }

        PendingArrival tick(LivingEntity living) {
            if (BookOfDragonsRescueCompatibility.isBookOfDragonsDragon(living)) {
                holdAtAnchor(living);
            }
            if (remainingTicks <= 1) {
                reveal(living);
                return null;
            }
            living.fallDistance = 0.0f;
            int nextHideTicks = Math.max(0, hideTicks - 1);
            if (hideTicks > 0) {
                living.setInvisible(hideTicks == 1 ? invisible : true);
            }
            return new PendingArrival(remainingTicks - 1, nextHideTicks, noAi, invisible, playerUuid, focus, purpose, anchor, yRot, xRot);
        }

        private void holdAtAnchor(LivingEntity living) {
            if (anchor == null) {
                return;
            }
            double driftSqr = living.position().distanceToSqr(anchor);
            if (driftSqr > 1.0E-6) {
                FindMeDebugLogger.info("bookofdragons-arrival",
                        "corrected hidden drift entity={} drift={} current={} anchor={} remainingTicks={}",
                        FindMeDebugLogger.entity(living), Math.sqrt(driftSqr), living.position(), anchor,
                        remainingTicks);
            }
            if (living instanceof Mob mob) {
                if (purpose == RescueMagicPacket.Purpose.RESCUE) {
                    BookOfDragonsRescueCompatibility.suspendConflictingGoals(living);
                }
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            living.moveTo(anchor.x, anchor.y, anchor.z, yRot, xRot);
            living.setPos(anchor.x, anchor.y, anchor.z);
            living.setDeltaMovement(Vec3.ZERO);
            living.fallDistance = 0.0f;
            living.hurtMarked = true;
        }

        void restore(LivingEntity living) {
            living.setInvisible(invisible);
            if (living instanceof Mob mob) {
                mob.setNoAi(noAi);
            }
        }

        void reveal(LivingEntity living) {
            restore(living);
            Vec3 launch = launchVelocity(living, focus, purpose);
            living.setDeltaMovement(launch);
            living.hurtMarked = true;
            living.fallDistance = 0.0f;
            FindMeDebugLogger.info("arrival-visibility",
                    "reveal entity={} purpose={} originalInvisible={} currentInvisible={} launch={}",
                    FindMeDebugLogger.entity(living), purpose, invisible, living.isInvisible(), launch);
            CompanionSummonLineService.showScheduled(living);
        }

        private static Vec3 launchVelocity(LivingEntity living, Vec3 focus, RescueMagicPacket.Purpose purpose) {
            Vec3 direction = purpose == RescueMagicPacket.Purpose.RESCUE && focus != null
                    ? focus.subtract(living.position())
                    : living.position().subtract(focus == null
                    ? living.position().add(0.0, 0.0, 1.0) : focus);
            direction = new Vec3(direction.x, 0.0, direction.z);
            if (direction.lengthSqr() < 0.01) {
                direction = living.getLookAngle();
                direction = new Vec3(direction.x, 0.0, direction.z);
            }
            if (direction.lengthSqr() < 0.01) {
                direction = new Vec3(0.0, 0.0, 1.0);
            }
            double sizeBoost = Math.min(0.22, Math.max(living.getBbWidth(), living.getBbHeight()) * 0.025);
            if (purpose == RescueMagicPacket.Purpose.RESCUE) {
                return direction.normalize().scale(0.46 + sizeBoost * 1.2).add(0.0, 0.34 + sizeBoost, 0.0);
            }
            return direction.normalize().scale(0.20 + sizeBoost * 0.55).add(0.0, 0.18 + sizeBoost * 0.5, 0.0);
        }

    }
}

