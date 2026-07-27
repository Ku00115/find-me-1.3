package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionMoveType;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Bounded client-only state used to bridge movement between optional ride systems. */
public final class ClientExternalRideHandoffState {
    private static final int MAX_AGE_TICKS = 40;
    private static Snapshot pending;
    private static int age;

    private ClientExternalRideHandoffState() {
    }

    public static void queue(UUID targetUuid, CompanionMoveType sourceMoveType, CompanionMoveType targetMoveType,
                             Vec3 velocity,
                             float yRot, float xRot) {
        pending = new Snapshot(targetUuid, sourceMoveType, targetMoveType,
                velocity == null ? Vec3.ZERO : velocity, yRot, xRot);
        age = 0;
    }

    public static Snapshot consume(UUID targetUuid) {
        if (pending == null || targetUuid == null || !pending.targetUuid().equals(targetUuid)) {
            return null;
        }
        Snapshot result = pending;
        clear();
        return result;
    }

    public static void tick() {
        if (Minecraft.getInstance().player == null) {
            clear();
            return;
        }
        if (pending != null && ++age > MAX_AGE_TICKS) {
            clear();
        }
    }

    public static void clear() {
        pending = null;
        age = 0;
    }

    public record Snapshot(UUID targetUuid, CompanionMoveType sourceMoveType, CompanionMoveType targetMoveType,
                           Vec3 velocity,
                           float yRot, float xRot) {
    }
}
