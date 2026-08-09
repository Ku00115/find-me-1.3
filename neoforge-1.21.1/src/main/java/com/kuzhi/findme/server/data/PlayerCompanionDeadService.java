package com.kuzhi.findme.server.data;

import com.kuzhi.findme.common.CompanionKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class PlayerCompanionDeadService {
    private PlayerCompanionDeadService() {
    }

    static boolean markDead(PlayerCompanionData data, UUID uuid, CompanionKind kind, int vaultLimit) {
        int previousMagicContributors = data.companionCreatureCount();
        data.vaultSnapshot(uuid, kind, "dead", vaultLimit);
        boolean wasKnown = false;
        for (CompanionKind liveKind : CompanionKind.values()) {
            wasKnown |= data.companions.get(liveKind).remove(uuid);
            data.wheelSlots.get(liveKind).remove(uuid);
            data.clearDeployed(liveKind, uuid);
            if (data.previous.get(liveKind) != null && data.previous.get(liveKind).equals(uuid)) {
                data.previous.remove(liveKind);
            }
        }
        if (!wasKnown && !data.deadCompanions.contains(uuid)) {
            return false;
        }
        if (!data.deadCompanions.contains(uuid)) {
            data.deadCompanions.add(uuid);
        }
        data.deadKinds.put(uuid, kind);
        data.removeTeamMember(uuid);
        data.mountEligible.remove(uuid);
        data.vehicleMounts.remove(uuid);
        data.reconcileCompanionMagicContributors(previousMagicContributors);
        data.markLifecycleChanged(uuid);
        return true;
    }

    static List<UUID> deadList(PlayerCompanionData data) {
        return List.copyOf(data.deadCompanions);
    }

    static Optional<UUID> deadUuidAt(PlayerCompanionData data, int index) {
        if (index < 0 || index >= data.deadCompanions.size()) {
            return Optional.empty();
        }
        return Optional.of(data.deadCompanions.get(index));
    }

    static boolean removeDeadAt(PlayerCompanionData data, int index, int vaultLimit) {
        Optional<UUID> uuid = deadUuidAt(data, index);
        if (uuid.isEmpty()) {
            return false;
        }
        UUID deadUuid = uuid.get();
        data.markLifecycleChanged(deadUuid);
        data.vaultSnapshot(deadUuid, data.deadKinds.getOrDefault(deadUuid, CompanionKind.COMPANION), "dead_remove", vaultLimit);
        data.deadCompanions.remove(index);
        data.deadKinds.remove(deadUuid);
        data.deadRecords.remove(deadUuid);
        data.origins.remove(deadUuid);
        data.lastKnownPositions.remove(deadUuid);
        data.storedEntities.remove(deadUuid);
        data.displayNames.remove(deadUuid);
        data.mountEligible.remove(deadUuid);
        data.vehicleMounts.remove(deadUuid);
        data.queueSpellItemReturns(deadUuid);
        return true;
    }

    static void purgeDeadFromLiveLists(PlayerCompanionData data) {
        for (UUID uuid : data.deadCompanions) {
            for (CompanionKind kind : CompanionKind.values()) {
                data.companions.get(kind).remove(uuid);
                data.wheelSlots.get(kind).remove(uuid);
                data.clearDeployed(kind, uuid);
            }
            data.mountEligible.remove(uuid);
            data.vehicleMounts.remove(uuid);
            data.removeTeamMember(uuid);
        }
    }
}

