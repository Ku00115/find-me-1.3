package com.kuzhi.findme.server.home;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.HomeResidentIndex;

import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.compat.CompanionFixedPostService;

import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.CompanionGuardPostService;
import com.kuzhi.findme.server.lifecycle.CompanionPlacementFinder;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.ModBlocks;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.kuzhi.findme.common.CompanionLifecycleState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class CompanionHomeResidentService {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int MAX_RECORDS_PER_CHECK = 10;
    private static final int MAX_RESTORES_PER_TICK = 1;
    private static final int MAX_RESIDENT_SEARCH_RADIUS = 24;
    private static final double HOUSE_INTERACTION_CLEARANCE = 2.5;
    private static final Set<UUID> RESIDENTS = new HashSet<>();
    private static final Map<UUID, LivingEntity> RESIDENT_ENTITIES = new HashMap<>();
    private static final Map<UUID, Integer> PLAYER_CHECK_CURSOR = new HashMap<>();
    private static final Map<UUID, RestoreRetry> RESTORE_RETRIES = new HashMap<>();
    private static final Map<TrackedChunkKey, Boolean> TRACKED_CHUNK_CACHE = new HashMap<>();
    private static long trackedChunkCacheTick = Long.MIN_VALUE;

    private CompanionHomeResidentService() {
    }

    public static void tickPlayer(ServerPlayer player) {
        if (Math.floorMod(player.serverLevel().getGameTime() + player.getUUID().getLeastSignificantBits(),
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        CompanionHomePerformanceTrace trace = new CompanionHomePerformanceTrace(player);
        long stageStartedAt = trace.start();
        List<HomeResidentIndex.Entry> entries = CompanionDataService.homeResidentIndex(player).entries();
        trace.record(CompanionHomePerformanceTrace.ROSTER, stageStartedAt);
        int total = entries.size();
        if (total == 0) {
            PLAYER_CHECK_CURSOR.remove(player.getUUID());
            trace.finish();
            return;
        }
        LazyPlayerData mutableData = new LazyPlayerData(player, trace);
        int cursor = Math.floorMod(PLAYER_CHECK_CURSOR.getOrDefault(player.getUUID(), 0), total);
        int records = Math.min(MAX_RECORDS_PER_CHECK, total);
        int restores = 0;
        boolean mountsChanged = false;
        boolean companionsChanged = false;
        for (int checked = 0; checked < records; checked++) {
            int index = (cursor + checked) % total;
            HomeResidentIndex.Entry entry = entries.get(index);
            CompanionKind kind = entry.kind();
            UUID uuid = entry.uuid();
            trace.companion(uuid);
            if (CompanionOperationLockService.get(uuid) != null) {
                continue;
            }
            boolean changed = false;
            stageStartedAt = trace.start();
            LivingEntity ridden = findRiddenLiving(player, uuid);
            if (ridden != null) {
                trace.record(CompanionHomePerformanceTrace.RESOLVE, stageStartedAt);
                if (isCoherentRiddenState(entry)) {
                    RESTORE_RETRIES.remove(uuid);
                    continue;
                }
                stageStartedAt = trace.start();
                changed = reconcileRiddenCompanion(mutableData.get(), kind, ridden);
                trace.record(CompanionHomePerformanceTrace.RECONCILE, stageStartedAt);
            } else {
                if (isNormalDeployedState(entry)) {
                    RESTORE_RETRIES.remove(uuid);
                    trace.record(CompanionHomePerformanceTrace.RESOLVE, stageStartedAt);
                    continue;
                }
                boolean resident = isResident(uuid);
                if (!entry.hasHomeAssignment() && !resident && !isHomeLifecycle(entry.lifecycleState())) {
                    RESTORE_RETRIES.remove(uuid);
                    trace.record(CompanionHomePerformanceTrace.RESOLVE, stageStartedAt);
                    continue;
                }
                HomeTarget homeTarget = resolveHomeTarget(player, entry);
                Entity liveEntity = null;
                if (resident || entry.lifecycleState() == CompanionLifecycleState.HOME_ACTIVE) {
                    liveEntity = residentEntity(uuid);
                    if (liveEntity == null) {
                        liveEntity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
                    }
                }
                trace.record(CompanionHomePerformanceTrace.RESOLVE, stageStartedAt);

                if (entry.lifecycleState() == CompanionLifecycleState.HOME_ACTIVE
                        && resident && liveEntity instanceof LivingEntity living && living.isAlive()
                        && homeTarget != null
                        && isHouseChunkTrackedByAnyPlayer(homeTarget.level, homeTarget.housePos)) {
                    CompanionMoveType residentMoveType = CompanionEntityClassifier.moveType(living, kind);
                    CompanionHomeBehaviorService.applyResidentBehavior(living, homeTarget.housePos,
                            residentMoveType, CompanionHomeBehaviorService.isAirborneResident(living)
                                    || residentMoveType == CompanionMoveType.FLY && !living.onGround());
                    RESTORE_RETRIES.remove(uuid);
                    continue;
                }
                stageStartedAt = trace.start();
                if (entry.lifecycleState() == CompanionLifecycleState.HOME_ACTIVE) {
                    changed |= reconcileResident(mutableData.get(), kind, uuid, liveEntity, homeTarget);
                }
                trace.record(CompanionHomePerformanceTrace.RECONCILE, stageStartedAt);
                stageStartedAt = trace.start();
                if (isResident(uuid)) {
                    changed |= collectResidentOutsideTrackedView(player, mutableData.get(), uuid, liveEntity, homeTarget);
                }
                trace.record(CompanionHomePerformanceTrace.COLLECT, stageStartedAt);
                stageStartedAt = trace.start();
                boolean restoreEligible = shouldRestoreResident(entry, homeTarget);
                boolean retryReady = restoreEligible && restoreRetryReady(uuid, player.serverLevel().getGameTime());
                trace.record(CompanionHomePerformanceTrace.ELIGIBILITY, stageStartedAt);
                if (restores < MAX_RESTORES_PER_TICK && retryReady) {
                    stageStartedAt = trace.start();
                    if (restoreResidentNearHouse(player, mutableData.get(), kind, uuid, homeTarget)) {
                        changed = true;
                        restores++;
                        RESTORE_RETRIES.remove(uuid);
                    } else {
                        rememberRestoreFailure(player, uuid);
                    }
                    trace.record(CompanionHomePerformanceTrace.RESTORE, stageStartedAt);
                } else if (!restoreEligible && shouldForgetRestoreRetry(entry)) {
                    RESTORE_RETRIES.remove(uuid);
                }
            }
            if (kind == CompanionKind.MOUNT) {
                mountsChanged |= changed;
            } else {
                companionsChanged |= changed;
            }
        }
        PLAYER_CHECK_CURSOR.put(player.getUUID(), (cursor + records) % total);
        if (mountsChanged || companionsChanged) {
            stageStartedAt = trace.start();
            CompanionDataService.save(player, mutableData.get());
            if (mountsChanged) {
                CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            }
            if (companionsChanged) {
                CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            }
            trace.record(CompanionHomePerformanceTrace.SAVE_SYNC, stageStartedAt);
        }
        trace.finish();
    }

    public static boolean isResident(UUID uuid) {
        return uuid != null && RESIDENTS.contains(uuid);
    }

    /**
     * Fast entity-local check used by AI goal hooks. It deliberately avoids
     * decoding player data or scanning the resident registry.
     */
    public static boolean isHomeResident(LivingEntity living) {
        return living != null && living.getPersistentData().getBoolean(CompanionHomeBehaviorService.HOME_RESIDENT_TAG);
    }

    static void markResident(UUID uuid) {
        if (uuid != null) {
            RESIDENTS.add(uuid);
        }
    }

    public static void clearResident(UUID uuid) {
        if (uuid != null) {
            RESIDENTS.remove(uuid);
            LivingEntity resident = RESIDENT_ENTITIES.remove(uuid);
            if (resident != null) {
                CompanionHomeBehaviorService.clearResidentBehavior(resident);
            } else {
                CompanionGuardPostService.releaseHome(uuid, "resident_reference_missing");
                CompanionFixedPostService.release(uuid, CompanionFixedPostService.Reason.HOME);
            }
            RESTORE_RETRIES.remove(uuid);
        }
    }

    public static void clearResident(LivingEntity living) {
        if (living != null) {
            clearResident(living.getUUID());
            CompanionHomeBehaviorService.clearResidentBehavior(living);
        }
    }

    public static void markResidentEntity(LivingEntity living) {
        if (living != null) {
            keepResidentPersistent(living);
            markResident(living.getUUID());
            RESIDENT_ENTITIES.put(living.getUUID(), living);
            CompanionHomeBehaviorService.applyResidentBehavior(living);
        }
    }

    public static void markResidentEntity(LivingEntity living, BlockPos center,
                                          CompanionMoveType moveType, boolean airborne) {
        if (living != null) {
            keepResidentPersistent(living);
            markResident(living.getUUID());
            RESIDENT_ENTITIES.put(living.getUUID(), living);
            CompanionHomeBehaviorService.applyResidentBehavior(living, center, moveType, airborne);
        }
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (player != null) {
            PLAYER_CHECK_CURSOR.remove(player.getUUID());
            RESTORE_RETRIES.entrySet().removeIf(entry -> player.getUUID().equals(entry.getValue().playerUuid));
            CompanionHomePerformanceTrace.forgetPlayer(player.getUUID());
        }
    }

    public static boolean isAssignedHouseVisible(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (player == null || data == null || uuid == null) {
            return false;
        }
        ServerLevel level = houseLevel(player, data, uuid).orElse(null);
        BlockPos housePos = houseBlock(player, data, uuid).orElse(null);
        return level != null && housePos != null && validLoadedHouse(level, housePos)
                && isHouseChunkTrackedByAnyPlayer(level, housePos);
    }

    public static boolean hasValidAssignedHouse(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (player == null || data == null || uuid == null) {
            return false;
        }
        ServerLevel level = houseLevel(player, data, uuid).orElse(null);
        BlockPos housePos = houseBlock(player, data, uuid).orElse(null);
        if (level == null || housePos == null) {
            return false;
        }
        if (!level.hasChunkAt(housePos) || !level.isLoaded(housePos)) {
            return data.homeHouseId(uuid)
                    .flatMap(id -> FindMeWorldSavedData.get(player.server).house(id))
                    .filter(record -> record.position().dimension().equals(level.dimension()))
                    .filter(record -> record.position().blockPos().equals(housePos))
                    .isPresent();
        }
        return validLoadedHouse(level, housePos);
    }

    public static boolean restoreResidentNow(ServerPlayer player, PlayerCompanionData data,
                                             CompanionKind kind, UUID uuid) {
        return restoreResidentNearHouse(player, data, kind, uuid, resolveHomeTarget(player, data, uuid));
    }

    public static boolean collectAllForPlayerSilently(ServerPlayer player, PlayerCompanionData data, String source) {
        if (player == null || data == null) {
            return false;
        }
        boolean changed = false;
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.lifecycleState(uuid) != CompanionLifecycleState.HOME_ACTIVE && !isResident(uuid)) {
                    continue;
                }
                Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    changed |= CompanionStorageService.storeHomeResidentSilently(player, data, living, source);
                    continue;
                }
                clearResident(uuid);
                data.clearDeployed(kind, uuid);
                data.setLifecycleState(uuid, data.storedEntity(uuid).isPresent()
                        ? CompanionLifecycleState.HOME_STORED
                        : CompanionLifecycleState.RECOVERY);
                changed = true;
            }
        }
        if (changed) {
            CompanionDataService.save(player, data);
        }
        return changed;
    }

    public static boolean sendHomeIfPossible(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
        if (player == null || data == null || kind == null || living == null || !data.contains(kind, living.getUUID())) {
            return false;
        }
        Optional<ServerLevel> maybeLevel = houseLevel(player, data, living.getUUID());
        Optional<BlockPos> maybeHouse = houseBlock(player, data, living.getUUID());
        if (maybeLevel.isEmpty() || maybeHouse.isEmpty()) {
            return false;
        }
        ServerLevel level = maybeLevel.get();
        BlockPos housePos = maybeHouse.get();
        if (!validLoadedHouse(level, housePos)) {
            return false;
        }
        CompanionMoveType moveType = CompanionEntityClassifier.moveType(living, kind);
        Optional<BlockPos> groundTarget = findResidentSpot(level, living, housePos)
                .or(() -> findResidentFootPos(level, housePos, living.getBbWidth(), living.getBbHeight()));
        Optional<BlockPos> maybeTarget = groundTarget.isPresent() || moveType != CompanionMoveType.FLY
                ? groundTarget : findResidentAirPos(level, housePos, living.getBbWidth(), living.getBbHeight());
        if (maybeTarget.isEmpty()) {
            return false;
        }
        BlockPos target = maybeTarget.get();
        CompanionHomeBehaviorService.configureResident(living, housePos, moveType, groundTarget.isEmpty());
        return CompanionStorageService.sendHomeAfterStorage(player, data, kind, living, level, target, living.getYRot(), living.getXRot());
    }

    public static void collectResidentsForBrokenHouse(ServerPlayer player, PlayerCompanionData data, SavedPosition source) {
        if (player == null || data == null || source == null) {
            return;
        }
        boolean mountsChanged = false;
        boolean companionsChanged = false;
        for (CompanionKind kind : CompanionKind.values()) {
            boolean changed = false;
            for (UUID uuid : data.list(kind)) {
                if (!isResident(uuid) || data.homeNestBlock(uuid).filter(saved -> sameBlock(saved, source)).isEmpty()) {
                    continue;
                }
                data.clearHomePosition(uuid);
                Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    changed |= CompanionDeploymentService.collectLiving(player, data, kind, living);
                } else {
                    clearResident(uuid);
                    changed = true;
                }
            }
            if (kind == CompanionKind.MOUNT) {
                mountsChanged |= changed;
            } else {
                companionsChanged |= changed;
            }
        }
        if (mountsChanged || companionsChanged) {
            CompanionDataService.save(player, data);
            if (mountsChanged) {
                CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            }
            if (companionsChanged) {
                CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            }
        }
    }

    private static boolean shouldRestoreResident(PlayerCompanionData data, CompanionKind kind, UUID uuid,
                                                 HomeTarget homeTarget) {
        if (uuid == null || kind == null
                || isResident(uuid) || data.isDeployed(kind, uuid) || !data.storedEntity(uuid).isPresent()) {
            return false;
        }
        if (CompanionOperationLockService.get(uuid) != null) {
            return false;
        }
        if (CompanionStorageService.isStoragePending(uuid) || data.homeNestBlock(uuid).isEmpty() || data.homePosition(uuid).isEmpty()) {
            return false;
        }
        if (homeTarget == null) {
            return false;
        }
        ServerLevel level = homeTarget.level;
        BlockPos housePos = homeTarget.housePos;
        if (!validLoadedHouse(level, housePos)) {
            return false;
        }
        return isHouseChunkTrackedByAnyPlayer(level, housePos);
    }

    private static boolean shouldRestoreResident(HomeResidentIndex.Entry entry, HomeTarget homeTarget) {
        if (entry == null || isResident(entry.uuid()) || entry.deployed() || !entry.stored()
                || CompanionOperationLockService.get(entry.uuid()) != null
                || CompanionStorageService.isStoragePending(entry.uuid())
                || entry.homeNestBlock() == null || entry.homePosition() == null || homeTarget == null) {
            return false;
        }
        return validLoadedHouse(homeTarget.level, homeTarget.housePos)
                && isHouseChunkTrackedByAnyPlayer(homeTarget.level, homeTarget.housePos);
    }

    private static LivingEntity findRiddenLiving(ServerPlayer player, UUID uuid) {
        if (player == null || uuid == null) {
            return null;
        }
        Entity ridden = player.getVehicle();
        while (ridden != null) {
            if (uuid.equals(ridden.getUUID())) {
                return ridden instanceof LivingEntity living && living.isAlive() ? living : null;
            }
            Entity parent = ridden.getVehicle();
            if (parent == ridden) {
                break;
            }
            ridden = parent;
        }
        return null;
    }

    private static boolean reconcileRiddenCompanion(PlayerCompanionData data, CompanionKind kind,
                                                     LivingEntity living) {
        UUID uuid = living.getUUID();
        boolean changed = isResident(uuid)
                || data.lifecycleState(uuid) != CompanionLifecycleState.DEPLOYED
                || !data.isDeployed(kind, uuid)
                || data.storedEntity(uuid).isPresent();
        clearResident(living);
        if (!changed) {
            return false;
        }
        data.removeStoredEntity(uuid);
        data.setDeployed(kind, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        data.setLastKnownPosition(uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(),
                living.getYRot(), living.getXRot()));
        return true;
    }

    public static void collectResidentsForBrokenHouse(ServerPlayer player, PlayerCompanionData data, UUID houseId) {
        if (player == null || data == null || houseId == null) {
            return;
        }
        boolean changed = false;
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.homeHouseId(uuid).filter(houseId::equals).isEmpty()) {
                    continue;
                }
                data.clearHomePosition(uuid);
                FindMeWorldSavedData.get(player.server).removeResident(houseId, uuid);
                Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    changed |= CompanionDeploymentService.collectLiving(player, data, kind, living);
                } else {
                    clearResident(uuid);
                    changed = true;
                }
            }
        }
        if (changed) {
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        }
    }

    private static boolean reconcileResident(PlayerCompanionData data, CompanionKind kind, UUID uuid,
                                             Entity entity, HomeTarget homeTarget) {
        if (uuid == null || data.lifecycleState(uuid) != CompanionLifecycleState.HOME_ACTIVE) {
            return false;
        }
        if (CompanionStorageService.isStoragePending(uuid)) {
            return false;
        }
        if (isResident(uuid) && entity instanceof LivingEntity resident && resident.isAlive()) {
            CompanionMoveType moveType = CompanionEntityClassifier.moveType(resident, kind);
            CompanionHomeBehaviorService.applyResidentBehavior(resident,
                    homeTarget == null ? resident.blockPosition() : homeTarget.housePos,
                    moveType, CompanionHomeBehaviorService.isAirborneResident(resident)
                            || moveType == CompanionMoveType.FLY && !resident.onGround());
            return false;
        }
        if (entity instanceof LivingEntity living && living.isAlive()) {
            CompanionMoveType moveType = CompanionEntityClassifier.moveType(living, kind);
            markResidentEntity(living, homeTarget == null ? living.blockPosition() : homeTarget.housePos,
                    moveType, CompanionHomeBehaviorService.isAirborneResident(living)
                            || moveType == CompanionMoveType.FLY && !living.onGround());
            return true;
        }
        clearResident(uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.HOME_STORED);
        return true;
    }

    private static boolean collectResidentOutsideTrackedView(ServerPlayer player, PlayerCompanionData data,
                                                              UUID uuid, Entity entity, HomeTarget homeTarget) {
        if (uuid == null || !isResident(uuid) || data.homeNestBlock(uuid).isEmpty()) {
            return false;
        }
        BlockPos housePos = homeTarget == null ? null : homeTarget.housePos;
        ServerLevel level = homeTarget == null ? null : homeTarget.level;
        if (housePos == null || level == null || !(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (isHouseChunkTrackedByAnyPlayer(level, housePos)) {
            return false;
        }
        if (!CompanionStorageService.storeHomeResidentSilently(player, data, living, "home:chunk_left_view")) {
            return false;
        }
        return true;
    }

    private static LivingEntity residentEntity(UUID uuid) {
        LivingEntity living = RESIDENT_ENTITIES.get(uuid);
        if (living != null && living.isAlive() && !living.isRemoved()) {
            return living;
        }
        RESIDENT_ENTITIES.remove(uuid);
        return null;
    }

    private static boolean restoreResidentNearHouse(ServerPlayer player, PlayerCompanionData data,
                                                    CompanionKind kind, UUID uuid, HomeTarget homeTarget) {
        if (uuid == null || CompanionOperationLockService.get(uuid) != null) {
            return false;
        }
        ServerLevel level = homeTarget == null ? null : homeTarget.level;
        BlockPos housePos = homeTarget == null ? null : homeTarget.housePos;
        if (level == null || housePos == null) {
            return false;
        }
        float width = data.storedEntity(uuid).map(tag -> tag.getFloat("CompanionPreviewWidth")).orElse(1.0f);
        float height = data.storedEntity(uuid).map(tag -> tag.getFloat("CompanionPreviewHeight")).orElse(1.8f);
        String entityType = data.storedEntity(uuid).map(com.kuzhi.findme.server.data.CompanionEntitySnapshots::storedEntityType)
                .orElse("");
        CompanionMoveType moveType = CompanionEntityClassifier.moveType(entityType, kind);
        Optional<BlockPos> groundTarget = findResidentFootPos(level, housePos, width, height);
        Optional<BlockPos> maybeTarget = groundTarget.isPresent() || moveType != CompanionMoveType.FLY
                ? groundTarget : findResidentAirPos(level, housePos, width, height);
        boolean airborne = groundTarget.isEmpty() && maybeTarget.isPresent();
        if (maybeTarget.isEmpty()) {
            if (com.kuzhi.findme.server.core.FindMeDebugLogger.enabled()) {
                com.kuzhi.findme.server.core.FindMeDebugLogger.info("home-resident",
                        "restore deferred reason=no_independent_slot owner={} companion={} kind={} house={} width={} height={} residents={}",
                        player.getUUID(), uuid, kind, housePos, width, height, residentCount(level, housePos));
            }
            return false;
        }
        BlockPos target = maybeTarget.get();
        Entity restored = CompanionLifecycleFacade.restoreStoredForHome(level, player, data, kind, uuid, target,
                player.getYRot(), 0.0f, "home_resident:chunk_visible").orElse(null);
        if (!(restored instanceof LivingEntity living) || !living.isAlive()) {
            com.kuzhi.findme.server.core.FindMeDebugLogger.info("home-resident",
                    "restore failed reason=entity_restore owner={} companion={} kind={} house={} target={}",
                    player.getUUID(), uuid, kind, housePos, target);
            return false;
        }
        if (!airborne) {
            findResidentSpot(level, living, housePos).ifPresent(actual -> living.teleportTo(
                    actual.getX() + 0.5, actual.getY(), actual.getZ() + 0.5));
        }
        markResidentEntity(living, housePos, moveType, airborne);
        if (com.kuzhi.findme.server.core.FindMeDebugLogger.enabled()) {
            com.kuzhi.findme.server.core.FindMeDebugLogger.info("home-resident",
                    "restore complete owner={} companion={} kind={} type={} house={} position={} residents={}",
                    player.getUUID(), uuid, kind, net.minecraft.world.entity.EntityType.getKey(living.getType()),
                    housePos, living.blockPosition(), residentCount(level, housePos));
        }
        return true;
    }

    private static HomeTarget resolveHomeTarget(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        ServerLevel level = houseLevel(player, data, uuid).orElse(null);
        BlockPos housePos = houseBlock(player, data, uuid).orElse(null);
        return level == null || housePos == null ? null : new HomeTarget(player, level, housePos);
    }

    private static HomeTarget resolveHomeTarget(ServerPlayer player, HomeResidentIndex.Entry entry) {
        SavedPosition position = entry.houseId() == null
                ? entry.homeNestBlock()
                : FindMeWorldSavedData.get(player.server).house(entry.houseId())
                        .map(FindMeWorldSavedData.HouseRecord::position)
                        .orElse(null);
        if (position == null) {
            return null;
        }
        ServerLevel level = player.getServer().getLevel(position.dimension());
        return level == null ? null : new HomeTarget(player, level, position.blockPos());
    }

    private static boolean restoreRetryReady(UUID uuid, long gameTime) {
        RestoreRetry retry = RESTORE_RETRIES.get(uuid);
        return retry == null || gameTime >= retry.nextAttemptAt;
    }

    private static void rememberRestoreFailure(ServerPlayer player, UUID uuid) {
        RestoreRetry previous = RESTORE_RETRIES.get(uuid);
        int failures = previous == null ? 1 : Math.min(6, previous.failures + 1);
        long delay = Math.min(600L, 20L << Math.max(0, failures - 1));
        RESTORE_RETRIES.put(uuid, new RestoreRetry(player.getUUID(), failures,
                player.serverLevel().getGameTime() + delay));
    }

    private static boolean shouldForgetRestoreRetry(PlayerCompanionData data, UUID uuid) {
        return data.lifecycleState(uuid) != CompanionLifecycleState.HOME_STORED
                || data.storedEntity(uuid).isEmpty()
                || data.homeNestBlock(uuid).isEmpty()
                || data.homePosition(uuid).isEmpty();
    }

    private static boolean shouldForgetRestoreRetry(HomeResidentIndex.Entry entry) {
        return entry.lifecycleState() != CompanionLifecycleState.HOME_STORED
                || !entry.stored() || entry.homeNestBlock() == null || entry.homePosition() == null;
    }

    private static boolean isCoherentRiddenState(HomeResidentIndex.Entry entry) {
        return entry.lifecycleState() == CompanionLifecycleState.DEPLOYED
                && entry.deployed() && !entry.stored() && !isResident(entry.uuid());
    }

    private static boolean isNormalDeployedState(HomeResidentIndex.Entry entry) {
        return entry.lifecycleState() == CompanionLifecycleState.DEPLOYED
                && entry.deployed() && !entry.stored() && !isResident(entry.uuid());
    }

    private static boolean isHomeLifecycle(CompanionLifecycleState state) {
        return state == CompanionLifecycleState.HOME_ACTIVE || state == CompanionLifecycleState.HOME_STORED;
    }

    private static Optional<ServerLevel> houseLevel(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        Optional<UUID> houseId = data.homeHouseId(uuid);
        if (houseId.isPresent()) {
            return FindMeWorldSavedData.get(player.server).house(houseId.get()).map(record -> record.position().dimension()).map(player.getServer()::getLevel);
        }
        return data.homeNestBlock(uuid).map(SavedPosition::dimension).map(player.getServer()::getLevel);
    }

    private static Optional<BlockPos> houseBlock(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        Optional<UUID> houseId = data.homeHouseId(uuid);
        if (houseId.isPresent()) {
            return FindMeWorldSavedData.get(player.server).house(houseId.get()).map(record -> record.position().blockPos());
        }
        return data.homeNestBlock(uuid).map(SavedPosition::blockPos);
    }

    private static boolean validLoadedHouse(ServerLevel level, BlockPos housePos) {
        return level.hasChunkAt(housePos) && level.isLoaded(housePos) && level.getBlockState(housePos).is(ModBlocks.SMALL_HOUSE.get());
    }

    private static boolean isHouseChunkTrackedByAnyPlayer(ServerLevel level, BlockPos housePos) {
        if (level == null || housePos == null) {
            return false;
        }
        long gameTime = level.getServer().getTickCount();
        if (trackedChunkCacheTick != gameTime) {
            TRACKED_CHUNK_CACHE.clear();
            trackedChunkCacheTick = gameTime;
        }
        ChunkPos chunkPos = new ChunkPos(housePos);
        TrackedChunkKey key = new TrackedChunkKey(level.dimension(), chunkPos.toLong());
        return TRACKED_CHUNK_CACHE.computeIfAbsent(key, ignored -> level.players().stream()
                .anyMatch(viewer -> viewer.getChunkTrackingView().contains(chunkPos)));
    }

    private record TrackedChunkKey(ResourceKey<Level> dimension, long chunkPos) {
    }

    static Optional<BlockPos> findResidentSpot(ServerLevel level, LivingEntity living, BlockPos housePos) {
        if (level == null || living == null || housePos == null) {
            return Optional.empty();
        }
        int startRadius = minimumResidentRadius(living.getBbWidth());
        for (int y = -1; y <= 2; ++y) {
            for (int r = startRadius; r <= MAX_RESIDENT_SEARCH_RADIUS; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        if (Math.abs(x) != r && Math.abs(z) != r) {
                            continue;
                        }
                        BlockPos candidate = housePos.offset(x, y, z);
                        if (outsideHouseInteractionArea(candidate, living.getBbWidth(), housePos)
                                && CompanionPlacementFinder.isSafe(level, candidate)
                                && CompanionPlacementFinder.hasOpenEntitySpace(level, living, candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)
                                && hasNoResidentOverlap(level, living, candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findResidentFootPos(ServerLevel level, BlockPos housePos, double width, double height) {
        if (level == null || housePos == null) {
            return Optional.empty();
        }
        int startRadius = minimumResidentRadius(width);
        for (int y = -1; y <= 2; ++y) {
            for (int r = startRadius; r <= MAX_RESIDENT_SEARCH_RADIUS; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        if (Math.abs(x) != r && Math.abs(z) != r) {
                            continue;
                        }
                        BlockPos candidate = housePos.offset(x, y, z);
                        if (outsideHouseInteractionArea(candidate, width, housePos)
                                && CompanionPlacementFinder.isSafe(level, candidate)
                                && hasOpenEntitySpace(level, candidate, width, height)
                                && !hasResidentOverlap(level, candidate, width, height)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hasOpenEntitySpace(ServerLevel level, BlockPos pos, double width, double height) {
        double safeWidth = Math.max(0.1, width);
        double safeHeight = Math.max(0.1, height);
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                x - safeWidth * 0.5, pos.getY(), z - safeWidth * 0.5,
                x + safeWidth * 0.5, pos.getY() + safeHeight, z + safeWidth * 0.5);
        return level.noCollision(box);
    }

    private static Optional<BlockPos> findResidentAirPos(ServerLevel level, BlockPos housePos,
                                                         double width, double height) {
        if (level == null || housePos == null) {
            return Optional.empty();
        }
        double safeWidth = Math.max(1.0, width + 0.75);
        double safeHeight = Math.max(1.8, height + 0.5);
        int startRadius = Math.max(5, minimumResidentRadius(width));
        int verticalStep = Math.max(3, (int)Math.ceil(safeHeight));
        for (int yOffset = Math.max(6, verticalStep); yOffset <= 30; yOffset += verticalStep) {
            for (int r = startRadius; r <= MAX_RESIDENT_SEARCH_RADIUS; ++r) {
                for (int x = -r; x <= r; ++x) {
                    for (int z = -r; z <= r; ++z) {
                        if (Math.abs(x) != r && Math.abs(z) != r) {
                            continue;
                        }
                        BlockPos candidate = housePos.offset(x, yOffset, z);
                        double cx = candidate.getX() + 0.5;
                        double cy = candidate.getY();
                        double cz = candidate.getZ() + 0.5;
                        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                                cx - safeWidth * 0.5, cy, cz - safeWidth * 0.5,
                                cx + safeWidth * 0.5, cy + safeHeight, cz + safeWidth * 0.5);
                        if (level.noCollision(box)
                                && !hasResidentOverlap(level, cx, cy, cz, safeWidth, safeHeight, null)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static int minimumResidentRadius(double width) {
        return Math.max(3, (int)Math.ceil(HOUSE_INTERACTION_CLEARANCE + Math.max(0.45, width * 0.5)));
    }

    private static boolean outsideHouseInteractionArea(BlockPos candidate, double width, BlockPos housePos) {
        double required = HOUSE_INTERACTION_CLEARANCE + Math.max(0.45, width * 0.5);
        double dx = candidate.getX() + 0.5 - (housePos.getX() + 0.5);
        double dz = candidate.getZ() + 0.5 - (housePos.getZ() + 0.5);
        return dx * dx + dz * dz >= required * required;
    }

    private static long residentCount(ServerLevel level, BlockPos housePos) {
        double radius = MAX_RESIDENT_SEARCH_RADIUS + 4.0;
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(housePos).inflate(radius, 8.0, radius);
        return level.getEntitiesOfClass(LivingEntity.class, area, living -> living.isAlive()
                && isResident(living.getUUID())).size();
    }

    private static boolean hasNoResidentOverlap(ServerLevel level, LivingEntity living, double x, double y, double z) {
        double width = Math.max(0.9, living.getBbWidth() + 0.55);
        double height = Math.max(1.8, living.getBbHeight() + 0.25);
        return !hasResidentOverlap(level, x, y, z, width, height, living);
    }

    private static boolean hasResidentOverlap(ServerLevel level, BlockPos pos, double width, double height) {
        double safeWidth = Math.max(0.9, width + 0.55);
        double safeHeight = Math.max(1.8, height + 0.25);
        return hasResidentOverlap(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, safeWidth, safeHeight, null);
    }

    private static boolean hasResidentOverlap(ServerLevel level, double x, double y, double z, double width, double height, Entity ignored) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(x - width * 0.5, y, z - width * 0.5, x + width * 0.5, y + height, z + width * 0.5);
        return !level.getEntities(ignored, box, entity -> entity instanceof LivingEntity living
                && living.isAlive() && isResident(living.getUUID())).isEmpty();
    }

    private static void keepResidentPersistent(LivingEntity living) {
        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    private static boolean sameBlock(SavedPosition first, SavedPosition second) {
        return first != null
                && second != null
                && first.dimension().equals(second.dimension())
                && first.blockPos().equals(second.blockPos());
    }

    private static final class LazyPlayerData {
        private final ServerPlayer player;
        private final CompanionHomePerformanceTrace trace;
        private PlayerCompanionData value;

        private LazyPlayerData(ServerPlayer player, CompanionHomePerformanceTrace trace) {
            this.player = player;
            this.trace = trace;
        }

        private PlayerCompanionData get() {
            if (value == null) {
                long startedAt = trace.start();
                value = CompanionDataService.data(player);
                trace.record(CompanionHomePerformanceTrace.DATA, startedAt);
            }
            return value;
        }
    }

    private record HomeTarget(ServerPlayer player, ServerLevel level, BlockPos housePos) {
    }

    private record RestoreRetry(UUID playerUuid, int failures, long nextAttemptAt) {
    }
}

