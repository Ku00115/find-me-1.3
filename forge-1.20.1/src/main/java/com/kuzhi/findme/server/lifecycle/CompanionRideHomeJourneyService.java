package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RideHomeCameraPacket;
import com.kuzhi.findme.network.RideHomeDestinationPacket;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.ui.CompanionMessageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Coordinates one server-authoritative ride-home transaction per player. */
public final class CompanionRideHomeJourneyService {
    private static final int CAMERA_LEAD_TICKS = 2;
    private static final int ACQUIRE_TIMEOUT_TICKS = 20 * 12;
    private static final int RIDE_STABLE_TICKS = 3;
    private static final int DEPARTURE_TICKS = 42;
    private static final int BLACKOUT_FADE_TICKS = 8;
    private static final int BLACKOUT_TRANSFER_DELAY_TICKS = BLACKOUT_FADE_TICKS + 2;
    private static final int REVEAL_FADE_TICKS = 10;
    private static final int HOME_LOAD_TIMEOUT_TICKS = 20 * 20;
    private static final int HOME_READY_STABLE_TICKS = 8;
    private static final int CLIENT_READY_TIMEOUT_TICKS = 20 * 30;
    private static final int ARRIVAL_TIMEOUT_TICKS = 20 * 8;
    private static final int JOURNEY_TIMEOUT_TICKS = 20 * 60;
    private static final Map<UUID, Journey> JOURNEYS = new HashMap<>();
    private static final Set<UUID> ROLLBACK_TELEPORT_BYPASS = new HashSet<>();

    private CompanionRideHomeJourneyService() {
    }

    public static boolean start(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int wheelIndex) {
        Optional<UUID> selected = data == null ? Optional.empty() : data.wheelUuidAt(kind, wheelIndex);
        if (selected.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED);
            return false;
        }
        return start(player, data, kind, selected.get());
    }

    public static boolean start(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID mountUuid) {
        if (player == null || data == null || kind != CompanionKind.MOUNT
                || mountUuid == null || !data.contains(kind, mountUuid)
                || JOURNEYS.containsKey(player.getUUID())) {
            return false;
        }
        SavedPosition homePosition = data.homePosition(mountUuid).orElse(null);
        if (homePosition == null) {
            CompanionMessageService.tell(player, "message.find_me.no_home", ChatFormatting.YELLOW);
            return false;
        }
        ServerLevel destination = player.getServer().getLevel(homePosition.dimension());
        if (destination == null) {
            CompanionMessageService.tell(player, "message.find_me.no_home", ChatFormatting.YELLOW);
            return false;
        }
        CompanionMoveType moveType = RideHomeJourneySupport.resolveMoveType(player, data, mountUuid);
        BlockPos summonStage = CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, false);
        Journey journey = new Journey(player.getUUID(), mountUuid, moveType,
                summonStage, homePosition,
                player.isInvisible(), player.isNoGravity());
        journey.headingYaw = Mth.wrapDegrees(player.getYRot());
        journey.destinationDimension = destination.dimension();
        journey.ticket = RideHomeJourneySupport.ChunkTicket.acquire(destination,
                homePosition.blockPos(), journey.playerUuid);
        JOURNEYS.put(player.getUUID(), journey);
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, null,
                Vec3.atBottomCenterOf(summonStage), RideHomeCameraPacket.Mode.START,
                JOURNEY_TIMEOUT_TICKS, journey.headingYaw));
        FindMeDebugLogger.info("ride-home", "started player={} mount={} moveType={} stage={}",
                player.getUUID(), mountUuid, moveType, journey.stage);
        FindMeMod.LOGGER.info("[FindMe ride-home] started player={} mount={} moveType={}",
                player.getUUID(), mountUuid, moveType);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || JOURNEYS.isEmpty()) {
            return;
        }
        for (UUID playerUuid : new ArrayList<>(JOURNEYS.keySet())) {
            Journey journey = JOURNEYS.get(playerUuid);
            if (journey == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) {
                abandon(server, journey, "journey_player_unavailable");
                continue;
            }
            if (!FindMeModuleService.enabled(FindMeModule.RIDING)
                    || !FindMeModuleService.enabled(FindMeModule.HOUSES)) {
                cancel(player, journey, "journey_module_disabled");
                continue;
            }
            journey.totalAge++;
            journey.stageAge++;
            if (!player.isAlive() || journey.totalAge >= JOURNEY_TIMEOUT_TICKS) {
                cancel(player, journey, player.isAlive() ? "journey_timeout" : "journey_player_dead");
                continue;
            }
            switch (journey.stage) {
                case ACQUIRING -> tickAcquiring(player, journey);
                case DEPARTING -> tickDeparting(player, journey);
                case LOADING_HOME -> tickLoadingHome(player, journey);
                case STORING -> tickStoring(player, journey);
                case WAITING_CLIENT -> tickWaitingClient(player, journey);
                case RESTORING -> tickRestoring(player, journey);
                case ARRIVING -> tickArriving(player, journey);
            }
        }
    }

    public static void onClientReady(ServerPlayer player, UUID mountUuid) {
        Journey journey = player == null ? null : JOURNEYS.get(player.getUUID());
        if (journey != null && journey.stage == Stage.WAITING_CLIENT
                && journey.mountUuid.equals(mountUuid)) {
            journey.destinationReady = true;
            FindMeMod.LOGGER.info("[FindMe ride-home] client route ready player={} mount={} waitTicks={}",
                    player.getUUID(), mountUuid, journey.stageAge);
        }
    }

    public static int cancelForPlayer(ServerPlayer player, String reason) {
        if (player == null) {
            return 0;
        }
        Journey removed = JOURNEYS.remove(player.getUUID());
        if (removed == null) {
            return 0;
        }
        cancelDetached(player, removed, reason);
        return 1;
    }

    static boolean cancelAcquiringRescue(ServerPlayer player, UUID mountUuid, String reason) {
        Journey journey = player == null ? null : JOURNEYS.get(player.getUUID());
        if (journey == null || journey.stage != Stage.ACQUIRING
                || !journey.mountUuid.equals(mountUuid)) {
            return false;
        }
        cancel(player, journey, reason);
        return true;
    }

    public static boolean bypassesTravelLifecycle(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Journey journey = JOURNEYS.get(player.getUUID());
        return ROLLBACK_TELEPORT_BYPASS.contains(player.getUUID())
                || journey != null && journey.internalTeleport;
    }

    /** True only while ride-home owns the mounted entity's movement instead of normal client riding input. */
    public static boolean ownsMountedMovement(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Journey journey = JOURNEYS.get(player.getUUID());
        if (journey == null || journey.stage != Stage.DEPARTING
                && journey.stage != Stage.LOADING_HOME
                && journey.stage != Stage.RESTORING
                && journey.stage != Stage.ARRIVING) {
            return false;
        }
        Entity vehicle = player.getVehicle();
        return vehicle != null && journey.mountUuid.equals(vehicle.getUUID());
    }

    private static void tickAcquiring(ServerPlayer player, Journey journey) {
        if (journey.stageAge < CAMERA_LEAD_TICKS) {
            return;
        }
        boolean descendingAirborne = isDescendingAirborne(player);
        if (descendingAirborne) {
            // Ride-home owns this rescue attempt. Do not let fall damage win while
            // its ordinary approach animation is still trying to make contact.
            player.fallDistance = 0.0f;
            player.invulnerableTime = Math.max(player.invulnerableTime,
                    com.kuzhi.findme.Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        LivingEntity mount = CompanionDeployRequestService.readyEntity(player, data, journey.mountUuid,
                CompanionDeployRequestService.Mode.RIDDEN);
        if (mount == null && !journey.summonRequested
                && !CompanionLifecycleFacade.isBusy(player, data, journey.mountUuid)) {
            CompanionDeployRequestService.Result result = CompanionDeployRequestService.request(player, data,
                    CompanionKind.MOUNT, journey.mountUuid, CompanionDeployRequestService.Mode.RIDDEN,
                    "home:ride_home_departure_deploy");
            if (result.state() == CompanionDeployRequestService.State.REJECTED) {
                cancel(player, journey, "journey_mount_deploy_rejected");
                return;
            }
            journey.summonRequested = result.state() == CompanionDeployRequestService.State.STARTED;
            mount = result.entity();
        }
        if (mount == null) {
            mount = findMount(player.getServer(), journey.mountUuid);
        }
        if (mount != null && !journey.cameraTracking) {
            journey.cameraTracking = true;
            ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                    RideHomeCameraPacket.Mode.TRACK, JOURNEY_TIMEOUT_TICKS, mount.getYRot()));
        }
        if (mount == null || !mount.isAlive()) {
            if (acquisitionTimedOut(journey.stageAge)) {
                cancel(player, journey, "journey_mount_deploy_timeout");
            }
            return;
        }
        CompanionHomeResidentService.clearResident(mount);
        if (player.getVehicle() != mount) {
            journey.ridingStableTicks = 0;
        }
        if (player.getVehicle() != mount) {
            // The deploy request owns rescue approach and boarding. Ride-home begins
            // only after the shared rescue flow has actually mounted the player.
            if (acquisitionTimedOut(journey.stageAge)) {
                cancel(player, journey, "journey_mount_contact_timeout");
            }
            return;
        }
        RideHomeJourneySupport.stabilize(player, mount);
        if (++journey.ridingStableTicks < RIDE_STABLE_TICKS) {
            return;
        }
        journey.moveType = CompanionEntityClassifier.moveType(mount, CompanionKind.MOUNT);
        journey.travelDirection = RideHomeJourneySupport.departureDirection(player, mount);
        if (!updateHomeReadiness(player, journey)) {
            if (journey.stageAge >= HOME_LOAD_TIMEOUT_TICKS) {
                cancel(player, journey, "journey_home_chunk_timeout_before_departure");
            }
            return;
        }
        if (journey.arrivalPlan == null) {
            ServerLevel destination = player.getServer().getLevel(journey.destinationDimension);
            if (destination == null) {
                cancel(player, journey, "journey_home_dimension_lost_before_departure");
                return;
            }
            RideHomeJourneySupport.ArrivalPlan plan = RideHomeJourneySupport.planArrival(destination, mount,
                    journey.homePosition.blockPos(), journey.travelDirection, journey.moveType, player.getRandom())
                    .orElse(null);
            if (plan == null) {
                cancel(player, journey, "journey_home_route_unavailable_before_departure");
                return;
            }
            journey.arrivalPlan = plan;
            journey.arrivalStart = plan.start();
            journey.arrivalTarget = plan.target();
            journey.arrivalMoveType = plan.moveType();
            FindMeMod.LOGGER.info("[FindMe ride-home] route planned before departure player={} mount={} departureType={} arrivalType={} start={} target={}",
                    player.getUUID(), journey.mountUuid, journey.moveType, journey.arrivalMoveType,
                    journey.arrivalStart, journey.arrivalTarget);
        }
        if (!journey.transactionStarted) {
            if (!CompanionOperationLockService.tryBegin(player, journey.mountUuid,
                    CompanionOperationLockService.Operation.JOURNEY, "wheel:ride_home",
                    JOURNEY_TIMEOUT_TICKS - journey.totalAge)) {
                journey.ridingStableTicks = 0;
                return;
            }
            journey.transactionStarted = true;
            journey.origin = RideHomeJourneyTransaction.capture(player, data, journey.mountUuid);
            journey.originalMountPosition = SavedPosition.of(mount.level(), mount.getX(), mount.getY(),
                    mount.getZ(), mount.getYRot(), mount.getXRot());
        }
        CompanionMountCinematicFlowService.cancelForCompanion(player, mount.getUUID(),
                "ride_home_departure_ready");
        journey.physics = RideHomeJourneySupport.capturePhysics(mount);
        journey.headingYaw = RideHomeJourneySupport.headingYaw(journey.travelDirection, mount.getYRot());
        journey.departureTarget = RideHomeJourneySupport.departureTarget(mount, journey.travelDirection,
                journey.moveType);
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
            cancel(player, journey, "journey_departure_ride_lost");
            return;
        }
        RideHomeJourneySupport.prepareCinematic(mount, journey.moveType);
        CompanionCinematicMovementService.moveMountedRideHome(mount, player, journey.moveType,
                journey.departureTarget, journey.stageAge, false);
        syncCinematicMount(player, mount);
        RideHomeJourneySupport.stabilize(player, mount);
        updateHomeReadiness(player, journey);
        if (journey.stageAge >= DEPARTURE_TICKS) {
            journey.holdAnchor = mount.position();
            enter(journey, Stage.LOADING_HOME, player, mount);
        }
    }

    private static void tickLoadingHome(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!RideHomeJourneySupport.holdRide(player, mount, journey.mountUuid, journey.holdAnchor,
                journey.headingYaw, journey.moveType)) {
            cancel(player, journey, "journey_loading_ride_lost");
            return;
        }
        syncCinematicMount(player, mount);
        if (!updateHomeReadiness(player, journey)) {
            if (journey.stageAge >= HOME_LOAD_TIMEOUT_TICKS) {
                cancel(player, journey, "journey_home_chunk_timeout");
            }
            return;
        }
        if (journey.arrivalPlan == null) {
            cancel(player, journey, "journey_home_route_missing");
            return;
        }
        journey.headingYaw = journey.arrivalPlan.headingYaw();
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.BLACKOUT, BLACKOUT_FADE_TICKS, journey.headingYaw));
        enter(journey, Stage.STORING, player, mount);
    }

    private static boolean updateHomeReadiness(ServerPlayer player, Journey journey) {
        if (journey.ticket == null || journey.homePosition == null
                || !journey.ticket.ready(player.getServer(), journey.homePosition.blockPos())) {
            journey.homeReadyTicks = 0;
            return false;
        }
        journey.homeReadyTicks = Math.min(HOME_READY_STABLE_TICKS, journey.homeReadyTicks + 1);
        return journey.homeReadyTicks >= HOME_READY_STABLE_TICKS;
    }

    private static void tickStoring(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!RideHomeJourneySupport.validRide(player, mount, journey.mountUuid)) {
            cancel(player, journey, "journey_storage_ride_lost");
            return;
        }
        if (journey.stageAge < BLACKOUT_TRANSFER_DELAY_TICKS) {
            RideHomeJourneySupport.holdRide(player, mount, journey.mountUuid, journey.holdAnchor,
                    journey.headingYaw, journey.moveType);
            syncCinematicMount(player, mount);
            return;
        }
        ServerLevel destination = player.getServer().getLevel(journey.destinationDimension);
        if (destination == null) {
            cancel(player, journey, "journey_home_dimension_lost_before_transfer");
            return;
        }
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        player.stopRiding();
        mount.ejectPassengers();
        PlayerCompanionData data = CompanionDataService.data(player);
        if (!CompanionStorageService.storeImmediatelyForJourney(player, data, CompanionKind.MOUNT, mount)) {
            cancel(player, journey, "journey_transfer_storage_failed");
            return;
        }
        journey.storedForTransfer = true;
        hidePlayerForTransfer(player, journey);
        journey.internalTeleport = true;
        try {
            RideHomeJourneySupport.transferPlayer(player, destination, journey.arrivalStart,
                    journey.headingYaw, journey.origin.playerPitch());
        } finally {
            journey.internalTeleport = false;
        }
        journey.holdAnchor = null;
        journey.arrivalRouteDistance = horizontalDistance(journey.arrivalStart, journey.arrivalTarget);
        enter(journey, Stage.WAITING_CLIENT, player, null);
        sendDestinationWait(player, journey);
        FindMeMod.LOGGER.info("[FindMe ride-home] transfer boundary stored player={} mount={} destination={} start={} target={}",
                player.getUUID(), journey.mountUuid, journey.destinationDimension,
                journey.arrivalStart, journey.arrivalTarget);
    }

    private static void tickWaitingClient(ServerPlayer player, Journey journey) {
        boolean held;
        journey.internalTeleport = true;
        try {
            held = RideHomeJourneySupport.holdTransferredPlayer(player, journey.destinationDimension,
                    journey.arrivalStart, journey.headingYaw, journey.origin.playerPitch());
        } finally {
            journey.internalTeleport = false;
        }
        if (!held) {
            cancel(player, journey, "journey_destination_player_lost");
            return;
        }
        if (journey.stageAge == 1 || journey.stageAge % 40 == 0) {
            sendDestinationWait(player, journey);
        }
        if (!journey.destinationReady) {
            if (journey.stageAge >= CLIENT_READY_TIMEOUT_TICKS) {
                cancel(player, journey, "journey_client_destination_timeout");
            }
            return;
        }
        enter(journey, Stage.RESTORING, player, null);
    }

    private static void tickRestoring(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!journey.destinationRestored) {
            ServerLevel destination = player.getServer().getLevel(journey.destinationDimension);
            if (destination == null || player.level() != destination) {
                cancel(player, journey, "journey_restore_dimension_lost");
                return;
            }
            PlayerCompanionData data = CompanionDataService.data(player);
            mount = CompanionLifecycleFacade.restoreStoredForJourney(destination, player, data,
                            journey.mountUuid,
                            BlockPos.containing(journey.arrivalStart), journey.headingYaw,
                            journey.arrivalMoveType == CompanionMoveType.FLY ? 8.0f : 0.0f,
                            "home:ride_home_destination_restore")
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .orElse(null);
            if (mount == null || !journey.mountUuid.equals(mount.getUUID())) {
                cancel(player, journey, "journey_destination_restore_failed");
                return;
            }
            mount.moveTo(journey.arrivalStart.x, journey.arrivalStart.y, journey.arrivalStart.z,
                    journey.headingYaw, journey.arrivalMoveType == CompanionMoveType.FLY ? 8.0f : 0.0f);
            mount.setPos(journey.arrivalStart.x, journey.arrivalStart.y, journey.arrivalStart.z);
            mount.setDeltaMovement(Vec3.ZERO);
            CompanionHomeResidentService.clearResident(mount);
            RideHomeJourneySupport.prepareCinematic(mount, journey.arrivalMoveType);
            RideHomeJourneySupport.stabilize(player, mount);
            journey.destinationRestored = true;
            if (!CompanionMountCinematicFlowService.startRidingAfterContact(player, mount,
                    MountCinematicMode.RIDE_HOME)) {
                cancel(player, journey, "journey_destination_remount_failed");
                return;
            }
            journey.ridingStableTicks = 0;
            RideHomeJourneyTransaction.markDeployed(player, mount, false);
            player.connection.send(new ClientboundSetPassengersPacket(mount));
            FindMeMod.LOGGER.info("[FindMe ride-home] destination restored player={} mount={} position={}",
                    player.getUUID(), journey.mountUuid, mount.position());
        }
        if (!RideHomeJourneySupport.holdRide(player, mount, journey.mountUuid, journey.arrivalStart,
                journey.headingYaw, journey.arrivalMoveType)) {
            cancel(player, journey, "journey_destination_remount_lost");
            return;
        }
        syncCinematicMount(player, mount);
        player.connection.send(new ClientboundSetPassengersPacket(mount));
        if (++journey.ridingStableTicks < RIDE_STABLE_TICKS) {
            return;
        }
        restorePlayerAfterTransfer(player, journey);
        journey.internalTeleport = false;
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.REVEAL, REVEAL_FADE_TICKS, journey.headingYaw));
        enter(journey, Stage.ARRIVING, player, mount);
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount, journey.arrivalTarget,
                RideHomeCameraPacket.Mode.TRACK, JOURNEY_TIMEOUT_TICKS, journey.headingYaw));
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.HEADING, 1, journey.headingYaw));
    }

    private static void sendDestinationWait(ServerPlayer player, Journey journey) {
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, null, journey.arrivalTarget,
                RideHomeCameraPacket.Mode.DESTINATION, JOURNEY_TIMEOUT_TICKS, journey.headingYaw));
        ModNetwork.sendToPlayer(player, new RideHomeDestinationPacket(journey.mountUuid,
                journey.arrivalStart.x, journey.arrivalStart.y, journey.arrivalStart.z,
                journey.arrivalTarget.x, journey.arrivalTarget.y, journey.arrivalTarget.z));
    }

    private static void tickArriving(ServerPlayer player, Journey journey) {
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        if (!validArrivalRide(player, mount, journey)) {
            cancel(player, journey, "journey_arrival_ride_lost");
            return;
        }
        if (moveArrival(player, mount, journey)) {
            complete(player, journey, mount);
        }
    }

    private static boolean moveArrival(ServerPlayer player, LivingEntity mount, Journey journey) {
        RideHomeJourneySupport.prepareCinematic(mount, journey.arrivalMoveType);
        RideHomeJourneySupport.stabilize(player, mount);
        double dx = journey.arrivalTarget.x - mount.getX();
        double dz = journey.arrivalTarget.z - mount.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double contactRadius = Mth.clamp(mount.getBbWidth() * 0.5 + 0.8, 1.25, 2.5);
        if (horizontalDistance > contactRadius && journey.arrivalMotionAge < ARRIVAL_TIMEOUT_TICKS
                && journey.arrivalTravelled + contactRadius < journey.arrivalRouteDistance) {
            journey.arrivalTravelled += CompanionCinematicMovementService.moveMountedRideHome(mount, player,
                    journey.arrivalMoveType, journey.arrivalTarget, ++journey.arrivalMotionAge, true);
            syncCinematicMount(player, mount);
            return false;
        }
        FindMeMod.LOGGER.info("[FindMe ride-home] arrival reached player={} mount={} horizontalDistance={} radius={} travelled={} routeDistance={} motionTicks={}",
                player.getUUID(), journey.mountUuid, horizontalDistance, contactRadius,
                journey.arrivalTravelled, journey.arrivalRouteDistance, journey.arrivalMotionAge);
        RideHomeJourneySupport.settleAtCurrentPosition(mount, journey.headingYaw);
        syncCinematicMount(player, mount);
        return true;
    }

    private static boolean validArrivalRide(ServerPlayer player, LivingEntity mount, Journey journey) {
        return RideHomeJourneySupport.validRide(player, mount, journey.mountUuid)
                && journey.destinationDimension != null
                && player.level().dimension().equals(journey.destinationDimension)
                && mount.level().dimension().equals(journey.destinationDimension);
    }

    private static void complete(ServerPlayer player, Journey journey, LivingEntity mount) {
        if (!JOURNEYS.remove(player.getUUID(), journey)) {
            return;
        }
        restorePlayerAfterTransfer(player, journey);
        journey.internalTeleport = false;
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        CompanionHomeResidentService.clearResident(mount);
        RideHomeJourneyTransaction.markDeployed(player, mount, true);
        CompanionMountSettleProtectionService.rememberSettledMount(player, mount);
        journey.releaseTicket(player.getServer());
        CompanionOperationLockService.end(player, journey.mountUuid,
                CompanionOperationLockService.Operation.JOURNEY, "journey_complete");
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.STOP, 12, journey.headingYaw));
        FindMeDebugLogger.info("ride-home", "completed player={} mount={} target={} departureType={} arrivalType={}",
                player.getUUID(), journey.mountUuid, journey.arrivalTarget, journey.moveType,
                journey.arrivalMoveType);
        FindMeMod.LOGGER.info("[FindMe ride-home] completed player={} mount={} departureType={} arrivalType={} ticks={} position={}",
                player.getUUID(), journey.mountUuid, journey.moveType, journey.arrivalMoveType,
                journey.totalAge, mount.position());
        CompanionWheelTransactionService.complete(player, CompanionKind.MOUNT, CompanionAction.RIDE_HOME,
                journey.mountUuid, "journey_complete");
    }

    private static LivingEntity findMount(MinecraftServer server, UUID uuid) {
        Entity entity = CompanionEntityLookup.findEntity(server, uuid).orElse(null);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void syncCinematicMount(ServerPlayer player, LivingEntity mount) {
        if (player != null && mount != null && player.getVehicle() == mount) {
            player.connection.send(new ClientboundMoveVehiclePacket(mount));
        }
    }

    private static void hidePlayerForTransfer(ServerPlayer player, Journey journey) {
        if (journey.playerHidden) {
            return;
        }
        journey.playerHidden = true;
        player.setInvisible(true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
    }

    private static void restorePlayerAfterTransfer(ServerPlayer player, Journey journey) {
        boolean transferred = journey.playerHidden || journey.storedForTransfer || journey.destinationRestored;
        if (!transferred) {
            return;
        }
        journey.playerHidden = false;
        player.setInvisible(journey.originalPlayerInvisible);
        player.setNoGravity(journey.originalPlayerNoGravity);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        RideHomeJourneySupport.syncPlayerAfterTransfer(player);
    }

    private static void enter(Journey journey, Stage next, ServerPlayer player, LivingEntity mount) {
        Stage previous = journey.stage;
        journey.stage = next;
        journey.stageAge = 0;
        FindMeDebugLogger.info("ride-home", "stage player={} mount={} {} -> {} pos={}",
                player.getUUID(), journey.mountUuid, previous, next,
                mount == null ? "missing" : mount.position());
        FindMeMod.LOGGER.info("[FindMe ride-home] stage player={} mount={} {} -> {} ticks={} position={}",
                player.getUUID(), journey.mountUuid, previous, next, journey.totalAge,
                mount == null ? "missing" : mount.position());
    }

    private static void cancel(ServerPlayer player, Journey journey, String reason) {
        if (!JOURNEYS.remove(player.getUUID(), journey)) {
            return;
        }
        cancelDetached(player, journey, reason);
    }

    private static void cancelDetached(ServerPlayer player, Journey journey, String reason) {
        CompanionMountCinematicFlowService.cancelForCompanion(player, journey.mountUuid, reason);
        LivingEntity mount = findMount(player.getServer(), journey.mountUuid);
        restorePlayerAfterTransfer(player, journey);
        journey.internalTeleport = false;
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        if (journey.transactionStarted && journey.origin != null) {
            ROLLBACK_TELEPORT_BYPASS.add(player.getUUID());
            try {
                RideHomeJourneyTransaction.rollback(player, journey.mountUuid, journey.origin,
                        journey.originalMountPosition, false,
                        journey.storedForTransfer, journey.destinationRestored, mount);
            } finally {
                ROLLBACK_TELEPORT_BYPASS.remove(player.getUUID());
            }
            CompanionOperationLockService.end(player, journey.mountUuid,
                    CompanionOperationLockService.Operation.JOURNEY, reason);
        }
        journey.releaseTicket(player.getServer());
        ModNetwork.sendToPlayer(player, RideHomeJourneySupport.camera(player, mount,
                RideHomeCameraPacket.Mode.STOP, 1, journey.headingYaw));
        FindMeDebugLogger.info("ride-home", "cancelled player={} mount={} stage={} reason={}",
                player.getUUID(), journey.mountUuid, journey.stage, reason);
        FindMeMod.LOGGER.warn("[FindMe ride-home] cancelled player={} mount={} stage={} reason={} ticks={}",
                player.getUUID(), journey.mountUuid, journey.stage, reason, journey.totalAge);
        CompanionWheelTransactionService.cancel(player, CompanionKind.MOUNT, CompanionAction.RIDE_HOME,
                journey.mountUuid, reason);
    }

    private static void abandon(MinecraftServer server, Journey journey, String reason) {
        JOURNEYS.remove(journey.playerUuid, journey);
        LivingEntity mount = findMount(server, journey.mountUuid);
        RideHomeJourneySupport.restorePhysics(mount, journey.physics);
        journey.releaseTicket(server);
        if (journey.transactionStarted) {
            CompanionOperationLockService.clear(null, journey.mountUuid, reason);
        }
    }

    private enum Stage {
        ACQUIRING,
        DEPARTING,
        LOADING_HOME,
        STORING,
        WAITING_CLIENT,
        RESTORING,
        ARRIVING
    }

    private static final class Journey {
        private final UUID playerUuid;
        private final UUID mountUuid;
        private CompanionMoveType moveType;
        private CompanionMoveType arrivalMoveType;
        private RideHomeJourneySupport.ArrivalPlan arrivalPlan;
        private RideHomeJourneyTransaction.Origin origin;
        private final BlockPos summonStage;
        private Stage stage = Stage.ACQUIRING;
        private boolean summonRequested;
        private boolean cameraTracking;
        private boolean transactionStarted;
        private boolean storedForTransfer;
        private boolean destinationRestored;
        private boolean destinationReady;
        private boolean internalTeleport;
        private boolean playerHidden;
        private final boolean originalPlayerInvisible;
        private final boolean originalPlayerNoGravity;
        private int totalAge;
        private int stageAge;
        private int ridingStableTicks;
        private int homeReadyTicks;
        private int arrivalMotionAge;
        private double arrivalTravelled;
        private double arrivalRouteDistance;
        private float headingYaw;
        private Vec3 travelDirection = Vec3.ZERO;
        private Vec3 departureTarget = Vec3.ZERO;
        private Vec3 holdAnchor = Vec3.ZERO;
        private Vec3 arrivalStart = Vec3.ZERO;
        private Vec3 arrivalTarget = Vec3.ZERO;
        private SavedPosition originalMountPosition;
        private SavedPosition homePosition;
        private ResourceKey<Level> destinationDimension;
        private RideHomeJourneySupport.Physics physics;
        private RideHomeJourneySupport.ChunkTicket ticket;

        private Journey(UUID playerUuid, UUID mountUuid, CompanionMoveType moveType,
                        BlockPos summonStage,
                        SavedPosition homePosition, boolean originalPlayerInvisible,
                        boolean originalPlayerNoGravity) {
            this.playerUuid = playerUuid;
            this.mountUuid = mountUuid;
            this.moveType = moveType;
            this.arrivalMoveType = moveType;
            this.summonStage = summonStage;
            this.homePosition = homePosition;
            this.originalPlayerInvisible = originalPlayerInvisible;
            this.originalPlayerNoGravity = originalPlayerNoGravity;
            this.headingYaw = 0.0f;
        }

        private void releaseTicket(MinecraftServer server) {
            if (this.ticket != null) {
                this.ticket.release(server);
                this.ticket = null;
            }
        }
    }
}
