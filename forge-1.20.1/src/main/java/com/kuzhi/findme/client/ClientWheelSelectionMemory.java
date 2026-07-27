package com.kuzhi.findme.client;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** Session-local wheel focus. Gameplay selection remains server-authoritative. */
final class ClientWheelSelectionMemory {
    private static final EnumMap<Wheel, State> STATES = new EnumMap<>(Wheel.class);

    private ClientWheelSelectionMemory() {
    }

    static int page(Wheel wheel) {
        return STATES.getOrDefault(wheel, State.EMPTY).page;
    }

    static int resolve(Wheel wheel, List<UUID> visibleOrder) {
        if (visibleOrder.isEmpty()) {
            return -1;
        }
        State state = STATES.getOrDefault(wheel, State.EMPTY);
        if (state.uuid != null) {
            int identityIndex = visibleOrder.indexOf(state.uuid);
            if (identityIndex >= 0) {
                return identityIndex;
            }
        }
        return Math.max(0, Math.min(state.fallbackIndex, visibleOrder.size() - 1));
    }

    static void remember(Wheel wheel, UUID uuid, int index, int page) {
        if (index < 0) {
            rememberPage(wheel, page);
            return;
        }
        STATES.put(wheel, new State(uuid, index, Math.max(0, page)));
    }

    static void rememberPage(Wheel wheel, int page) {
        State previous = STATES.getOrDefault(wheel, State.EMPTY);
        STATES.put(wheel, new State(previous.uuid, previous.fallbackIndex, Math.max(0, page)));
    }

    static void reset() {
        STATES.clear();
    }

    enum Wheel {
        MOUNT,
        COMPANION,
        VEHICLE
    }

    private record State(UUID uuid, int fallbackIndex, int page) {
        private static final State EMPTY = new State(null, 0, 0);
    }
}
