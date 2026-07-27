package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.lifecycle.CompanionCollectionService;
import com.kuzhi.findme.server.lifecycle.CompanionEntityTransferService;
import com.kuzhi.findme.server.lifecycle.CompanionPlayerLifecycleService;
import com.kuzhi.findme.server.lifecycle.CompanionShoulderService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.ui.CompanionTeamService;
import com.kuzhi.findme.server.vehicle.VehicleSeatService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import com.kuzhi.findme.server.ui.CompanionSyncService;
public final class CompanionDeathService {
    private CompanionDeathService() {
    }

    public static boolean markRegisteredDead(MinecraftServer server, LivingEntity living) {
        return markRegisteredDead(server, living, false);
    }

    public static boolean markRegisteredDead(MinecraftServer server, LivingEntity living, boolean confirmedDeath) {
        if (server == null || living.level().isClientSide()) {
            return false;
        }
        if (!confirmedDeath && living.getHealth() > 0.0f) {
            return false;
        }
        UUID uuid = living.getUUID();
        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionDataService.data(player);
            Optional<CompanionKind> maybeKind = data.kindOf(uuid);
            if (maybeKind.isEmpty() || data.deadList().contains(uuid)) {
                continue;
            }
            CompanionKind kind = maybeKind.get();
            if (kind == CompanionKind.COMPANION && CompanionShoulderService.shoulderEntityTag(player, uuid).isPresent()) {
                continue;
            }
            boolean backupCreated = CompanionSafetyService.createForcedBackup(player, data, "before_mark_dead");
            if (!backupCreated) {
                FindMeDebugLogger.lifecycle("DEATH_BACKUP_FAILED_CONTINUE", player, uuid, living,
                        "ACTIVE", "DEAD", "death:mark_registered", data.storedEntity(uuid).isPresent(), true);
            }
            CompanionHomeResidentService.clearResident(living);
            String entityType = EntityType.getKey(living.getType()).toString();
            long deathTime = living.level().getGameTime();
            CompoundTag tag = CompanionStorageService.createDeathSnapshot(living, deathTime)
                    .orElseGet(() -> data.storedEntity(uuid)
                            .map(CompanionStorageService::sanitizedStoredTag)
                            .orElseGet(() -> CompanionEntitySnapshots.previewEntityTag(living, entityType)));
            String customName = data.displayName(uuid).orElse(living.getDisplayName().getString());
            int previousTeamIndex = data.teamIndexOf(uuid);
            float maxHealth = living.getMaxHealth();
            float previewHealth = Math.max(1.0f, maxHealth);
            tag.putString("id", entityType);
            tag.putString("CompanionRescueName", living.getDisplayName().getString());
            tag.putString("CompanionRescueType", entityType);
            tag.putFloat("CompanionRescueHealth", previewHealth);
            tag.putFloat("CompanionRescueMaxHealth", Math.max(1.0f, maxHealth));
            tag.putInt("CompanionRescueArmor", living.getArmorValue());
            tag.putBoolean("CompanionRescueDead", true);
            tag.putFloat("Health", previewHealth);
            data.storeEntity(uuid, tag);
            data.setLastKnownPosition(uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
            String cause = living.getLastDamageSource() == null ? "unknown" : living.getLastDamageSource().getMsgId();
            boolean sleepRecoverable = tag.contains("id") && !tag.getString("id").isBlank();
            data.putDeadRecord(new DeadCompanionRecord(UUID.randomUUID(), uuid, customName, entityType, kind, previousTeamIndex,
                    deathTime, living.level().getDayTime() / 24000L, living.level().dimension().location().toString(),
                    living.getX(), living.getY(), living.getZ(), cause, sleepRecoverable, ""));
            data.markDead(uuid, kind);
            data.setLifecycleState(uuid, CompanionLifecycleState.DEAD);
            FindMeDebugLogger.lifecycle("MARK_DEAD", player, uuid, living,
                    "ACTIVE", "DEAD", "death:mark_registered", true, true);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            CompanionSyncService.syncDeadToClient(player);
            CompanionTeamService.syncToClient(player);
            changed = true;
        }
        return changed;
    }

    public static void handleEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (FindMeApi.isTemporaryActionPerformer(entity) || VehicleSeatService.isSeatAnchor(entity)) {
            return;
        }
        if (entity instanceof ServerPlayer) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            if (CompanionEntityTransferService.consumeIntentionalTransferRemoval(event.getLevel().getServer(), living.getUUID())) {
                return;
            }
            if (CompanionStorageService.consumeIntentionalStorageRemoval(event.getLevel().getServer(), living.getUUID())) {
                return;
            }
            if (markRegisteredDead(event.getLevel().getServer(), living)) {
                return;
            }
        }
        CompanionCollectionService.snapshotRegisteredBeforeUnload(event.getLevel().getServer(), entity);
    }

    public static void handleLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (VehicleSeatService.isSeatAnchor(living)) {
            event.setCanceled(true);
            living.discard();
            return;
        }
        if (FindMeApi.isTemporaryActionPerformer(living)) {
            event.setCanceled(true);
            living.discard();
            return;
        }
        if (living instanceof ServerPlayer player) {
            CompanionPlayerLifecycleService.handleDeath(player);
            return;
        }
        if (CompanionStorageService.isStoragePending(living)) {
            event.setCanceled(true);
            return;
        }
        markRegisteredDead(living.getServer(), living, true);
    }

    public static void handleLivingDrops(LivingDropsEvent event) {
        LivingEntity living = event.getEntity();
        if (FindMeApi.isTemporaryActionPerformer(living) || VehicleSeatService.isSeatAnchor(living)) {
            event.getDrops().clear();
            return;
        }
        if (Config.preventBoundCreatureDeathDrops && shouldSuppressDeathDrops(living)) {
            event.getDrops().clear();
        }
    }

    static boolean shouldSuppressDeathDrops(LivingEntity living) {
        if (living == null || living.level().isClientSide()) {
            return false;
        }
        MinecraftServer server = living.getServer();
        if (server == null) {
            return false;
        }
        UUID uuid = living.getUUID();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionDataService.data(player);
            if (data.kindOf(uuid).isPresent()) {
                return true;
            }
        }
        return false;
    }
}


