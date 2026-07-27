package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Owns the effect-only lead-in before a stored companion is physically created. */
public final class CompanionPreSpawnPresentationService {
    private static final Map<Key, Pending> PENDING = new HashMap<>();
    private static final Map<Key, Presentation> READY = new HashMap<>();

    private CompanionPreSpawnPresentationService() {
    }

    public static boolean schedule(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid,
                                   CompoundTag storedTag, BlockPos spawn, Vec3 focus, int durationTicks,
                                   RescueMagicPacket.Style style, CompanionAnimationPurpose animationPurpose,
                                   boolean freshRestore, boolean tacticalDeploy) {
        if (animationPurpose == CompanionAnimationPurpose.RESCUE) {
            return false;
        }
        String entityType = CompanionEntitySnapshots.storedEntityType(storedTag);
        CompanionEntityVisualBoundsService.VisualDimensions dimensions =
                CompanionEntityVisualBoundsService.storedEffectDimensions(storedTag).orElse(null);
        Vec3 anchor = Vec3.atBottomCenterOf(spawn);
        boolean opened = CompanionArrivalMagicService.openStoredAt(player.serverLevel(), player, uuid, entityType,
                anchor, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON, dimensions, animationPurpose);
        if (!opened) {
            return false;
        }
        CompanionAnimationStyle animation = data.animationStyle(uuid, animationPurpose, entityType);
        int delay = CompanionArrivalMagicService.revealDelayTicks(durationTicks, style,
                RescueMagicPacket.Purpose.SUMMON, animation);
        Key key = new Key(player.getUUID(), uuid);
        Presentation presentation = new Presentation(kind, uuid, spawn.immutable(), focus, durationTicks, style,
                animationPurpose, freshRestore, tacticalDeploy, player.level().dimension());
        // The effect may open before the entity is restored, but the restore
        // barrier must be one server tick at most. Waiting for the reveal
        // marker here made a normal key press feel half a second late.
        PENDING.put(key, new Pending(presentation, player.serverLevel().getGameTime() + 1));
        FindMeDebugLogger.info("pre-spawn", "state=scheduled player={} companion={} type={} revealDelay={} restoreDelay=1 spawn={}",
                player.getUUID(), uuid, entityType, delay, spawn);
        return true;
    }

    public static boolean scheduleLive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                       LivingEntity living, BlockPos spawn, Vec3 focus, int durationTicks,
                                       RescueMagicPacket.Style style, CompanionAnimationPurpose animationPurpose,
                                       boolean tacticalDeploy) {
        if (animationPurpose == CompanionAnimationPurpose.RESCUE) {
            return false;
        }
        String entityType = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();
        Vec3 anchor = Vec3.atBottomCenterOf(spawn);
        boolean opened = CompanionArrivalMagicService.openStoredAt(player.serverLevel(), player, living.getUUID(),
                entityType, anchor, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON,
                CompanionEntityVisualBoundsService.effectDimensions(living), animationPurpose);
        if (!opened) {
            return false;
        }
        CompanionAnimationStyle animation = data.animationStyle(living.getUUID(), animationPurpose, entityType);
        int delay = CompanionArrivalMagicService.revealDelayTicks(durationTicks, style,
                RescueMagicPacket.Purpose.SUMMON, animation);
        Key key = new Key(player.getUUID(), living.getUUID());
        Presentation presentation = new Presentation(kind, living.getUUID(), spawn.immutable(), focus, durationTicks,
                style, animationPurpose, false, tacticalDeploy, player.level().dimension());
        PENDING.put(key, new Pending(presentation, player.serverLevel().getGameTime() + 1));
        FindMeDebugLogger.info("pre-spawn", "state=scheduled_live player={} companion={} type={} revealDelay={} restoreDelay=1 spawn={}",
                player.getUUID(), living.getUUID(), entityType, delay, spawn);
        return true;
    }

    public static Optional<Presentation> ready(ServerPlayer player, UUID uuid) {
        return Optional.ofNullable(READY.get(new Key(player.getUUID(), uuid)));
    }

    public static boolean isPending(UUID uuid) {
        return uuid != null && PENDING.keySet().stream().anyMatch(key -> key.companionUuid.equals(uuid));
    }

    public static void cancelPlayer(UUID playerUuid) {
        PENDING.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
        READY.keySet().removeIf(key -> key.playerUuid.equals(playerUuid));
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<Key, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Pending> entry = iterator.next();
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey().playerUuid);
            // The pending entry is cancelled by the lifecycle/session cleanup paths. Do not
            // decode the complete player NBT on every waiting tick just to re-check the UUID;
            // that decode includes stored entity tags and can stretch a 16-tick reveal into
            // a visibly late spawn under load. The authoritative summon call revalidates it
            // once at the reveal barrier.
            if (player == null || !player.isAlive() || !player.level().dimension().equals(pending.presentation.dimension)) {
                iterator.remove();
                continue;
            }
            if (player.serverLevel().getGameTime() < pending.spawnAtTick) {
                continue;
            }
            iterator.remove();
            READY.put(entry.getKey(), pending.presentation);
            try {
                long totalStartedAt = System.nanoTime();
                long dataStartedAt = totalStartedAt;
                PlayerCompanionData data = CompanionDataService.data(player);
                double dataMs = (System.nanoTime() - dataStartedAt) / 1_000_000.0;
                long summonStartedAt = System.nanoTime();
                boolean accepted = pending.presentation.tacticalDeploy
                        ? CompanionLifecycleFacade.summonActiveForTacticalOrder(player, data,
                                pending.presentation.kind, "pre_spawn:resume_tactical")
                        : CompanionLifecycleFacade.summonActive(player, data, pending.presentation.kind,
                                 "pre_spawn:resume");
                double summonMs = (System.nanoTime() - summonStartedAt) / 1_000_000.0;
                FindMeDebugLogger.info("pre-spawn", "state=resume player={} companion={} accepted={}",
                        player.getUUID(), pending.presentation.uuid, accepted);
                FindMeDebugLogger.info("arrival-perf",
                        "player={} companion={} kind={} totalMs={} dataMs={} summonMs={} accepted={}",
                        player.getUUID(), pending.presentation.uuid, pending.presentation.kind,
                        (System.nanoTime() - totalStartedAt) / 1_000_000.0, dataMs, summonMs, accepted);
            } finally {
                READY.remove(entry.getKey());
            }
        }
    }

    public record Presentation(CompanionKind kind, UUID uuid, BlockPos spawn, Vec3 focus, int durationTicks,
                               RescueMagicPacket.Style style, CompanionAnimationPurpose animationPurpose,
                               boolean freshRestore, boolean tacticalDeploy, ResourceKey<Level> dimension) {
    }

    private record Key(UUID playerUuid, UUID companionUuid) {
    }

    private record Pending(Presentation presentation, long spawnAtTick) {
    }
}
