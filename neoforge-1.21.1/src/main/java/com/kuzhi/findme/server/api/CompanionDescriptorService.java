package com.kuzhi.findme.server.api;

import com.kuzhi.findme.api.CompanionDescriptor;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.CompanionRuntimeIndex;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionDescriptorService {
    private static final Map<MinecraftServer, OwnerIndex> OWNER_INDEXES = new WeakHashMap<>();

    private CompanionDescriptorService() {
    }

    public static Optional<CompanionDescriptor> describe(MinecraftServer server, UUID ownerUuid, UUID companionUuid) {
        if (server == null || ownerUuid == null || companionUuid == null) return Optional.empty();
        PlayerCompanionData data = CompanionDataService.data(server, ownerUuid);
        return describe(server, ownerUuid, data, companionUuid,
                CompanionDataService.revision(server, ownerUuid));
    }

    public static List<CompanionDescriptor> describeAll(MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) return List.of();
        PlayerCompanionData data = CompanionDataService.data(server, ownerUuid);
        long revision = CompanionDataService.revision(server, ownerUuid);
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(data.list(CompanionKind.COMPANION));
        ids.addAll(data.list(CompanionKind.MOUNT));
        ids.addAll(data.deadList());
        List<CompanionDescriptor> descriptors = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            describe(server, ownerUuid, data, id, revision).ifPresent(descriptors::add);
        }
        return List.copyOf(descriptors);
    }

    public static Optional<UUID> ownerUuid(MinecraftServer server, UUID companionUuid) {
        if (server == null || companionUuid == null) return Optional.empty();
        OwnerIndex index;
        synchronized (OWNER_INDEXES) {
            index = OWNER_INDEXES.computeIfAbsent(server, ignored -> new OwnerIndex());
        }
        return index.ownerUuid(FindMeWorldSavedData.get(server), companionUuid);
    }

    public static Optional<CompanionDescriptor> describe(MinecraftServer server, UUID ownerUuid,
                                                         PlayerCompanionData data, UUID companionUuid,
                                                         long revision) {
        if (server == null || ownerUuid == null || data == null || companionUuid == null) {
            return Optional.empty();
        }
        boolean dead = data.deadList().contains(companionUuid);
        Optional<CompanionKind> kind = data.kindOf(companionUuid);
        if (kind.isEmpty() || !data.contains(companionUuid) && !dead) return Optional.empty();

        Entity entity = CompanionEntityLookup.findEntity(server, companionUuid).orElse(null);
        Optional<ResourceLocation> entityType = entity == null
                ? storedOrDeadType(data, companionUuid)
                : Optional.of(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        CompanionMoveType movement = entity instanceof LivingEntity living
                ? FindMeApi.resolveMovement(living, kind.get())
                : entityType.flatMap(type -> FindMeApi.externalMovement(server, type))
                .orElseGet(() -> entityType.map(type -> FindMeApi.entityProfile(type).movement())
                        .orElse(CompanionMoveType.WALK));
        CompanionLifecycleState lifecycle = dead ? CompanionLifecycleState.DEAD
                : data.lifecycleState(companionUuid);
        boolean deployed = data.contains(companionUuid) && data.isDeployed(kind.get(), companionUuid);
        return Optional.of(new CompanionDescriptor(ownerUuid, companionUuid, kind.get(), lifecycle, movement,
                entityType, deployed, entity instanceof LivingEntity living && living.isAlive(),
                data.storedEntity(companionUuid).isPresent(), data.homeHouseId(companionUuid), revision));
    }

    private static Optional<ResourceLocation> storedOrDeadType(PlayerCompanionData data, UUID uuid) {
        String type = data.storedEntity(uuid).map(CompanionEntitySnapshots::storedEntityType)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> data.deadRecord(uuid).map(DeadCompanionRecord::entityType).orElse(""));
        return Optional.ofNullable(ResourceLocation.tryParse(type));
    }

    private static final class OwnerIndex {
        private final Map<UUID, UUID> ownersByCompanion = new HashMap<>();
        private final Map<UUID, Set<UUID>> companionsByOwner = new HashMap<>();
        private final Map<UUID, Long> revisions = new HashMap<>();

        private synchronized Optional<UUID> ownerUuid(FindMeWorldSavedData world, UUID companionUuid) {
            Set<UUID> currentOwners = world.playerUuids();
            for (UUID removedOwner : Set.copyOf(revisions.keySet())) {
                if (!currentOwners.contains(removedOwner)) removeOwner(removedOwner);
            }
            for (UUID ownerUuid : currentOwners) {
                long revision = world.playerRevision(ownerUuid);
                if (revisions.getOrDefault(ownerUuid, -1L) == revision) continue;
                removeOwner(ownerUuid);
                CompanionRuntimeIndex runtimeIndex = world.companionRuntimeIndex(ownerUuid);
                Set<UUID> companionUuids = Set.copyOf(runtimeIndex.uuids());
                companionsByOwner.put(ownerUuid, companionUuids);
                revisions.put(ownerUuid, revision);
                for (UUID uuid : companionUuids) ownersByCompanion.put(uuid, ownerUuid);
            }
            return Optional.ofNullable(ownersByCompanion.get(companionUuid));
        }

        private void removeOwner(UUID ownerUuid) {
            Set<UUID> previous = companionsByOwner.remove(ownerUuid);
            if (previous != null) {
                for (UUID uuid : previous) ownersByCompanion.remove(uuid, ownerUuid);
            }
            revisions.remove(ownerUuid);
        }
    }
}
