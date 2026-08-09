package com.kuzhi.findme.server.api;

import com.kuzhi.findme.api.CompanionDescriptor;
import com.kuzhi.findme.api.event.CompanionLifecycleEvent;
import com.kuzhi.findme.api.event.CompanionLifecycleEvent.Change;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

public final class CompanionLifecycleEventService {
    private CompanionLifecycleEventService() {
    }

    public static void publishChanges(MinecraftServer server, UUID ownerUuid,
                                      PlayerCompanionData before, PlayerCompanionData after,
                                      Set<UUID> changedUuids,
                                      long beforeRevision, long afterRevision) {
        if (server == null || ownerUuid == null || before == null || after == null
                || changedUuids == null || changedUuids.isEmpty() || beforeRevision == afterRevision) return;
        for (UUID uuid : changedUuids) {
            CompanionDescriptor oldDescriptor = CompanionDescriptorService
                    .describeSnapshot(server, ownerUuid, before, uuid, beforeRevision).orElse(null);
            CompanionDescriptor newDescriptor = CompanionDescriptorService
                    .describe(server, ownerUuid, after, uuid, afterRevision).orElse(null);
            for (Change change : changes(before, after, uuid)) {
                MinecraftForge.EVENT_BUS.post(new CompanionLifecycleEvent(server, change, ownerUuid, uuid,
                        oldDescriptor, newDescriptor, afterRevision));
            }
        }
    }

    static EnumSet<Change> changes(PlayerCompanionData before, PlayerCompanionData after, UUID uuid) {
        EnumSet<Change> result = EnumSet.noneOf(Change.class);
        boolean oldRegistered = before.contains(uuid);
        boolean newRegistered = after.contains(uuid);
        boolean oldDead = before.deadList().contains(uuid);
        boolean newDead = after.deadList().contains(uuid);

        if (!oldRegistered && !oldDead && newRegistered) result.add(Change.REGISTERED);
        if (oldRegistered && newDead) result.add(Change.DIED);
        if (oldDead && newRegistered) result.add(Change.RECOVERED);
        if ((oldRegistered || oldDead) && !newRegistered && !newDead) result.add(Change.RELEASED);

        if (newRegistered) {
            CompanionLifecycleState oldState = oldRegistered ? before.lifecycleState(uuid) : null;
            CompanionLifecycleState newState = after.lifecycleState(uuid);
            if (newState != oldState) {
                if (newState == CompanionLifecycleState.DEPLOYED) result.add(Change.DEPLOYED);
                if (newState == CompanionLifecycleState.STORED
                        || newState == CompanionLifecycleState.HOME_STORED) result.add(Change.STORED);
                result.add(Change.LIFECYCLE_CHANGED);
            }
        }

        boolean oldHome = before.homeHouseId(uuid).isPresent() || before.homeNestBlock(uuid).isPresent();
        boolean newHome = after.homeHouseId(uuid).isPresent() || after.homeNestBlock(uuid).isPresent();
        if (!oldHome && newHome) result.add(Change.HOME_ASSIGNED);
        if (oldHome && !newHome) result.add(Change.HOME_CLEARED);
        if (oldHome && newHome && (!before.homeHouseId(uuid).equals(after.homeHouseId(uuid))
                || !before.homeNestBlock(uuid).equals(after.homeNestBlock(uuid)))) {
            result.add(Change.HOME_CHANGED);
        }
        return result;
    }

}
