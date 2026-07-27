package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.VehicleSeatOffset;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SableVehicleCompatibility {
    static final String MARKER = "FindMeSableSubLevel";
    public static final String TYPE = "simulated:sable_sublevel";
    private static final String BLUEPRINT = "FindMeSableBlueprint";
    private static final String FORMAT = "FindMeSableFormat";
    private static final String FORMAT_BLUEPRINT_V1 = "sable_blueprint_v1";
    private static final String STORED_DIMENSION = "FindMeSableDimension";
    private static final String STORED_X = "FindMeSableX";
    private static final String STORED_Y = "FindMeSableY";
    private static final String STORED_Z = "FindMeSableZ";
    private static final String CENTER_Y_OFFSET = "FindMeSableCenterYOffset";
    private static final String SEAT_OFFSET = "FindMeSableSeatOffset";

    private static final Reflection REFLECTION = Reflection.load();

    private SableVehicleCompatibility() {
    }

    public static void bootstrap() {
        FindMeMod.LOGGER.info("FindMe Sable compatibility: sable={}, sable_schematic_api={}",
                REFLECTION.available(), REFLECTION.snapshotAvailable());
    }

    public static boolean available() {
        return REFLECTION.available();
    }

    public static boolean snapshotAvailable() {
        return REFLECTION.snapshotAvailable();
    }

    public static boolean isStoredSable(CompoundTag tag) {
        return tag != null && tag.getBoolean(MARKER);
    }

    public static boolean isStored(PlayerCompanionData data, UUID uuid) {
        return data != null && uuid != null && data.storedEntity(uuid).map(SableVehicleCompatibility::isStoredSable).orElse(false);
    }

    public static boolean canRestore(PlayerCompanionData data, UUID uuid) {
        CompoundTag tag = data == null || uuid == null ? null : data.storedEntity(uuid).orElse(null);
        return isStoredSable(tag) && REFLECTION.snapshotAvailable() && tag.contains(BLUEPRINT);
    }

    public static Optional<Handle> findAt(ServerLevel level, BlockPos pos) {
        if (!available() || level == null || pos == null) {
            return Optional.empty();
        }
        return wrap(level, REFLECTION.getContaining(level, pos));
    }

    public static Vec3 projectOut(ServerLevel level, Vec3 pos) {
        return REFLECTION.projectOutOfSubLevel(level, pos);
    }

    public static Optional<Vec3> stableSeatOffset(Handle handle, Vec3 plotPoint) {
        if (handle == null || plotPoint == null) {
            return Optional.empty();
        }
        BlockPos center = REFLECTION.plotCenter(handle.subLevel);
        return center == null ? Optional.empty() : Optional.of(plotPoint.subtract(Vec3.atLowerCornerOf(center)));
    }

    public static Optional<Vec3> projectStableSeat(ServerLevel level, Handle handle, Vec3 stableOffset) {
        if (level == null || handle == null || stableOffset == null) {
            return Optional.empty();
        }
        BlockPos center = REFLECTION.plotCenter(handle.subLevel);
        if (center == null) {
            return Optional.empty();
        }
        Vec3 plotPoint = Vec3.atLowerCornerOf(center).add(stableOffset);
        return Optional.of(REFLECTION.projectOutOfSubLevel(level, plotPoint));
    }

    public static void attachPlayerToProjectedPoint(ServerPlayer player, Handle handle, Vec3 worldPoint) {
        if (player != null && handle != null && worldPoint != null) {
            REFLECTION.attachPlayer(player, handle.subLevel, worldPoint);
        }
    }

    public static boolean isPlayerTracking(ServerPlayer player, Handle handle) {
        if (player == null || handle == null) {
            return false;
        }
        return findForEntity(player).map(tracked -> handle.uuid().equals(tracked.uuid())).orElse(false);
    }

    public static Optional<Handle> findForEntity(Entity entity) {
        if (!available() || entity == null || !(entity.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return wrap(level, REFLECTION.getTrackingOrVehicleSubLevel(entity));
    }

    public static Optional<Handle> find(ServerPlayer player, UUID uuid) {
        return player == null ? Optional.empty() : find(player.getServer(), uuid);
    }

    public static Optional<Handle> find(MinecraftServer server, UUID uuid) {
        if (!available() || server == null || uuid == null) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Optional<Handle> handle = find(level, uuid);
            if (handle.isPresent()) {
                return handle;
            }
        }
        return Optional.empty();
    }

    public static Optional<Handle> find(ServerLevel level, UUID uuid) {
        if (!available() || level == null || uuid == null) {
            return Optional.empty();
        }
        Object container = REFLECTION.container(level);
        return wrap(level, REFLECTION.getSubLevel(container, uuid));
    }

    public static boolean store(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (isStored(data, uuid)) {
            return true;
        }
        Optional<Handle> maybeHandle = find(player, uuid);
        return maybeHandle.isPresent() && store(player, data, maybeHandle.get());
    }

    public static boolean store(ServerPlayer player, PlayerCompanionData data, Handle handle) {
        if (!snapshotAvailable() || player == null || data == null || handle == null || handle.subLevel == null) {
            return false;
        }
        UUID uuid = handle.uuid();
        AABB box = handle.box();
        if (uuid == null || !isReasonableVehicleBox(box)) {
            return false;
        }
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.STORE, "sable:store")) {
            return false;
        }
        try {
            Vec3 center = box.getCenter();
            CompoundTag blueprint = REFLECTION.exportBlueprint(handle.level, center, box.inflate(0.25));
            if (blueprint == null || blueprint.isEmpty()) {
                FindMeMod.LOGGER.warn("FindMe Sable storage refused: blueprint export returned empty for sub-level {}", uuid);
                return false;
            }
            int removedDuplicateBearings = deduplicatePropellerBearingEntities(blueprint);
            if (removedDuplicateBearings > 0) {
                FindMeMod.LOGGER.info("FindMe removed {} stale duplicate Aeronautics bearing contraption snapshot(s) from vehicle {}", removedDuplicateBearings, uuid);
            }
            List<Handle> exportedHandles = exportedHandles(handle.level, blueprint, handle);
            if (exportedHandles.isEmpty()) {
                FindMeMod.LOGGER.warn("FindMe Sable storage refused: blueprint for {} did not map back to live sub-levels", uuid);
                return false;
            }

            String name = handle.name();
            CompoundTag tag = storedTag(player, handle, box, center);
            tag.putString(FORMAT, FORMAT_BLUEPRINT_V1);
            tag.put(BLUEPRINT, blueprint);
            data.vehicleSeatOffset(uuid).ifPresent(offset -> tag.put(SEAT_OFFSET, saveSeatOffset(offset)));
            data.storeEntity(uuid, tag);
            data.setDisplayName(uuid, name);
            data.setLastKnownPosition(uuid, SavedPosition.of(handle.level, center.x, center.y, center.z, 0.0f, 0.0f));
            CompanionDataService.save(player, data);
            FindMeDebugLogger.info("lifecycle", "operation=SABLE_VEHICLE_SNAPSHOT_CREATED player={} vehicle={} snapshotPresent=true sublevels={}",
                    player.getUUID(), uuid, exportedHandles.size());

            if (isPlayerTracking(player, handle)) {
                REFLECTION.detachPlayer(player);
                movePlayerOutOfSubLevel(player, box);
            }
            for (Handle exported : exportedHandles) {
                REFLECTION.stopMotion(exported.subLevel);
            }
            for (Handle exported : exportedHandles) {
                if (!REFLECTION.removeSubLevel(exported.level, exported.subLevel)) {
                    FindMeMod.LOGGER.warn("FindMe Sable storage warning: exported blueprint for {} but could not remove live sub-level {}", uuid, exported.uuid());
                }
            }

            FindMeMod.LOGGER.info("FindMe stored Sable vehicle {} as blueprint snapshot with {} sub-level(s)", uuid, exportedHandles.size());
            return true;
        } finally {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "sable:store");
        }
    }

    public static Optional<Handle> restore(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
        if (!snapshotAvailable() || player == null || data == null || uuid == null || pos == null) {
            return Optional.empty();
        }
        CompoundTag tag = data.storedEntity(uuid).orElse(null);
        if (!isStoredSable(tag) || !tag.contains(BLUEPRINT)) {
            return Optional.empty();
        }
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.DEPLOY, "sable:restore")) {
            return Optional.empty();
        }

        try {
            Vec3 anchor = restoreOrigin(tag, pos);
            if (findForEntity(player).isPresent()) {
                REFLECTION.detachPlayer(player);
            }
            CompoundTag placementBlueprint = tag.getCompound(BLUEPRINT).copy();
            int removedDuplicateBearings = deduplicatePropellerBearingEntities(placementBlueprint);
            if (removedDuplicateBearings > 0) {
                FindMeMod.LOGGER.info("FindMe ignored {} stale duplicate Aeronautics bearing contraption snapshot(s) while restoring vehicle {}", removedDuplicateBearings, uuid);
            }
            Placement placement = REFLECTION.placeBlueprint(player.serverLevel(), placementBlueprint, anchor);
            if (placement == null || placement.placedCount <= 0) {
                FindMeMod.LOGGER.warn("FindMe Sable restore failed: blueprint placement returned no sub-levels for {}", uuid);
                return Optional.empty();
            }

            UUID placedUuid = placement.mappedUuid(uuid);
            if (placedUuid == null) {
                placedUuid = placement.firstPlacedUuid();
            }
            if (placedUuid == null) {
                FindMeMod.LOGGER.warn("FindMe Sable restore failed: blueprint placement produced no UUID mapping for {}", uuid);
                return Optional.empty();
            }

            Optional<Handle> handle = find(player.serverLevel(), placedUuid);
            if (handle.isEmpty()) {
                FindMeMod.LOGGER.warn("FindMe Sable restore failed: placed sub-level {} was not found after blueprint placement", placedUuid);
                return Optional.empty();
            }

            VehicleSeatOffset embeddedSeat = loadSeatOffset(tag);
            data.removeStoredEntity(uuid);
            if (!uuid.equals(placedUuid)) {
                data.replaceUuid(uuid, placedUuid);
            }
            if (data.vehicleSeatOffset(placedUuid).isEmpty() && embeddedSeat != null) {
                data.setVehicleSeatOffset(placedUuid, embeddedSeat);
                FindMeMod.LOGGER.info("FindMe recovered Sable seat position from vehicle snapshot: {} -> {} offset={}", uuid, placedUuid, embeddedSeat);
            }
            data.setDisplayName(placedUuid, storedName(tag));
            Vec3 center = handle.get().box().getCenter();
            data.setLastKnownPosition(placedUuid, SavedPosition.of(player.serverLevel(), center.x, center.y, center.z, player.getYRot(), player.getXRot()));
            pushPlayerOutIfTracking(player, handle.get());
            CompanionDataService.save(player, data);
            FindMeMod.LOGGER.info("FindMe restored Sable vehicle {} from blueprint snapshot as {}", uuid, placedUuid);
            return handle;
        } finally {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.DEPLOY, "sable:restore");
        }
    }

    public static boolean move(Handle handle, ServerLevel level, BlockPos pos) {
        if (handle == null || level == null || pos == null || handle.level != level) {
            return false;
        }
        REFLECTION.stopMotion(handle.subLevel);
        REFLECTION.teleport(handle.subLevel, moveOrigin(handle, pos));
        return true;
    }

    public static void pushPlayerOut(ServerPlayer player, Handle handle) {
        if (player != null && handle != null) {
            REFLECTION.detachPlayer(player);
            movePlayerOutOfSubLevel(player, handle.box());
        }
    }

    public static void pushPlayerOut(ServerPlayer player, Handle handle, Vec3 destination) {
        if (player == null || handle == null || destination == null) {
            return;
        }
        REFLECTION.detachPlayer(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        teleportPlayerKeepingView(player, destination);
    }

    public static Vec3 externalRendezvous(ServerPlayer player, Handle handle, double clearance) {
        if (player == null || handle == null || !isReasonableVehicleBox(handle.box())) {
            return player == null ? null : player.position();
        }
        AABB box = handle.box();
        Vec3 pos = player.position();
        double margin = Math.max(2.0, clearance);
        double east = Math.abs(box.maxX - pos.x);
        double west = Math.abs(pos.x - box.minX);
        double south = Math.abs(box.maxZ - pos.z);
        double north = Math.abs(pos.z - box.minZ);
        double x = Mth.clamp(pos.x, box.minX, box.maxX);
        double z = Mth.clamp(pos.z, box.minZ, box.maxZ);
        double min = Math.min(Math.min(east, west), Math.min(south, north));
        if (min == east) {
            x = box.maxX + margin;
        } else if (min == west) {
            x = box.minX - margin;
        } else if (min == south) {
            z = box.maxZ + margin;
        } else {
            z = box.minZ - margin;
        }
        return new Vec3(x, pos.y, z);
    }

    private static void pushPlayerOutIfTracking(ServerPlayer player, Handle handle) {
        if (player != null && handle != null && isPlayerTracking(player, handle)) {
            pushPlayerOut(player, handle);
        }
    }

    public static void release(Handle handle) {
        // Blueprint storage does not create FindMe-owned Sable loading tickets.
    }

    private static CompoundTag storedTag(ServerPlayer player, Handle handle, AABB box, Vec3 center) {
        UUID uuid = handle.uuid();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        tag.putString("id", TYPE);
        tag.putString("CompanionRescueType", TYPE);
        tag.putString("CompanionRescueName", handle.name());
        tag.putUUID("UUID", uuid);
        tag.putFloat("CompanionPreviewWidth", (float)Math.max(1.0, box.getXsize()));
        tag.putFloat("CompanionPreviewHeight", (float)Math.max(1.0, box.getYsize()));
        tag.putFloat("CompanionPreviewDepth", (float)Math.max(1.0, box.getZsize()));
        tag.putFloat("CompanionEffectWidth", (float)Math.max(0.05, box.getXsize()));
        tag.putFloat("CompanionEffectHeight", (float)Math.max(0.05, box.getYsize()));
        tag.putFloat("CompanionEffectDepth", (float)Math.max(0.05, box.getZsize()));
        tag.putDouble(CENTER_Y_OFFSET, Math.max(0.0, center.y - box.minY));
        tag.putLong("CompanionRescueStoredAt", player.serverLevel().getGameTime());
        tag.putString(STORED_DIMENSION, handle.level.dimension().location().toString());
        tag.putDouble(STORED_X, center.x);
        tag.putDouble(STORED_Y, center.y);
        tag.putDouble(STORED_Z, center.z);
        return tag;
    }

    private static Vec3 restoreOrigin(CompoundTag tag, BlockPos pos) {
        double offset = tag.contains(CENTER_Y_OFFSET) ? tag.getDouble(CENTER_Y_OFFSET) : Math.max(0.5, tag.getFloat("CompanionPreviewHeight") * 0.5);
        return new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + offset, (double)pos.getZ() + 0.5);
    }

    private static Vec3 moveOrigin(Handle handle, BlockPos pos) {
        AABB box = handle.box();
        double offset = isReasonableVehicleBox(box) ? Math.max(0.0, box.getCenter().y - box.minY) : 1.0;
        return new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + offset, (double)pos.getZ() + 0.5);
    }

    private static String storedName(CompoundTag tag) {
        String name = tag.getString("CompanionRescueName");
        return name == null || name.isBlank() ? "Sable Vehicle" : name;
    }

    private static CompoundTag saveSeatOffset(VehicleSeatOffset offset) {
        CompoundTag seat = new CompoundTag();
        seat.putDouble("x", offset.x());
        seat.putDouble("y", offset.y());
        seat.putDouble("z", offset.z());
        return seat;
    }

    private static VehicleSeatOffset loadSeatOffset(CompoundTag tag) {
        if (tag == null || !tag.contains(SEAT_OFFSET, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag seat = tag.getCompound(SEAT_OFFSET);
        double x = seat.getDouble("x");
        double y = seat.getDouble("y");
        double z = seat.getDouble("z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        return new VehicleSeatOffset(x, y, z);
    }

    private static List<Handle> exportedHandles(ServerLevel level, CompoundTag blueprint, Handle fallback) {
        ArrayList<Handle> result = new ArrayList<>();
        if (blueprint != null && blueprint.contains("sub_levels", Tag.TAG_LIST)) {
            ListTag subLevels = blueprint.getList("sub_levels", Tag.TAG_COMPOUND);
            for (int i = 0; i < subLevels.size(); i++) {
                CompoundTag subLevel = subLevels.getCompound(i);
                if (!subLevel.hasUUID("source_uuid")) {
                    continue;
                }
                UUID sourceUuid = subLevel.getUUID("source_uuid");
                Optional<Handle> handle = find(level, sourceUuid);
                if (handle.isPresent() && result.stream().noneMatch(existing -> sourceUuid.equals(existing.uuid()))) {
                    result.add(handle.get());
                }
            }
        }
        if (result.isEmpty() && fallback != null) {
            result.add(fallback);
        }
        return result;
    }

    private static int deduplicatePropellerBearingEntities(CompoundTag blueprint) {
        if (blueprint == null || !blueprint.contains("sub_levels", Tag.TAG_LIST)) {
            return 0;
        }
        int removed = 0;
        ListTag subLevels = blueprint.getList("sub_levels", Tag.TAG_COMPOUND);
        for (int subIndex = 0; subIndex < subLevels.size(); subIndex++) {
            CompoundTag subLevel = subLevels.getCompound(subIndex);
            java.util.Map<String, Float> bearingAngles = new java.util.HashMap<>();
            ListTag blockEntities = subLevel.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag blockEntity = blockEntities.getCompound(i);
                if (blockEntity.getString("id").contains("propeller_bearing")) {
                    bearingAngles.put(blockKey(blockEntity.getInt("x"), blockEntity.getInt("y"), blockEntity.getInt("z")), blockEntity.getFloat("Angle"));
                }
            }

            ListTag entities = subLevel.getList("entities", Tag.TAG_COMPOUND);
            java.util.Map<String, Integer> bestIndex = new java.util.HashMap<>();
            java.util.Map<String, Float> bestDistance = new java.util.HashMap<>();
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag wrapped = entities.getCompound(i);
                CompoundTag entity = wrapped.getCompound("entity");
                if (!"aeronautics:propeller_bearing_contraption".equals(entity.getString("id"))) continue;
                String controller = controllerKey(wrapped, entity);
                float distance = bearingAngles.containsKey(controller)
                        ? circularAngleDistance(entity.getFloat("Angle"), bearingAngles.get(controller))
                        : 0.0f;
                if (!bestIndex.containsKey(controller) || distance < bestDistance.get(controller)) {
                    bestIndex.put(controller, i);
                    bestDistance.put(controller, distance);
                }
            }

            if (bestIndex.isEmpty()) continue;
            java.util.Set<Integer> retainedBearingIndexes = new java.util.HashSet<>(bestIndex.values());
            ListTag sanitized = new ListTag();
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag wrapped = entities.getCompound(i);
                CompoundTag entity = wrapped.getCompound("entity");
                if (!"aeronautics:propeller_bearing_contraption".equals(entity.getString("id")) || retainedBearingIndexes.contains(i)) {
                    sanitized.add(wrapped.copy());
                } else {
                    removed++;
                }
            }
            subLevel.put("entities", sanitized);
        }
        return removed;
    }

    private static String controllerKey(CompoundTag wrapped, CompoundTag entity) {
        CompoundTag local = wrapped.getCompound("local_pos");
        int x = (int)Math.round(local.getDouble("x"));
        int y = (int)Math.round(local.getDouble("y"));
        int z = (int)Math.round(local.getDouble("z"));
        int[] relative = entity.getIntArray("ControllerRelative");
        if (relative.length >= 3) {
            x += relative[0];
            y += relative[1];
            z += relative[2];
        }
        return blockKey(x, y, z);
    }

    private static String blockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static float circularAngleDistance(float left, float right) {
        float delta = Math.abs((left - right) % 360.0f);
        return Math.min(delta, 360.0f - delta);
    }

    private static void movePlayerOutOfSubLevel(ServerPlayer player, AABB box) {
        if (!isReasonableVehicleBox(box) || !player.getBoundingBox().intersects(box.inflate(0.75))) {
            return;
        }
        Vec3 pos = player.position();
        double east = Math.abs(box.maxX - pos.x);
        double west = Math.abs(pos.x - box.minX);
        double south = Math.abs(box.maxZ - pos.z);
        double north = Math.abs(pos.z - box.minZ);
        double x = pos.x;
        double z = pos.z;
        double min = Math.min(Math.min(east, west), Math.min(south, north));
        if (min == east) {
            x = box.maxX + 2.0;
        } else if (min == west) {
            x = box.minX - 2.0;
        } else if (min == south) {
            z = box.maxZ + 2.0;
        } else {
            z = box.minZ - 2.0;
        }
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        teleportPlayerKeepingView(player, new Vec3(x, pos.y, z));
    }

    private static void teleportPlayerKeepingView(ServerPlayer player, Vec3 pos) {
        if (player == null || pos == null) {
            return;
        }
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        player.teleportTo(pos.x, pos.y, pos.z);
        player.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.yRotO = yRot;
        player.xRotO = xRot;
        player.xOld = pos.x;
        player.xo = pos.x;
        player.yOld = pos.y;
        player.yo = pos.y;
        player.zOld = pos.z;
        player.zo = pos.z;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        REFLECTION.clearTracking(player);
    }

    private static boolean isReasonableVehicleBox(AABB box) {
        return box != null
                && Double.isFinite(box.minX)
                && Double.isFinite(box.minY)
                && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX)
                && Double.isFinite(box.maxY)
                && Double.isFinite(box.maxZ)
                && box.getXsize() > 0.0
                && box.getYsize() > 0.0
                && box.getZsize() > 0.0
                && box.getXsize() <= 512.0
                && box.getYsize() <= 256.0
                && box.getZsize() <= 512.0;
    }

    private static Optional<Handle> wrap(ServerLevel level, Object subLevel) {
        if (!available() || level == null || subLevel == null || REFLECTION.isRemoved(subLevel)) {
            return Optional.empty();
        }
        UUID uuid = REFLECTION.uuid(subLevel);
        return uuid == null ? Optional.empty() : Optional.of(new Handle(level, subLevel));
    }

    public static final class Handle {
        private final ServerLevel level;
        private final Object subLevel;

        private Handle(ServerLevel level, Object subLevel) {
            this.level = level;
            this.subLevel = subLevel;
        }

        public UUID uuid() {
            return REFLECTION.uuid(this.subLevel);
        }

        public String name() {
            String name = REFLECTION.name(this.subLevel);
            return name == null || name.isBlank() ? "Sable Vehicle" : name;
        }

        public AABB box() {
            return REFLECTION.box(this.subLevel);
        }

        public float radius() {
            AABB box = this.box();
            double halfX = box.getXsize() * 0.5;
            double halfZ = box.getZsize() * 0.5;
            return (float)Math.max(0.45, Math.min(128.0, Math.sqrt(halfX * halfX + halfZ * halfZ) * 1.28 + 0.16));
        }

        public float height() {
            return (float)Math.max(0.25, Math.min(128.0, this.box().getYsize()));
        }
    }

    private record Placement(int placedCount, Map<UUID, UUID> uuidMap) {
        private UUID mappedUuid(UUID source) {
            return this.uuidMap == null ? null : this.uuidMap.get(source);
        }

        private UUID firstPlacedUuid() {
            return this.uuidMap == null || this.uuidMap.isEmpty() ? null : this.uuidMap.values().iterator().next();
        }
    }

    private static final class Reflection {
        private final boolean available;
        private final boolean snapshotAvailable;
        private final Field helperField;
        private final Method getContaining;
        private final Method getTrackingOrVehicleSubLevel;
        private final Method getContainer;
        private final Method getSubLevel;
        private final Method removeSubLevel;
        private final Object removalReasonRemoved;
        private final Method rigidBodyOf;
        private final Method teleport;
        private final Method getLinearVelocity;
        private final Method getAngularVelocity;
        private final Method addLinearAndAngularVelocity;
        private final Method logicalPose;
        private final Method orientation;
        private final Method boundingBox;
        private final Method toMojang;
        private final Method uuid;
        private final Method name;
        private final Method isRemoved;
        private final Method kickEntity;
        private final Class<?> entityMovementExtension;
        private final Method setTrackingSubLevel;
        private final Method setLastTrackingSubLevelID;
        private final Method setPosField;
        private final Method projectOutOfSubLevel;
        private final Method getPlot;
        private final Method getCenterBlock;
        private final Constructor<?> boundingBoxConstructor;
        private final Method exportBlueprint;
        private final Method blueprintSave;
        private final Method blueprintLoad;
        private final Method blueprintIsEmpty;
        private final Method placeBlueprint;
        private final Method placedSubLevels;
        private final Method subLevelUuidMap;

        private Reflection() {
            this.available = false;
            this.snapshotAvailable = false;
            this.helperField = null;
            this.getContaining = null;
            this.getTrackingOrVehicleSubLevel = null;
            this.getContainer = null;
            this.getSubLevel = null;
            this.removeSubLevel = null;
            this.removalReasonRemoved = null;
            this.rigidBodyOf = null;
            this.teleport = null;
            this.getLinearVelocity = null;
            this.getAngularVelocity = null;
            this.addLinearAndAngularVelocity = null;
            this.logicalPose = null;
            this.orientation = null;
            this.boundingBox = null;
            this.toMojang = null;
            this.uuid = null;
            this.name = null;
            this.isRemoved = null;
            this.kickEntity = null;
            this.entityMovementExtension = null;
            this.setTrackingSubLevel = null;
            this.setLastTrackingSubLevelID = null;
            this.setPosField = null;
            this.projectOutOfSubLevel = null;
            this.getPlot = null;
            this.getCenterBlock = null;
            this.boundingBoxConstructor = null;
            this.exportBlueprint = null;
            this.blueprintSave = null;
            this.blueprintLoad = null;
            this.blueprintIsEmpty = null;
            this.placeBlueprint = null;
            this.placedSubLevels = null;
            this.subLevelUuidMap = null;
        }

        private Reflection(boolean available, boolean snapshotAvailable, Field helperField,
                           Method getContaining, Method getTrackingOrVehicleSubLevel, Method getContainer, Method getSubLevel,
                           Method removeSubLevel, Object removalReasonRemoved, Method rigidBodyOf, Method teleport,
                           Method getLinearVelocity, Method getAngularVelocity, Method addLinearAndAngularVelocity,
                           Method logicalPose, Method orientation, Method boundingBox, Method toMojang, Method uuid,
                           Method name, Method isRemoved, Method kickEntity, Class<?> entityMovementExtension,
                           Method setTrackingSubLevel, Method setLastTrackingSubLevelID, Method setPosField,
                           Method projectOutOfSubLevel, Method getPlot, Method getCenterBlock,
                           Constructor<?> boundingBoxConstructor, Method exportBlueprint,
                           Method blueprintSave, Method blueprintLoad, Method blueprintIsEmpty, Method placeBlueprint,
                           Method placedSubLevels, Method subLevelUuidMap) {
            this.available = available;
            this.snapshotAvailable = snapshotAvailable;
            this.helperField = helperField;
            this.getContaining = getContaining;
            this.getTrackingOrVehicleSubLevel = getTrackingOrVehicleSubLevel;
            this.getContainer = getContainer;
            this.getSubLevel = getSubLevel;
            this.removeSubLevel = removeSubLevel;
            this.removalReasonRemoved = removalReasonRemoved;
            this.rigidBodyOf = rigidBodyOf;
            this.teleport = teleport;
            this.getLinearVelocity = getLinearVelocity;
            this.getAngularVelocity = getAngularVelocity;
            this.addLinearAndAngularVelocity = addLinearAndAngularVelocity;
            this.logicalPose = logicalPose;
            this.orientation = orientation;
            this.boundingBox = boundingBox;
            this.toMojang = toMojang;
            this.uuid = uuid;
            this.name = name;
            this.isRemoved = isRemoved;
            this.kickEntity = kickEntity;
            this.entityMovementExtension = entityMovementExtension;
            this.setTrackingSubLevel = setTrackingSubLevel;
            this.setLastTrackingSubLevelID = setLastTrackingSubLevelID;
            this.setPosField = setPosField;
            this.projectOutOfSubLevel = projectOutOfSubLevel;
            this.getPlot = getPlot;
            this.getCenterBlock = getCenterBlock;
            this.boundingBoxConstructor = boundingBoxConstructor;
            this.exportBlueprint = exportBlueprint;
            this.blueprintSave = blueprintSave;
            this.blueprintLoad = blueprintLoad;
            this.blueprintIsEmpty = blueprintIsEmpty;
            this.placeBlueprint = placeBlueprint;
            this.placedSubLevels = placedSubLevels;
            this.subLevelUuidMap = subLevelUuidMap;
        }

        static Reflection load() {
            try {
                Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
                Class<?> helperClass = Class.forName("dev.ryanhcode.sable.ActiveSableCompanion");
                Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                Class<?> serverSubLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel");
                Class<?> removalReasonClass = Class.forName("dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
                Class<?> rigidBodyHandleClass = Class.forName("dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle");
                Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                Class<?> boxInterfaceClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
                Class<?> boxClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
                Class<?> entitySubLevelUtil = Class.forName("dev.ryanhcode.sable.api.entity.EntitySubLevelUtil");
                Class<?> entityMovementExtension = Class.forName("dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension");

                Field helperField = sable.getField("HELPER");
                Method getContaining = helperClass.getMethod("getContaining", net.minecraft.world.level.Level.class, net.minecraft.core.Vec3i.class);
                Method getTracking = helperClass.getMethod("getTrackingOrVehicleSubLevel", Entity.class);
                Method getContainer = containerClass.getMethod("getContainer", ServerLevel.class);
                Method getSubLevel = containerClass.getMethod("getSubLevel", UUID.class);
                Method removeSubLevel = containerClass.getMethod("removeSubLevel", subLevelClass, removalReasonClass);
                Object removalReasonRemoved = Enum.valueOf((Class<Enum>)removalReasonClass.asSubclass(Enum.class), "REMOVED");
                Method rigidBodyOf = rigidBodyHandleClass.getMethod("of", serverSubLevelClass);
                Method teleport = rigidBodyHandleClass.getMethod("teleport", Vector3dc.class, Quaterniondc.class);
                Method getLinearVelocity = rigidBodyHandleClass.getMethod("getLinearVelocity", Vector3d.class);
                Method getAngularVelocity = rigidBodyHandleClass.getMethod("getAngularVelocity", Vector3d.class);
                Method addLinearAndAngularVelocity = rigidBodyHandleClass.getMethod("addLinearAndAngularVelocity", Vector3dc.class, Vector3dc.class);
                Method logicalPose = subLevelClass.getMethod("logicalPose");
                Method orientation = poseClass.getMethod("orientation");
                Method boundingBox = subLevelClass.getMethod("boundingBox");
                Method toMojang = boxInterfaceClass.getMethod("toMojang");
                Method uuid = subLevelClass.getMethod("getUniqueId");
                Method name = subLevelClass.getMethod("getName");
                Method isRemoved = subLevelClass.getMethod("isRemoved");
                Method kickEntity = entitySubLevelUtil.getMethod("kickEntity", subLevelClass, Entity.class);
                Method setTrackingSubLevel = entityMovementExtension.getMethod("sable$setTrackingSubLevel", subLevelClass);
                Method setLastTrackingSubLevelID = entityMovementExtension.getMethod("sable$setLastTrackingSubLevelID", UUID.class);
                Method setPosField = entityMovementExtension.getMethod("sable$setPosField", Vec3.class);
                Method projectOutOfSubLevel = helperClass.getMethod("projectOutOfSubLevel", net.minecraft.world.level.Level.class, Vec3.class);
                Method getPlot = subLevelClass.getMethod("getPlot");
                Class<?> levelPlotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
                Method getCenterBlock = levelPlotClass.getMethod("getCenterBlock");
                Constructor<?> boundingBoxConstructor = boxClass.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);

                Method exportBlueprint = null;
                Method blueprintSave = null;
                Method blueprintLoad = null;
                Method blueprintIsEmpty = null;
                Method placeBlueprint = null;
                Method placedSubLevels = null;
                Method subLevelUuidMap = null;
                boolean snapshotAvailable = false;
                try {
                    Class<?> exporterClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter");
                    Class<?> blueprintClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprint");
                    Class<?> placerClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer");
                    Class<?> resultClass = Class.forName("dev.rew1nd.sableschematicapi.blueprint.SableBlueprintPlacer$Result");
                    exportBlueprint = exporterClass.getMethod("export", ServerLevel.class, Vec3.class, boxClass);
                    blueprintSave = blueprintClass.getMethod("save");
                    blueprintLoad = blueprintClass.getMethod("load", CompoundTag.class);
                    blueprintIsEmpty = blueprintClass.getMethod("isEmpty");
                    placeBlueprint = placerClass.getMethod("place", ServerLevel.class, blueprintClass, Vec3.class);
                    placedSubLevels = resultClass.getMethod("placedSubLevels");
                    subLevelUuidMap = resultClass.getMethod("subLevelUuidMap");
                    snapshotAvailable = true;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    snapshotAvailable = false;
                }

                return new Reflection(true, snapshotAvailable, helperField, getContaining, getTracking, getContainer, getSubLevel,
                        removeSubLevel, removalReasonRemoved, rigidBodyOf, teleport, getLinearVelocity, getAngularVelocity,
                        addLinearAndAngularVelocity, logicalPose, orientation, boundingBox, toMojang, uuid, name, isRemoved,
                        kickEntity, entityMovementExtension, setTrackingSubLevel, setLastTrackingSubLevelID, setPosField,
                        projectOutOfSubLevel, getPlot, getCenterBlock, boundingBoxConstructor, exportBlueprint, blueprintSave, blueprintLoad,
                        blueprintIsEmpty, placeBlueprint, placedSubLevels, subLevelUuidMap);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return new Reflection();
            }
        }

        boolean available() {
            return this.available;
        }

        boolean snapshotAvailable() {
            return this.available && this.snapshotAvailable;
        }

        Object helper() {
            try {
                return this.helperField.get(null);
            } catch (IllegalAccessException e) {
                return null;
            }
        }

        Object getContaining(ServerLevel level, BlockPos pos) {
            try {
                return this.getContaining.invoke(helper(), level, pos);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        Object getTrackingOrVehicleSubLevel(Entity entity) {
            try {
                return this.getTrackingOrVehicleSubLevel.invoke(helper(), entity);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        Object container(ServerLevel level) {
            try {
                return this.getContainer.invoke(null, level);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        Object getSubLevel(Object container, UUID uuid) {
            try {
                return container == null ? null : this.getSubLevel.invoke(container, uuid);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        CompoundTag exportBlueprint(ServerLevel level, Vec3 origin, AABB box) {
            if (!snapshotAvailable() || level == null || origin == null || box == null) {
                return null;
            }
            try {
                Object bounds = this.boundingBoxConstructor.newInstance(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                Object blueprint = this.exportBlueprint.invoke(null, level, origin, bounds);
                Object empty = this.blueprintIsEmpty.invoke(blueprint);
                if (Boolean.TRUE.equals(empty)) {
                    return null;
                }
                Object saved = this.blueprintSave.invoke(blueprint);
                return saved instanceof CompoundTag tag ? tag.copy() : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        Placement placeBlueprint(ServerLevel level, CompoundTag tag, Vec3 origin) {
            if (!snapshotAvailable() || level == null || tag == null || origin == null) {
                return null;
            }
            try {
                Object blueprint = this.blueprintLoad.invoke(null, tag.copy());
                Object result = this.placeBlueprint.invoke(null, level, blueprint, origin);
                Object placed = this.placedSubLevels.invoke(result);
                Object map = this.subLevelUuidMap.invoke(result);
                int placedCount = placed instanceof Number number ? number.intValue() : 0;
                Map<UUID, UUID> uuidMap = map instanceof Map<?, ?> raw ? (Map<UUID, UUID>)raw : Map.of();
                return new Placement(placedCount, uuidMap);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        boolean removeSubLevel(ServerLevel level, Object subLevel) {
            try {
                Object container = container(level);
                if (container == null || subLevel == null || this.removalReasonRemoved == null) {
                    return false;
                }
                this.removeSubLevel.invoke(container, subLevel, this.removalReasonRemoved);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        void stopMotion(Object subLevel) {
            try {
                Object handle = this.rigidBodyOf.invoke(null, subLevel);
                if (handle == null) {
                    return;
                }
                Vector3d linear = (Vector3d)this.getLinearVelocity.invoke(handle, new Vector3d());
                Vector3d angular = (Vector3d)this.getAngularVelocity.invoke(handle, new Vector3d());
                linear.negate();
                angular.negate();
                this.addLinearAndAngularVelocity.invoke(handle, linear, angular);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        void teleport(Object subLevel, Vec3 pos) {
            try {
                Object handle = this.rigidBodyOf.invoke(null, subLevel);
                Object pose = this.logicalPose.invoke(subLevel);
                Object rotation = this.orientation.invoke(pose);
                Quaterniondc orientation = rotation instanceof Quaterniondc quaternion ? quaternion : new Quaterniond();
                this.teleport.invoke(handle, new Vector3d(pos.x, pos.y, pos.z), orientation);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        AABB box(Object subLevel) {
            try {
                Object box = this.boundingBox.invoke(subLevel);
                Object mojang = this.toMojang.invoke(box);
                return mojang instanceof AABB aabb ? aabb : new AABB(0, 0, 0, 1, 1, 1);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new AABB(0, 0, 0, 1, 1, 1);
            }
        }

        BlockPos plotCenter(Object subLevel) {
            try {
                Object plot = subLevel == null ? null : this.getPlot.invoke(subLevel);
                Object center = plot == null ? null : this.getCenterBlock.invoke(plot);
                return center instanceof BlockPos blockPos ? blockPos : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        UUID uuid(Object subLevel) {
            try {
                Object uuid = subLevel == null ? null : this.uuid.invoke(subLevel);
                return uuid instanceof UUID value ? value : null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        String name(Object subLevel) {
            try {
                Object name = subLevel == null ? null : this.name.invoke(subLevel);
                return name instanceof String value ? value : "";
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "";
            }
        }

        boolean isRemoved(Object subLevel) {
            try {
                Object removed = subLevel == null ? Boolean.TRUE : this.isRemoved.invoke(subLevel);
                return Boolean.TRUE.equals(removed);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return true;
            }
        }

        void kickEntity(Object subLevel, Entity entity) {
            try {
                if (subLevel != null && entity != null) {
                    this.kickEntity.invoke(null, subLevel, entity);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        void detachPlayer(ServerPlayer player) {
            if (player == null) {
                return;
            }
            float yRot = player.getYRot();
            float xRot = player.getXRot();
            Vec3 pos = projectOutOfSubLevel(player.serverLevel(), player.position());
            clearTracking(player);
            player.stopRiding();
            player.teleportTo(pos.x, pos.y, pos.z);
            player.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yRotO = yRot;
            player.xRotO = xRot;
            player.xOld = pos.x;
            player.xo = pos.x;
            player.yOld = pos.y;
            player.yo = pos.y;
            player.zOld = pos.z;
            player.zo = pos.z;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            setPosField(player, pos);
        }

        void attachPlayer(ServerPlayer player, Object subLevel, Vec3 worldPoint) {
            try {
                if (player == null || subLevel == null || worldPoint == null
                        || this.entityMovementExtension == null || !this.entityMovementExtension.isInstance(player)) {
                    return;
                }
                UUID uuid = uuid(subLevel);
                this.setTrackingSubLevel.invoke(player, subLevel);
                this.setLastTrackingSubLevelID.invoke(player, uuid);
                setPosField(player, worldPoint);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        Vec3 projectOutOfSubLevel(ServerLevel level, Vec3 pos) {
            try {
                Object projected = this.projectOutOfSubLevel.invoke(helper(), level, pos);
                return projected instanceof Vec3 vec ? vec : pos;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return pos;
            }
        }

        void clearTracking(Entity entity) {
            try {
                if (entity != null && this.entityMovementExtension != null && this.entityMovementExtension.isInstance(entity)) {
                    this.setTrackingSubLevel.invoke(entity, new Object[]{null});
                    this.setLastTrackingSubLevelID.invoke(entity, new Object[]{null});
                    setPosField(entity, entity.position());
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        private void setPosField(Entity entity, Vec3 pos) {
            try {
                if (entity != null && pos != null && this.entityMovementExtension != null && this.entityMovementExtension.isInstance(entity)) {
                    this.setPosField.invoke(entity, pos);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
    }
}

