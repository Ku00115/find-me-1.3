package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class RideHomeJourneyTransaction {
    private RideHomeJourneyTransaction() {
    }

    static Origin capture(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        return new Origin(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
                data.lifecycleState(uuid), data.isDeployed(CompanionKind.MOUNT, uuid),
                player.getVehicle() != null && uuid.equals(player.getVehicle().getUUID()));
    }

    static Entity rollback(ServerPlayer player, UUID uuid, Origin origin, SavedPosition originalMountPosition,
                           boolean restoredFromStorage, boolean storedForTransfer,
                           boolean destinationRestored, Entity entity) {
        PlayerCompanionData data = CompanionDataService.data(player);
        if (destinationRestored && entity instanceof LivingEntity living && living.isAlive()) {
            markDeployed(player, living, false);
            return entity;
        }
        player.stopRiding();
        if (storedForTransfer) {
            entity = restoreOriginalLiveState(player, data, uuid, origin, originalMountPosition);
        } else if (entity instanceof LivingEntity living && living.isAlive()) {
            if (origin.stored() && restoredFromStorage) {
                CompanionStorageService.storeImmediatelyForJourney(player, data, CompanionKind.MOUNT, living);
                entity = null;
            } else if (originalMountPosition != null && !restoredFromStorage) {
                ServerLevel originalLevel = player.getServer().getLevel(originalMountPosition.dimension());
                if (originalLevel != null) {
                    entity = CompanionEntityTransferService.moveEntityTo(living, originalLevel,
                            originalMountPosition.blockPos(), originalMountPosition.yRot(),
                            originalMountPosition.xRot(), false);
                }
            }
        }
        ServerLevel startLevel = player.getServer().getLevel(origin.dimension());
        if (startLevel != null) {
            Vec3 pos = origin.playerPosition();
            if (player.level() != startLevel) {
                player.teleportTo(startLevel, pos.x, pos.y, pos.z, Set.<RelativeMovement>of(),
                        origin.playerYaw(), origin.playerPitch());
            } else {
                player.connection.teleport(pos.x, pos.y, pos.z, origin.playerYaw(), origin.playerPitch());
            }
            if (origin.riding() && entity instanceof LivingEntity living && living.isAlive()) {
                CompanionMountCinematicFlowService.startRidingAfterContact(player, living,
                        MountCinematicMode.RIDE_HOME);
            }
        }
        boolean liveRollback = entity instanceof LivingEntity living && living.isAlive();
        boolean restoreFailedSafelyStored = storedForTransfer && !origin.stored() && !liveRollback;
        if (origin.deployed() && !restoreFailedSafelyStored) {
            data.setDeployed(CompanionKind.MOUNT, uuid);
        } else {
            data.clearDeployed(CompanionKind.MOUNT, uuid);
        }
        data.setLifecycleState(uuid, restoreFailedSafelyStored
                ? CompanionLifecycleState.STORED : origin.lifecycle());
        if (origin.lifecycle() == CompanionLifecycleState.HOME_ACTIVE
                && entity instanceof LivingEntity living && living.isAlive()) {
            CompanionHomeResidentService.markResidentEntity(living);
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        return entity;
    }

    private static Entity restoreOriginalLiveState(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                                   Origin origin, SavedPosition originalMountPosition) {
        if (origin.stored()) {
            return null;
        }
        ServerLevel restoreLevel = originalMountPosition == null
                ? player.getServer().getLevel(origin.dimension())
                : player.getServer().getLevel(originalMountPosition.dimension());
        if (restoreLevel == null) {
            return null;
        }
        Vec3 restorePosition = originalMountPosition == null
                ? origin.playerPosition()
                : new Vec3(originalMountPosition.x(), originalMountPosition.y(), originalMountPosition.z());
        float yRot = originalMountPosition == null ? origin.playerYaw() : originalMountPosition.yRot();
        float xRot = originalMountPosition == null ? origin.playerPitch() : originalMountPosition.xRot();
        Entity restored = CompanionLifecycleFacade.restoreStoredForJourney(restoreLevel, player, data, uuid,
                net.minecraft.core.BlockPos.containing(restorePosition), yRot, xRot,
                "home:ride_home_rollback_restore").orElse(null);
        if (restored != null) {
            restored.moveTo(restorePosition.x, restorePosition.y, restorePosition.z, yRot, xRot);
            restored.setDeltaMovement(Vec3.ZERO);
        }
        return restored;
    }

    static void markDeployed(ServerPlayer player, LivingEntity mount, boolean applyCooldown) {
        PlayerCompanionData data = CompanionDataService.data(player);
        UUID uuid = mount.getUUID();
        data.removeStoredEntity(uuid);
        data.setDeployed(CompanionKind.MOUNT, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        data.setLastKnownPosition(uuid, SavedPosition.of(mount.level(), mount.getX(), mount.getY(), mount.getZ(),
                mount.getYRot(), mount.getXRot()));
        if (applyCooldown) {
            data.setReadyAt(CompanionKind.MOUNT,
                    player.serverLevel().getGameTime() + Config.summonCooldownTicks);
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
    }

    record Origin(ResourceKey<Level> dimension, Vec3 playerPosition, float playerYaw, float playerPitch,
                  CompanionLifecycleState lifecycle, boolean deployed, boolean riding) {
        boolean stored() {
            return this.lifecycle == CompanionLifecycleState.STORED
                    || this.lifecycle == CompanionLifecycleState.HOME_STORED;
        }
    }
}
