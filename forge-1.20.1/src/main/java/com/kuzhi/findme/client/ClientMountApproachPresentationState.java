package com.kuzhi.findme.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;

/** Masks the first few client frames while the authoritative server tick commits a summon. */
public final class ClientMountApproachPresentationState {
    private static final Map<Integer, Integer> HIDDEN_TICKS = new HashMap<>();

    private ClientMountApproachPresentationState() {
    }

    public static void hide(int entityId, int ticks) {
        if (entityId >= 0 && ticks > 0) {
            HIDDEN_TICKS.put(entityId, Math.max(HIDDEN_TICKS.getOrDefault(entityId, 0), ticks));
        }
    }

    public static void show(int entityId) {
        if (entityId >= 0) {
            HIDDEN_TICKS.remove(entityId);
        }
    }

    static boolean hidden(Entity entity) {
        if (entity == null || HIDDEN_TICKS.getOrDefault(entity.getId(), 0) <= 0) {
            return false;
        }
        if (entity.getDeltaMovement().lengthSqr() > 1.0E-4) {
            HIDDEN_TICKS.remove(entity.getId());
            return false;
        }
        return true;
    }

    static void tick() {
        HIDDEN_TICKS.replaceAll((id, ticks) -> ticks - 1);
        HIDDEN_TICKS.values().removeIf(ticks -> ticks <= 0);
    }

    static void clear() {
        HIDDEN_TICKS.clear();
    }
}
