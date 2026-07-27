package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.server.animation.CompanionEnderEffectService;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.animation.CompanionMagicAudioService;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.StorageEffectPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CompanionStorageService {
    private static final Map<UUID, Long> INTENTIONAL_STORAGE_REMOVALS = new HashMap<>();
    private static final List<PendingStorageEffect> PENDING_STORAGE_EFFECTS = new ArrayList<>();
    private static final String SHOULDER_COMPANION_UUID = "FindMeCompanionUUID";
    private static final int STORAGE_EFFECT_TICKS = 18;

    private CompanionStorageService() {
    }

    public static boolean consumeIntentionalStorageRemoval(MinecraftServer server, UUID uuid) {
        Long expiresAt = INTENTIONAL_STORAGE_REMOVALS.remove(uuid);
        if (expiresAt == null) {
            return false;
        }
        long now = server == null ? 0L : server.overworld().getGameTime();
        return now <= expiresAt;
    }

    public static boolean storeAndDiscard(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
        if (!data.contains(living.getUUID())) {
            return false;
        }
        if (isStoragePending(living)) {
            return false;
        }
        UUID uuid = living.getUUID();
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.STORE, "store_and_discard")) {
            return false;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.AUTO_STORE);
        CompanionHomeResidentService.clearResident(living);
        FindMeDebugLogger.lifecycle("STORE_REQUEST", player, uuid, living, "ACTIVE", "RETURNING", "store_and_discard", data.storedEntity(uuid).isPresent(), true);
        if (!storeEntity(player, data, living)) {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "snapshot_failed");
            return false;
        }
        beginStorageEffect(player, living);
        living.stopRiding();
        markIntentionalStorageRemoval(player.getServer(), uuid);
        PENDING_STORAGE_EFFECTS.removeIf(pending -> pending.entityUuid.equals(uuid));
        PendingStorageEffect pending = PendingStorageEffect.normal(living, player.getUUID(), storageDelay(player, living));
        freezePendingStorage(living, pending);
        PENDING_STORAGE_EFFECTS.add(pending);
        return true;
    }

    public static boolean sendHomeAfterStorage(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living, ServerLevel destination, BlockPos target, float yRot, float xRot) {
        if (player == null || data == null || kind == null || living == null || destination == null || target == null || !data.contains(kind, living.getUUID())) {
            return false;
        }
        if (isStoragePending(living)) {
            return false;
        }
        UUID uuid = living.getUUID();
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.STORE, "send_home_after_storage")) {
            return false;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.AUTO_STORE);
        CompanionHomeResidentService.clearResident(living);
        FindMeDebugLogger.lifecycle("STORE_REQUEST", player, uuid, living, "ACTIVE", "HOME_RETURNING", "send_home_after_storage", data.storedEntity(uuid).isPresent(), true);
        if (!storeEntity(player, data, living)) {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "snapshot_failed");
            return false;
        }
        beginStorageEffect(player, living);
        living.stopRiding();
        PENDING_STORAGE_EFFECTS.removeIf(pending -> pending.entityUuid.equals(uuid));
        SavedPosition homeTarget = SavedPosition.of(destination, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, yRot, xRot);
        PendingStorageEffect pending = PendingStorageEffect.homeReturn(living, player.getUUID(), storageDelay(player, living), kind, homeTarget);
        freezePendingStorage(living, pending);
        PENDING_STORAGE_EFFECTS.add(pending);
        return true;
    }

    public static void tickStorageEffects(MinecraftServer server) {
        if (server == null || PENDING_STORAGE_EFFECTS.isEmpty()) {
            return;
        }
        Iterator<PendingStorageEffect> iterator = PENDING_STORAGE_EFFECTS.iterator();
        while (iterator.hasNext()) {
            PendingStorageEffect pending = iterator.next();
            Entity entity = CompanionEntityLookup.findEntity(server, pending.entityUuid).orElse(null);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
            if (player == null && !pending.continueWithoutPlayer) {
                if (pending.homeTarget != null) {
                    finishHomeReturn(server, living, pending, null);
                } else {
                    finishStorageEffect(server, living, null);
                }
                iterator.remove();
                continue;
            }
            stabilizePendingStorage(living, pending);
            if (player != null && (pending.age == 4 || pending.age == 9 || pending.age == 13)) {
                CompanionMagicAudioService.playStorageWhoosh(player, living.position(), pending.age == 4 ? 0 : pending.age == 9 ? 1 : 2);
            }
            pending.age++;
            if (pending.age >= pending.durationTicks) {
                if (pending.homeTarget != null) {
                    finishHomeReturn(server, living, pending, player);
                } else {
                    finishStorageEffect(server, living, player);
                }
                iterator.remove();
            }
        }
    }

    public static boolean isStoragePending(LivingEntity living) {
        return living != null && PENDING_STORAGE_EFFECTS.stream().anyMatch(pending -> pending.entityUuid.equals(living.getUUID()));
    }

    public static boolean isStoragePending(UUID uuid) {
        return uuid != null && PENDING_STORAGE_EFFECTS.stream().anyMatch(pending -> pending.entityUuid.equals(uuid));
    }

    public static void freezeForStorageTransition(LivingEntity living) {
        if (living != null) {
            stabilizePendingStorage(living, PendingStorageEffect.snapshot(living));
        }
    }

    public static boolean storeEntity(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
        return saveEntitySnapshot(player, data, living, true);
    }

    /**
     * Completes a retreat through the same configured storage presentation as an explicit collect.
     * The caller must already hold the STORE operation lock and have committed a valid snapshot.
     */
    public static boolean finishRetreatWithStoragePresentation(ServerPlayer player, LivingEntity living) {
        if (player == null || living == null || living.isRemoved() || !living.isAlive()) {
            return false;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!hasValidStoredSnapshot(data, living.getUUID())) {
            return false;
        }
        beginStorageEffect(player, living);
        living.stopRiding();
        markIntentionalStorageRemoval(player.getServer(), living.getUUID());
        PENDING_STORAGE_EFFECTS.removeIf(pending -> pending.entityUuid.equals(living.getUUID()));
        int delay = storageDelay(data, living);
        if (delay <= 0) {
            finishStorageEffect(player.getServer(), living, player);
            return true;
        }
        PendingStorageEffect pending = PendingStorageEffect.normal(living, player.getUUID(), delay);
        freezePendingStorage(living, pending);
        PENDING_STORAGE_EFFECTS.add(pending);
        FindMeDebugLogger.info("storage-presentation",
                "retreat entered configured storage presentation entity={} owner={} duration={}",
                FindMeDebugLogger.entity(living), player.getUUID(), delay);
        return true;
    }

    public static void beginMovingRetreatPresentation(ServerPlayer player, PlayerCompanionData data,
                                                       LivingEntity living) {
        if (player == null || data == null || living == null || living.isRemoved()) {
            return;
        }
        FindMeDebugLogger.info("storage-presentation",
                "switch retreat started animation-only presentation entity={} owner={}",
                FindMeDebugLogger.entity(living), player.getUUID());
    }

    public static boolean finishMovingRetreat(ServerPlayer player, PlayerCompanionData data,
                                              LivingEntity living) {
        if (player == null || data == null || living == null || living.isRemoved()) {
            return false;
        }
        if (!hasValidStoredSnapshot(data, living.getUUID())) {
            return false;
        }
        markIntentionalStorageRemoval(player.getServer(), living.getUUID());
        living.discard();
        FindMeDebugLogger.lifecycle("ENTITY_REMOVED", player, living.getUUID(), living,
                "RETREATING", "STORED", "moving_switch_retreat_finished", true, false);
        CompanionOperationLockService.end(player, living.getUUID(),
                CompanionOperationLockService.Operation.STORE, "moving_switch_retreat_finished");
        return true;
    }

    public static boolean snapshotEntity(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
        return saveEntitySnapshot(player, data, living, false);
    }

    /**
     * Creates the normal sanitized storage snapshot and removes the live entity while a ride-home
     * transaction owns the lifecycle lock. The cinematic transfer boundary is presentation-free.
     */
    public static boolean storeImmediatelyForJourney(ServerPlayer player, PlayerCompanionData data,
                                                      CompanionKind kind, LivingEntity living) {
        if (player == null || data == null || kind == null || living == null || !living.isAlive()
                || !data.contains(kind, living.getUUID())
                || !CompanionOperationLockService.heldBy(player, living.getUUID(),
                CompanionOperationLockService.Operation.JOURNEY)) {
            return false;
        }
        UUID uuid = living.getUUID();
        CompanionHomeResidentService.clearResident(living);
        if (!storeEntity(player, data, living)) {
            return false;
        }
        living.stopRiding();
        markIntentionalStorageRemoval(player.getServer(), uuid);
        living.discard();
        data.clearDeployed(kind, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
        CompanionDataService.save(player, data);
        FindMeDebugLogger.lifecycle("JOURNEY_TRANSFER_STORED", player, uuid, living,
                "DEPLOYED", "STORED", "ride_home_transfer_boundary", true, false);
        return true;
    }

    public static boolean snapshotAndDiscardHomeResidentForSummon(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
        if (player == null || data == null || living == null || !data.contains(living.getUUID()) || isStoragePending(living)) {
            return false;
        }
        CompanionHomeResidentService.clearResident(living);
        CompanionTransientStateService.cancelTargetForDeploy(player, data, living.getUUID(),
                CompanionTransientStateService.Reason.DEPLOY);
        FindMeDebugLogger.lifecycle("STORE_REQUEST", player, living.getUUID(), living, "HOME", "STORED", "home_resident_summon", data.storedEntity(living.getUUID()).isPresent(), true);
        if (!CompanionOperationLockService.tryBegin(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "home_resident_summon")) {
            return false;
        }
        if (!storeEntity(player, data, living)) {
            CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "snapshot_failed");
            return false;
        }
        living.stopRiding();
        markIntentionalStorageRemoval(player.getServer(), living.getUUID());
        living.discard();
        FindMeDebugLogger.lifecycle("ENTITY_REMOVED", player, living.getUUID(), living, "HOME", "STORED", "home_resident_summon", data.storedEntity(living.getUUID()).isPresent(), false);
        CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "home_resident_discarded");
        CompanionDataService.save(player, data);
        return true;
    }

    /**
     * Stores a home resident as part of chunk visibility/lifecycle cleanup.
     * This is intentionally presentation-free: no storage packet, sound, dialogue, or delay is emitted,
     * so returning to the house feels like the resident remained there while its chunk was out of view.
     */
    public static boolean storeHomeResidentSilently(ServerPlayer player, PlayerCompanionData data,
                                                     LivingEntity living, String source) {
        if (player == null || data == null || living == null || !living.isAlive()
                || !data.contains(living.getUUID()) || isStoragePending(living)) {
            return false;
        }
        UUID uuid = living.getUUID();
        CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
        String operationSource = source == null || source.isBlank() ? "home:silent_store" : source;
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.STORE, operationSource)) {
            return false;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.AUTO_STORE);
        CompoundTag tag = new CompoundTag();
        if (!living.save(tag)) {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "snapshot_failed");
            FindMeDebugLogger.lifecycle("HOME_SILENT_STORE_FAILED", player, uuid, living,
                    "HOME_ACTIVE", "HOME_ACTIVE", operationSource + ":snapshot_failed", false, true);
            return false;
        }
        writeStoredEntitySnapshot(data, living, tag, player.serverLevel().getGameTime());
        if (!hasValidStoredSnapshot(data, uuid)) {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "snapshot_invalid");
            FindMeDebugLogger.lifecycle("HOME_SILENT_STORE_FAILED", player, uuid, living,
                    "HOME_ACTIVE", "HOME_ACTIVE", operationSource + ":snapshot_invalid", false, true);
            return false;
        }
        data.clearDeployed(kind, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.HOME_STORED);
        CompanionDataService.save(player, data);
        CompanionHomeResidentService.clearResident(living);
        living.stopRiding();
        markIntentionalStorageRemoval(player.getServer(), uuid);
        living.discard();
        CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "home_silent_stored");
        FindMeDebugLogger.lifecycle("HOME_SILENT_STORED", player, uuid, living,
                "HOME_ACTIVE", "HOME_STORED", operationSource, true, false);
        return true;
    }

    /**
     * Immediate write-before-remove storage used when the house block itself no longer exists.
     * This path deliberately accepts an offline owner because house destruction is world-owned,
     * not dependent on the owner currently having a ServerPlayer instance.
     */
    public static boolean storeDetachedHomeResident(MinecraftServer server, UUID ownerUuid,
                                                    PlayerCompanionData data, LivingEntity living) {
        if (server == null || ownerUuid == null || data == null || living == null || !living.isAlive()
                || !data.contains(living.getUUID())) {
            return false;
        }
        UUID uuid = living.getUUID();
        CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
        CompanionHomeResidentService.clearResident(living);
        resetLiveStateForStorage(living);
        CompoundTag tag = new CompoundTag();
        if (!living.save(tag)) {
            FindMeDebugLogger.info("house", "broken house resident snapshot failed owner={} companion={} entity={}",
                    ownerUuid, uuid, FindMeDebugLogger.entity(living));
            return false;
        }
        writeStoredEntitySnapshot(data, living, tag, server.overworld().getGameTime());
        if (!hasValidStoredSnapshot(data, uuid)) {
            FindMeDebugLogger.info("house", "broken house resident snapshot invalid owner={} companion={} entity={}",
                    ownerUuid, uuid, FindMeDebugLogger.entity(living));
            return false;
        }
        data.clearDeployed(kind, uuid);
        data.clearHomePosition(uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
        CompanionDataService.save(server, ownerUuid, data);
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
        int delay = storageDelay(data, living);
        beginStorageEffect(server, data, owner, living);
        living.stopRiding();
        markIntentionalStorageRemoval(server, uuid);
        if (delay <= 0) {
            living.discard();
            FindMeDebugLogger.info("house", "broken house resident stored immediately owner={} companion={} kind={}",
                    ownerUuid, uuid, kind);
            return true;
        }
        PENDING_STORAGE_EFFECTS.removeIf(pending -> pending.entityUuid.equals(uuid));
        PendingStorageEffect pending = PendingStorageEffect.detached(living, ownerUuid, delay);
        freezePendingStorage(living, pending);
        PENDING_STORAGE_EFFECTS.add(pending);
        FindMeDebugLogger.info("house", "broken house resident storage animation queued owner={} companion={} kind={} ticks={}",
                ownerUuid, uuid, kind, delay);
        return true;
    }

    public static CompoundTag storedShoulderTag(ServerPlayer player, UUID uuid, CompoundTag source) {
        CompoundTag tag = source.copy();
        resetStoredStateTags(tag);
        if (!tag.hasUUID("UUID")) {
            tag.putUUID("UUID", uuid);
        }
        tag.putUUID(SHOULDER_COMPANION_UUID, uuid);
        if (!tag.contains("CompanionRescueType") && tag.contains("id")) {
            tag.putString("CompanionRescueType", tag.getString("id"));
        }
        if (!tag.contains("CompanionRescueName")) {
            tag.putString("CompanionRescueName", CompanionEntitySnapshots.storedEntityName(tag, uuid));
        }
        float health = tag.contains("Health") ? tag.getFloat("Health") : tag.getFloat("CompanionRescueHealth");
        if (health <= 0.0f) {
            health = 1.0f;
        }
        float maxHealth = tag.getFloat("CompanionRescueMaxHealth");
        if (maxHealth <= 0.0f) {
            maxHealth = Math.max(health, 1.0f);
        }
        tag.putFloat("CompanionRescueHealth", Math.min(health, maxHealth));
        tag.putFloat("CompanionRescueMaxHealth", maxHealth);
        tag.putFloat("Health", Math.min(health, maxHealth));
        if (!tag.contains("CompanionRescueArmor")) {
            tag.putInt("CompanionRescueArmor", 0);
        }
        tag.putLong("CompanionRescueStoredAt", player.serverLevel().getGameTime());
        return tag;
    }

    public static CompoundTag healedStoredEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompoundTag storedTag) {
        CompoundTag tag = sanitizedStoredTag(storedTag);
        float health = tag.getFloat("CompanionRescueHealth");
        float maxHealth = tag.getFloat("CompanionRescueMaxHealth");
        long storedAt = tag.getLong("CompanionRescueStoredAt");
        if (maxHealth <= 0.0f || health <= 0.0f || health >= maxHealth) {
            return tag;
        }
        long now = player.serverLevel().getGameTime();
        if (storedAt <= 0L) {
            tag.putLong("CompanionRescueStoredAt", now);
            data.storeEntity(uuid, tag);
            CompanionDataService.save(player, data);
            return tag;
        }
        float healed = Math.min(maxHealth, health + (float)Math.max(0L, now - storedAt) / 20.0f);
        if (healed > health) {
            tag.putFloat("CompanionRescueHealth", healed);
            tag.putLong("CompanionRescueStoredAt", now);
            if (tag.contains("Health")) {
                tag.putFloat("Health", healed);
            }
            data.storeEntity(uuid, tag);
            CompanionDataService.save(player, data);
        }
        return tag;
    }

    public static CompoundTag sanitizedStoredTag(CompoundTag source) {
        CompoundTag tag = source == null ? new CompoundTag() : source.copy();
        resetStoredStateTags(tag);
        return tag;
    }

    public static Optional<CompoundTag> createDeathSnapshot(LivingEntity living, long storedAt) {
        if (living == null) {
            return Optional.empty();
        }
        CompoundTag tag = new CompoundTag();
        if (!living.save(tag)) {
            return Optional.empty();
        }
        resetStoredStateTags(tag);
        String entityType = EntityType.getKey(living.getType()).toString();
        float maxHealth = Math.max(1.0f, living.getMaxHealth());
        tag.putString("id", entityType);
        tag.putString("CompanionRescueName", living.getDisplayName().getString());
        tag.putString("CompanionRescueType", entityType);
        CompanionEntitySnapshots.writePreviewBounds(living, tag);
        tag.putFloat("CompanionRescueHealth", maxHealth);
        tag.putFloat("CompanionRescueMaxHealth", maxHealth);
        tag.putInt("CompanionRescueArmor", living.getArmorValue());
        tag.putLong("CompanionRescueStoredAt", storedAt);
        tag.putBoolean("CompanionRescueDead", true);
        tag.putFloat("Health", maxHealth);
        return Optional.of(tag);
    }

    public static void markIntentionalStorageRemoval(MinecraftServer server, UUID uuid) {
        if (server == null) {
            return;
        }
        INTENTIONAL_STORAGE_REMOVALS.put(uuid, server.overworld().getGameTime() + 40L);
    }

    private static void beginStorageEffect(ServerPlayer player, LivingEntity living) {
        beginStorageEffect(player.getServer(), CompanionDataService.data(player), player, living);
    }

    private static void beginStorageEffect(MinecraftServer server, PlayerCompanionData data,
                                           ServerPlayer player, LivingEntity living) {
        if (server == null || data == null || living == null) return;
        com.kuzhi.findme.common.CompanionEffectStyle selected = storageStyle(data, living);
        com.kuzhi.findme.common.CompanionAnimationStyle animation = storageAnimation(data, living);
        if (player != null) CompanionSummonLineService.showStorage(player, living);
        UUID ownerUuid = player == null ? null : player.getUUID();
        if (FindMeApi.beginExternalStoragePresentation(server, ownerUuid, living, STORAGE_EFFECT_TICKS + 8)) {
            FindMeDebugLogger.info("storage-presentation", "external provider owns storage visual entity={} owner={} duration={}",
                    FindMeDebugLogger.entity(living), ownerUuid, STORAGE_EFFECT_TICKS + 8);
            return;
        }
        AABB box = CompanionEntityVisualBoundsService.effectBounds(living);
        Vec3 anchor = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        float radius = CompanionEntityVisualBoundsService.contractCircleRadius(living);
        float height = CompanionEntityVisualBoundsService.portalHeight(living);
        if (animation == com.kuzhi.findme.common.CompanionAnimationStyle.GROUND_SINK) {
            sendStoragePacket(player, living, anchor, radius, height, StorageEffectPacket.Visual.GROUND_SINK);
        }
        if (selected == com.kuzhi.findme.common.CompanionEffectStyle.NONE) return;
        if (selected == com.kuzhi.findme.common.CompanionEffectStyle.ENDER) {
            if (player != null) CompanionEnderEffectService.play(player, living);
            else if (living.level() instanceof ServerLevel level) CompanionEnderEffectService.play(level, living);
            return;
        }
        StorageEffectPacket.Visual visual = selected == com.kuzhi.findme.common.CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE
                ? StorageEffectPacket.Visual.CUSTOM_TEXTURE : StorageEffectPacket.Visual.DEFAULT;
        FindMeDebugLogger.info("effect-sizing", "side=server entity={} purpose=STORAGE style={} visual={} source=live bounds={} packetRadius={} packetHeight={}",
                FindMeDebugLogger.entity(living), selected, visual, FindMeDebugLogger.box(box), radius, height);
        sendStoragePacket(player, living, anchor, radius, height, visual);
        CompanionMagicAudioService.playStorageOpen(player, anchor, radius);
    }

    private static void sendStoragePacket(ServerPlayer player, LivingEntity living, Vec3 anchor, float radius,
                                          float height, StorageEffectPacket.Visual visual) {
        StorageEffectPacket packet = new StorageEffectPacket(living.getId(), anchor.x, anchor.y, anchor.z,
                radius, height, STORAGE_EFFECT_TICKS + 8, visual);
        if (living.level() instanceof ServerLevel level) {
            ModNetwork.sendToPlayersNear(level, anchor, 96.0, packet);
        } else if (player != null) {
            ModNetwork.sendToPlayer(player, packet);
        }
    }

    private static int storageDelay(ServerPlayer player, LivingEntity living) {
        return storageDelay(CompanionDataService.data(player), living);
    }

    private static int storageDelay(PlayerCompanionData data, LivingEntity living) {
        if (FindMeApi.hasExternalStoragePresentation(living)) return STORAGE_EFFECT_TICKS;
        var animation = storageAnimation(data, living);
        return animation == com.kuzhi.findme.common.CompanionAnimationStyle.NONE ? 0 : STORAGE_EFFECT_TICKS;
    }

    private static com.kuzhi.findme.common.CompanionEffectStyle storageStyle(PlayerCompanionData data, LivingEntity living) {
        String entityType = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();
        return data.effectStyle(living.getUUID(), com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType);
    }

    private static com.kuzhi.findme.common.CompanionAnimationStyle storageAnimation(PlayerCompanionData data,
                                                                                     LivingEntity living) {
        String entityType = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();
        return data.animationStyle(living.getUUID(), com.kuzhi.findme.common.CompanionAnimationPurpose.STORAGE, entityType);
    }

    private static void freezePendingStorage(LivingEntity living, PendingStorageEffect pending) {
        stabilizePendingStorage(living, pending);
    }

    private static void stabilizePendingStorage(LivingEntity living, PendingStorageEffect pending) {
        living.stopRiding();
        living.setDeltaMovement(Vec3.ZERO);
        living.noPhysics = true;
        living.setNoGravity(true);
        living.fallDistance = 0.0f;
        living.invulnerableTime = Math.max(living.invulnerableTime, 12);
        living.hurtMarked = true;
        if (living instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
        }
    }

    private static void restorePendingStorage(LivingEntity living, PendingStorageEffect pending) {
        living.noPhysics = pending.originalNoPhysics;
        living.setNoGravity(pending.originalNoGravity);
        if (living instanceof Mob mob) {
            mob.setNoAi(pending.originalNoAi);
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
        }
        living.setDeltaMovement(Vec3.ZERO);
        living.fallDistance = 0.0f;
        living.hurtMarked = true;
    }

    private static void finishStorageEffect(MinecraftServer server, LivingEntity living, ServerPlayer player) {
        PendingStorageEffect pending = pendingStorage(living.getUUID());
        stabilizePendingStorage(living, pending == null ? PendingStorageEffect.snapshot(living) : pending);
        if (player != null && !hasValidStoredSnapshot(CompanionDataService.data(player), living.getUUID())) {
            FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, living.getUUID(), living, "RETURNING", "ACTIVE", "missing_valid_snapshot", false, true);
            if (pending != null) {
                restorePendingStorage(living, pending);
            }
            CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "missing_valid_snapshot");
            return;
        }
        String entityType = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();
        if (player != null && CompanionDataService.data(player).effectStyle(living.getUUID(), com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType) != com.kuzhi.findme.common.CompanionEffectStyle.NONE && CompanionDataService.data(player).effectStyle(living.getUUID(), com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType) != com.kuzhi.findme.common.CompanionEffectStyle.ENDER) {
            CompanionMagicAudioService.playStorageComplete(player, living.position());
        }
        markIntentionalStorageRemoval(server, living.getUUID());
        living.discard();
        FindMeDebugLogger.lifecycle("ENTITY_REMOVED", player, living.getUUID(), living, "RETURNING", "STORED", "storage_effect_finished", player != null && CompanionDataService.data(player).storedEntity(living.getUUID()).isPresent(), false);
        CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "storage_effect_finished");
    }

    private static void finishHomeReturn(MinecraftServer server, LivingEntity living, PendingStorageEffect pending, ServerPlayer player) {
        stabilizePendingStorage(living, pending);
        String entityType = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();
        if (player != null && CompanionDataService.data(player).effectStyle(living.getUUID(), com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType) != com.kuzhi.findme.common.CompanionEffectStyle.NONE && CompanionDataService.data(player).effectStyle(living.getUUID(), com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType) != com.kuzhi.findme.common.CompanionEffectStyle.ENDER) {
            CompanionMagicAudioService.playStorageComplete(player, living.position());
        }
        ServerLevel destination = server.getLevel(pending.homeTarget.dimension());
        if (destination == null) {
            if (player == null) {
                return;
            }
            PlayerCompanionData data = CompanionDataService.data(player);
            if (!storeEntity(player, data, living)) {
                FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, living.getUUID(), living, "HOME_RETURNING", "ACTIVE", "home_dimension_missing_snapshot_failed", false, true);
                restorePendingStorage(living, pending);
                CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "snapshot_failed");
                return;
            }
            data.clearDeployed(pending.kind, living.getUUID());
            data.setLifecycleState(living.getUUID(), CompanionLifecycleState.HOME_STORED);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, pending.kind);
            markIntentionalStorageRemoval(server, living.getUUID());
            living.discard();
            FindMeDebugLogger.lifecycle("ENTITY_REMOVED", player, living.getUUID(), living, "HOME_RETURNING", "STORED", "home_dimension_missing", data.storedEntity(living.getUUID()).isPresent(), false);
            CompanionOperationLockService.end(player, living.getUUID(), CompanionOperationLockService.Operation.STORE, "home_dimension_missing");
            return;
        }
        BlockPos target = pending.homeTarget.blockPos();
        if (player == null) {
            restorePendingStorage(living, pending);
            LivingEntity moved = CompanionEntityTransferService.moveEntityTo(living, destination, target, pending.homeTarget.yRot(), pending.homeTarget.xRot(), false);
            CompanionHomeResidentService.markResidentEntity(moved);
            CompanionOperationLockService.end(null, living.getUUID(), CompanionOperationLockService.Operation.STORE, "home_return_offline");
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        UUID uuid = living.getUUID();
        if (!hasValidStoredSnapshot(data, uuid)) {
            FindMeDebugLogger.lifecycle("ENTITY_REMOVE_BLOCKED", player, uuid, living, "HOME_RETURNING", "ACTIVE", "missing_valid_snapshot", false, true);
            restorePendingStorage(living, pending);
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "missing_valid_snapshot");
            return;
        }
        markIntentionalStorageRemoval(server, uuid);
        living.discard();
        FindMeDebugLogger.lifecycle("ENTITY_REMOVED", player, uuid, living, "HOME_RETURNING", "HOME_RESTORE", "home_return_transfer", data.storedEntity(uuid).isPresent(), false);
        CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "home_return_transfer");
        Entity restored = CompanionEntityTransferService.restoreStoredEntityFreshForHome(destination, player, data,
                uuid, target, pending.homeTarget.yRot(), pending.homeTarget.xRot()).orElse(null);
        if (restored instanceof LivingEntity moved && moved.isAlive()) {
            CompanionHomeResidentService.markResidentEntity(moved);
            data.clearDeployed(pending.kind, moved.getUUID());
            data.setLifecycleState(moved.getUUID(), CompanionLifecycleState.HOME_ACTIVE);
            data.setLastKnownPosition(moved.getUUID(), SavedPosition.of(moved.level(), moved.getX(), moved.getY(), moved.getZ(), moved.getYRot(), moved.getXRot()));
        } else {
            data.clearDeployed(pending.kind, uuid);
            data.setLifecycleState(uuid, CompanionLifecycleState.HOME_STORED);
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, pending.kind);
    }

    private static boolean saveEntitySnapshot(ServerPlayer player, PlayerCompanionData data, LivingEntity living,
                                              boolean resetLiveState) {
        CompoundTag tag;
        if (resetLiveState) {
            resetLiveStateForStorage(living);
        }
        if (living.save(tag = new CompoundTag())) {
            writeStoredEntitySnapshot(player, data, living, tag);
            boolean valid = hasValidStoredSnapshot(data, living.getUUID());
            if (valid) {
                CompanionDataService.save(player, data);
            }
            return valid;
        }
        FindMeDebugLogger.lifecycle("SNAPSHOT_FAILED", player, living.getUUID(), living, "ACTIVE", "ACTIVE", "entity_save_failed", false, true);
        return false;
    }

    public static void writeStoredEntitySnapshot(ServerPlayer player, PlayerCompanionData data, LivingEntity living, CompoundTag tag) {
        writeStoredEntitySnapshot(data, living, tag, player.serverLevel().getGameTime());
        FindMeDebugLogger.lifecycle("SNAPSHOT_CREATED", player, living.getUUID(), living, "ACTIVE", "SNAPSHOT", "store_entity", true, true);
    }

    private static void writeStoredEntitySnapshot(PlayerCompanionData data, LivingEntity living, CompoundTag tag,
                                                  long storedAt) {
        resetStoredStateTags(tag);
        tag.putString("CompanionRescueName", living.getDisplayName().getString());
        tag.putString("CompanionRescueType", EntityType.getKey(living.getType()).toString());
        CompanionEntitySnapshots.writePreviewBounds((Entity)living, tag);
        tag.putFloat("CompanionRescueHealth", living.getHealth());
        tag.putFloat("CompanionRescueMaxHealth", living.getMaxHealth());
        tag.putInt("CompanionRescueArmor", living.getArmorValue());
        tag.putLong("CompanionRescueStoredAt", storedAt);
        tag.putBoolean("NoAI", false);
        tag.putBoolean("NoGravity", false);
        tag.putBoolean("Invisible", false);
        data.storeEntity(living.getUUID(), tag);
        data.setLastKnownPosition(living.getUUID(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
    }

    private static boolean hasValidStoredSnapshot(PlayerCompanionData data, UUID uuid) {
        if (data == null || uuid == null) {
            return false;
        }
        CompoundTag tag = data.storedEntity(uuid).orElse(null);
        return tag != null && !tag.isEmpty() && tag.contains("id");
    }

    private static void resetLiveStateForStorage(LivingEntity living) {
        living.stopRiding();
        living.clearFire();
        living.setTicksFrozen(0);
        living.setDeltaMovement(Vec3.ZERO);
        living.setInvisible(false);
        living.setNoGravity(false);
        living.fallDistance = 0.0f;
        living.hurtMarked = true;
        if (living instanceof TamableAnimal tamable) {
            tamable.setOrderedToSit(false);
            tamable.setInSittingPose(false);
        }
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setNoAi(false);
        }
    }

    private static void resetStoredStateTags(CompoundTag tag) {
        tag.putShort("Fire", (short)0);
        tag.putFloat("FallDistance", 0.0f);
        tag.putBoolean("NoAI", false);
        tag.putBoolean("NoGravity", false);
        tag.putBoolean("Invisible", false);
        tag.putBoolean("Sitting", false);
        tag.putBoolean("OrderedToSit", false);
        tag.putBoolean("InSittingPose", false);
        tag.putBoolean("IsSitting", false);
        tag.putBoolean("SittingPose", false);
        tag.putBoolean("Flying", false);
        tag.putBoolean("Hovering", false);
        tag.putBoolean("Tackle", false);
        tag.remove("Motion");
        tag.remove("Passengers");
        tag.remove("Leash");
    }

    private static PendingStorageEffect pendingStorage(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (PendingStorageEffect pending : PENDING_STORAGE_EFFECTS) {
            if (pending.entityUuid.equals(uuid)) {
                return pending;
            }
        }
        return null;
    }

    private static final class PendingStorageEffect {
        final UUID entityUuid;
        final UUID playerUuid;
        final int durationTicks;
        final CompanionKind kind;
        final SavedPosition homeTarget;
        final boolean continueWithoutPlayer;
        final boolean originalNoGravity;
        final boolean originalNoPhysics;
        final boolean originalNoAi;
        int age;

        private PendingStorageEffect(UUID entityUuid, UUID playerUuid, int durationTicks, CompanionKind kind, SavedPosition homeTarget,
                                     boolean continueWithoutPlayer,
                                     boolean originalNoGravity, boolean originalNoPhysics, boolean originalNoAi) {
            this.entityUuid = entityUuid;
            this.playerUuid = playerUuid;
            this.durationTicks = durationTicks;
            this.kind = kind;
            this.homeTarget = homeTarget;
            this.continueWithoutPlayer = continueWithoutPlayer;
            this.originalNoGravity = originalNoGravity;
            this.originalNoPhysics = originalNoPhysics;
            this.originalNoAi = originalNoAi;
        }

        static PendingStorageEffect snapshot(LivingEntity living) {
            return new PendingStorageEffect(living.getUUID(), null, 0, null, null, false,
                    living.isNoGravity(), living.noPhysics, living instanceof Mob mob && mob.isNoAi());
        }

        static PendingStorageEffect normal(LivingEntity living, UUID playerUuid, int durationTicks) {
            return new PendingStorageEffect(living.getUUID(), playerUuid, durationTicks, null, null, false,
                    living.isNoGravity(), living.noPhysics, living instanceof Mob mob && mob.isNoAi());
        }

        static PendingStorageEffect detached(LivingEntity living, UUID playerUuid, int durationTicks) {
            return new PendingStorageEffect(living.getUUID(), playerUuid, durationTicks, null, null, true,
                    living.isNoGravity(), living.noPhysics, living instanceof Mob mob && mob.isNoAi());
        }

        static PendingStorageEffect homeReturn(LivingEntity living, UUID playerUuid, int durationTicks, CompanionKind kind, SavedPosition homeTarget) {
            return new PendingStorageEffect(living.getUUID(), playerUuid, durationTicks, kind, homeTarget, false,
                    living.isNoGravity(), living.noPhysics, living instanceof Mob mob && mob.isNoAi());
        }
    }
}


