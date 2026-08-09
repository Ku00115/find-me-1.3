package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMePerformanceMonitor;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.lifecycle.CompanionShoulderService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.api.FindMeApi;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionSyncService {
    private static final Map<UUID, EnumMap<CompanionKind, Map<UUID, PreviewCache>>> PREVIEW_CACHES = new HashMap<>();
    private static MinecraftServer batchingServer;
    private static final Map<SyncKey, PendingSync> PENDING_SYNCS = new LinkedHashMap<>();

    private CompanionSyncService() {
    }

    public static void beginServerTick(MinecraftServer server) {
        batchingServer = server;
        PENDING_SYNCS.clear();
    }

    public static void flushServerTick(MinecraftServer server) {
        if (server == null || batchingServer != server) {
            return;
        }
        List<PendingSync> pending = List.copyOf(PENDING_SYNCS.values());
        PENDING_SYNCS.clear();
        batchingServer = null;
        for (PendingSync sync : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(sync.playerUuid());
            if (player != null) {
                syncToClientNow(player, sync.kind(), sync.data());
            }
        }
    }

    public static void syncToClient(ServerPlayer player, CompanionKind kind) {
        syncToClient(player, kind, CompanionDataService.data(player));
    }

    public static void syncToClient(ServerPlayer player, CompanionKind kind, PlayerCompanionData data) {
        if (player == null || kind == null || data == null) {
            return;
        }
        if (batchingServer == player.getServer()) {
            PENDING_SYNCS.put(new SyncKey(player.getUUID(), kind),
                    new PendingSync(player.getUUID(), kind, data));
            return;
        }
        syncToClientNow(player, kind, data);
    }

    public static void resetServerState() {
        batchingServer = null;
        PENDING_SYNCS.clear();
    }

    private static void syncToClientNow(ServerPlayer player, CompanionKind kind, PlayerCompanionData data) {
        long startedAt = FindMePerformanceMonitor.start();
        try {
            ArrayList<CompanionListPacket.Entry> entries = new ArrayList<>();
            LinkedHashMap<UUID, CompanionListPacket.Entry> allByUuid = new LinkedHashMap<>();
            for (UUID uuid : data.list(kind)) {
                if (data.isRecovery(uuid)) continue;
                allByUuid.put(uuid, entryFor(player, data, kind, uuid));
            }
            for (UUID uuid : data.wheelOrder(kind)) {
                CompanionListPacket.Entry entry = allByUuid.get(uuid);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            prunePreviewCache(player.getUUID(), kind, allByUuid.keySet());
            ArrayList<CompanionListPacket.Entry> allEntries = new ArrayList<>(allByUuid.values());
            ModNetwork.sendToPlayer(player, new CompanionListPacket(kind, CompanionDataService.revision(player),
                    data.activeWheelIndex(kind), entries, allEntries));
            CompanionWheelTransactionService.observeRoster(player, kind, allEntries);
        } finally {
            FindMePerformanceMonitor.recordRosterSync(startedAt);
        }
    }

    private static CompanionListPacket.Entry entryFor(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        Entity entity = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
        Optional<CompoundTag> shoulderTag = kind == CompanionKind.COMPANION ? CompanionShoulderService.shoulderEntityTag(player, uuid) : Optional.empty();
        Optional<CompoundTag> storedTag = shoulderTag.map(tag -> CompanionStorageService.storedShoulderTag(player, uuid, tag)).or(() -> data.storedEntity(uuid).map(tag -> CompanionStorageService.healedStoredEntity(player, data, uuid, tag)));
        String entityType = entity == null ? storedTag.map(CompanionEntitySnapshots::storedEntityType).orElse("") : EntityType.getKey(entity.getType()).toString();
        String name = data.displayName(uuid).orElseGet(() -> entity == null ? storedTag.map(tag -> CompanionEntitySnapshots.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8)) : entity.getDisplayName().getString());
        boolean loaded = entity != null;
        boolean alive = entity == null ? storedTag.isPresent() : entity.isAlive();
        boolean ridden = player.getVehicle() != null && player.getVehicle().getUUID().equals(uuid);
        boolean liveInWorld = entity instanceof LivingEntity living && living.isAlive() && !CompanionStorageService.isStoragePending(living);
        boolean homeResident = CompanionHomeResidentService.isResident(uuid);
        boolean deployed = kind == CompanionKind.MOUNT
                ? ridden || data.isDeployed(kind, uuid)
                : ridden || shoulderTag.isPresent() || data.isDeployed(kind, uuid) || liveInWorld && !homeResident;
        boolean hasHome = data.homePosition(uuid).isPresent();
        float health = 0.0f;
        float maxHealth = 0.0f;
        float armor = 0.0f;
        if (entity instanceof LivingEntity living) {
            health = living.getHealth();
            maxHealth = living.getMaxHealth();
            armor = living.getArmorValue();
        } else if (storedTag.isPresent()) {
            CompoundTag tag = storedTag.get();
            health = tag.getFloat("CompanionRescueHealth");
            maxHealth = tag.getFloat("CompanionRescueMaxHealth");
            armor = tag.getInt("CompanionRescueArmor");
        }
        CompanionMoveType entryMoveType = entity == null ? CompanionEntityClassifier.moveType(entityType, kind) : CompanionEntityClassifier.moveType(entity, kind);
        CompoundTag previewTag = previewTagForPacket(player, kind, uuid, entityType, entity,
                storedTag.orElse(null), CompanionDataService.revision(player));
        var spellBindings = data.spellBindings(uuid);
        var magicState = FindMeApi.companionMagicState(player, uuid,
                entity instanceof LivingEntity living ? living : null, storedTag.orElse(null), spellBindings);
        return new CompanionListPacket.Entry(uuid, entity == null ? -1 : entity.getId(), entityType, name, loaded,
                alive, data.isCritical(uuid), deployed, ridden, hasHome, homeResident,
                CompanionTacticalOrderService.currentAction(uuid), health, maxHealth, armor, entryMoveType,
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.SUMMON, entityType),
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.RESCUE, entityType),
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.STORAGE, entityType),
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.SWITCH, entityType),
                data.effectStyle(uuid, CompanionEffectPurpose.SUMMON, entityType),
                data.effectStyle(uuid, CompanionEffectPurpose.RESCUE, entityType),
                data.effectStyle(uuid, CompanionEffectPurpose.STORAGE, entityType),
                spellBindings, magicState, previewTag);
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (player != null) {
            PREVIEW_CACHES.remove(player.getUUID());
        }
    }

    private static void prunePreviewCache(UUID playerUuid, CompanionKind kind,
                                          java.util.Set<UUID> retainedUuids) {
        EnumMap<CompanionKind, Map<UUID, PreviewCache>> playerCache = PREVIEW_CACHES.get(playerUuid);
        Map<UUID, PreviewCache> kindCache = playerCache == null ? null : playerCache.get(kind);
        if (kindCache != null) kindCache.keySet().retainAll(retainedUuids);
    }

    public static void syncDeadToClient(ServerPlayer player) {
        PlayerCompanionData data = CompanionDataService.data(player);
        ArrayList<DeadCompanionListPacket.Entry> entries = new ArrayList<>();
        for (UUID uuid : data.deadList()) {
            Optional<CompoundTag> storedTag = data.storedEntity(uuid);
            CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
            String entityType = storedTag.map(CompanionEntitySnapshots::storedEntityType).orElse("");
            String name = data.displayName(uuid).orElseGet(() -> storedTag.map(tag -> CompanionEntitySnapshots.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8)));
            DeadCompanionRecord record = data.deadRecord(uuid).orElse(new DeadCompanionRecord(UUID.randomUUID(), uuid, name, entityType, kind,
                    -1, 0L, 0L, "", 0.0, 0.0, 0.0, "鏈煡", false, ""));
            boolean sleepRecoverable = storedTag
                    .map(tag -> tag.contains("id") && !tag.getString("id").isBlank())
                    .orElse(false);
            float health = storedTag.map(tag -> tag.getFloat("CompanionRescueHealth")).orElse(0.0f);
            float maxHealth = storedTag.map(tag -> tag.getFloat("CompanionRescueMaxHealth")).orElse(0.0f);
            float armor = storedTag.map(tag -> (float)tag.getInt("CompanionRescueArmor")).orElse(0.0f);
            CompanionMoveType moveType = CompanionEntityClassifier.moveType(entityType, kind);
            CompoundTag previewTag = storedTag.map(CompanionEntitySnapshots::previewStoredEntityTag).orElse(null);
            entries.add(new DeadCompanionListPacket.Entry(uuid, kind, entityType, name, health, maxHealth, armor, moveType,
                    record.recordId(), record.previousTeamIndex(), record.deathTime(), record.worldDay(), record.dimension(), record.x(), record.y(),
                    record.z(), record.deathCause(), sleepRecoverable, record.recoveryRequirements(), previewTag));
        }
        ModNetwork.sendToPlayer(player, new DeadCompanionListPacket(entries));
    }

    private static CompoundTag previewTagForPacket(ServerPlayer player, CompanionKind kind, UUID uuid,
                                                    String entityType, Entity entity, CompoundTag storedTag,
                                                    long revision) {
        long startedAt = FindMePerformanceMonitor.start();
        try {
            Map<UUID, PreviewCache> kindCache = PREVIEW_CACHES
                .computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(CompanionKind.class))
                .computeIfAbsent(kind, ignored -> new HashMap<>());
            PreviewCache cache = kindCache.computeIfAbsent(uuid, ignored -> new PreviewCache());
            long gameTime = player.getServer().overworld().getGameTime();
            int entityId = entity == null ? -1 : entity.getId();
            int storedHash = storedTag == null ? 0 : storedTag.hashCode();
            if (cache.builtTag == null || cache.buildTick != gameTime || cache.revision != revision
                    || cache.entityId != entityId || cache.storedHash != storedHash
                    || !entityType.equals(cache.entityType)) {
                cache.builtTag = entity == null
                        ? (storedTag == null ? null : storedTag.copy())
                        : CompanionEntitySnapshots.previewEntityTag(entity, entityType);
                cache.buildTick = gameTime;
                cache.revision = revision;
                cache.entityId = entityId;
                cache.storedHash = storedHash;
                cache.entityType = entityType;
            }
            CompoundTag previewTag = cache.builtTag;
            if (previewTag == null) return null;
            if (entityType.equals(cache.sentEntityType) && cache.sentTag != null
                    && cache.sentTag.equals(previewTag)) {
                return null;
            }
            cache.sentEntityType = entityType;
            cache.sentTag = previewTag.copy();
            return previewTag;
        } finally {
            FindMePerformanceMonitor.recordPreviewBuild(startedAt);
        }
    }

    private static final class PreviewCache {
        private long buildTick = Long.MIN_VALUE;
        private long revision = Long.MIN_VALUE;
        private int entityId = Integer.MIN_VALUE;
        private int storedHash;
        private String entityType = "";
        private CompoundTag builtTag;
        private String sentEntityType = "";
        private CompoundTag sentTag;
    }

    private record SyncKey(UUID playerUuid, CompanionKind kind) {
    }

    private record PendingSync(UUID playerUuid, CompanionKind kind, PlayerCompanionData data) {
    }
}

