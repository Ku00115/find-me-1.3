package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.ui.CompanionMessageService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionRuntimeIndex;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.ui.CompanionSummonLineService;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.kuzhi.findme.server.ui.CompanionSyncService;
public final class CompanionEscortService {
    private static final double TELEPORT_DISTANCE_SQR = 48.0 * 48.0;
    private static final double CANCEL_DISTANCE_SQR = 160.0 * 160.0;
    private static final int POSITION_SAVE_INTERVAL_TICKS = 20 * 10;
    private static final Map<UUID, EscortState> ESCORTS = new HashMap<>();

    private CompanionEscortService() {
    }

    public static void toggleWheelIndex(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return;
        }
        toggleUuid(player, data, kind, maybeUuid.get());
    }

    public static void toggleListIndex(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index) {
        Optional<UUID> maybeUuid = data.uuidAt(kind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return;
        }
        toggleUuid(player, data, kind, maybeUuid.get());
    }

    public static void toggleUuidForCommand(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        toggleUuid(player, data, kind, uuid);
    }

    public static boolean isEscorting(ServerPlayer player, UUID uuid) {
        EscortState state = player == null ? null : ESCORTS.get(player.getUUID());
        return state != null && uuid != null && state.uuid().equals(uuid);
    }

    public static boolean isEscorting(UUID uuid) {
        return uuid != null && ESCORTS.values().stream().anyMatch(state -> state.uuid().equals(uuid));
    }

    public static void tick(ServerPlayer player) {
        EscortState state = ESCORTS.get(player.getUUID());
        if (state == null) {
            return;
        }
        CompanionRuntimeIndex runtime = CompanionDataService.runtimeIndex(player);
        if (!runtime.contains(state.kind(), state.uuid())) {
            ESCORTS.remove(player.getUUID());
            return;
        }
        if (CompanionStorageService.isStoragePending(state.uuid())) {
            stop(player, CompanionDataService.data(player), state, false, false);
            return;
        }
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), state.uuid()).orElse(null);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            ESCORTS.remove(player.getUUID());
            return;
        }
        Entity anchor = player.getVehicle() != null && !player.getVehicle().getUUID().equals(state.uuid()) ? player.getVehicle() : player;
        if (anchor.getUUID().equals(state.uuid())) {
            stop(player, CompanionDataService.data(player), state, false, false);
            return;
        }
        if (CompanionArrivalSequenceService.isPending(living)) {
            return;
        }
        CompanionMoveType moveType = CompanionEntityClassifier.moveType(living, state.kind());
        Vec3 target = CompanionEscortMovementService.followPosition(player, anchor, living, moveType, state.side());
        double distanceSqr = living.position().distanceToSqr(target);
        if (!living.level().dimension().equals(player.level().dimension()) || distanceSqr > TELEPORT_DISTANCE_SQR || distanceSqr > CANCEL_DISTANCE_SQR) {
            PlayerCompanionData data = CompanionDataService.data(player);
            CompanionEscortMovementService.moveNearPlayer(player, data, living, target, moveType);
            return;
        }
        CompanionEscortMovementService.control(living, target, moveType);
        if (++state.age % POSITION_SAVE_INTERVAL_TICKS == 0) {
            PlayerCompanionData data = CompanionDataService.data(player);
            data.setLastKnownPosition(state.uuid(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
            CompanionDataService.save(player, data);
        }
    }

    public static void cancelForTransition(ServerPlayer player) {
        EscortState state = ESCORTS.get(player.getUUID());
        if (state == null) {
            return;
        }
        stop(player, CompanionDataService.data(player), state, true, true);
    }

    public static void stopForCommand(ServerPlayer player) {
        EscortState state = ESCORTS.get(player.getUUID());
        if (state == null) {
            CompanionMessageService.tell(player, "message.find_me.escort_stopped", ChatFormatting.GRAY);
            return;
        }
        stop(player, CompanionDataService.data(player), state, true, false);
        CompanionMessageService.tell(player, "message.find_me.escort_stopped", ChatFormatting.GRAY);
        CompanionSyncService.syncToClient(player, state.kind());
    }

    public static void cancelIfEscorting(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        EscortState state = ESCORTS.get(player.getUUID());
        if (state != null && state.uuid().equals(uuid)) {
            stop(player, data, state, false, false);
        }
    }

    public static void cancelIfEscortingForStorage(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        EscortState state = ESCORTS.get(player.getUUID());
        if (state == null || !state.uuid().equals(uuid)) {
            return;
        }
        ESCORTS.remove(player.getUUID());
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), state.uuid()).orElse(null);
        if (entity instanceof LivingEntity living) {
            CompanionStorageService.freezeForStorageTransition(living);
            data.setLastKnownPosition(state.uuid(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
            CompanionDataService.save(player, data);
        }
    }

    private static void toggleUuid(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        if (kind != CompanionKind.COMPANION) {
            CompanionMessageService.tell(player, "message.find_me.escort_unavailable", ChatFormatting.YELLOW);
            return;
        }
        CompanionTacticalOrderService.cancelTarget(player.getServer(), uuid, "escort_toggle");
        EscortState current = ESCORTS.get(player.getUUID());
        if (current != null && current.uuid().equals(uuid)) {
            stop(player, data, current, true, false);
            CompanionMessageService.tell(player, "message.find_me.escort_stopped", ChatFormatting.GRAY);
            CompanionSyncService.syncToClient(player, kind);
            return;
        }
        if (current != null) {
            stop(player, data, current, true, true);
        }
        if (player.getVehicle() != null && player.getVehicle().getUUID().equals(uuid)) {
            CompanionMessageService.tell(player, "message.find_me.escort_current_ride", ChatFormatting.YELLOW);
            return;
        }
        if (CompanionStorageService.isStoragePending(uuid)) {
            CompanionSummonLineService.showBusy(player, data, kind, uuid);
            return;
        }
        boolean wasStored = data.storedEntity(uuid).isPresent();
        boolean wasDeployed = data.isDeployed(kind, uuid);
        LivingEntity living = resolveLivingForEscort(player, data, kind, uuid).orElse(null);
        if (living == null || !living.isAlive()) {
            CompanionSummonLineService.showUnavailable(player, data, kind);
            return;
        }
        if (living.isVehicle() || living.isPassenger()) {
            CompanionMessageService.tell(player, "message.find_me.escort_unavailable", ChatFormatting.YELLOW);
            return;
        }
        CompanionMoveType moveType = CompanionEntityClassifier.moveType(living, kind);
        boolean spawnedForEscort = wasStored || !wasDeployed;
        EscortState state = EscortState.capture(player, kind, living, spawnedForEscort);
        ESCORTS.put(player.getUUID(), state);
        CompanionHomeResidentService.clearResident(living);
        CompanionEscortMovementService.control(living, CompanionEscortMovementService.followPosition(player, player.getVehicle() == null ? player : player.getVehicle(), living, moveType, state.side()), moveType);
        CompanionMessageService.tell(player, "message.find_me.escort_started", ChatFormatting.GREEN, living.getDisplayName().getString());
        CompanionSyncService.syncToClient(player, kind);
    }

    private static Optional<LivingEntity> resolveLivingForEscort(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        Entity loaded = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
        if (loaded instanceof LivingEntity living) {
            return Optional.of(living);
        }
        Optional<CompoundTag> stored = data.storedEntity(uuid).map(CompoundTag::copy);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        String entityType = CompanionEntitySnapshots.storedEntityType(stored.get());
        CompanionMoveType moveType = CompanionEntityClassifier.moveType(entityType, kind);
        BlockPos spawn = CompanionEscortMovementService.spawnPos(player, moveType);
        return CompanionLifecycleFacade
                .restoreStoredForArrival(player, data, uuid, spawn, player.getYRot(), player.getXRot(), player.position(), 30, RescueMagicPacket.Style.VERTICAL_PORTAL, RescueMagicPacket.Purpose.SUMMON, "escort:restore")
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast);
    }

    private static void stop(ServerPlayer player, PlayerCompanionData data, EscortState state, boolean collect, boolean forceCollect) {
        ESCORTS.remove(player.getUUID());
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), state.uuid()).orElse(null);
        if (entity instanceof LivingEntity living) {
            if (collect && (forceCollect || state.spawnedForEscort())) {
                CompanionDeploymentService.collectLiving(player, data, state.kind(), living);
                CompanionDataService.save(player, data);
            } else {
                restore(living, state);
                data.setLastKnownPosition(state.uuid(), SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
                CompanionDataService.save(player, data);
            }
        }
    }

    private static void restore(LivingEntity living, EscortState state) {
        living.noPhysics = state.originalNoPhysics();
        living.setNoGravity(state.originalNoGravity());
        if (living instanceof Mob mob) {
            mob.setNoAi(state.originalNoAi());
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
        }
        living.fallDistance = 0.0f;
        living.hurtMarked = true;
    }

    private static final class EscortState {
        private final CompanionKind kind;
        private final UUID uuid;
        private final int side;
        private final boolean spawnedForEscort;
        private final boolean originalNoGravity;
        private final boolean originalNoPhysics;
        private final boolean originalNoAi;
        private int age;

        private static EscortState capture(ServerPlayer player, CompanionKind kind, LivingEntity living, boolean spawnedForEscort) {
            int side = (((player.getUUID().getLeastSignificantBits() ^ living.getUUID().getLeastSignificantBits()) & 1L) == 0L) ? 1 : -1;
            boolean noAi = living instanceof Mob mob && mob.isNoAi();
            return new EscortState(kind, living.getUUID(), side, spawnedForEscort, living.isNoGravity(), living.noPhysics, noAi, 0);
        }

        private EscortState(CompanionKind kind, UUID uuid, int side,
                            boolean spawnedForEscort, boolean originalNoGravity, boolean originalNoPhysics,
                            boolean originalNoAi, int age) {
            this.kind = kind;
            this.uuid = uuid;
            this.side = side;
            this.spawnedForEscort = spawnedForEscort;
            this.originalNoGravity = originalNoGravity;
            this.originalNoPhysics = originalNoPhysics;
            this.originalNoAi = originalNoAi;
            this.age = age;
        }

        private CompanionKind kind() { return this.kind; }
        private UUID uuid() { return this.uuid; }
        private int side() { return this.side; }
        private boolean spawnedForEscort() { return this.spawnedForEscort; }
        private boolean originalNoGravity() { return this.originalNoGravity; }
        private boolean originalNoPhysics() { return this.originalNoPhysics; }
        private boolean originalNoAi() { return this.originalNoAi; }
    }
}

