package com.kuzhi.findme.server.data;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Performs the retryable one-time 1.2 player-root import. */
public final class FindMeWorldMigrationService {
    private FindMeWorldMigrationService() {
    }

    public static FindMeWorldSavedData ensureMigrated(ServerPlayer player) {
        FindMeWorldSavedData world = FindMeWorldSavedData.get(player.server);
        UUID playerUuid = player.getUUID();
        if (world.migration(playerUuid).isPresent()) {
            if (world.needsDestroyedHouseReconciliation(playerUuid)) {
                reconcileDestroyedLegacyHomes(world, playerUuid);
            }
            return world;
        }

        // An existing world root is already authoritative. Never overwrite it
        // with a stale player NBT or mirror copy.
        if (world.playerRoot(playerUuid).isPresent()) {
            if (world.needsDestroyedHouseReconciliation(playerUuid)) {
                reconcileDestroyedLegacyHomes(world, playerUuid);
            }
            world.markMigration(playerUuid, "existing_world_data", player.server.getTickCount(), "", "existing");
            return world;
        }

        Optional<LegacyRoot> legacy = currentPlayerRoot(player).map(root -> new LegacyRoot("player_nbt", root));
        if (legacy.isEmpty()) {
            legacy = PlayerCompanionDataMirror.copyForMigration(player)
                    .map(root -> new LegacyRoot("mirror", root));
        }

        if (legacy.isEmpty()) {
            world.markMigration(playerUuid, "none", player.server.getTickCount(), "", "empty");
            return world;
        }

        LegacyRoot source = legacy.get();
        Optional<String> sourceError = LegacyPlayerRootValidator.firstError(source.root());
        if (sourceError.isPresent()) {
            FindMeMod.LOGGER.error(
                    "FindMe migration source rejected: player={} source={} firstError={} sourceChecksum={}",
                    playerUuid,
                    source.name(),
                    sourceError.get(),
                    FindMeWorldSavedData.checksum(source.root())
            );
            throw new IllegalStateException("FindMe migration source is malformed for " + playerUuid + ": " + sourceError.get());
        }
        if (!PlayerCompanionDataCodec.isMeaningfulRoot(source.root())) {
            world.markMigration(
                    playerUuid,
                    source.name(),
                    player.server.getTickCount(),
                    FindMeWorldSavedData.checksum(source.root()),
                    "empty"
            );
            FindMeMod.LOGGER.info(
                    "FindMe migration completed: player={} source={} result=empty sourceChecksum={}",
                    playerUuid,
                    source.name(),
                    FindMeWorldSavedData.checksum(source.root())
            );
            world.markDestroyedHousesReconciled(playerUuid);
            return world;
        }
        // Decode once before committing the world record. This catches a
        // malformed legacy root while leaving the source untouched for retry.
        CompoundTag expected = normalizedForWorld(world, source.root());

        world.putPlayerRoot(playerUuid, expected);
        CompoundTag committed = world.playerRoot(playerUuid).orElseThrow();
        CompoundTag actual = PlayerCompanionDataCodec.normalizedRoot(committed);
        String expectedChecksum = NbtFingerprint.sha256(expected);
        String actualChecksum = NbtFingerprint.sha256(actual);
        if (!expectedChecksum.equals(actualChecksum)) {
            world.removePlayerRoot(playerUuid);
            FindMeMod.LOGGER.error(
                    "FindMe migration verification failed: player={} source={} expectedChecksum={} actualChecksum={} firstDifference={}",
                    playerUuid,
                    source.name(),
                    expectedChecksum,
                    actualChecksum,
                    NbtFingerprint.firstDifference(expected, actual).orElse("unknown")
            );
            throw new IllegalStateException("FindMe migration verification failed for " + playerUuid);
        }
        world.markMigration(
                playerUuid,
                source.name(),
                player.server.getTickCount(),
                FindMeWorldSavedData.checksum(source.root()),
                "imported"
        );
        FindMeMod.LOGGER.info(
                "FindMe migration completed: player={} source={} result=imported sourceChecksum={}",
                playerUuid,
                source.name(),
                FindMeWorldSavedData.checksum(source.root())
        );
        world.markDestroyedHousesReconciled(playerUuid);
        return world;
    }

    private static void reconcileDestroyedLegacyHomes(FindMeWorldSavedData world, UUID playerUuid) {
        CompoundTag root = world.playerRoot(playerUuid).orElse(null);
        if (root == null) {
            world.markDestroyedHousesReconciled(playerUuid);
            return;
        }
        CompoundTag reconciled = normalizedForWorld(world, root);
        if (!NbtFingerprint.sha256(PlayerCompanionDataCodec.normalizedRoot(root))
                .equals(NbtFingerprint.sha256(reconciled))) {
            world.putPlayerRoot(playerUuid, reconciled);
        }
        world.markDestroyedHousesReconciled(playerUuid);
    }

    private static CompoundTag normalizedForWorld(FindMeWorldSavedData world, CompoundTag root) {
        CompoundTag container = new CompoundTag();
        PlayerCompanionDataCodec.putRoot(container, root);
        PlayerCompanionData data = PlayerCompanionData.load(container);
        boolean changed = false;
        for (CompanionKind kind : CompanionKind.values()) {
            for (UUID uuid : data.list(kind)) {
                if (data.homeHouseId(uuid).isPresent()) {
                    continue;
                }
                if (data.homeNestBlock(uuid).filter(world::wasHouseDestroyed).isEmpty()) {
                    continue;
                }
                data.clearHomePosition(uuid);
                if (data.deadList().contains(uuid)) {
                    data.setLifecycleState(uuid, CompanionLifecycleState.DEAD);
                } else {
                    data.setLifecycleState(uuid, data.storedEntity(uuid).isPresent()
                            ? CompanionLifecycleState.STORED
                            : CompanionLifecycleState.RECOVERY);
                }
                changed = true;
            }
        }
        if (!changed) {
            return PlayerCompanionDataCodec.normalizedRoot(root);
        }
        CompoundTag reconciled = new CompoundTag();
        data.save(reconciled);
        return PlayerCompanionDataCodec.rootCopy(reconciled);
    }

    public static void copyOnClone(ServerPlayer original, ServerPlayer clone) {
        FindMeWorldSavedData world = FindMeWorldSavedData.get(clone.server);
        if (!original.getUUID().equals(clone.getUUID())) {
            world.copyPlayer(original.getUUID(), clone.getUUID());
        }
        if (!world.hasAuthoritativePlayer(clone.getUUID())) {
            ensureMigrated(clone);
        }
    }

    private static Optional<CompoundTag> currentPlayerRoot(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (!PlayerCompanionDataCodec.hasRoot(persistentData)) {
            return Optional.empty();
        }
        return Optional.of(PlayerCompanionDataCodec.rootCopy(persistentData));
    }

    private record LegacyRoot(String name, CompoundTag root) {
        private LegacyRoot {
            root = root == null ? new CompoundTag() : root.copy();
        }
    }
}
