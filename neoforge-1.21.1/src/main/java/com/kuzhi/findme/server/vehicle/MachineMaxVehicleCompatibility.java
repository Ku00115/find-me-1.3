package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.mojang.serialization.Codec;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Optional whole-vehicle integration for MachineMax. */
public final class MachineMaxVehicleCompatibility {
    public static final String TYPE = "simulated:machine_max_vehicle";
    static final String MARKER = "FindMeMachineMaxVehicle";
    private static final String FORMAT = "FindMeMachineMaxFormat";
    private static final String FORMAT_VEHICLE_DATA_V1 = "machine_max_vehicle_data_v1";
    private static final String VEHICLE_DATA = "FindMeMachineMaxVehicleData";

    private static final Reflection REFLECTION = Reflection.load();

    private MachineMaxVehicleCompatibility() {
    }

    public static void bootstrap() {
        FindMeMod.LOGGER.info("FindMe MachineMax compatibility: loaded={}, apiAvailable={}",
                ModList.get().isLoaded("machine_max"), REFLECTION.available());
    }

    public static boolean available() {
        return REFLECTION.available();
    }

    public static boolean isPartEntity(Entity entity) {
        return REFLECTION.isPartEntity(entity);
    }

    public static boolean isStoredMachineMax(CompoundTag tag) {
        return tag != null && tag.getBoolean(MARKER);
    }

    public static boolean isStored(PlayerCompanionData data, UUID uuid) {
        return data != null && uuid != null
                && data.storedEntity(uuid).map(MachineMaxVehicleCompatibility::isStoredMachineMax).orElse(false);
    }

    public static boolean canRestore(PlayerCompanionData data, UUID uuid) {
        CompoundTag tag = data == null || uuid == null ? null : data.storedEntity(uuid).orElse(null);
        return available() && isStoredMachineMax(tag) && tag.contains(VEHICLE_DATA);
    }

    public static Optional<Handle> findForEntity(Entity entity) {
        return wrap(REFLECTION.vehicleForEntity(entity));
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
        return level == null || uuid == null ? Optional.empty() : wrap(REFLECTION.getVehicle(level, uuid));
    }

    public static Optional<Handle> currentRide(ServerPlayer player) {
        return player == null ? Optional.empty() : findForEntity(player.getVehicle());
    }

    public static boolean store(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (isStored(data, uuid)) {
            return true;
        }
        return find(player, uuid).map(handle -> store(player, data, handle)).orElse(false);
    }

    public static boolean store(ServerPlayer player, PlayerCompanionData data, Handle handle) {
        if (!available() || player == null || data == null || handle == null || !handle.valid()) {
            return false;
        }
        UUID uuid = handle.uuid();
        if (!CompanionOperationLockService.tryBegin(player, uuid,
                CompanionOperationLockService.Operation.STORE, "machine_max:store")) {
            return false;
        }
        try {
            Tag encoded = REFLECTION.encodeVehicle(handle.vehicle);
            AABB box = handle.box();
            if (encoded == null || box == null || !reasonableBox(box)) {
                FindMeMod.LOGGER.warn("FindMe MachineMax storage refused: invalid vehicle data or bounds for {}", uuid);
                return false;
            }

            CompoundTag previous = data.storedEntity(uuid).map(CompoundTag::copy).orElse(null);
            CompoundTag tag = storedTag(player, handle, box, encoded);
            data.storeEntity(uuid, tag);
            data.setDisplayName(uuid, handle.name());
            Vec3 center = box.getCenter();
            data.setLastKnownPosition(uuid, SavedPosition.of(handle.level(), center.x, center.y, center.z, 0.0f, 0.0f));
            CompanionDataService.save(player, data);

            REFLECTION.detachPassenger(handle.vehicle, player);
            REFLECTION.removeVehicle(handle.vehicle);
            if (REFLECTION.getVehicle(handle.level(), uuid) != null) {
                if (previous == null) {
                    data.removeStoredEntity(uuid);
                } else {
                    data.storeEntity(uuid, previous);
                }
                CompanionDataService.save(player, data);
                FindMeMod.LOGGER.error("FindMe MachineMax storage failed: ObjectManager still owns vehicle {}", uuid);
                return false;
            }

            FindMeDebugLogger.lifecycle("MACHINE_MAX_VEHICLE_STORED", player, uuid, null,
                    "ACTIVE", "STORED", "machine_max:store", true, false);
            return true;
        } catch (RuntimeException exception) {
            FindMeMod.LOGGER.error("FindMe MachineMax storage failed for {}", handle.uuid(), exception);
            return false;
        } finally {
            CompanionOperationLockService.end(player, uuid,
                    CompanionOperationLockService.Operation.STORE, "machine_max:store");
        }
    }

    public static Optional<Handle> restore(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
        if (!canRestore(data, uuid) || player == null || pos == null) {
            return Optional.empty();
        }
        if (!CompanionOperationLockService.tryBegin(player, uuid,
                CompanionOperationLockService.Operation.DEPLOY, "machine_max:restore")) {
            return Optional.empty();
        }
        Object vehicle = null;
        try {
            CompoundTag tag = data.storedEntity(uuid).orElse(null);
            if (tag == null) {
                return Optional.empty();
            }
            Object vehicleData = REFLECTION.decodeVehicle(tag.get(VEHICLE_DATA));
            if (vehicleData == null) {
                FindMeMod.LOGGER.warn("FindMe MachineMax restore refused: vehicle data could not be decoded for {}", uuid);
                return Optional.empty();
            }

            vehicleData = REFLECTION.withUuid(vehicleData, uuid);
            vehicle = REFLECTION.createVehicle(player.serverLevel(), vehicleData);
            if (vehicle == null) {
                return Optional.empty();
            }
            REFLECTION.setPosition(vehicle, Vec3.atBottomCenterOf(pos));
            REFLECTION.addVehicle(vehicle);
            Handle restored = wrap(vehicle).orElse(null);
            if (restored == null || REFLECTION.getVehicle(player.serverLevel(), uuid) == null) {
                REFLECTION.removeVehicle(vehicle);
                return Optional.empty();
            }

            data.removeStoredEntity(uuid);
            AABB box = restored.box();
            Vec3 center = box == null ? Vec3.atBottomCenterOf(pos) : box.getCenter();
            data.setLastKnownPosition(uuid, SavedPosition.of(player.serverLevel(), center.x, center.y, center.z, 0.0f, 0.0f));
            CompanionDataService.save(player, data);
            FindMeDebugLogger.lifecycle("MACHINE_MAX_VEHICLE_RESTORED", player, uuid, restored.anchorEntity().orElse(null),
                    "STORED", "ACTIVE", "machine_max:restore", false, true);
            return Optional.of(restored);
        } catch (RuntimeException exception) {
            FindMeMod.LOGGER.error("FindMe MachineMax restore failed for {}", uuid, exception);
            if (vehicle != null && REFLECTION.getVehicle(player.serverLevel(), uuid) == vehicle) {
                REFLECTION.removeVehicle(vehicle);
            }
            return Optional.empty();
        } finally {
            CompanionOperationLockService.end(player, uuid,
                    CompanionOperationLockService.Operation.DEPLOY, "machine_max:restore");
        }
    }

    public static boolean move(Handle handle, ServerLevel level, BlockPos pos) {
        if (handle == null || !handle.valid() || level == null || pos == null || handle.level() != level) {
            return false;
        }
        REFLECTION.setPosition(handle.vehicle, Vec3.atBottomCenterOf(pos));
        return true;
    }

    public static boolean tryBoard(ServerPlayer player, Handle handle) {
        if (player == null || handle == null || !handle.valid()) {
            return false;
        }
        boolean result = REFLECTION.tryBoard(handle.vehicle, player);
        boolean ridingVehicle = currentRide(player).map(current -> current.uuid().equals(handle.uuid())).orElse(false);
        FindMeMod.LOGGER.info("FindMe MachineMax seat handshake: player={}, vehicle={}, invoked={}, riding={}",
                player.getGameProfile().getName(), handle.uuid(), result, ridingVehicle);
        return result && ridingVehicle;
    }

    public static void release(Handle handle) {
        // A restored MachineMax vehicle remains owned and persisted by MachineMax.
    }

    private static Optional<Handle> wrap(Object vehicle) {
        if (vehicle == null || !REFLECTION.isVehicle(vehicle)) {
            return Optional.empty();
        }
        Handle handle = new Handle(vehicle);
        return handle.valid() ? Optional.of(handle) : Optional.empty();
    }

    private static CompoundTag storedTag(ServerPlayer player, Handle handle, AABB box, Tag vehicleData) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        tag.putString(FORMAT, FORMAT_VEHICLE_DATA_V1);
        tag.putString("id", TYPE);
        tag.putString("CompanionRescueType", TYPE);
        tag.putString("CompanionRescueName", handle.name());
        tag.putUUID("UUID", handle.uuid());
        tag.putFloat("CompanionPreviewWidth", (float)Math.max(1.0, box.getXsize()));
        tag.putFloat("CompanionPreviewHeight", (float)Math.max(1.0, box.getYsize()));
        tag.putFloat("CompanionPreviewDepth", (float)Math.max(1.0, box.getZsize()));
        tag.putFloat("CompanionEffectWidth", (float)Math.max(0.05, box.getXsize()));
        tag.putFloat("CompanionEffectHeight", (float)Math.max(0.05, box.getYsize()));
        tag.putFloat("CompanionEffectDepth", (float)Math.max(0.05, box.getZsize()));
        tag.putLong("CompanionRescueStoredAt", player.serverLevel().getGameTime());
        tag.put(VEHICLE_DATA, vehicleData.copy());
        return tag;
    }

    private static boolean reasonableBox(AABB box) {
        return box != null
                && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.getXsize() > 0.0 && box.getYsize() > 0.0 && box.getZsize() > 0.0
                && box.getXsize() <= 256.0 && box.getYsize() <= 256.0 && box.getZsize() <= 256.0;
    }

    public static final class Handle {
        private final Object vehicle;

        private Handle(Object vehicle) {
            this.vehicle = vehicle;
        }

        public UUID uuid() {
            return REFLECTION.uuid(vehicle);
        }

        public String name() {
            String name = REFLECTION.name(vehicle);
            return name == null || name.isBlank() ? "MachineMax Vehicle" : name;
        }

        public ServerLevel level() {
            Level level = REFLECTION.level(vehicle);
            return level instanceof ServerLevel serverLevel ? serverLevel : null;
        }

        public AABB box() {
            return REFLECTION.box(vehicle);
        }

        public Optional<Entity> anchorEntity() {
            return Optional.ofNullable(REFLECTION.anchorEntity(vehicle));
        }

        public boolean valid() {
            return uuid() != null && level() != null && !REFLECTION.removed(vehicle);
        }
    }

    private static final class Reflection {
        private final Class<?> partEntityClass;
        private final Class<?> vehicleClass;
        private final Class<?> vehicleDataClass;
        private final Class<?> seatClass;
        private final Field partEntitySubPart;
        private final Field vehicleCodecField;
        private final Constructor<?> vehicleDataConstructor;
        private final Constructor<?> vehicleConstructor;
        private final Method subPartGetPart;
        private final Method subPartGetEntity;
        private final Method partGetVehicle;
        private final Method partGetSubParts;
        private final Method partGetAllSubsystems;
        private final Method vehicleGetUuid;
        private final Method vehicleGetName;
        private final Method vehicleGetLevel;
        private final Method vehicleGetAabb;
        private final Method vehicleGetPartMap;
        private final Method vehicleIsRemoved;
        private final Method vehicleSetPos;
        private final Method vehicleDataWithUuid;
        private final Method objectGetVehicle;
        private final Method objectAddVehicle;
        private final Method objectRemoveVehicle;
        private final Method seatIsActive;
        private final Method seatIsOccupied;
        private final Method seatGetPassenger;
        private final Method seatGetTargetNames;
        private final Method seatSetPassenger;
        private final Method seatRemovePassenger;

        private Reflection(Class<?> partEntityClass, Class<?> vehicleClass, Class<?> vehicleDataClass,
                           Class<?> seatClass, Field partEntitySubPart, Field vehicleCodecField,
                           Constructor<?> vehicleDataConstructor, Constructor<?> vehicleConstructor,
                           Method subPartGetPart, Method subPartGetEntity, Method partGetVehicle,
                           Method partGetSubParts, Method partGetAllSubsystems, Method vehicleGetUuid,
                           Method vehicleGetName, Method vehicleGetLevel, Method vehicleGetAabb,
                           Method vehicleGetPartMap, Method vehicleIsRemoved, Method vehicleSetPos,
                           Method vehicleDataWithUuid, Method objectGetVehicle, Method objectAddVehicle,
                           Method objectRemoveVehicle, Method seatIsActive, Method seatIsOccupied,
                           Method seatGetPassenger, Method seatGetTargetNames, Method seatSetPassenger,
                           Method seatRemovePassenger) {
            this.partEntityClass = partEntityClass;
            this.vehicleClass = vehicleClass;
            this.vehicleDataClass = vehicleDataClass;
            this.seatClass = seatClass;
            this.partEntitySubPart = partEntitySubPart;
            this.vehicleCodecField = vehicleCodecField;
            this.vehicleDataConstructor = vehicleDataConstructor;
            this.vehicleConstructor = vehicleConstructor;
            this.subPartGetPart = subPartGetPart;
            this.subPartGetEntity = subPartGetEntity;
            this.partGetVehicle = partGetVehicle;
            this.partGetSubParts = partGetSubParts;
            this.partGetAllSubsystems = partGetAllSubsystems;
            this.vehicleGetUuid = vehicleGetUuid;
            this.vehicleGetName = vehicleGetName;
            this.vehicleGetLevel = vehicleGetLevel;
            this.vehicleGetAabb = vehicleGetAabb;
            this.vehicleGetPartMap = vehicleGetPartMap;
            this.vehicleIsRemoved = vehicleIsRemoved;
            this.vehicleSetPos = vehicleSetPos;
            this.vehicleDataWithUuid = vehicleDataWithUuid;
            this.objectGetVehicle = objectGetVehicle;
            this.objectAddVehicle = objectAddVehicle;
            this.objectRemoveVehicle = objectRemoveVehicle;
            this.seatIsActive = seatIsActive;
            this.seatIsOccupied = seatIsOccupied;
            this.seatGetPassenger = seatGetPassenger;
            this.seatGetTargetNames = seatGetTargetNames;
            this.seatSetPassenger = seatSetPassenger;
            this.seatRemovePassenger = seatRemovePassenger;
        }

        static Reflection load() {
            if (!ModList.get().isLoaded("machine_max")) {
                return unavailable();
            }
            try {
                Class<?> partEntity = Class.forName("io.github.sweetzonzi.machine_max.common.entity.MMPartEntity");
                Class<?> vehicle = Class.forName("io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore");
                Class<?> vehicleData = Class.forName("io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData");
                Class<?> objectManager = Class.forName("io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager");
                Class<?> part = Class.forName("io.github.sweetzonzi.machine_max.common.vehicle.Part");
                Class<?> subPart = Class.forName("io.github.sweetzonzi.machine_max.common.vehicle.SubPart");
                Class<?> seat = Class.forName("io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem");
                return new Reflection(
                        partEntity, vehicle, vehicleData, seat,
                        partEntity.getField("subPart"), vehicleData.getField("CODEC"),
                        vehicleData.getConstructor(vehicle), vehicle.getConstructor(Level.class, vehicleData, boolean.class),
                        subPart.getMethod("getPart"), subPart.getMethod("getEntity"),
                        part.getMethod("getVehicle"), part.getMethod("getSubParts"), part.getMethod("getAllSubsystems"),
                        vehicle.getMethod("getUuid"), vehicle.getMethod("getName"), vehicle.getMethod("getLevel"),
                        vehicle.getMethod("getAABB"), vehicle.getMethod("getPartMap"), vehicle.getMethod("isRemoved"),
                        vehicle.getMethod("setPos", Vec3.class), vehicleData.getMethod("withNewUUID", UUID.class),
                        objectManager.getMethod("getVehicle", Level.class, UUID.class),
                        objectManager.getMethod("addVehicle", vehicle), objectManager.getMethod("removeVehicle", vehicle),
                        seat.getMethod("isActive"), seat.getMethod("isOccupied"), seat.getMethod("getPassenger"),
                        seat.getMethod("getTargetNames"), seat.getMethod("setPassenger", LivingEntity.class),
                        seat.getMethod("removePassenger"));
            } catch (ReflectiveOperationException | LinkageError exception) {
                FindMeMod.LOGGER.warn("FindMe MachineMax compatibility disabled: unsupported API", exception);
                return unavailable();
            }
        }

        static Reflection unavailable() {
            return new Reflection(null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);
        }

        boolean available() {
            return vehicleClass != null;
        }

        boolean isPartEntity(Entity entity) {
            return available() && entity != null && partEntityClass.isInstance(entity);
        }

        boolean isVehicle(Object vehicle) {
            return available() && vehicle != null && vehicleClass.isInstance(vehicle);
        }

        Object vehicleForEntity(Entity entity) {
            if (!isPartEntity(entity)) {
                return null;
            }
            try {
                Object subPart = partEntitySubPart.get(entity);
                Object part = subPart == null ? null : subPartGetPart.invoke(subPart);
                return part == null ? null : partGetVehicle.invoke(part);
            } catch (ReflectiveOperationException exception) {
                FindMeMod.LOGGER.warn("FindMe could not resolve MachineMax vehicle from part entity {}", entity.getUUID(), exception);
                return null;
            }
        }

        Object getVehicle(Level level, UUID uuid) {
            if (!available() || level == null || uuid == null) {
                return null;
            }
            try {
                return objectGetVehicle.invoke(null, level, uuid);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        UUID uuid(Object vehicle) {
            Object value = invoke(vehicleGetUuid, vehicle);
            return value instanceof UUID uuid ? uuid : null;
        }

        String name(Object vehicle) {
            Object value = invoke(vehicleGetName, vehicle);
            return value instanceof String name ? name : null;
        }

        Level level(Object vehicle) {
            Object value = invoke(vehicleGetLevel, vehicle);
            return value instanceof Level level ? level : null;
        }

        AABB box(Object vehicle) {
            Object value = invoke(vehicleGetAabb, vehicle);
            return value instanceof AABB box ? box : null;
        }

        boolean removed(Object vehicle) {
            return Boolean.TRUE.equals(invoke(vehicleIsRemoved, vehicle));
        }

        @SuppressWarnings("unchecked")
        Tag encodeVehicle(Object vehicle) {
            try {
                Object vehicleData = vehicleDataConstructor.newInstance(vehicle);
                Codec<Object> codec = (Codec<Object>)vehicleCodecField.get(null);
                return codec.encodeStart(NbtOps.INSTANCE, vehicleData)
                        .resultOrPartial(message -> FindMeMod.LOGGER.error("MachineMax vehicle encode error: {}", message))
                        .orElse(null);
            } catch (ReflectiveOperationException exception) {
                FindMeMod.LOGGER.error("FindMe could not encode MachineMax vehicle", exception);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        Object decodeVehicle(Tag tag) {
            if (tag == null) {
                return null;
            }
            try {
                Codec<Object> codec = (Codec<Object>)vehicleCodecField.get(null);
                return codec.parse(NbtOps.INSTANCE, tag)
                        .resultOrPartial(message -> FindMeMod.LOGGER.error("MachineMax vehicle decode error: {}", message))
                        .orElse(null);
            } catch (IllegalAccessException exception) {
                return null;
            }
        }

        Object withUuid(Object vehicleData, UUID uuid) {
            return invoke(vehicleDataWithUuid, vehicleData, uuid);
        }

        Object createVehicle(ServerLevel level, Object vehicleData) {
            try {
                return vehicleConstructor.newInstance(level, vehicleData, true);
            } catch (ReflectiveOperationException exception) {
                FindMeMod.LOGGER.error("FindMe could not construct MachineMax vehicle", exception);
                return null;
            }
        }

        void setPosition(Object vehicle, Vec3 pos) {
            invoke(vehicleSetPos, vehicle, pos);
        }

        void addVehicle(Object vehicle) {
            invoke(objectAddVehicle, null, vehicle);
        }

        void removeVehicle(Object vehicle) {
            if (vehicle != null && isVehicle(vehicle) && !removed(vehicle)) {
                invoke(objectRemoveVehicle, null, vehicle);
            }
        }

        Entity anchorEntity(Object vehicle) {
            for (Object part : parts(vehicle)) {
                Object subParts = invoke(partGetSubParts, part);
                if (!(subParts instanceof Map<?, ?> map)) {
                    continue;
                }
                for (Object subPart : map.values()) {
                    Object entity = invoke(subPartGetEntity, subPart);
                    if (entity instanceof Entity anchor && !anchor.isRemoved()) {
                        return anchor;
                    }
                }
            }
            return null;
        }

        void detachPassenger(Object vehicle, LivingEntity passenger) {
            for (Object seat : seats(vehicle)) {
                if (invoke(seatGetPassenger, seat) == passenger) {
                    invoke(seatRemovePassenger, seat);
                }
            }
            if (passenger.getVehicle() != null && findVehicleForSeat(passenger.getVehicle()) == vehicle) {
                passenger.stopRiding();
            }
        }

        boolean tryBoard(Object vehicle, LivingEntity passenger) {
            List<Object> seats = seats(vehicle);
            seats.sort(Comparator.comparingInt(seat -> -seatScore(seat, passenger)));
            for (Object seat : seats) {
                Object currentPassenger = invoke(seatGetPassenger, seat);
                boolean occupied = Boolean.TRUE.equals(invoke(seatIsOccupied, seat));
                boolean active = Boolean.TRUE.equals(invoke(seatIsActive, seat));
                if (!active || occupied && currentPassenger != passenger) {
                    continue;
                }
                invoke(seatSetPassenger, seat, passenger);
                if (invoke(seatGetPassenger, seat) == passenger
                        && findVehicleForSeat(passenger.getVehicle()) == vehicle) {
                    return true;
                }
            }
            return false;
        }

        private int seatScore(Object seat, LivingEntity passenger) {
            int score = invoke(seatGetPassenger, seat) == passenger ? 100 : 0;
            Object targets = invoke(seatGetTargetNames, seat);
            if (targets instanceof Map<?, ?> map && !map.isEmpty()) {
                score += 10;
            }
            return score;
        }

        private Object findVehicleForSeat(Entity entity) {
            return vehicleForEntity(entity);
        }

        private List<Object> seats(Object vehicle) {
            List<Object> seats = new ArrayList<>();
            for (Object part : parts(vehicle)) {
                Object subsystems = invoke(partGetAllSubsystems, part);
                if (!(subsystems instanceof Collection<?> collection)) {
                    continue;
                }
                for (Object subsystem : collection) {
                    if (seatClass.isInstance(subsystem)) {
                        seats.add(subsystem);
                    }
                }
            }
            return seats;
        }

        private Collection<?> parts(Object vehicle) {
            Object value = invoke(vehicleGetPartMap, vehicle);
            return value instanceof Map<?, ?> map ? map.values() : List.of();
        }

        private Object invoke(Method method, Object owner, Object... args) {
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(owner, args);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FindMeMod.LOGGER.debug("FindMe MachineMax reflective call failed: {}", method.getName(), exception);
                return null;
            }
        }
    }
}
