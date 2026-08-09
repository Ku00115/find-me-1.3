package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.ui.CompanionMessageService;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionTeamService;

import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.FindMePerformanceMonitor;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.data.PlayerCompanionDataMirror;
import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.lifecycle.CompanionSpawnPlacementService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.api.FindMeApi;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

import com.kuzhi.findme.server.ui.CompanionSyncService;
public final class CompanionSafetyService {
    public static final int MANUAL_BACKUP_CAPACITY = 4;
    private static final java.util.Map<UUID, Long> LAST_SLEEP_REVIVE = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Long> NEXT_AUTO_BACKUP_CHECK = new java.util.HashMap<>();

    private CompanionSafetyService() {
    }

    public static int createBackup(ServerPlayer player, String reason) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!hasBackupWorthyState(data)) {
            CompanionMessageService.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionMessageService.label(CompanionKind.COMPANION));
            return 0;
        }
        long now = player.serverLevel().getGameTime();
        data.createBackup(now, reason, MANUAL_BACKUP_CAPACITY, true, backupPreviewContents(player.getServer(), data));
        CompanionDataService.save(player, data);
        PlayerCompanionDataMirror.rememberBackup(player, now, reason, MANUAL_BACKUP_CAPACITY);
        scheduleNextAutoBackup(player, now);
        CompanionMessageService.tell(player, "message.find_me.backup_created", ChatFormatting.GREEN, new Object[0]);
        return 1;
    }

    public static int restoreBackup(ServerPlayer player, int index) {
        return restoreBackup(player, index, -1L);
    }

    public static int restoreBackup(ServerPlayer player, int index, long expectedSavedAt) {
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<PlayerCompanionData.BackupEntry> targetBackup = data.backupAt(index);
        if (targetBackup.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_backup_index", ChatFormatting.RED, new Object[0]);
            return 0;
        }
        PlayerCompanionData.BackupEntry backup = targetBackup.get();
        if (!data.backupChecksumMatches(backup)) {
            player.sendSystemMessage(Component.translatable("message.find_me.backup_restore_checksum_mismatch").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (expectedSavedAt > 0L && backup.savedAt() != expectedSavedAt) {
            player.sendSystemMessage(Component.translatable("message.find_me.backup_restore_changed").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (CompanionOperationLockService.hasActiveForPlayer(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.find_me.backup_restore_active_lock").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!createForcedBackup(player, data, "before_restore")) {
            CompanionMessageService.tell(player, "message.find_me.backup_required_failed", ChatFormatting.RED, new Object[0]);
            return 0;
        }
        CompanionTransientStateService.cancelPlayerAll(player, CompanionTransientStateService.Reason.RECOVER);
        data.restoreBackup(backup);
        CompanionDataService.save(player, data);
        syncAll(player);
        FindMeDebugLogger.info("recovery", "backup_restored player={} index={} savedAt={} reason={}",
                player.getUUID(), index, backup.savedAt(), backup.reason());
        CompanionMessageService.tell(player, "message.find_me.backup_restored", ChatFormatting.GREEN, index);
        return 1;
    }

    static int recoveryRemoveTemporary(CommandSourceStack source, ServerPlayer player, boolean confirm) {
        int count = countTemporaryPerformers(player);
        if (!confirm) {
            reply(source, "FindMe recovery dry-run: temporary skill performer entities=" + count);
            return Math.max(1, count);
        }
        if (!createForcedBackup(player, "before_remove_temporary")) {
            reply(source, "FindMe recovery refused: could not create a safety backup first.");
            return 0;
        }
        int removed = removeTemporaryPerformers(player);
        FindMeDebugLogger.info("recovery", "admin={} player={} removed_temporary={}",
                source.getTextName(), player.getUUID(), removed);
        reply(source, "FindMe recovery: removed temporary skill performer entities=" + removed);
        return Math.max(1, removed);
    }

    static int recoveryClearLock(CommandSourceStack source, ServerPlayer player, UUID uuid, boolean confirm) {
        CompanionOperationLockService.ActiveOperation lock = CompanionOperationLockService.get(uuid);
        if (lock == null) {
            reply(source, "FindMe recovery: no active operation lock for " + uuid);
            return 0;
        }
        reply(source, "FindMe recovery " + (confirm ? "confirm" : "dry-run") + ": lock " + uuid
                + " operation=" + lock.operation() + " source=" + lock.source() + " player=" + lock.playerUuid());
        if (!player.getUUID().equals(lock.playerUuid())) {
            reply(source, "FindMe recovery refused: operation lock belongs to another player. Use the matching player target.");
            return 0;
        }
        if (!confirm) {
            return 1;
        }
        if (!createForcedBackup(player, "before_clear_operation_lock")) {
            reply(source, "FindMe recovery refused: could not create a safety backup first.");
            return 0;
        }
        boolean cleared = CompanionOperationLockService.clear(player, uuid, "admin_recovery:" + source.getTextName());
        reply(source, cleared ? "FindMe recovery: operation lock cleared." : "FindMe recovery: operation lock already gone.");
        return cleared ? 1 : 0;
    }

    static int recoveryRecoverStored(CommandSourceStack source, ServerPlayer player, UUID uuid, StoredRecoveryMode mode, boolean confirm) {
        PlayerCompanionData data = CompanionDataService.data(player);
        RecoveryCheck check = checkStoredRecovery(player, data, uuid, mode);
        for (String line : check.lines()) {
            reply(source, line);
        }
        if (!check.ok()) {
            return 0;
        }
        if (!confirm || mode == StoredRecoveryMode.DRY_RUN) {
            reply(source, "FindMe recovery dry-run: no data was changed.");
            return 1;
        }
        if (!createForcedBackup(player, data, "before_recover_stored")) {
            reply(source, "FindMe recovery refused: could not create a meaningful backup first.");
            return 0;
        }
        if (mode == StoredRecoveryMode.TO_STORAGE) {
            if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.RECOVER, "recover_stored:to_storage")) {
                reply(source, "FindMe recovery refused: operation lock is active for " + uuid);
                return 0;
            }
            try {
                data.kindOf(uuid).ifPresent(kind -> data.clearDeployed(kind, uuid));
                data.clearDeployedVehicle(uuid);
                CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.RECOVER);
                CompanionDataService.save(player, data);
                syncAll(player);
                FindMeDebugLogger.lifecycle("RECOVER_TO_STORAGE", player, uuid, null,
                        "UNKNOWN", "STORED", "admin_recovery:" + source.getTextName(), true, true);
                reply(source, "FindMe recovery: kept snapshot in storage and cleared deployed state for " + uuid);
                return 1;
            } finally {
                CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.RECOVER, "recover_stored:to_storage");
            }
        }
        BlockPos restorePos = recoveryRestorePos(player, data, uuid);
        Optional<Entity> restored = CompanionLifecycleFacade.restoreStored(player.serverLevel(), player, data, uuid,
                restorePos, player.getYRot(), player.getXRot(), "recovery:recover_stored");
        if (restored.isEmpty()) {
            reply(source, "FindMe recovery failed: stored snapshot was kept.");
            return 0;
        }
        Entity entity = restored.get();
        data.kindOf(uuid).ifPresent(kind -> data.setDeployed(kind, uuid));
        if (data.containsVehicle(uuid)) {
            data.setDeployedVehicle(uuid);
        }
        data.setLastKnownPosition(uuid, SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
        CompanionDataService.save(player, data);
        syncAll(player);
        FindMeDebugLogger.lifecycle("RECOVER_TO_WORLD", player, uuid, entity,
                "STORED", "ACTIVE", "admin_recovery:" + source.getTextName(), true, true);
        reply(source, "FindMe recovery: restored " + uuid + " to world at " + restorePos.toShortString());
        return 1;
    }

    static int recoveryDropStaleStored(CommandSourceStack source, ServerPlayer player, UUID uuid, boolean confirm) {
        PlayerCompanionData data = CompanionDataService.data(player);
        RecoveryCheck check = checkDropStaleStored(player, data, uuid);
        for (String line : check.lines()) {
            reply(source, line);
        }
        if (!check.ok()) {
            return 0;
        }
        if (!confirm) {
            reply(source, "FindMe recovery dry-run: no data was changed.");
            return 1;
        }
        if (!createForcedBackup(player, data, "before_drop_stale_stored")) {
            reply(source, "FindMe recovery refused: could not create a meaningful backup first.");
            return 0;
        }
        Entity loaded = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (loaded == null || loaded.isRemoved()) {
            reply(source, "FindMe recovery refused: loaded world entity disappeared before confirmation.");
            return 0;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.RECOVER);
        data.removeStoredEntity(uuid);
        data.setLastKnownPosition(uuid, SavedPosition.of(loaded.level(), loaded.getX(), loaded.getY(), loaded.getZ(), loaded.getYRot(), loaded.getXRot()));
        CompanionDataService.save(player, data);
        syncAll(player);
        FindMeDebugLogger.lifecycle("DROP_STALE_STORED", player, uuid, loaded,
                "STORED_AND_ACTIVE", "ACTIVE", "admin_recovery:" + source.getTextName(), true, true);
        reply(source, "FindMe recovery: dropped stale stored snapshot and kept loaded world entity for " + uuid);
        return 1;
    }

    public static void tickPlayerSafety(ServerPlayer player) {
        tickAutomaticBackup(player);
    }

    public static void ensureLoginSafetyBackup(ServerPlayer player) {
        PlayerCompanionData data = CompanionDataService.data(player);
        scheduleNextAutoBackupFromData(player, data);
        if (data.safetySchemaVersion() >= PlayerCompanionData.currentSafetySchemaVersion()) {
            return;
        }
        if (!hasBackupWorthyState(data)) {
            data.markSafetySchemaCurrent();
            CompanionDataService.save(player, data);
            return;
        }
        int previousVersion = data.safetySchemaVersion();
        if (!createForcedBackup(player, data, "before_1_3_upgrade_login")) {
            com.kuzhi.findme.FindMeMod.LOGGER.warn("FindMe 1.3 login safety backup failed: player={} uuid={} loadedSafetySchema={}",
                    player.getGameProfile().getName(), player.getUUID(), previousVersion);
            FindMeDebugLogger.lifecycle("LOGIN_UPGRADE_BACKUP", player, null, null,
                    "SCHEMA_" + previousVersion, "SCHEMA_" + PlayerCompanionData.currentSafetySchemaVersion(),
                    "backup_failed", false, false);
            return;
        }
        data.markSafetySchemaCurrent();
        CompanionDataService.save(player, data);
        FindMeDebugLogger.lifecycle("LOGIN_UPGRADE_BACKUP", player, null, null,
                "SCHEMA_" + previousVersion, "SCHEMA_" + PlayerCompanionData.currentSafetySchemaVersion(),
                "before_1_3_upgrade_login", true, false);
    }

    public static void handleWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            trySleepRevive(serverPlayer);
        }
    }

    public static void trySleepRevive(ServerPlayer player) {
        if (!FindMeModuleService.enabled(FindMeModule.SLEEP_REVIVAL)
                || player.getRandom().nextDouble() > Config.sleepReviveChance) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        long cooldown = Math.max(0, Config.sleepReviveCooldownMinutes) * 1200L;
        Long last = LAST_SLEEP_REVIVE.get(player.getUUID());
        if (last != null && cooldown > 0L && now - last < cooldown) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        List<UUID> dead = data.deadList();
        if (dead.isEmpty()) {
            return;
        }
        List<UUID> eligible = dead.stream().filter(uuid -> data.storedEntity(uuid).isPresent()).toList();
        if (eligible.isEmpty()) {
            return;
        }
        UUID uuid = eligible.get(player.getRandom().nextInt(eligible.size()));
        Optional<CompoundTag> maybeTag = data.storedEntity(uuid);
        if (maybeTag.isEmpty()) {
            return;
        }
        if (!createForcedBackup(player, data, "before_sleep_revive")) {
            FindMeDebugLogger.lifecycle("SLEEP_REVIVE_REJECTED", player, uuid, null,
                    "DEAD", "DEAD", "backup_failed", true, false);
            return;
        }
        CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
        CompoundTag tag = maybeTag.get();
        String name = data.displayName(uuid).orElseGet(() -> CompanionEntitySnapshots.storedEntityName(tag, uuid).replace(" (Dead)", ""));
        tag.remove("CompanionRescueDead");
        tag.putString("CompanionRescueName", name.replace(" (Dead)", ""));
        float maxHealth = Math.max(1.0f, tag.getFloat("CompanionRescueMaxHealth"));
        tag.putFloat("CompanionRescueHealth", maxHealth);
        tag.putFloat("Health", maxHealth);
        data.addVaultSnapshot(uuid, kind, name, maybeTag.get(), now, "revive", Config.DEFAULT_VAULT_CAPACITY);
        data.storeEntity(uuid, tag);
        data.setDisplayName(uuid, name.replace(" (Dead)", ""));
        data.add(kind, uuid);
        data.setActiveUuid(kind, uuid);
        LAST_SLEEP_REVIVE.put(player.getUUID(), now);
        boolean spawned = false;
        boolean homeResident = CompanionHomeResidentService.hasValidAssignedHouse(player, data, uuid);
        if (homeResident) {
            data.clearDeployed(kind, uuid);
            data.setLifecycleState(uuid, CompanionLifecycleState.HOME_STORED);
            data.homeHouseId(uuid).ifPresent(houseId -> FindMeWorldSavedData.get(player.server).addResident(houseId, uuid));
            if (Config.sleepReviveSpawnEntity
                    && CompanionHomeResidentService.isAssignedHouseVisible(player, data, uuid)) {
                spawned = CompanionHomeResidentService.restoreResidentNow(player, data, kind, uuid);
            }
        } else if (Config.sleepReviveSpawnEntity) {
            data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
            CompanionMoveType moveType = CompanionEntityClassifier.moveType(CompanionEntitySnapshots.storedEntityType(tag), kind);
            Optional<BlockPos> reviveSpot = findReviveSpot(player, tag, kind, moveType);
            if (reviveSpot.isPresent()) {
                Optional<Entity> restored = CompanionLifecycleFacade.restoreStored(player, data, uuid, reviveSpot.get(), player.getYRot(), player.getXRot(), "safety:sleep_revive");
                if (restored.isPresent() && restored.get() instanceof LivingEntity living) {
                    data.setDeployed(kind, uuid);
                    data.setLastKnownPosition(uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
                    spawned = true;
                }
            }
        } else {
            data.setLifecycleState(uuid, CompanionLifecycleState.STORED);
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionSyncService.syncDeadToClient(player);
        CompanionSummonLineService.showSleepRevived(player, name.replace(" (Dead)", ""), spawned || homeResident);
    }

    private static void tickAutomaticBackup(ServerPlayer player) {
        if (!Config.enableAutoBackups) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long scheduled = NEXT_AUTO_BACKUP_CHECK.get(player.getUUID());
        if (scheduled == null) {
            NEXT_AUTO_BACKUP_CHECK.put(player.getUUID(), now + initialAutoBackupDelay(player.getUUID()));
            return;
        }
        long nextCheck = scheduled;
        if (now < nextCheck) return;
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!hasBackupWorthyState(data)) {
            NEXT_AUTO_BACKUP_CHECK.put(player.getUUID(), now + 1200L);
            return;
        }
        long intervalTicks = Math.max(1, Config.backupIntervalMinutes) * 1200L;
        if (data.lastBackupAt() > 0L && now - data.lastBackupAt() < intervalTicks) {
            NEXT_AUTO_BACKUP_CHECK.put(player.getUUID(), data.lastBackupAt() + intervalTicks);
            return;
        }
        long startedAt = FindMePerformanceMonitor.start();
        try {
            data.createBackup(now, "auto", Config.DEFAULT_BACKUP_CAPACITY, false, backupPreviewContents(player.getServer(), data));
            CompanionDataService.save(player, data);
            PlayerCompanionDataMirror.rememberBackup(player, now, "auto", Config.DEFAULT_BACKUP_CAPACITY);
            NEXT_AUTO_BACKUP_CHECK.put(player.getUUID(), now + intervalTicks);
        } finally {
            FindMePerformanceMonitor.recordAutoBackup(startedAt);
        }
    }

    /** Stores display-only history data without changing the restorable backup state. */
    private static CompoundTag backupPreviewContents(MinecraftServer server, PlayerCompanionData data) {
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        Set<UUID> captured = new HashSet<>();
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                addBackupPreviewEntry(server, data, kind, false, uuid, captured, entries);
            }
        }
        for (UUID uuid : data.vehicleList()) {
            addBackupPreviewEntry(server, data, CompanionKind.MOUNT, true, uuid, captured, entries);
        }
        root.put("entries", entries);
        return root;
    }

    private static void addBackupPreviewEntry(MinecraftServer server, PlayerCompanionData data, CompanionKind kind,
                                               boolean vehicle, UUID uuid, Set<UUID> captured, ListTag entries) {
        if (uuid == null || !captured.add(uuid)) return;
        Entity entity = CompanionEntityLookup.findEntity(server, uuid).orElse(null);
        CompoundTag stored = data.rawStoredEntityForDiagnostics(uuid).orElse(null);
        String type = entity == null ? stored == null ? "" : CompanionEntitySnapshots.storedEntityType(stored)
                : EntityType.getKey(entity.getType()).toString();
        String name = data.displayName(uuid).orElseGet(() -> entity == null
                ? stored == null ? uuid.toString().substring(0, 8) : CompanionEntitySnapshots.storedEntityName(stored, uuid)
                : entity.getDisplayName().getString());
        CompoundTag preview = entity == null
                ? stored == null ? null : CompanionEntitySnapshots.previewStoredEntityTag(stored)
                : CompanionEntitySnapshots.previewEntityTag(entity, type);
        CompoundTag entry = new CompoundTag();
        entry.putString("kind", kind.name());
        entry.putBoolean("vehicle", vehicle);
        entry.putUUID("uuid", uuid);
        entry.putString("name", name);
        entry.putString("entityType", type);
        entry.putBoolean("alive", entity == null ? stored != null && !stored.getBoolean("CompanionRescueDead") : entity.isAlive());
        if (preview != null && !preview.isEmpty()) entry.put("preview", preview);
        entries.add(entry);
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        NEXT_AUTO_BACKUP_CHECK.remove(uuid);
        LAST_SLEEP_REVIVE.remove(uuid);
    }

    public static void resetRuntimeState() {
        NEXT_AUTO_BACKUP_CHECK.clear();
        LAST_SLEEP_REVIVE.clear();
    }

    private static void scheduleNextAutoBackupFromData(ServerPlayer player, PlayerCompanionData data) {
        long now = player.serverLevel().getGameTime();
        long intervalTicks = Math.max(1, Config.backupIntervalMinutes) * 1200L;
        long lastBackup = data.lastBackupAt();
        long staggeredStart = now + initialAutoBackupDelay(player.getUUID());
        NEXT_AUTO_BACKUP_CHECK.put(player.getUUID(), lastBackup > 0L
                ? Math.max(staggeredStart, lastBackup + intervalTicks) : staggeredStart);
    }

    static int initialAutoBackupDelay(UUID playerUuid) {
        int hash = playerUuid == null ? 0 : playerUuid.hashCode();
        return 20 + Math.floorMod(hash, 181);
    }

    private static void scheduleNextAutoBackup(ServerPlayer player, long backupAt) {
        long intervalTicks = Math.max(1, Config.backupIntervalMinutes) * 1200L;
        NEXT_AUTO_BACKUP_CHECK.put(player.getUUID(), backupAt + intervalTicks);
    }

    private static Optional<BlockPos> findReviveSpot(ServerPlayer player, CompoundTag tag, CompanionKind kind, CompanionMoveType moveType) {
        double width = Math.max((double)tag.getFloat("CompanionPreviewWidth"), 1.0);
        double height = Math.max((double)tag.getFloat("CompanionPreviewHeight"), 1.0);
        boolean large = width >= 3.0 || height >= 3.0;
        if (!large) {
            return Optional.of(CompanionSpawnPlacementService.findSummonSpot(player, kind, moveType));
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        for (int radius = 16; radius <= 32; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos candidate = CompanionSpawnPlacementService.walkSurfacePos(level, origin.offset(dx, 0, dz), player.getY());
                    if (CompanionSpawnPlacementService.hasOpenBox(level, candidate, width, height)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static RecoveryCheck checkStoredRecovery(ServerPlayer player, PlayerCompanionData data, UUID uuid, StoredRecoveryMode mode) {
        RecoveryCheck check = new RecoveryCheck();
        check.add("FindMe recovery check: uuid=" + uuid + " mode=" + mode);
        Optional<CompoundTag> maybeTag = data.storedEntity(uuid);
        if (maybeTag.isEmpty()) {
            check.reject("no stored snapshot exists");
            return check;
        }
        CompoundTag tag = maybeTag.get();
        if (tag.isEmpty() || !tag.contains("id") || tag.getString("id").isBlank()) {
            check.reject("stored snapshot is missing entity id");
        }
        if (tag.hasUUID("UUID") && !uuid.equals(tag.getUUID("UUID"))) {
            check.reject("stored snapshot UUID mismatch: " + tag.getUUID("UUID"));
        }
        boolean known = data.kindOf(uuid).isPresent() || data.containsVehicle(uuid);
        if (!known) {
            check.reject("uuid is not in this player's living or vehicle records");
        }
        if (data.deadList().contains(uuid)) {
            check.reject("uuid is in death records; use death recovery instead of stored recovery");
        }
        CompanionOperationLockService.ActiveOperation lock = CompanionOperationLockService.get(uuid);
        if (lock != null) {
            check.reject("operation lock is active: " + lock.operation() + " source=" + lock.source());
        }
        Optional<Entity> loaded = CompanionEntityLookup.findEntity(player.getServer(), uuid);
        if (loaded.isPresent() && !loaded.get().isRemoved()) {
            check.reject("loaded world entity already exists; refusing to duplicate");
        }
        if (CompanionStorageService.isStoragePending(uuid)) {
            check.reject("storage animation is still pending");
        }
        if (check.ok()) {
            String type = tag.getString("id");
            String target = data.kindOf(uuid).map(kind -> kind.name().toLowerCase(Locale.ROOT))
                    .orElse(data.containsVehicle(uuid) ? "vehicle" : "unknown");
            check.add("FindMe recovery check OK: type=" + type + " record=" + target);
        }
        return check;
    }

    private static RecoveryCheck checkDropStaleStored(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        RecoveryCheck check = new RecoveryCheck();
        check.add("FindMe stale stored check: uuid=" + uuid);
        if (data.storedEntity(uuid).isEmpty()) {
            check.reject("no stored snapshot exists");
            return check;
        }
        boolean known = data.kindOf(uuid).isPresent() || data.containsVehicle(uuid);
        if (!known) {
            check.reject("uuid is not in this player's living or vehicle records");
        }
        if (data.deadList().contains(uuid)) {
            check.reject("uuid is in death records; use death recovery instead");
        }
        CompanionOperationLockService.ActiveOperation lock = CompanionOperationLockService.get(uuid);
        if (lock != null) {
            check.reject("operation lock is active: " + lock.operation() + " source=" + lock.source());
        }
        if (CompanionStorageService.isStoragePending(uuid)) {
            check.reject("storage animation is still pending");
        }
        Entity loaded = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        if (loaded == null || loaded.isRemoved()) {
            check.reject("no loaded world entity exists; refusing to drop the only stored snapshot");
        }
        if (check.ok()) {
            check.add("FindMe stale stored check OK: loaded entity exists and stored snapshot can be dropped.");
        }
        return check;
    }

    private static BlockPos recoveryRestorePos(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        Optional<CompoundTag> maybeTag = data.storedEntity(uuid);
        Optional<CompanionKind> maybeKind = data.kindOf(uuid);
        if (maybeTag.isPresent() && maybeKind.isPresent()) {
            CompanionKind kind = maybeKind.get();
            CompanionMoveType moveType = CompanionEntityClassifier.moveType(CompanionEntitySnapshots.storedEntityType(maybeTag.get()), kind);
            return CompanionSpawnPlacementService.findSummonSpot(player, kind, moveType);
        }
        return player.blockPosition().relative(player.getDirection(), 2);
    }

    private static int countTemporaryPerformers(ServerPlayer player) {
        if (player.getServer() == null) {
            return 0;
        }
        int count = 0;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (FindMeApi.isTemporaryActionPerformer(entity)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int removeTemporaryPerformers(ServerPlayer player) {
        if (player.getServer() == null) {
            return 0;
        }
        int removed = 0;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!FindMeApi.isTemporaryActionPerformer(entity)) {
                    continue;
                }
                entity.discard();
                removed++;
            }
        }
        return removed;
    }

    public static boolean createForcedBackup(ServerPlayer player, String reason) {
        PlayerCompanionData data = CompanionDataService.data(player);
        return createForcedBackup(player, data, reason);
    }

    public static boolean createForcedBackup(ServerPlayer player, PlayerCompanionData data, String reason) {
        if (!hasBackupWorthyState(data)) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        data.createBackup(now, "safety",
                Math.max(1, Config.DEFAULT_BACKUP_CAPACITY), false, backupPreviewContents(player.getServer(), data));
        CompanionDataService.save(player, data);
        PlayerCompanionDataMirror.rememberBackup(player, now, reason, Math.max(1, Config.DEFAULT_BACKUP_CAPACITY));
        scheduleNextAutoBackup(player, now);
        FindMeDebugLogger.info("recovery", "forced_backup_created player={} reason={} tick={}",
                player.getUUID(), reason, now);
        return true;
    }

    public static boolean createForcedBackup(MinecraftServer server, UUID playerUuid,
                                             PlayerCompanionData data, String reason) {
        if (server == null || playerUuid == null || !hasBackupWorthyState(data)) {
            return false;
        }
        long now = server.overworld().getGameTime();
        data.createBackup(now, "safety",
                Math.max(1, Config.DEFAULT_BACKUP_CAPACITY), false,
                backupPreviewContents(server, data));
        CompanionDataService.save(server, playerUuid, data);
        FindMeDebugLogger.info("recovery", "forced_backup_created player={} reason={} tick={} offline=true",
                playerUuid, reason, now);
        return true;
    }

    private static void syncAll(ServerPlayer player) {
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        VehicleManager.syncToClient(player);
        CompanionSyncService.syncDeadToClient(player);
        CompanionTeamService.syncToClient(player);
    }

    private static void reply(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static boolean hasBackupWorthyState(PlayerCompanionData data) {
        return data.hasMeaningfulBackupState();
    }

    enum StoredRecoveryMode {
        DRY_RUN,
        TO_STORAGE,
        TO_WORLD
    }

    private static final class RecoveryCheck {
        private final java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        private boolean ok = true;

        void add(String line) {
            this.lines.add(line);
        }

        void reject(String reason) {
            this.ok = false;
            this.lines.add("FindMe recovery refused: " + reason);
        }

        boolean ok() {
            return this.ok;
        }

        List<String> lines() {
            return List.copyOf(this.lines);
        }
    }
}

