package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.compat.waystones.WaystonesIntegration;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RideHomeCameraPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Cinematic wrapper around Waystones' authoritative teleport transaction. */
public final class CompanionWaystoneJourneyService {
    private static final int CAMERA_LEAD_TICKS = 2;
    private static final int ACQUIRE_TIMEOUT_TICKS = 20 * 12;
    private static final int DEPARTURE_TICKS = 42;
    private static final int BLACKOUT_TICKS = 8;
    private static final int TELEPORT_DELAY_TICKS = 10;
    private static final int TELEPORT_TIMEOUT_TICKS = 20 * 30;
    private static final int ARRIVAL_PRESENTATION_HOLD_TICKS = 4;
    private static final int ARRIVAL_TICKS = 34;
    private static final int TOTAL_TIMEOUT_TICKS = 20 * 60;
    private static final Map<UUID, Journey> JOURNEYS = new HashMap<>();

    private CompanionWaystoneJourneyService() {
    }

    public static boolean start(ServerPlayer player, UUID mountUuid, UUID waystoneUuid) {
        if (player == null || mountUuid == null || waystoneUuid == null || !WaystonesIntegration.available()
                || JOURNEYS.containsKey(player.getUUID()) || !WaystonesIntegration.isActivated(player, waystoneUuid)) {
            return false;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!data.contains(CompanionKind.MOUNT, mountUuid)
                || CompanionOperationLockService.get(mountUuid) != null) {
            return false;
        }
        CompanionMoveType moveType = RideHomeJourneySupport.resolveMoveType(player, data, mountUuid);
        Journey journey = new Journey(player.getUUID(), mountUuid, waystoneUuid, moveType,
                RideHomeJourneyTransaction.capture(player, data, mountUuid));
        Entity existing = CompanionEntityLookup.findEntity(player.getServer(), mountUuid).orElse(null);
        if (existing instanceof LivingEntity living && living.isAlive()) {
            journey.originalMountPosition = SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(),
                    living.getYRot(), living.getXRot());
        }
        journey.headingYaw = Mth.wrapDegrees(player.getYRot());
        JOURNEYS.put(player.getUUID(), journey);
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, null, player.position(),
                RideHomeCameraPacket.Mode.START, TOTAL_TIMEOUT_TICKS, journey.headingYaw));
        FindMeMod.LOGGER.info("[FindMe waystones] started player={} mount={} waystone={}",
                player.getUUID(), mountUuid, waystoneUuid);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || JOURNEYS.isEmpty()) return;
        for (Journey journey : new ArrayList<>(JOURNEYS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(journey.playerUuid);
            if (player == null) {
                abandon(server, journey, "player_unavailable");
                continue;
            }
            journey.totalAge++;
            journey.stageAge++;
            if (!player.isAlive() || journey.totalAge >= TOTAL_TIMEOUT_TICKS) {
                cancel(player, journey, player.isAlive() ? "journey_timeout" : "player_dead", null);
                continue;
            }
            switch (journey.stage) {
                case ACQUIRING -> tickAcquiring(player, journey);
                case DEPARTING -> tickDeparting(player, journey);
                case BLACKOUT -> tickBlackout(player, journey);
                case TELEPORTING -> tickTeleporting(player, journey);
                case ARRIVING -> tickArriving(player, journey);
            }
        }
    }

    public static boolean bypassesTravelLifecycle(ServerPlayer player) {
        Journey journey = player == null ? null : JOURNEYS.get(player.getUUID());
        return journey != null && journey.teleportInFlight;
    }

    public static boolean ownsMountedMovement(ServerPlayer player) {
        Journey journey = player == null ? null : JOURNEYS.get(player.getUUID());
        if (journey == null || journey.stage != Stage.DEPARTING
                && journey.stage != Stage.BLACKOUT && journey.stage != Stage.ARRIVING) return false;
        return player.getVehicle() != null && journey.mountUuid.equals(player.getVehicle().getUUID());
    }

    public static int cancelForPlayer(ServerPlayer player, String reason) {
        Journey journey = player == null ? null : JOURNEYS.get(player.getUUID());
        if (journey == null) return 0;
        cancel(player, journey, reason, null);
        return 1;
    }

    public static void resetServerState(MinecraftServer server) {
        for (Journey journey : new ArrayList<>(JOURNEYS.values())) abandon(server, journey, "server_stopped");
    }

    private static void tickAcquiring(ServerPlayer player, Journey journey) {
        if (journey.stageAge < CAMERA_LEAD_TICKS) return;
        boolean descendingAirborne = isDescendingAirborne(player);
        if (descendingAirborne) {
            player.fallDistance = 0.0f;
            player.invulnerableTime = Math.max(player.invulnerableTime,
                    com.kuzhi.findme.Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        LivingEntity mount = CompanionDeployRequestService.readyEntity(player, data, journey.mountUuid,
                CompanionDeployRequestService.Mode.RIDDEN);
        String busyReason = CompanionLifecycleFacade.busyReason(player, data, journey.mountUuid);
        if (mount == null && !journey.summonRequested && busyReason == null) {
            CompanionDeployRequestService.Result result = CompanionDeployRequestService.request(player, data,
                    CompanionKind.MOUNT, journey.mountUuid, CompanionDeployRequestService.Mode.RIDDEN,
                    "waystones:departure_deploy");
            if (result.state() == CompanionDeployRequestService.State.REJECTED) {
                cancel(player, journey, "mount_deploy_rejected", null);
                return;
            }
            journey.summonRequested = result.state() == CompanionDeployRequestService.State.STARTED;
            mount = result.entity();
            logAcquireState(journey, player, "deploy_" + result.state().name().toLowerCase());
        } else if (mount == null && !journey.summonRequested && busyReason != null) {
            logAcquireState(journey, player, "waiting_" + busyReason.toLowerCase());
        }
        if (mount == null) mount = findMount(player.getServer(), journey.mountUuid);
        if (mount != null && !journey.cameraTracking) {
            journey.cameraTracking = true;
            ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                    RideHomeCameraPacket.Mode.TRACK, TOTAL_TIMEOUT_TICKS, mount.getYRot()));
        }
        if (mount == null || player.getVehicle() != mount) {
            if (journey.summonRequested && mount == null && busyReason == null
                    && journey.stageAge > CAMERA_LEAD_TICKS + 5) {
                cancel(player, journey, "mount_deploy_lost", null);
                return;
            }
            if (acquisitionTimedOut(journey.stageAge)) cancel(player, journey, "mount_acquire_timeout", null);
            return;
        }
        if (++journey.stableTicks < 3) return;
        if (!journey.lockHeld) {
            journey.lockHeld = CompanionOperationLockService.tryBegin(player, journey.mountUuid,
                    CompanionOperationLockService.Operation.JOURNEY, "waystones:teleport",
                    TOTAL_TIMEOUT_TICKS - journey.totalAge);
            if (!journey.lockHeld) {
                journey.stableTicks = 0;
                logAcquireState(journey, player, "waiting_journey_lock");
                return;
            }
        }
        CompanionMountCinematicFlowService.cancelForCompanion(player, mount.getUUID(),
                "waystones_departure_ready");
        journey.moveType = CompanionEntityClassifier.moveType(mount, CompanionKind.MOUNT);
        journey.travelDirection = RideHomeJourneySupport.departureDirection(player, mount);
        journey.departureTarget = RideHomeJourneySupport.departureTarget(mount, journey.travelDirection, journey.moveType);
        journey.physics = RideHomeJourneySupport.capturePhysics(mount);
        journey.headingYaw = RideHomeJourneySupport.headingYaw(journey.travelDirection, mount.getYRot());
        enter(journey, Stage.DEPARTING, player, mount);
    }

    static boolean acquisitionTimedOut(int stageAge) {
        return stageAge >= ACQUIRE_TIMEOUT_TICKS;
    }

    private static boolean isDescendingAirborne(ServerPlayer player) {
        return player != null && !player.onGround() && !player.isInWater()
                && !player.isFallFlying() && !player.onClimbable()
                && !player.getAbilities().flying && player.getDeltaMovement().y < -0.01;
    }

    private static void tickDeparting(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!RideHomeJourneySupport.validRide(player, mount, journey.mountUuid)) {
            cancel(player, journey, "departure_ride_lost", null);
            return;
        }
        RideHomeJourneySupport.prepareCinematic(mount, journey.moveType);
        CompanionCinematicMovementService.moveMountedRideHome(mount, player, journey.moveType,
                journey.departureTarget, journey.stageAge, false);
        RideHomeJourneySupport.stabilize(player, mount);
        if (journey.stageAge >= DEPARTURE_TICKS) {
            journey.holdAnchor = mount.position();
            ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                    RideHomeCameraPacket.Mode.BLACKOUT, BLACKOUT_TICKS, journey.headingYaw));
            enter(journey, Stage.BLACKOUT, player, mount);
        }
    }

    private static void tickBlackout(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!RideHomeJourneySupport.holdRide(player, mount, journey.mountUuid, journey.holdAnchor,
                journey.headingYaw, journey.moveType)) {
            cancel(player, journey, "blackout_ride_lost", null);
            return;
        }
        if (journey.stageAge < TELEPORT_DELAY_TICKS) return;
        journey.teleportInFlight = true;
        enter(journey, Stage.TELEPORTING, player, mount);
        WaystonesIntegration.teleport(player, mount, journey.waystoneUuid,
                entities -> completeTeleport(player, journey, entities),
                error -> failTeleport(player, journey, error));
    }

    private static void tickTeleporting(ServerPlayer player, Journey journey) {
        if (journey.teleportCompleted || journey.failure != null) return;
        if (journey.stageAge >= TELEPORT_TIMEOUT_TICKS) cancel(player, journey, "teleport_timeout", null);
    }

    private static void completeTeleport(ServerPlayer player, Journey journey, List<Entity> entities) {
        if (JOURNEYS.get(player.getUUID()) != journey) return;
        journey.teleportInFlight = false;
        LivingEntity mount = entities.stream().filter(entity -> journey.mountUuid.equals(entity.getUUID()))
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).findFirst()
                .orElseGet(() -> findMount(player.getServer(), journey.mountUuid));
        if (mount == null || player.getVehicle() != mount) {
            cancel(player, journey, "destination_mount_relationship_lost", null);
            return;
        }
        journey.teleportCompleted = true;
        journey.moveType = CompanionEntityClassifier.moveType(mount, CompanionKind.MOUNT);
        journey.arrivalTarget = mount.position();
        RideHomeJourneySupport.prepareCinematic(mount, journey.moveType);
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount, journey.arrivalTarget,
                RideHomeCameraPacket.Mode.DESTINATION, TOTAL_TIMEOUT_TICKS - journey.totalAge, mount.getYRot()));
        FindMeMod.LOGGER.info("[FindMe waystones] destination accepted player={} mount={} dimension={} playerPos={} mountPos={}",
                player.getUUID(), journey.mountUuid, player.level().dimension().location(),
                player.position(), mount.position());
        journey.arrivalRevealSent = false;
        enter(journey, Stage.ARRIVING, player, mount);
    }

    private static void failTeleport(ServerPlayer player, Journey journey, Component error) {
        if (JOURNEYS.get(player.getUUID()) != journey) return;
        journey.failure = error;
        cancel(player, journey, "waystones_rejected", error);
    }

    private static void tickArriving(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!RideHomeJourneySupport.validRide(player, mount, journey.mountUuid)) {
            finishAtDestination(player, journey, mount, "arrival_ride_lost");
            return;
        }
        if (!journey.arrivalRevealSent) {
            RideHomeJourneySupport.holdRide(player, mount, journey.mountUuid, mount.position(),
                    journey.headingYaw, journey.moveType);
            if (journey.stageAge < ARRIVAL_PRESENTATION_HOLD_TICKS) {
                WaystonesIntegration.syncMountedState(player, mount);
                return;
            }
            ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                    RideHomeCameraPacket.Mode.REVEAL, 10, mount.getYRot()));
            journey.arrivalRevealSent = true;
            FindMeMod.LOGGER.info("[FindMe waystones] arrival presentation released player={} mount={} age={}",
                    player.getUUID(), journey.mountUuid, journey.stageAge);
        }
        CompanionCinematicMovementService.moveMountedRideHome(mount, player, journey.moveType,
                journey.arrivalTarget, journey.stageAge, true);
        RideHomeJourneySupport.stabilize(player, mount);
        WaystonesIntegration.syncMountedState(player, mount);
        if (journey.stageAge >= ARRIVAL_TICKS) finishAtDestination(player, journey, mount, "complete");
    }

    private static void finishAtDestination(ServerPlayer player, Journey journey, LivingEntity mount, String reason) {
        if (!JOURNEYS.remove(player.getUUID(), journey)) return;
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        if (mount != null && mount.isAlive()) RideHomeJourneyTransaction.markDeployed(player, mount, true);
        if (journey.lockHeld) {
            CompanionOperationLockService.end(player, journey.mountUuid, CompanionOperationLockService.Operation.JOURNEY, reason);
        }
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.STOP, 1, journey.headingYaw));
        FindMeMod.LOGGER.info("[FindMe waystones] completed player={} mount={} ticks={}",
                player.getUUID(), journey.mountUuid, journey.totalAge);
    }

    private static void cancel(ServerPlayer player, Journey journey, String reason, Component message) {
        if (!JOURNEYS.remove(player.getUUID(), journey)) return;
        CompanionMountCinematicFlowService.cancelForCompanion(player, journey.mountUuid,
                "waystones:" + reason);
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        journey.teleportInFlight = false;
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        if (!journey.teleportCompleted) {
            RideHomeJourneyTransaction.rollback(player, journey.mountUuid, journey.origin, journey.originalMountPosition,
                    journey.origin.stored(), false, false, mount);
        }
        CompanionOperationLockService.end(player, journey.mountUuid, CompanionOperationLockService.Operation.JOURNEY, reason);
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.STOP, 1, journey.headingYaw));
        if (message != null) player.displayClientMessage(message, true);
        FindMeMod.LOGGER.warn("[FindMe waystones] cancelled player={} mount={} stage={} reason={}",
                player.getUUID(), journey.mountUuid, journey.stage, reason);
    }

    private static void abandon(MinecraftServer server, Journey journey, String reason) {
        JOURNEYS.remove(journey.playerUuid, journey);
        LivingEntity mount = findMount(server, journey.mountUuid);
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        if (journey.lockHeld) CompanionOperationLockService.clear(null, journey.mountUuid, reason);
    }

    private static LivingEntity findMount(MinecraftServer server, UUID uuid) {
        Entity entity = CompanionEntityLookup.findEntity(server, uuid).orElse(null);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static void enter(Journey journey, Stage stage, ServerPlayer player, LivingEntity mount) {
        Stage previous = journey.stage;
        journey.stage = stage;
        journey.stageAge = 0;
        FindMeMod.LOGGER.info("[FindMe waystones] stage player={} mount={} {} -> {} pos={}",
                player.getUUID(), journey.mountUuid, previous, stage, mount == null ? "missing" : mount.position());
    }

    private static void logAcquireState(Journey journey, ServerPlayer player, String state) {
        if (state.equals(journey.lastAcquireState)) return;
        journey.lastAcquireState = state;
        FindMeMod.LOGGER.info("[FindMe waystones] acquire player={} mount={} state={} age={} vehicle={}",
                player.getUUID(), journey.mountUuid, state, journey.stageAge,
                player.getVehicle() == null ? "none" : player.getVehicle().getUUID());
    }

    private enum Stage { ACQUIRING, DEPARTING, BLACKOUT, TELEPORTING, ARRIVING }

    private static final class Journey {
        final UUID playerUuid;
        final UUID mountUuid;
        final UUID waystoneUuid;
        final RideHomeJourneyTransaction.Origin origin;
        Stage stage = Stage.ACQUIRING;
        CompanionMoveType moveType;
        RideHomeJourneySupport.Physics physics;
        boolean summonRequested;
        boolean cameraTracking;
        boolean teleportInFlight;
        boolean teleportCompleted;
        boolean arrivalRevealSent;
        boolean lockHeld;
        int totalAge;
        int stageAge;
        int stableTicks;
        float headingYaw;
        Vec3 travelDirection = Vec3.ZERO;
        Vec3 departureTarget = Vec3.ZERO;
        Vec3 holdAnchor = Vec3.ZERO;
        Vec3 arrivalTarget = Vec3.ZERO;
        Component failure;
        SavedPosition originalMountPosition;
        String lastAcquireState = "";

        Journey(UUID playerUuid, UUID mountUuid, UUID waystoneUuid, CompanionMoveType moveType,
                RideHomeJourneyTransaction.Origin origin) {
            this.playerUuid = playerUuid;
            this.mountUuid = mountUuid;
            this.waystoneUuid = waystoneUuid;
            this.moveType = moveType;
            this.origin = origin;
        }
    }
}
