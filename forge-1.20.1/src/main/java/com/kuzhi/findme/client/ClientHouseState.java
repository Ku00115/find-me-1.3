package com.kuzhi.findme.client;

import com.kuzhi.findme.network.HousePagePacket;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client cache for the currently opened house page, including visitors. */
public final class ClientHouseState {
    private static final Map<UUID, HousePagePacket.Resident> RESIDENTS = new LinkedHashMap<>();
    private static final Set<UUID> CURRENT_HOUSE_RESIDENTS = new HashSet<>();

    private ClientHouseState() {
    }

    public static void update(HousePagePacket page) {
        RESIDENTS.clear();
        CURRENT_HOUSE_RESIDENTS.clear();
        if (page == null) return;
        page.residents().forEach(resident -> {
            RESIDENTS.put(resident.uuid(), resident);
            CURRENT_HOUSE_RESIDENTS.add(resident.uuid());
        });
        page.available().forEach(resident -> RESIDENTS.putIfAbsent(resident.uuid(), resident));
    }

    public static HousePagePacket.Resident find(UUID uuid) {
        return uuid == null ? null : RESIDENTS.get(uuid);
    }

    public static boolean isCurrentHouseResident(UUID uuid) {
        return uuid != null && CURRENT_HOUSE_RESIDENTS.contains(uuid);
    }
}
