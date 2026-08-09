package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.server.profile.CompanionBindingProfileService;
import com.kuzhi.findme.server.safety.CompanionDeathService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.server.ui.CompanionTeamService;
import com.kuzhi.findme.server.ui.WarehouseEntityService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.PackEntityCategoryOverride;
import com.kuzhi.findme.common.SavedPosition;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
public final class CompanionRegistrationService {
    private static final int RIDDEN_PROMOTION_RECHECK_TICKS = 100;
    private static final Map<UUID, RiddenPromotionCheck> RIDDEN_PROMOTION_CHECKS = new HashMap<>();

    private CompanionRegistrationService() {
    }

    public static void promoteRiddenRegisteredCompanionToMount(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity living) || living == player) {
            RIDDEN_PROMOTION_CHECKS.remove(player.getUUID());
            return;
        }
        com.kuzhi.findme.server.data.CompanionRuntimeIndex runtimeIndex =
                CompanionDataService.runtimeIndex(player);
        if (!runtimeIndex.autoPromoteRiddenCompanions()) {
            RIDDEN_PROMOTION_CHECKS.remove(player.getUUID());
            return;
        }
        long now = player.serverLevel().getGameTime();
        RiddenPromotionCheck previous = RIDDEN_PROMOTION_CHECKS.get(player.getUUID());
        if (previous != null && previous.vehicleUuid().equals(living.getUUID())
                && (previous.resolved() || now < previous.nextCheckTick())) {
            return;
        }
        if (runtimeIndex.contains(CompanionKind.MOUNT, living.getUUID())) {
            RIDDEN_PROMOTION_CHECKS.put(player.getUUID(), new RiddenPromotionCheck(
                    living.getUUID(), Long.MAX_VALUE, true));
            return;
        }
        if (!runtimeIndex.contains(CompanionKind.COMPANION, living.getUUID())) {
            RIDDEN_PROMOTION_CHECKS.put(player.getUUID(), new RiddenPromotionCheck(
                    living.getUUID(), now + RIDDEN_PROMOTION_RECHECK_TICKS, false));
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        boolean alreadyMount = data.contains(CompanionKind.MOUNT, living.getUUID());
        if (!data.contains(CompanionKind.COMPANION, living.getUUID()) || alreadyMount) {
            RIDDEN_PROMOTION_CHECKS.put(player.getUUID(), new RiddenPromotionCheck(
                    living.getUUID(), now + RIDDEN_PROMOTION_RECHECK_TICKS, alreadyMount));
            return;
        }
        promoteRiddenCompanionToMount(player, data, living);
        RIDDEN_PROMOTION_CHECKS.put(player.getUUID(), new RiddenPromotionCheck(
                living.getUUID(), Long.MAX_VALUE, true));
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (player != null) {
            RIDDEN_PROMOTION_CHECKS.remove(player.getUUID());
        }
    }

    public static void sweepDeadEntriesAndSync(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        if (!moveDeadEntriesOutOfLiveList(player, data, kind)) {
            return;
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionSyncService.syncDeadToClient(player);
    }

    static Optional<CompanionKind> manualBindingKind(ServerPlayer player, LivingEntity living) {
        return CompanionBindingProfileService.manualBindingKind(player, living);
    }

    static boolean registerContracted(ServerPlayer player, LivingEntity living, CompanionKind kind) {
        return registerContracted(player, living, kind, true);
    }

    static boolean registerContractedForStorage(ServerPlayer player, LivingEntity living, CompanionKind kind) {
        return registerContracted(player, living, kind, false);
    }

    private static boolean registerContracted(ServerPlayer player, LivingEntity living, CompanionKind kind, boolean rememberDeployed) {
        if (living == player || !living.isAlive()) {
            return false;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (kind == CompanionKind.MOUNT && data.contains(CompanionKind.COMPANION, living.getUUID()) && !data.contains(CompanionKind.MOUNT, living.getUUID())) {
            promoteRiddenCompanionToMount(player, data, living);
            return true;
        }
        if (data.contains(kind, living.getUUID())) {
            if (rememberDeployed) {
                CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
            } else {
                data.clearDeployed(kind, living.getUUID());
            }
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            CompanionTeamService.syncToClient(player);
            return true;
        }
        if (!data.add(kind, living.getUUID())) {
            return false;
        }
        WarehouseEntityService.notifyAutoTeam(player, data, living.getUUID(), living.getDisplayName().getString());
        SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
        data.setOrigin(living.getUUID(), position);
        data.setLastKnownPosition(living.getUUID(), position);
        if (rememberDeployed) {
            CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, living.getUUID());
            data.setLifecycleState(living.getUUID(), CompanionLifecycleState.DEPLOYED);
        } else {
            data.clearDeployed(kind, living.getUUID());
            data.setLifecycleState(living.getUUID(), CompanionLifecycleState.STORED);
        }
        if (kind == CompanionKind.MOUNT) {
            data.markMountEligible(living.getUUID());
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionTeamService.syncToClient(player);
        CompanionSummonLineService.showRegistered(player, living);
        return true;
    }

    private static void promoteRiddenCompanionToMount(ServerPlayer player, PlayerCompanionData data, LivingEntity living) {
        UUID uuid = living.getUUID();
        data.add(CompanionKind.MOUNT, uuid);
        SavedPosition position = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot());
        data.setOrigin(uuid, position);
        data.setLastKnownPosition(uuid, position);
        CompanionDeploymentService.rememberDeployedWithinLimit(player, data, CompanionKind.MOUNT, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        data.markMountEligible(uuid);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionTeamService.syncToClient(player);
    }

    private static boolean moveDeadEntriesOutOfLiveList(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
        boolean changed = false;
        for (UUID uuid : List.copyOf(data.list(kind))) {
            Entity entity = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
            if (entity != null && entity.isAlive()) {
                continue;
            }
            if (kind == CompanionKind.COMPANION && CompanionShoulderService.shoulderEntityTag(player, uuid).isPresent()) {
                continue;
            }
            Optional<CompoundTag> stored = data.storedEntity(uuid);
            boolean deadStored = stored.isPresent() && stored.get().contains("CompanionRescueDead") && stored.get().getBoolean("CompanionRescueDead");
            if (entity == null && !deadStored) {
                continue;
            }
            if (entity instanceof LivingEntity living) {
                CompanionDeathService.markRegisteredDead(player.getServer(), living);
                changed = true;
            } else if (stored.isPresent()) {
                boolean backupCreated = CompanionSafetyService.createForcedBackup(player, data, "before_mark_dead_stored");
                if (!backupCreated) {
                    FindMeDebugLogger.lifecycle("STORED_DEAD_BACKUP_FAILED_CONTINUE", player, uuid, null,
                            "STORED", "DEAD", "registration:sweep_dead_entries", true, false);
                }
                CompoundTag tag = stored.get();
                if (!tag.getString("CompanionRescueName").endsWith(" (Dead)")) {
                    tag.putString("CompanionRescueName", tag.getString("CompanionRescueName"));
                }
                data.storeEntity(uuid, tag);
                changed |= data.markDead(uuid, kind);
                FindMeDebugLogger.lifecycle("MARK_STORED_DEAD", player, uuid, null,
                        "STORED", "DEAD", "registration:sweep_dead_entries", true, false);
            }
        }
        return changed;
    }

    private record RiddenPromotionCheck(UUID vehicleUuid, long nextCheckTick, boolean resolved) {
    }
}

