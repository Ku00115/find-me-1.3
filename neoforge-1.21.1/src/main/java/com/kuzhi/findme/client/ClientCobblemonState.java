package com.kuzhi.findme.client;

import com.kuzhi.findme.network.CobblemonPartyPacket;
import java.util.ArrayList;
import java.util.List;

public final class ClientCobblemonState {
    private static List<CobblemonPartyPacket.Entry> entries = List.of();
    private static int activeSlot = -1;
    private static long revision;
    private static long serverRevision = -1L;

    private ClientCobblemonState() {
    }

    public static void update(long incomingServerRevision, int selectedSlot,
                              List<CobblemonPartyPacket.Entry> incoming) {
        if (incomingServerRevision < serverRevision) {
            return;
        }
        List<CobblemonPartyPacket.Entry> merged = new ArrayList<>(incoming.size());
        for (CobblemonPartyPacket.Entry entry : incoming) {
            CobblemonPartyPacket.Entry old = entries.stream()
                    .filter(candidate -> candidate.uuid().equals(entry.uuid()))
                    .findFirst()
                    .orElse(null);
            merged.add(entry.previewTag() == null && old != null
                    ? new CobblemonPartyPacket.Entry(entry.slot(), entry.uuid(), entry.entityId(), entry.name(), entry.deployed(), entry.ridden(), entry.fainted(), entry.rideable(), entry.moveType(), entry.health(), entry.maxHealth(), old.previewTag())
                    : entry);
        }
        entries = List.copyOf(merged);
        activeSlot = selectedSlot;
        serverRevision = incomingServerRevision;
        entries.stream().filter(entry -> entry.slot() == selectedSlot).findFirst().ifPresent(entry ->
                ClientCompanionWheelController.acknowledgeSwitch(
                        com.kuzhi.findme.common.CompanionKind.MOUNT, entry.uuid(), true));
        ClientCompanionWheelController.observeRoster(com.kuzhi.findme.common.CompanionKind.MOUNT,
                entries.stream().map(CobblemonPartyPacket.Entry::asPreviewEntry).toList());
        revision++;
    }

    public static List<CobblemonPartyPacket.Entry> entries() {
        return entries;
    }

    public static int activeEntryIndex() {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).slot() == activeSlot) {
                return i;
            }
        }
        return entries.isEmpty() ? -1 : 0;
    }

    public static int activeSlot() {
        return activeSlot;
    }

    public static long revision() {
        return revision;
    }

    public static long serverRevision() {
        return serverRevision;
    }

    public static void selectSlot(int slot) {
        if (entries.stream().anyMatch(entry -> entry.slot() == slot)) {
            activeSlot = slot;
        }
    }

    public static boolean shouldLockCamera(int slot) {
        for (CobblemonPartyPacket.Entry entry : entries) {
            if (entry.slot() == slot) {
                return entry.rideable() && !entry.deployed() && !entry.ridden();
            }
        }
        return false;
    }

    public static void reset() {
        entries = List.of();
        activeSlot = -1;
        serverRevision = -1L;
        revision++;
    }
}
