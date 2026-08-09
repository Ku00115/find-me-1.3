package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RecoveryCompanionListPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.RecoveryCompanionRecord;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.ui.CompanionTeamService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Bounded, server-authoritative reconciliation for registered creatures with missing entity data. */
public final class CompanionRecoveryService {
    private static final int RECONCILE_INTERVAL_TICKS = 100;
    private static final int MAX_RECORDS_PER_PASS = 8;
    private static final int INTEGRITY_AUDIT_INTERVAL_TICKS = 1_200;
    private static final int MAX_AUDITS_PER_PASS = 8;
    private static final int DEFERRED_REMOVAL_TICKS = 3;
    private static final long AUTOMATIC_STATUS_PERSIST_INTERVAL_TICKS = 1_200L;
    private static final Map<UUID, DeferredRemoval> DEFERRED_REMOVALS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, LinkedHashMap<RecoveryKey, RecoveryTarget>> RECOVERY_QUEUES = new WeakHashMap<>();
    private static final Map<MinecraftServer, Boolean> RECOVERY_QUEUE_INITIALIZED = new WeakHashMap<>();
    private static int integrityAuditCursor;

    private CompanionRecoveryService() {
    }

    public static Optional<UUID> ownerUuid(MinecraftServer server, UUID companionUuid) {
        if (server == null || companionUuid == null) return Optional.empty();
        return FindMeWorldSavedData.get(server).companionOwner(companionUuid);
    }

    public static boolean snapshotBeforeChunkUnload(MinecraftServer server, LivingEntity living) {
        Optional<UUID> owner = ownerUuid(server, living == null ? null : living.getUUID());
        if (owner.isEmpty() || living == null) return false;
        PlayerCompanionData data = CompanionDataService.data(server, owner.get());
        Optional<CompoundTag> snapshot = snapshot(living, server.overworld().getGameTime());
        if (snapshot.isEmpty()) return false;
        data.storeEntity(living.getUUID(), snapshot.get());
        data.setLastKnownPosition(living.getUUID(), position(living));
        CompanionDataService.save(server, owner.get(), data);
        return true;
    }

    public static void deferUnexpectedRemoval(MinecraftServer server, LivingEntity living,
                                              Entity.RemovalReason removalReason) {
        if (server == null || living == null) return;
        ownerUuid(server, living.getUUID()).ifPresent(owner -> DEFERRED_REMOVALS.put(living.getUUID(),
                new DeferredRemoval(owner, living.getUUID(), removalReason == null ? "unknown" : removalReason.name(),
                        server.overworld().getGameTime() + DEFERRED_REMOVAL_TICKS,
                        snapshot(living, server.overworld().getGameTime()).orElse(null))));
    }

    public static boolean salvageDestructiveRemoval(MinecraftServer server, LivingEntity living,
                                                     Entity.RemovalReason removalReason) {
        if (server == null || living == null) return false;
        Optional<UUID> owner = ownerUuid(server, living.getUUID());
        if (owner.isEmpty()) return false;
        PlayerCompanionData data = CompanionDataService.data(server, owner.get());
        CompanionLifecycleState source = data.lifecycleState(living.getUUID());
        Optional<CompoundTag> snapshot = snapshot(living, server.overworld().getGameTime());
        if (snapshot.isEmpty()) {
            snapshot = data.rawStoredEntityForDiagnostics(living.getUUID());
        }
        if (snapshot.filter(tag -> validSnapshot(living.getUUID(), tag)).isPresent()) {
            commitStored(server, owner.get(), data, living.getUUID(), snapshot.get(), "destructive_removal_salvaged");
            FindMeDebugLogger.info("recovery", "destructive removal salvaged owner={} companion={} reason={}",
                    owner.get(), living.getUUID(), removalReason);
            return true;
        }
        markRecovery(server, owner.get(), data, living.getUUID(), living, source,
                "destructive_removal", removalReason == null ? "unknown" : removalReason.name());
        return true;
    }

    public static void markRecovery(MinecraftServer server, UUID ownerUuid, PlayerCompanionData data,
                                    UUID companionUuid, LivingEntity living, CompanionLifecycleState sourceState,
                                    String reason, String detail) {
        if (server == null || ownerUuid == null || data == null || companionUuid == null) return;
        CompanionKind kind = data.kindOf(companionUuid).orElse(CompanionKind.COMPANION);
        RecoveryCompanionRecord old = data.recoveryRecord(companionUuid).orElse(null);
        long now = server.overworld().getGameTime();
        SavedPosition last = living == null ? data.lastKnownPosition(companionUuid).orElse(null) : position(living);
        CompoundTag stored = data.rawStoredEntityForDiagnostics(companionUuid).orElse(null);
        String entityType = living != null ? EntityType.getKey(living.getType()).toString()
                : stored == null ? data.storedEntityType(companionUuid).orElse("")
                : CompanionEntitySnapshots.storedEntityType(stored);
        String name = data.displayName(companionUuid).orElseGet(() -> living != null
                ? living.getDisplayName().getString()
                : stored == null ? companionUuid.toString().substring(0, 8)
                : CompanionEntitySnapshots.storedEntityName(stored, companionUuid));
        CompanionLifecycleState original = old == null
                ? (sourceState == null ? data.lifecycleState(companionUuid) : sourceState)
                : old.sourceState();
        int previousTeam = old == null ? data.teamIndexOf(companionUuid) : old.previousTeamIndex();
        RecoveryCompanionRecord record = new RecoveryCompanionRecord(old == null ? UUID.randomUUID() : old.recordId(),
                companionUuid, name, entityType, kind, previousTeam,
                old == null ? now : old.detectedAt(), now,
                last == null ? "" : last.dimension().location().toString(),
                last == null ? 0.0 : last.x(), last == null ? 0.0 : last.y(), last == null ? 0.0 : last.z(),
                reason, detail, original);
        data.putRecoveryRecord(record);
        data.removeTeamMember(companionUuid);
        data.clearDeployed(kind, companionUuid);
        data.setLifecycleState(companionUuid, CompanionLifecycleState.RECOVERY);
        List<UUID> available = data.wheelOrder(kind);
        if (!available.isEmpty()) data.setActiveUuid(kind, available.get(0));
        CompanionDataService.save(server, ownerUuid, data);
        enqueueRecovery(server, ownerUuid, companionUuid);
        syncOnline(server, ownerUuid);
        FindMeDebugLogger.info("recovery", "marked recovery owner={} companion={} reason={} detail={}",
                ownerUuid, companionUuid, reason, detail);
    }

    public static Result retry(ServerPlayer player, UUID companionUuid) {
        if (player == null || companionUuid == null) return Result.NOT_FOUND;
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!data.isRecovery(companionUuid)) return Result.NOT_FOUND;
        return reconcileOne(player.getServer(), player.getUUID(), data, companionUuid, true);
    }

    public static boolean delete(ServerPlayer player, UUID companionUuid) {
        if (player == null || companionUuid == null) return false;
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!data.isRecovery(companionUuid)) return false;
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_delete_recovery_record")) return false;
        data.remove(companionUuid);
        CompanionDataService.save(player, data);
        dequeueRecovery(player.getServer(), player.getUUID(), companionUuid);
        syncOnline(player.getServer(), player.getUUID());
        return true;
    }

    /** Converts a missing-data record into a normal death record without pretending missing data is recoverable. */
    public static boolean moveToDead(ServerPlayer player, UUID companionUuid) {
        if (player == null || companionUuid == null) return false;
        PlayerCompanionData data = CompanionDataService.data(player);
        RecoveryCompanionRecord recovery = data.recoveryRecord(companionUuid).orElse(null);
        if (recovery == null || !data.isRecovery(companionUuid) || !data.contains(companionUuid)) return false;
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_move_recovery_to_dead")) return false;

        long now = player.serverLevel().getGameTime();
        CompoundTag stored = data.rawStoredEntityForDiagnostics(companionUuid).orElse(null);
        boolean recoverable = validSnapshot(companionUuid, stored);
        String entityType = recovery.entityType();
        String name = data.displayName(companionUuid).orElse(recovery.customName());
        if (recoverable) {
            CompoundTag deadTag = CompanionStorageService.sanitizedStoredTag(stored);
            entityType = CompanionEntitySnapshots.storedEntityType(deadTag);
            if (entityType.isBlank()) entityType = recovery.entityType();
            if (name == null || name.isBlank()) name = CompanionEntitySnapshots.storedEntityName(deadTag, companionUuid);
            float savedMaxHealth = deadTag.getFloat("CompanionRescueMaxHealth");
            float maxHealth = Math.max(1.0f, savedMaxHealth > 0.0f ? savedMaxHealth : deadTag.getFloat("Health"));
            deadTag.putString("id", entityType);
            deadTag.putString("CompanionRescueType", entityType);
            deadTag.putString("CompanionRescueName", name);
            deadTag.putFloat("CompanionRescueMaxHealth", maxHealth);
            deadTag.putFloat("CompanionRescueHealth", maxHealth);
            deadTag.putBoolean("CompanionRescueDead", true);
            deadTag.putFloat("Health", maxHealth);
            data.storeEntity(companionUuid, deadTag);
        }
        if (name == null || name.isBlank()) name = companionUuid.toString().substring(0, 8);
        data.putDeadRecord(new DeadCompanionRecord(
                UUID.randomUUID(), companionUuid, name, entityType, recovery.kind(), recovery.previousTeamIndex(),
                now, player.serverLevel().getDayTime() / 24000L, recovery.dimension(), recovery.x(), recovery.y(), recovery.z(),
                "recovery:" + recovery.reason(), recoverable, recoverable ? "" : "No valid entity snapshot is available."));
        data.markDead(companionUuid, recovery.kind());
        data.setLifecycleState(companionUuid, CompanionLifecycleState.DEAD);
        CompanionDataService.save(player, data);
        dequeueRecovery(player.getServer(), player.getUUID(), companionUuid);
        syncOnline(player.getServer(), player.getUUID());
        FindMeDebugLogger.info("recovery", "moved recovery to dead owner={} companion={} recoverable={}",
                player.getUUID(), companionUuid, recoverable);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long now = server.overworld().getGameTime();
        processDeferred(server, now);
        boolean reconcileDue = now % RECONCILE_INTERVAL_TICKS == 0L;
        boolean auditDue = now % INTEGRITY_AUDIT_INTERVAL_TICKS == 0L;
        if (!reconcileDue && !auditDue) return;
        FindMeWorldSavedData world = FindMeWorldSavedData.get(server);
        ArrayList<RecoveryTarget> auditTargets = new ArrayList<>();
        if (reconcileDue) {
            initializeRecoveryQueue(server, world);
            processRecoveryQueue(server, MAX_RECORDS_PER_PASS);
        }
        if (auditDue) {
            for (UUID owner : world.playerUuids()) {
                for (UUID companionUuid : world.companionRuntimeIndex(owner).uuids()) {
                    var indexEntry = world.companionRuntimeIndex(owner).entry(companionUuid);
                    if (indexEntry == null || indexEntry.lifecycleState() == CompanionLifecycleState.DEAD
                            || indexEntry.lifecycleState() == CompanionLifecycleState.SHOULDER
                            || indexEntry.lifecycleState() == CompanionLifecycleState.RECOVERY) continue;
                    auditTargets.add(new RecoveryTarget(owner, companionUuid));
                }
            }
        }
        Map<UUID, PlayerCompanionData> ownerData = new HashMap<>();
        if (auditDue) {
            integrityAuditCursor = processAuditTargets(server, auditTargets, integrityAuditCursor,
                    MAX_AUDITS_PER_PASS, ownerData);
        }
    }

    public static void resetServerState() {
        DEFERRED_REMOVALS.clear();
        RECOVERY_QUEUES.clear();
        RECOVERY_QUEUE_INITIALIZED.clear();
        integrityAuditCursor = 0;
    }

    /** Enqueue a recovery state created by a subsystem that has no entity snapshot to record. */
    public static void scheduleRecovery(MinecraftServer server, UUID owner, UUID companionUuid) {
        enqueueRecovery(server, owner, companionUuid);
    }

    public static void syncToClient(ServerPlayer player) {
        if (player == null) return;
        PlayerCompanionData data = CompanionDataService.data(player);
        ArrayList<RecoveryCompanionListPacket.Entry> entries = new ArrayList<>();
        for (RecoveryCompanionRecord record : data.recoveryRecords()) {
            CompoundTag stored = data.rawStoredEntityForDiagnostics(record.sourceEntityId()).orElse(null);
            String entityType = !record.entityType().isBlank() ? record.entityType()
                    : stored == null ? "" : CompanionEntitySnapshots.storedEntityType(stored);
            CompoundTag preview = stored == null ? null : CompanionEntitySnapshots.previewStoredEntityTag(stored);
            entries.add(new RecoveryCompanionListPacket.Entry(record.sourceEntityId(), record.kind(), entityType,
                    record.customName(), record.recordId(), record.previousTeamIndex(), record.detectedAt(),
                    record.lastCheckedAt(), record.dimension(), record.x(), record.y(), record.z(), record.reason(),
                    record.detail(), record.sourceState(), CompanionEntityClassifier.moveType(entityType, record.kind()),
                    preview));
        }
        ModNetwork.sendToPlayer(player, new RecoveryCompanionListPacket(entries));
    }

    public static boolean validSnapshot(UUID expectedUuid, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return false;
        String id = CompanionEntitySnapshots.storedEntityType(tag);
        ResourceLocation typeId = ResourceLocation.tryParse(id);
        if (typeId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)) return false;
        return !tag.hasUUID("UUID") || expectedUuid != null && expectedUuid.equals(tag.getUUID("UUID"));
    }

    private static Result reconcileOne(MinecraftServer server, UUID owner, PlayerCompanionData data,
                                       UUID companionUuid, boolean manual) {
        Entity loaded = CompanionEntityLookup.findEntity(server, companionUuid).orElse(null);
        if (loaded instanceof LivingEntity living && living.isAlive()) {
            Optional<CompoundTag> snapshot = snapshot(living, server.overworld().getGameTime());
            if (snapshot.filter(tag -> validSnapshot(companionUuid, tag)).isPresent()) {
                CompanionHomeResidentService.clearResident(living);
                living.ejectPassengers();
                living.stopRiding();
                CompanionStorageService.markIntentionalStorageRemoval(server, companionUuid);
                living.discard();
                commitStored(server, owner, data, companionUuid, snapshot.get(), "loaded_entity_recovered");
                return Result.RECOVERED;
            }
        }
        Optional<CompoundTag> current = data.rawStoredEntityForDiagnostics(companionUuid)
                .filter(tag -> validSnapshot(companionUuid, tag));
        if (current.isPresent()) {
            commitStored(server, owner, data, companionUuid, current.get(), "current_snapshot_recovered");
            return Result.RECOVERED;
        }
        Optional<CompoundTag> archived = data.recoverySnapshotsFromArchives(companionUuid).stream()
                .filter(tag -> validSnapshot(companionUuid, tag)).findFirst();
        if (archived.isPresent()) {
            commitStored(server, owner, data, companionUuid, archived.get(), "archive_snapshot_recovered");
            return Result.RECOVERED;
        }
        RecoveryCompanionRecord record = data.recoveryRecord(companionUuid).orElse(null);
        if (record != null) {
            long now = server.overworld().getGameTime();
            String detail = manual ? "Manual retry found no valid entity data."
                    : "Automatic retry found no valid entity data.";
            boolean persistStatus = manual || !detail.equals(record.detail())
                    || now - record.lastCheckedAt() >= AUTOMATIC_STATUS_PERSIST_INTERVAL_TICKS;
            if (persistStatus) {
                data.putRecoveryRecord(record.checkedAt(now, detail));
                CompanionDataService.save(server, owner, data);
                ServerPlayer online = server.getPlayerList().getPlayer(owner);
                if (online != null) syncToClient(online);
            }
        }
        return Result.STILL_MISSING;
    }

    private static int processAuditTargets(MinecraftServer server, List<RecoveryTarget> targets, int cursor, int limit,
                                            Map<UUID, PlayerCompanionData> ownerData) {
        if (targets.isEmpty()) return 0;
        targets.sort(Comparator.comparing(RecoveryTarget::ownerUuid)
                .thenComparing(RecoveryTarget::companionUuid));
        int attempts = Math.min(limit, targets.size());
        int start = Math.floorMod(cursor, targets.size());
        for (int offset = 0; offset < attempts; offset++) {
            RecoveryTarget target = targets.get((start + offset) % targets.size());
            PlayerCompanionData data = ownerData.computeIfAbsent(target.ownerUuid(),
                    owner -> CompanionDataService.data(server, owner));
            auditOne(server, target.ownerUuid(), data, target.companionUuid());
        }
        return (start + attempts) % targets.size();
    }

    private static void initializeRecoveryQueue(MinecraftServer server, FindMeWorldSavedData world) {
        if (RECOVERY_QUEUE_INITIALIZED.putIfAbsent(server, Boolean.TRUE) != null) return;
        LinkedHashMap<RecoveryKey, RecoveryTarget> queue = recoveryQueue(server);
        for (UUID owner : world.playerUuids()) {
            for (UUID companionUuid : world.companionRuntimeIndex(owner).uuids()) {
                var entry = world.companionRuntimeIndex(owner).entry(companionUuid);
                if (entry != null && entry.lifecycleState() == CompanionLifecycleState.RECOVERY) {
                    queue.put(new RecoveryKey(owner, companionUuid), new RecoveryTarget(owner, companionUuid));
                }
            }
        }
    }

    private static void processRecoveryQueue(MinecraftServer server, int limit) {
        LinkedHashMap<RecoveryKey, RecoveryTarget> queue = recoveryQueue(server);
        int processed = 0;
        while (processed < limit && !queue.isEmpty()) {
            RecoveryTarget target = queue.values().iterator().next();
            queue.remove(new RecoveryKey(target.ownerUuid(), target.companionUuid()));
            PlayerCompanionData data = CompanionDataService.data(server, target.ownerUuid());
            if (data.isRecovery(target.companionUuid())) {
                reconcileOne(server, target.ownerUuid(), data, target.companionUuid(), false);
                if (data.isRecovery(target.companionUuid())) {
                    queue.put(new RecoveryKey(target.ownerUuid(), target.companionUuid()), target);
                }
            }
            processed++;
        }
    }

    private static void enqueueRecovery(MinecraftServer server, UUID owner, UUID companionUuid) {
        if (server == null || owner == null || companionUuid == null) return;
        recoveryQueue(server).put(new RecoveryKey(owner, companionUuid), new RecoveryTarget(owner, companionUuid));
    }

    private static void dequeueRecovery(MinecraftServer server, UUID owner, UUID companionUuid) {
        if (server == null || owner == null || companionUuid == null) return;
        LinkedHashMap<RecoveryKey, RecoveryTarget> queue = RECOVERY_QUEUES.get(server);
        if (queue != null) queue.remove(new RecoveryKey(owner, companionUuid));
    }

    private static LinkedHashMap<RecoveryKey, RecoveryTarget> recoveryQueue(MinecraftServer server) {
        return RECOVERY_QUEUES.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
    }

    private static void auditOne(MinecraftServer server, UUID owner, PlayerCompanionData data, UUID companionUuid) {
        if (!data.contains(companionUuid) || data.isRecovery(companionUuid)) return;
        CompanionLifecycleState state = data.lifecycleState(companionUuid);
        if (state == CompanionLifecycleState.DEAD || state == CompanionLifecycleState.SHOULDER) return;
        Entity loaded = CompanionEntityLookup.findEntity(server, companionUuid).orElse(null);
        if (loaded instanceof LivingEntity living && living.isAlive()) return;
        CompoundTag stored = data.rawStoredEntityForDiagnostics(companionUuid).orElse(null);
        if (stored != null && validSnapshot(companionUuid, stored)) return;
        String detail = stored == null ? "No stored snapshot is available for lifecycle " + state + "."
                : "The stored snapshot has an invalid entity type or mismatched UUID.";
        markRecovery(server, owner, data, companionUuid, null, state,
                stored == null ? "missing_snapshot" : "invalid_snapshot", detail);
    }

    private static void commitStored(MinecraftServer server, UUID owner, PlayerCompanionData data, UUID companionUuid,
                                     CompoundTag snapshot, String source) {
        CompanionKind kind = data.kindOf(companionUuid).orElse(CompanionKind.COMPANION);
        data.storeEntity(companionUuid, snapshot);
        data.clearDeployed(kind, companionUuid);
        data.setLifecycleState(companionUuid, CompanionLifecycleState.STORED);
        CompanionDataService.save(server, owner, data);
        dequeueRecovery(server, owner, companionUuid);
        syncOnline(server, owner);
        FindMeDebugLogger.info("recovery", "recovered to storage owner={} companion={} source={}",
                owner, companionUuid, source);
    }

    private static void processDeferred(MinecraftServer server, long now) {
        Iterator<DeferredRemoval> iterator = DEFERRED_REMOVALS.values().iterator();
        while (iterator.hasNext()) {
            DeferredRemoval pending = iterator.next();
            if (pending.dueTick > now) continue;
            iterator.remove();
            if (CompanionEntityLookup.findEntity(server, pending.companionUuid).isPresent()) continue;
            PlayerCompanionData data = CompanionDataService.data(server, pending.ownerUuid);
            if (!data.contains(pending.companionUuid)
                    || data.lifecycleState(pending.companionUuid) == CompanionLifecycleState.DEAD) continue;
            if (pending.snapshot != null && validSnapshot(pending.companionUuid, pending.snapshot)) {
                commitStored(server, pending.ownerUuid, data, pending.companionUuid, pending.snapshot,
                        "deferred_" + pending.reason.toLowerCase());
            } else {
                markRecovery(server, pending.ownerUuid, data, pending.companionUuid, null,
                        data.lifecycleState(pending.companionUuid), "unexpected_removal", pending.reason);
            }
        }
    }

    private static Optional<CompoundTag> snapshot(LivingEntity living, long storedAt) {
        if (living == null) return Optional.empty();
        CompoundTag raw = new CompoundTag();
        if (!living.save(raw)) return Optional.empty();
        CompoundTag tag = CompanionStorageService.sanitizedStoredTag(raw);
        String entityType = EntityType.getKey(living.getType()).toString();
        tag.putString("id", entityType);
        tag.putString("CompanionRescueType", entityType);
        tag.putString("CompanionRescueName", living.getDisplayName().getString());
        tag.putFloat("CompanionRescueHealth", Math.max(1.0f, living.getHealth()));
        tag.putFloat("CompanionRescueMaxHealth", Math.max(1.0f, living.getMaxHealth()));
        tag.putInt("CompanionRescueArmor", living.getArmorValue());
        tag.putLong("CompanionRescueStoredAt", storedAt);
        tag.putBoolean("CompanionRescueDead", false);
        tag.putFloat("Health", Math.max(1.0f, living.getHealth()));
        CompanionEntitySnapshots.writePreviewBounds(living, tag);
        return Optional.of(tag);
    }

    private static SavedPosition position(LivingEntity living) {
        return SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(),
                living.getYRot(), living.getXRot());
    }

    private static void syncOnline(MinecraftServer server, UUID owner) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) return;
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionTeamService.syncToClient(player);
        syncToClient(player);
    }

    public enum Result {
        RECOVERED,
        STILL_MISSING,
        NOT_FOUND
    }

    private record DeferredRemoval(UUID ownerUuid, UUID companionUuid, String reason, long dueTick,
                                   CompoundTag snapshot) {
        private DeferredRemoval {
            snapshot = snapshot == null ? null : snapshot.copy();
        }
    }

    private record RecoveryTarget(UUID ownerUuid, UUID companionUuid) {
    }

    private record RecoveryKey(UUID ownerUuid, UUID companionUuid) {
    }
}
