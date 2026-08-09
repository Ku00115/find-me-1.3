package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class PlayerCompanionWheelService {
    private PlayerCompanionWheelService() {
    }

    static List<UUID> wheelOrder(PlayerCompanionData data, CompanionKind kind) {
        ArrayList<UUID> order = new ArrayList<UUID>();
        List<UUID> list = data.companions.get(kind);
        for (UUID uuid : data.wheelSlots.get(kind)) {
            if (!list.contains(uuid) || order.contains(uuid)
                    || data.isRecovery(uuid)) continue;
            order.add(uuid);
        }
        if (!order.isEmpty()) {
            return List.copyOf(order);
        }
        for (UUID uuid : list) {
            if (order.contains(uuid) || data.isRecovery(uuid)) continue;
            order.add(uuid);
        }
        return List.copyOf(order);
    }

    static int activeWheelIndex(PlayerCompanionData data, CompanionKind kind) {
        Optional<UUID> active = data.active(kind);
        if (active.isEmpty()) {
            return -1;
        }
        return wheelOrder(data, kind).indexOf(active.get());
    }

    static Optional<UUID> wheelUuidAt(PlayerCompanionData data, CompanionKind kind, int index) {
        List<UUID> order = wheelOrder(data, kind);
        if (index < 0 || index >= order.size()) {
            return Optional.empty();
        }
        return Optional.of(order.get(index));
    }

    static boolean setWheelSlot(PlayerCompanionData data, CompanionKind kind, int sourceIndex, int slotIndex) {
        Optional<UUID> maybeUuid = wheelUuidAt(data, kind, sourceIndex);
        if (maybeUuid.isEmpty() || slotIndex < 0) {
            return false;
        }
        UUID uuid = maybeUuid.get();
        List<UUID> slots = data.wheelSlots.get(kind);
        slots.remove(uuid);
        int insertAt = Math.min(slotIndex, slots.size());
        slots.add(insertAt, uuid);
        return true;
    }

    static boolean reorder(PlayerCompanionData data, CompanionKind kind, int from, int to) {
        List<UUID> list = data.companions.get(kind);
        if (from < 0 || from >= list.size() || to < 0 || to >= list.size()) {
            return false;
        }
        UUID uuid = list.remove(from);
        list.add(to, uuid);
        data.activeIndexes.put(kind, to);
        return true;
    }
}

