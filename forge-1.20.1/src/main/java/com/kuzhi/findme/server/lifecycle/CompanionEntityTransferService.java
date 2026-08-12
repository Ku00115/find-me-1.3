package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.animation.CompanionAnimationHelper;
import com.kuzhi.findme.server.animation.CompanionBlinkEffectService;
import com.kuzhi.findme.server.home.CompanionHomeService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CompanionEntityTransferService {
    private static final Map<UUID, Long> INTENTIONAL_TRANSFERS = new HashMap<>();

    private CompanionEntityTransferService() {
    }

    public static boolean consumeIntentionalTransferRemoval(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) {
            return false;
        }
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Long>> iterator = INTENTIONAL_TRANSFERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() < now) {
                iterator.remove();
            }
        }
        Long expiresAt = INTENTIONAL_TRANSFERS.remove(uuid);
        return expiresAt != null && expiresAt >= now;
    }

    public static void returnActive(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        CompanionHomeService.returnActive(player, data, kind);
    }

    public static Optional<Entity> restoreStoredEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot) {
        return restoreStoredEntity(player.serverLevel(), player, data, uuid, pos, yRot, xRot, false);
    }

    public static Optional<Entity> restoreStoredEntity(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, false);
    }

    static Optional<Entity> restoreStoredEntityForHome(ServerLevel level, ServerPlayer player,
                                                       PlayerCompanionData data, UUID uuid, BlockPos pos,
                                                       float yRot, float xRot) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true,
                Vec3.atCenterOf(pos), 42, RescueMagicPacket.Style.VERTICAL_PORTAL,
                RescueMagicPacket.Purpose.SUMMON, true, false, false, false,
                CompanionAnimationPurpose.SUMMON, false);
    }

    static Optional<Entity> restoreStoredEntityFreshForHome(ServerLevel level, ServerPlayer player,
                                                            PlayerCompanionData data, UUID uuid, BlockPos pos,
                                                            float yRot, float xRot) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true,
                Vec3.atCenterOf(pos), 42, RescueMagicPacket.Style.VERTICAL_PORTAL,
                RescueMagicPacket.Purpose.SUMMON, false, true, false, false,
                CompanionAnimationPurpose.SUMMON, true);
    }

    public static Optional<Entity> restoreStoredEntityDirect(ServerLevel level, ServerPlayer player,
                                                              PlayerCompanionData data, UUID uuid,
                                                              BlockPos pos, float yRot, float xRot) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, false,
                player.position(), 1, RescueMagicPacket.Style.VERTICAL_PORTAL,
                RescueMagicPacket.Purpose.SUMMON, true, false, false, false);
    }

    static Optional<Entity> restoreStoredEntityForJourney(ServerLevel level, ServerPlayer player,
                                                          PlayerCompanionData data, UUID uuid, BlockPos pos,
                                                          float yRot, float xRot) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, false,
                player.position(), 42, RescueMagicPacket.Style.VERTICAL_PORTAL,
                RescueMagicPacket.Purpose.SUMMON, true, false, true, true);
    }

    public static Optional<Entity> restoreStoredEntityFresh(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, false, player.position(), 42, RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON, false, true, false, true);
    }

    public static Optional<Entity> restoreStoredEntityAfterPresentation(ServerLevel level, ServerPlayer player,
            PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean freshRestore) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, false, player.position(), 42,
                RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON,
                !freshRestore, freshRestore, false, false);
    }

    public static Optional<Entity> restoreStoredEntityFreshForArrival(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true, focus, durationTicks, style, purpose, false, true, false, true);
    }

    public static Optional<Entity> restoreStoredEntityFreshForArrival(ServerLevel level, ServerPlayer player,
            PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus,
            int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
            CompanionAnimationPurpose animationPurpose) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true, focus, durationTicks,
                style, purpose, false, true, false, true, animationPurpose);
    }

    public static Optional<Entity> restoreStoredEntityForArrival(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot) {
        return restoreStoredEntity(player.serverLevel(), player, data, uuid, pos, yRot, xRot, true);
    }

    public static Optional<Entity> restoreStoredEntityForArrival(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, true, focus, durationTicks, style, purpose);
    }

    public static Optional<Entity> restoreStoredEntityForArrival(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style) {
        return restoreStoredEntityForArrival(player, data, uuid, pos, yRot, xRot, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
    }

    public static Optional<Entity> restoreStoredEntityForArrival(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return restoreStoredEntity(player, data, uuid, pos, yRot, xRot, true, focus, durationTicks, style, purpose);
    }

    public static Optional<Entity> restoreStoredEntityForArrival(ServerPlayer player, PlayerCompanionData data,
            UUID uuid, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks,
            RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
            CompanionAnimationPurpose animationPurpose) {
        return restoreStoredEntity(player.serverLevel(), player, data, uuid, pos, yRot, xRot, true, focus,
                durationTicks, style, purpose, true, false, false, true, animationPurpose);
    }

    private static Optional<Entity> restoreStoredEntity(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, prepareArrival, player.position(), 42, RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON);
    }

    private static Optional<Entity> restoreStoredEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return restoreStoredEntity(player.serverLevel(), player, data, uuid, pos, yRot, xRot, prepareArrival, focus, durationTicks, style, purpose);
    }

    private static Optional<Entity> restoreStoredEntity(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, prepareArrival, focus, durationTicks, style, purpose, true, false, false, true);
    }

    private static Optional<Entity> restoreStoredEntity(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, boolean reuseLoadedEntity, boolean allowWhileStoragePending, boolean journeyRestore, boolean spawnBlinkEffect) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, prepareArrival, focus,
                durationTicks, style, purpose, reuseLoadedEntity, allowWhileStoragePending, journeyRestore,
                spawnBlinkEffect, purpose == RescueMagicPacket.Purpose.RESCUE
                        ? CompanionAnimationPurpose.RESCUE : CompanionAnimationPurpose.SUMMON);
    }

    private static Optional<Entity> restoreStoredEntity(ServerLevel level, ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, boolean reuseLoadedEntity, boolean allowWhileStoragePending, boolean journeyRestore, boolean spawnBlinkEffect, CompanionAnimationPurpose animationPurpose) {
        return restoreStoredEntity(level, player, data, uuid, pos, yRot, xRot, prepareArrival, focus,
                durationTicks, style, purpose, reuseLoadedEntity, allowWhileStoragePending, journeyRestore,
                spawnBlinkEffect, animationPurpose, true);
    }

    private static Optional<Entity> restoreStoredEntity(ServerLevel level, ServerPlayer player,
            PlayerCompanionData data, UUID uuid, BlockPos pos, float yRot, float xRot, boolean prepareArrival,
            Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose,
            boolean reuseLoadedEntity, boolean allowWhileStoragePending, boolean journeyRestore,
            boolean spawnBlinkEffect, CompanionAnimationPurpose animationPurpose, boolean persistData) {
        Optional<CompoundTag> maybeTag = data.storedEntity(uuid);
        if (maybeTag.isEmpty()) {
            FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, null, "STORED", "UNKNOWN", "missing_stored_snapshot", false, false);
            return Optional.empty();
        }
        if (!allowWhileStoragePending && CompanionStorageService.isStoragePending(uuid)) {
            FindMeMod.LOGGER.warn("FindMe blocked restore for companion {} while its storage animation is still pending. Stored data was kept.", uuid);
            FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, null, "STORED", "STORED", "storage_pending", true, false);
            return Optional.empty();
        }
        boolean journeyOwned = CompanionOperationLockService.heldBy(player, uuid,
                CompanionOperationLockService.Operation.JOURNEY);
        if (journeyRestore) {
            if (!journeyOwned) {
                FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, null,
                        "UNOWNED", "DEPLOY", "journey_restore_without_lock", true, false);
                return Optional.empty();
            }
        } else {
            if (journeyOwned || !CompanionOperationLockService.tryBegin(player, uuid,
                    CompanionOperationLockService.Operation.DEPLOY,
                    prepareArrival ? "restore_for_arrival" : "restore")) {
                return Optional.empty();
            }
        }
        try {
            CompanionTransientStateService.cancelTargetForDeploy(player, data, uuid,
                    CompanionTransientStateService.Reason.DEPLOY);
            if (reuseLoadedEntity) {
                Optional<Entity> alreadyLoaded = CompanionEntityLookup.findEntityForRestore(player.getServer(), uuid);
                if (alreadyLoaded.isPresent()) {
                    Entity existing = alreadyLoaded.get();
                    Entity moved = moveLoadedDuplicateInsteadOfRestoring(player, existing, level, pos, yRot, xRot,
                            prepareArrival, focus, durationTicks, style, purpose, spawnBlinkEffect, animationPurpose);
                    data.removeStoredEntity(uuid);
                    data.setLastKnownPosition(uuid, SavedPosition.of(moved.level(), moved.getX(), moved.getY(), moved.getZ(), moved.getYRot(), moved.getXRot()));
                    if (persistData) {
                        CompanionDataService.save(player, data);
                    }
                    FindMeMod.LOGGER.warn("FindMe found a live entity while restoring stored companion {}; reused the live entity and removed the stale stored copy.", uuid);
                    FindMeDebugLogger.lifecycle("ENTITY_REUSED", player, uuid, moved, "STORED_AND_ACTIVE", "ACTIVE", "restore_reused_loaded", false, true);
                    return Optional.of(moved);
                }
            }
            CompoundTag tag = CompanionStorageService.healedStoredEntity(player, data, uuid, maybeTag.get());
            CompanionEntityVisualBoundsService.VisualDimensions storedEffectDimensions =
                    CompanionEntityVisualBoundsService.storedEffectDimensions(tag).orElse(null);
            tag.putUUID("UUID", uuid);
            Entity restored = EntityType.loadEntityRecursive(tag, level, entity -> {
                entity.moveTo((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, yRot, xRot);
                return entity;
            });
            if (restored == null) {
                FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, null, "STORED", "STORED", "entity_load_failed", true, false);
                return Optional.empty();
            }
            // A stored entity may come from a legacy snapshot captured while the
            // storage animation was protecting it. Summoning must restore normal combat rules.
            restored.setInvulnerable(false);
            boolean originalNoAi = false;
            if (restored instanceof Mob mob) {
                mob.setNoAi(false);
            }
            boolean originalInvisible = restored.isInvisible();
            // Put the hidden state into the initial spawn packet. A metadata update
            // sent after addFreshEntity is late enough to produce a visible flash.
            if (prepareArrival && restored instanceof LivingEntity) {
                restored.setInvisible(true);
            }
            FindMeDebugLogger.info("arrival-visibility",
                    "before-spawn entity={}/{} prepareArrival={} originalInvisible={} spawnInvisible={}",
                    uuid, EntityType.getKey(restored.getType()), prepareArrival, originalInvisible,
                    restored.isInvisible());
            boolean added = level.addFreshEntity(restored);
            if (!added) {
                Optional<Entity> conflict = CompanionEntityLookup.findEntityForRestore(player.getServer(), uuid)
                        .filter(existing -> existing != restored);
                if (reuseLoadedEntity && conflict.isPresent()) {
                    Entity existing = conflict.get();
                    Entity moved = moveLoadedDuplicateInsteadOfRestoring(player, existing, level, pos, yRot, xRot,
                            prepareArrival, focus, durationTicks, style, purpose, spawnBlinkEffect, animationPurpose);
                    data.removeStoredEntity(uuid);
                    data.setLastKnownPosition(uuid, SavedPosition.of(moved.level(), moved.getX(), moved.getY(),
                            moved.getZ(), moved.getYRot(), moved.getXRot()));
                    if (persistData) {
                        CompanionDataService.save(player, data);
                    }
                    FindMeMod.LOGGER.warn("FindMe recovered companion {} after Minecraft rejected a duplicate UUID restore; the existing world entity remains authoritative.", uuid);
                    FindMeDebugLogger.lifecycle("ENTITY_REUSED", player, uuid, moved, "STORED_AND_ACTIVE",
                            "ACTIVE", "restore_recovered_uuid_conflict", false, true);
                    return Optional.of(moved);
                }
                FindMeMod.LOGGER.error("FindMe failed to restore stored companion {} because Minecraft rejected the entity add. Stored data was kept for recovery.", uuid);
                FindMeDebugLogger.lifecycle("DEPLOY_REJECTED", player, uuid, restored, "STORED", "STORED", "add_fresh_entity_failed", true, false);
                return Optional.empty();
            }
            FindMeDebugLogger.info("arrival-visibility",
                    "after-spawn entity={}/{} prepareArrival={} invisible={}", uuid,
                    EntityType.getKey(restored.getType()), prepareArrival, restored.isInvisible());
            if (restored instanceof LivingEntity living) {
                if (prepareArrival) {
                    CompanionArrivalMagicService.openAt(player, living, restored.position(), focus, durationTicks,
                            style, purpose, storedEffectDimensions, animationPurpose);
                    CompanionArrivalSequenceService.startWithCapturedStateAndDelay(player, living,
                            focus == null ? player.position() : focus, purpose, originalNoAi,
                            CompanionArrivalMagicService.revealDelayTicks(player, living, durationTicks, style,
                                    purpose, animationPurpose), originalInvisible);
                }
                if (!prepareArrival) {
                    CompanionAnimationHelper.restoreAnimationControl(living);
                }
                if (spawnBlinkEffect && !prepareArrival && !CompanionArrivalSequenceService.isPending(living)) {
                    CompanionBlinkEffectService.spawn(living);
                }
            }
            data.removeStoredEntity(uuid);
            data.setLastKnownPosition(uuid, SavedPosition.of(restored.level(), restored.getX(), restored.getY(), restored.getZ(), restored.getYRot(), restored.getXRot()));
            if (persistData) {
                CompanionDataService.save(player, data);
            }
            FindMeDebugLogger.lifecycle("ENTITY_SPAWNED", player, uuid, restored, "STORED", "ACTIVE", prepareArrival ? "restore_for_arrival" : "restore", false, true);
            return Optional.of(restored);
        } finally {
            if (!journeyRestore) {
                CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.DEPLOY,
                        prepareArrival ? "restore_for_arrival" : "restore");
            }
        }
    }

    private static Entity moveLoadedDuplicateInsteadOfRestoring(ServerPlayer player, Entity existing, ServerLevel level, BlockPos pos, float yRot, float xRot, boolean prepareArrival, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose, boolean spawnBlinkEffect, CompanionAnimationPurpose animationPurpose) {
        existing.setInvulnerable(false);
        if (existing instanceof LivingEntity living) {
            if (prepareArrival) {
                return moveEntityForArrival(player, living, level, pos, yRot, xRot, focus, durationTicks, style,
                        purpose, animationPurpose);
            }
            return moveEntityTo(living, level, pos, yRot, xRot, spawnBlinkEffect);
        }
        if (existing.level() != level) {
            double x = (double)pos.getX() + 0.5;
            double y = pos.getY();
            double z = (double)pos.getZ() + 0.5;
            Entity changed = ExactEntityTeleporter.transfer(existing, level, x, y, z, yRot, xRot);
            if (changed != null) {
                existing = changed;
            }
        }
        double x = (double)pos.getX() + 0.5;
        double y = pos.getY();
        double z = (double)pos.getZ() + 0.5;
        existing.teleportTo(x, y, z);
        existing.moveTo(x, y, z, yRot, xRot);
        return existing;
    }

    public static LivingEntity moveEntityForArrival(ServerPlayer player, LivingEntity entity, ServerLevel level, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style) {
        return moveEntityForArrival(player, entity, level, pos, yRot, xRot, focus, durationTicks, style, RescueMagicPacket.Purpose.SUMMON);
    }

    public static LivingEntity moveEntityForArrival(ServerPlayer player, LivingEntity entity, ServerLevel level, BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style, RescueMagicPacket.Purpose purpose) {
        return moveEntityForArrival(player, entity, level, pos, yRot, xRot, focus, durationTicks, style, purpose,
                purpose == RescueMagicPacket.Purpose.RESCUE
                        ? CompanionAnimationPurpose.RESCUE : CompanionAnimationPurpose.SUMMON);
    }

    public static LivingEntity moveEntityForArrival(ServerPlayer player, LivingEntity entity, ServerLevel level,
            BlockPos pos, float yRot, float xRot, Vec3 focus, int durationTicks, RescueMagicPacket.Style style,
            RescueMagicPacket.Purpose purpose, CompanionAnimationPurpose animationPurpose) {
        CompanionArrivalMagicService.openAt(level, player, entity, Vec3.atBottomCenterOf(pos), focus,
                durationTicks, style, purpose, animationPurpose);
        CompanionArrivalSequenceService.startWithDelay(player, entity,
                focus == null ? player.position() : focus, purpose,
                CompanionArrivalMagicService.revealDelayTicks(player, entity, durationTicks, style, purpose,
                        animationPurpose));
        LivingEntity moved = moveEntityTo(entity, level, pos, yRot, xRot);
        return moved;
    }

    public static LivingEntity moveEntityTo(LivingEntity entity, ServerLevel level, BlockPos pos, float yRot, float xRot) {
        return moveEntityTo(entity, level, pos, yRot, xRot, true);
    }

    public static LivingEntity moveEntityTo(LivingEntity entity, ServerLevel level, BlockPos pos, float yRot, float xRot, boolean spawnBlinkParticles) {
        if (spawnBlinkParticles && !CompanionArrivalSequenceService.isPending(entity)) {
            CompanionBlinkEffectService.spawn(entity);
        }
        Entity changed = null;
        if (entity.level() != level) {
            markIntentionalTransfer(entity);
            changed = ExactEntityTeleporter.transfer(entity, level,
                    (double)pos.getX() + 0.5, pos.getY(), (double)pos.getZ() + 0.5, yRot, xRot);
        }
        if (changed instanceof LivingEntity changedLiving) {
            entity = changedLiving;
        }
        double x = (double)pos.getX() + 0.5;
        double y = pos.getY();
        double z = (double)pos.getZ() + 0.5;
        entity.teleportTo(x, y, z);
        entity.moveTo(x, y, z, yRot, xRot);
        entity.setPos(x, y, z);
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0f;
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (!CompanionArrivalSequenceService.isPending(entity)) {
            CompanionAnimationHelper.restoreAnimationControl(entity);
        }
        if (spawnBlinkParticles && !CompanionArrivalSequenceService.isPending(entity)) {
            CompanionBlinkEffectService.spawn(entity);
        }
        return entity;
    }

    private static void markIntentionalTransfer(Entity entity) {
        MinecraftServer server = entity.getServer();
        if (server != null) {
            INTENTIONAL_TRANSFERS.put(entity.getUUID(), server.overworld().getGameTime() + 40L);
        }
    }

}

