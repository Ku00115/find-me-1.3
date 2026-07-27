package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Read-only tombstone for unsupported 1.21-only vehicle records. */
final class SableVehicleCompatibility {
    static final String TYPE = "simulated:sable_sublevel";

    private SableVehicleCompatibility() {
    }

    static void bootstrap() {}
    static boolean available() { return false; }
    static boolean snapshotAvailable() { return false; }
    static boolean isStoredSable(CompoundTag tag) { return false; }
    static boolean isStored(PlayerCompanionData data, UUID uuid) { return false; }
    static boolean canRestore(PlayerCompanionData data, UUID uuid) { return false; }
    static Optional<Handle> findAt(ServerLevel level, BlockPos pos) { return Optional.empty(); }
    static Vec3 projectOut(ServerLevel level, Vec3 pos) { return pos; }
    static Optional<Vec3> stableSeatOffset(Handle handle, Vec3 plotPoint) { return Optional.empty(); }
    static Optional<Vec3> projectStableSeat(ServerLevel level, Handle handle, Vec3 offset) { return Optional.empty(); }
    static void attachPlayerToProjectedPoint(ServerPlayer player, Handle handle, Vec3 point) {}
    static boolean isPlayerTracking(ServerPlayer player, Handle handle) { return false; }
    static Optional<Handle> findForEntity(Entity entity) { return Optional.empty(); }
    static Optional<Handle> find(ServerPlayer player, UUID uuid) { return Optional.empty(); }
    static Optional<Handle> find(MinecraftServer server, UUID uuid) { return Optional.empty(); }
    static Optional<Handle> find(ServerLevel level, UUID uuid) { return Optional.empty(); }
    static boolean store(ServerPlayer player, PlayerCompanionData data, UUID uuid) { return false; }
    static boolean store(ServerPlayer player, PlayerCompanionData data, Handle handle) { return false; }
    static Optional<Handle> restore(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) { return Optional.empty(); }
    static boolean move(Handle handle, ServerLevel level, BlockPos pos) { return false; }
    static void pushPlayerOut(ServerPlayer player, Handle handle) {}
    static void release(Handle handle) {}

    static final class Handle {
        UUID uuid() { return new UUID(0L, 0L); }
        String name() { return ""; }
        AABB box() { return new AABB(0, 0, 0, 0, 0, 0); }
        float radius() { return 0.0f; }
        float height() { return 0.0f; }
    }
}
