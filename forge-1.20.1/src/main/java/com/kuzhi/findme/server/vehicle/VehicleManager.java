package com.kuzhi.findme.server.vehicle;

import com.kuzhi.findme.compat.vehicles.ExternalVehicleBridge;
import com.kuzhi.findme.server.lifecycle.CompanionDeploymentService;
import com.kuzhi.findme.server.lifecycle.ExactEntityTeleporter;

import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;

import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.RideHandoffService;

import com.kuzhi.findme.server.lifecycle.CompanionArrivalMagicService;
import com.kuzhi.findme.server.animation.CompanionEnderEffectService;
import com.kuzhi.findme.server.animation.CompanionMagicAudioService;
import com.kuzhi.findme.server.ui.WarehouseEntityService;
import com.kuzhi.findme.server.ui.CompanionTeamService;



import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import com.kuzhi.findme.server.profile.CompanionBindingProfileService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.core.CompanionOperationLockService;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.NamePaperItem;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.common.MountRosterAction;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RescueMagicPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import com.kuzhi.findme.network.VehicleSealEffectPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Method;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.kuzhi.findme.server.ui.CompanionSyncService;
public final class VehicleManager {
    private static final String VEHICLE_MARKER = "FindMeVehicle";
    private static final int VEHICLE_SUMMON_MAGIC_TICKS = 36;
    private static final int VEHICLE_SEAL_TICKS = 34;
    private static final double VEHICLE_EFFECT_PACKET_RADIUS = 96.0;
    private static final int SWITCH_DAMAGE_PROTECTION_TICKS = 80;
    private static final List<PendingVehicleSummon> PENDING_SUMMONS = new ArrayList<>();
    private static final List<VehicleSwitchDamageProtection> SWITCH_DAMAGE_PROTECTIONS = new ArrayList<>();

    private VehicleManager() {
    }

    public static boolean isBinder(ItemStack stack) {
        return stack.is(ModItems.VEHICLE_BINDER.get());
    }

    public static boolean handleBinderInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!isBinder(event.getItemStack())) {
            return false;
        }
        Player player = event.getEntity();
        event.setCanceled(true);
        if (player.level().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return true;
        }
        boolean bound = bindVehicle(serverPlayer, event.getTarget(), event.getHand());
        event.setCancellationResult(bound ? InteractionResult.CONSUME : InteractionResult.FAIL);
        return true;
    }

    public static void handle(ServerPlayer player, VehicleCommandAction action, UUID targetUuid, int position) {
        if (ExternalVehicleBridge.available()) {
            ExternalVehicleBridge.command(player, action, targetUuid, position);
            if (ExternalVehicleBridge.available()) return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        switch (action) {
            case SYNC -> syncToClient(player);
            case STORE_CURRENT -> CompanionLifecycleFacade.collectCurrentRide(player, data, "vehicle_command:store_current");
            case SELECT -> {
                if (targetUuid == null || !data.containsVehicle(targetUuid)) {
                    syncToClient(player);
                    return;
                }
                if (rejectBusy(player, data, targetUuid, "vehicle:select")) {
                    syncToClient(player);
                    return;
                }
                cancelPendingSummons(player.getUUID());
                if (data.setActiveVehicleUuid(targetUuid)) CompanionDataService.save(player, data);
                syncToClient(player);
            }
            case SELECT_SUMMON -> {
                if (targetUuid == null || !data.containsVehicle(targetUuid)) {
                    syncToClient(player);
                    return;
                }
                if (rejectBusy(player, data, targetUuid, "vehicle:select_summon")) {
                    syncToClient(player);
                    return;
                }
                if (data.setActiveVehicleUuid(targetUuid)) {
                    CompanionDataService.save(player, data);
                    CompanionLifecycleFacade.summonOrStoreActiveVehicle(player, data, "vehicle_command:select_summon");
                } else {
                    tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
                }
                syncToClient(player);
            }
            case RECALL -> {
                if (targetUuid != null && data.containsVehicle(targetUuid)) {
                    CompanionLifecycleFacade.recallVehicleUuid(player, data, targetUuid, "vehicle_command:recall");
                }
                syncToClient(player);
            }
            case REORDER_WHEEL -> {
                int from = data.vehicleWheelOrder().indexOf(targetUuid);
                if (from >= 0 && rejectBusy(player, data, targetUuid, "vehicle:reorder_wheel")) {
                    syncToClient(player);
                    return;
                }
                if (data.reorderVehicleWheel(from, position)) {
                    CompanionDataService.save(player, data);
                }
                syncToClient(player);
            }
            case ADD_TO_WHEEL -> {
                if (targetUuid != null && data.containsVehicle(targetUuid)) {
                    if (!rejectBusy(player, data, targetUuid, "vehicle:add_to_wheel")) {
                        data.addVehicleWheelSlot(targetUuid);
                        CompanionDataService.save(player, data);
                    }
                }
                syncToClient(player);
            }
            case REMOVE_FROM_WHEEL -> {
                if (targetUuid != null && data.containsVehicle(targetUuid)
                        && !rejectBusy(player, data, targetUuid, "vehicle:remove_from_wheel")) {
                    data.removeVehicleWheelSlot(targetUuid);
                    CompanionDataService.save(player, data);
                }
                syncToClient(player);
            }
            case REMOVE -> {
                removeUuidCommand(player, data, targetUuid);
                syncToClient(player);
            }
        }
    }

    public static boolean bindVehicle(ServerPlayer player, Entity target, InteractionHand hand) {
        if (ExternalVehicleBridge.available()) {
            return false;
        }
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return false;
        }
        Entity profileTarget = unwrapPart(target);
        if (!CompanionBindingProfileService.allowsVehicleBinding(entityType(profileTarget), false)) {
            tell(player, "message.find_me.vehicle_not_supported", ChatFormatting.YELLOW);
            return false;
        }
        Entity vehicle = unwrapPart(target);
        if (!isBindableVehicle(vehicle)) {
            tell(player, "message.find_me.vehicle_not_supported", ChatFormatting.YELLOW);
            return false;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        UUID uuid = vehicle.getUUID();
        if (data.containsVehicle(uuid)) {
            data.setActiveVehicleUuid(uuid);
            CompanionDataService.save(player, data);
            syncToClient(player);
            tell(player, "message.find_me.vehicle_already_bound", ChatFormatting.YELLOW, Component.literal(storedName(data, uuid)));
            return true;
        }
        if (!storeVehicle(player, data, vehicle)) {
            tell(player, "message.find_me.vehicle_store_failed", ChatFormatting.RED);
            return false;
        }
        data.addVehicle(uuid);
        WarehouseEntityService.notifyAutoTeam(player, data, uuid, storedName(data, uuid));
        data.clearDeployedVehicle(uuid);
        NamePaperItem.consumeOne(player.getItemInHand(hand), player);
        CompanionDataService.save(player, data);
        syncToClient(player);
        CompanionTeamService.syncToClient(player);
        tell(player, "message.find_me.vehicle_bound", ChatFormatting.GREEN, Component.literal(storedName(data, uuid)));
        return true;
    }

    public static boolean handleRosterAction(ServerPlayer player, MountRosterAction action, UUID targetUuid) {
        if (ExternalVehicleBridge.available()) {
            VehicleCommandAction translated = switch (action) {
                case SELECT -> VehicleCommandAction.SELECT;
                case ACTIVATE -> VehicleCommandAction.SELECT_SUMMON;
                case RECALL -> VehicleCommandAction.RECALL;
            };
            boolean result = ExternalVehicleBridge.command(player, translated, targetUuid, -1);
            if (ExternalVehicleBridge.available()) return result;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (action == null || targetUuid == null || !data.containsVehicle(targetUuid)) return false;
        if (rejectBusy(player, data, targetUuid, "vehicle:merged_roster_" + action.name().toLowerCase())) {
            syncToClient(player);
            return false;
        }
        boolean accepted = switch (action) {
            case SELECT -> {
                cancelPendingSummons(player.getUUID());
                boolean selected = data.setActiveVehicleUuid(targetUuid);
                if (selected) CompanionDataService.save(player, data);
                yield selected;
            }
            case ACTIVATE -> {
                if (!data.setActiveVehicleUuid(targetUuid)) yield false;
                CompanionDataService.save(player, data);
                yield CompanionLifecycleFacade.summonOrStoreActiveVehicle(
                        player, data, "vehicle:merged_roster_activate");
            }
            case RECALL -> CompanionLifecycleFacade.recallVehicleUuid(
                    player, data, targetUuid, "vehicle:merged_roster_recall");
        };
        syncToClient(player);
        return accepted;
    }

    public static boolean isRideReady(ServerPlayer player, PlayerCompanionData data, UUID targetUuid) {
        if (player == null || data == null || targetUuid == null) return false;
        RideHandoffService.Source source = RideHandoffService.resolveSource(player, data);
        return source.present() && targetUuid.equals(source.uuid())
                && source.type() == RideHandoffService.SourceType.ENTITY_VEHICLE;
    }

    public static void cancelRosterActivation(ServerPlayer player, UUID targetUuid, String reason) {
        if (player == null) return;
        cancelPendingSummons(player.getUUID());
        VehicleCinematicService.cancelForPlayer(player, "mount_roster:" + reason);
        if (targetUuid == null) return;
        PlayerCompanionData data = CompanionDataService.data(player);
        if (isRideReady(player, data, targetUuid) || !data.containsVehicle(targetUuid)) {
            return;
        }
        if (data.isVehicleDeployed(targetUuid) || data.storedEntity(targetUuid).isEmpty()) {
            recallUuid(player, data, targetUuid);
            syncToClient(player);
        }
    }

    public static boolean summonOrStoreActive(ServerPlayer player, PlayerCompanionData data) {
        if (ExternalVehicleBridge.available()) {
            boolean result = data.activeVehicle().map(uuid -> ExternalVehicleBridge.command(
                    player, VehicleCommandAction.SELECT_SUMMON, uuid, -1)).orElse(false);
            if (ExternalVehicleBridge.available()) return result;
        }
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return false;
        }
        Optional<UUID> active = data.activeVehicle();
        if (active.isEmpty()) {
            tell(player, "message.find_me.no_vehicles", ChatFormatting.YELLOW);
            return false;
        }
        UUID uuid = active.get();
        String busyReason = CompanionLifecycleFacade.busyReason(player, data, uuid);
        if (busyReason != null) {
            FindMeDebugLogger.lifecycle("VEHICLE_SUMMON_REJECTED_LOCKED", player, uuid, null,
                    busyReason, "VEHICLE", "vehicle:summon_or_store", data.storedEntity(uuid).isPresent(), false);
            tell(player, "message.find_me.busy", ChatFormatting.YELLOW,
                    data.displayName(uuid).orElse(uuid.toString().substring(0, 8)));
            return false;
        }
        Entity live = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
        Entity currentRide = VehicleSeatService.resolveCurrentRide(player);
        if (currentRide != null && currentRide.getUUID().equals(uuid)) {
            tell(player, "message.find_me.vehicle_already_summoned", ChatFormatting.YELLOW, Component.literal(storedName(data, uuid)));
            return false;
        }
        if (currentRide != null && live != currentRide) {
            return switchToVehicle(player, data, uuid, currentRide, live);
        }
        if (live != null && !live.isRemoved() && (data.isVehicleDeployed(uuid) || live.level() == player.level())) {
            tell(player, "message.find_me.vehicle_already_summoned", ChatFormatting.YELLOW, live.getDisplayName());
            return false;
        }
        data.deployedVehicle().ifPresent(previous -> {
            if (!previous.equals(uuid)) {
                CompanionEntityLookup.findEntity(player.getServer(), previous).ifPresent(entity -> storeSelected(player, data, previous, entity));
                data.clearDeployedVehicle(previous);
            }
        });
        Entity entity = live;
        if (entity == null || entity.isRemoved()) {
            if (scheduleStoredVehicleSummon(player, data, uuid)) {
                return true;
            }
            entity = null;
        } else {
            entity = moveVehicleTo(entity, player.serverLevel(), findVehicleSpotForEntity(player, entity, vehiclePlacementOrigin(player)), player.getYRot(), player.getXRot());
            sendVehicleSummonMagic(player, entity.position(), vehicleRadius(entity), vehicleHeight(entity));
        }
        if (entity == null || entity.isRemoved()) {
            data.clearDeployedVehicle(uuid);
            FindMeDebugLogger.lifecycle("VEHICLE_SUMMON_UNAVAILABLE_KEEP_RECORD", player, uuid, null,
                    "STORED", "UNKNOWN", "vehicle:summon_or_store", rawStoredTag(data, uuid).isPresent(), false);
            CompanionDataService.save(player, data);
            tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
            return false;
        }
        enforceSingleRideSlotForVehicle(player, data, uuid, currentRide);
        data.setDeployedVehicle(uuid);
        data.setLastKnownPosition(uuid, SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
        CompanionDataService.save(player, data);
        tell(player, "message.find_me.vehicle_summoned", ChatFormatting.AQUA, entity.getDisplayName());
        return true;
    }

    private static boolean switchToVehicle(ServerPlayer player, PlayerCompanionData data, UUID targetUuid, Entity currentRide, Entity liveTarget) {
        if (currentRide == null || currentRide.isRemoved()) {
            return summonOrStoreActive(player, data);
        }
        cancelPendingSummons(player.getUUID());
        RideHandoffService.Source rideSource = RideHandoffService.resolveSource(player, data);
        boolean protectingOldMount = data.contains(com.kuzhi.findme.common.CompanionKind.MOUNT, currentRide.getUUID());
        if (protectingOldMount) {
            protectOldMountFromVehicleSwitch(player, currentRide.getUUID(), targetUuid);
        }
        if (liveTarget != null && liveTarget.getFirstPassenger() != null && liveTarget.getFirstPassenger() != player) {
            clearSwitchDamageProtection(player.getUUID(), currentRide.getUUID(), targetUuid);
            tell(player, "message.find_me.occupied", ChatFormatting.RED, Component.literal(storedName(data, targetUuid)));
            return false;
        }
        Entity target = liveTarget;
        BlockPos switchPos = BlockPos.containing(currentRide.getX(), currentRide.getY(), currentRide.getZ());
        boolean restored = false;
        if (target == null || target.isRemoved()) {
            target = restoreVehicle(player, data, targetUuid, findVehicleSpotForStored(player, data, targetUuid, switchPos)).orElse(null);
            restored = target != null;
        } else if (target.level() != player.level() || target.distanceToSqr(currentRide) > 1.0) {
            target = moveVehicleTo(target, player.serverLevel(), findVehicleSpotForEntityNear(player, target, switchPos), currentRide.getYRot(), currentRide.getXRot());
        }
        if (target == null || target.isRemoved()) {
            clearSwitchDamageProtection(player.getUUID(), currentRide.getUUID(), targetUuid);
            tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
            return false;
        }
        RideHandoffService.MotionSnapshot transactionMotion = RideHandoffService.transactionMotion(
                player.getUUID(), targetUuid).orElse(null);
        if (transactionMotion == null
                || !RideHandoffService.applyCompatibleMotion(transactionMotion, target,
                CompanionEntityClassifier.moveType(target, com.kuzhi.findme.common.CompanionKind.MOUNT))) {
            target.setYRot(currentRide.getYRot());
            target.setXRot(currentRide.getXRot());
            target.setDeltaMovement(currentRide.getDeltaMovement());
        }
        target.fallDistance = 0.0f;
        boolean riding = VehicleCompatibilityService.tryBoardVehicle(player, target, currentRide);
        if (!riding) {
            if (restored || data.containsVehicle(targetUuid)) {
                storeVehicle(player, data, target);
                data.clearDeployedVehicle(targetUuid);
                CompanionDataService.save(player, data);
            }
            clearSwitchDamageProtection(player.getUUID(), currentRide.getUUID(), targetUuid);
            tell(player, "message.find_me.vehicle_switch_failed", ChatFormatting.YELLOW);
            return false;
        }
        UUID oldUuid = currentRide.getUUID();
        if (!RideHandoffService.retireSourceForSwitch(
                player, data, rideSource, targetUuid, "vehicle:switch_to_entity")) {
            if (restored || data.containsVehicle(targetUuid)) {
                storeVehicle(player, data, target);
                data.clearDeployedVehicle(targetUuid);
            }
            clearSwitchDamageProtection(player.getUUID(), oldUuid, targetUuid);
            restoreSourceRideAfterFailedRetirement(player, data, rideSource);
            CompanionDataService.save(player, data);
            tell(player, "message.find_me.vehicle_switch_failed", ChatFormatting.YELLOW);
            return false;
        }
        data.deployedVehicle().ifPresent(previous -> {
            if (!previous.equals(targetUuid) && !previous.equals(oldUuid)) {
                locateEntity(player.getServer(), data, previous).ifPresent(entity -> {
                    storeVehicle(player, data, entity);
                    data.clearDeployedVehicle(previous);
                });
            }
        });
        data.setDeployedVehicle(targetUuid);
        data.setLastKnownPosition(targetUuid, SavedPosition.of(target.level(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot()));
        enforceSingleRideSlotForVehicle(player, data, targetUuid, null);
        CompanionDataService.save(player, data);
        spawnBlinkParticles(target);
        tell(player, "message.find_me.vehicle_switched", ChatFormatting.AQUA, target.getDisplayName());
        return true;
    }

    private static boolean restoreSourceRideAfterFailedRetirement(ServerPlayer player, PlayerCompanionData data,
                                                                  RideHandoffService.Source source) {
        if (source == null || !source.present() || source.type() == RideHandoffService.SourceType.OTHER) {
            return true;
        }
        boolean restored = switch (source.type()) {
            case FINDME_MOUNT, ENTITY_VEHICLE ->
                    VehicleCompatibilityService.restorePreviousRide(player, source.entity());
            case NONE, OTHER -> true;
        };
        FindMeDebugLogger.info("ride-handoff",
                "phase=SOURCE_RIDE_ROLLBACK player={} sourceType={} source={} result={}",
                player.getUUID(), source.type(), source.uuid(), restored);
        return restored;
    }

    public static void syncToClient(ServerPlayer player) {
        if (ExternalVehicleBridge.available() && ExternalVehicleBridge.sendRoster(player)) return;
        PlayerCompanionData data = CompanionDataService.data(player);
        List<VehicleListPacket.Entry> wheelEntries = entries(player, data, data.vehicleWheelOrder());
        List<VehicleListPacket.Entry> allEntries = entries(player, data, data.vehicleList());
        FindMeDebugLogger.info("vehicle-roster",
                "phase=SYNC player={} revision={} vehicles={} wheel={} warehouse={} activeIndex={}",
                player.getUUID(), CompanionDataService.revision(player), allEntries.size(), wheelEntries.size(),
                data.vehicleWarehouseOrder().size(), data.activeVehicleIndex());
        ModNetwork.sendToPlayer(player, new VehicleListPacket(CompanionDataService.revision(player),
                data.activeVehicleIndex(), wheelEntries, allEntries));
    }

    public static void collectDeployed(ServerPlayer player) {
        if (ExternalVehicleBridge.available()) {
            ExternalVehicleBridge.command(player, VehicleCommandAction.STORE_CURRENT, null, -1);
            if (ExternalVehicleBridge.available()) return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        cancelPendingSummons(player.getUUID());
        data.deployedVehicle().ifPresent(uuid -> {
            Entity entity = locateEntity(player.getServer(), data, uuid).orElse(null);
            boolean collected = false;
            if (entity != null && !entity.isRemoved()) {
                collected = storeVehicle(player, data, entity);
            } else {
                collected = true;
            }
            if (collected) {
                data.clearDeployedVehicle(uuid);
            }
        });
        CompanionDataService.save(player, data);
    }

    public static void cancelRuntimeForPlayer(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        if (ExternalVehicleBridge.available()) {
            return;
        }
        UUID playerUuid = player.getUUID();
        cancelPendingSummons(playerUuid);
        SWITCH_DAMAGE_PROTECTIONS.removeIf(protection -> protection.playerUuid.equals(playerUuid));
        VehicleCinematicService.cancelForPlayer(player, reason);
        VehicleSeatService.cleanup(player);
        FindMeDebugLogger.info("module", "vehicle runtime cancelled player={} reason={}", playerUuid, reason);
    }

    public static void rename(ServerPlayer player, boolean wheel, int index, String name) {
        PlayerCompanionData data = CompanionDataService.data(player);
        List<UUID> warehouse = data.vehicleWarehouseOrder();
        Optional<UUID> uuid = wheel ? data.vehicleWheelUuidAt(index) : (index >= 0 && index < warehouse.size() ? Optional.of(warehouse.get(index)) : Optional.empty());
        if (uuid.isEmpty()) {
            tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
            return;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        data.setDisplayName(uuid.get(), trimmed);
        CompanionDataService.save(player, data);
        syncToClient(player);
    }

    public static boolean isVehicleEntry(PlayerCompanionData data, UUID uuid) {
        return data.containsVehicle(uuid) || data.isVehicleMount(uuid) || rawStoredTag(data, uuid)
                .map(tag -> tag.getBoolean(VEHICLE_MARKER) || tag.getBoolean("FindMeVehicleMount"))
                .orElse(false);
    }

    public static boolean collectVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
        if (!isVehicleEntry(data, uuid)) {
            return false;
        }
        if (rejectBusy(player, data, uuid, "vehicle:collect_direct")) {
            return false;
        }
        storeSelected(player, data, uuid, entity);
        syncToClient(player);
        return true;
    }

    public static boolean summonVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, long now, boolean inCombat) {
        if (ExternalVehicleBridge.available()) {
            boolean result = ExternalVehicleBridge.command(player, VehicleCommandAction.SELECT_SUMMON, uuid, -1);
            if (ExternalVehicleBridge.available()) return result;
        }
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return false;
        }
        if (rejectBusy(player, data, uuid, "vehicle:summon_direct")) {
            return false;
        }
        if (!data.containsVehicle(uuid)) {
            data.addVehicle(uuid);
        }
        data.setActiveVehicleUuid(uuid);
        return CompanionLifecycleFacade.summonOrStoreActiveVehicle(player, data, "vehicle_service:summon_vehicle");
    }

    public static void tickVehicleCinematics(MinecraftServer server) {
        // Vehicle support in 1.2.3 intentionally has no cinematic rescue/switch flow.
    }

    public static void tickPendingSummons(MinecraftServer server) {
        tickSwitchDamageProtections();
        Iterator<PendingVehicleSummon> iterator = PENDING_SUMMONS.iterator();
        while (iterator.hasNext()) {
            PendingVehicleSummon pending = iterator.next();
            pending.age++;
            if (pending.age < pending.delayTicks) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerUuid);
            if (player == null) {
                continue;
            }
            if (!FindMeModuleService.enabled(FindMeModule.RIDING)) {
                continue;
            }
            PlayerCompanionData data = CompanionDataService.data(player);
            if (!data.containsVehicle(pending.vehicleUuid) || data.activeVehicle().filter(pending.vehicleUuid::equals).isEmpty()) {
                continue;
            }
            RideHandoffService.Source rideSource = RideHandoffService.resolveSource(player, data);
            Entity currentRide = VehicleSeatService.resolveCurrentRide(player);
            Entity entity = restoreVehicle(player, data, pending.vehicleUuid, pending.position).orElse(null);
            if (entity == null || entity.isRemoved()) {
                data.clearDeployedVehicle(pending.vehicleUuid);
                FindMeDebugLogger.lifecycle("VEHICLE_PENDING_SUMMON_UNAVAILABLE_KEEP_RECORD", player, pending.vehicleUuid, null,
                        "STORED", "UNKNOWN", "vehicle:pending_restore", rawStoredTag(data, pending.vehicleUuid).isPresent(), false);
                CompanionDataService.save(player, data);
                syncToClient(player);
                tell(player, "message.find_me.unavailable_vehicle", ChatFormatting.RED);
                continue;
            }
            entity.setYRot(player.getYRot());
            entity.setXRot(player.getXRot());
            entity.fallDistance = 0.0f;
            if (rideSource.present() && !rideSource.uuid().equals(pending.vehicleUuid)) {
                RideHandoffService.MotionSnapshot transactionMotion = RideHandoffService.transactionMotion(
                        player.getUUID(), pending.vehicleUuid).orElse(null);
                if (transactionMotion == null
                        || !RideHandoffService.applyCompatibleMotion(transactionMotion, entity,
                        CompanionEntityClassifier.moveType(entity, CompanionKind.MOUNT))) {
                    Entity sourceEntity = rideSource.entity();
                    if (sourceEntity != null && !sourceEntity.isRemoved()) {
                        entity.setYRot(sourceEntity.getYRot());
                        entity.setXRot(sourceEntity.getXRot());
                        entity.setDeltaMovement(sourceEntity.getDeltaMovement());
                    }
                }
                if (!tryBoardManagedVehicle(player, data, entity, currentRide)) {
                    storeVehicle(player, data, entity);
                    data.clearDeployedVehicle(pending.vehicleUuid);
                    CompanionDataService.save(player, data);
                    syncToClient(player);
                    FindMeDebugLogger.info("vehicle-handoff",
                            "phase=DESTINATION_BOARD_FAILED player={} sourceType={} source={} destination={} destinationType={}",
                            player.getUUID(), rideSource.type(), rideSource.uuid(), pending.vehicleUuid,
                            entityType(entity));
                    tell(player, "message.find_me.vehicle_switch_failed", ChatFormatting.YELLOW);
                    continue;
                }
                if (!RideHandoffService.retireSourceForSwitch(player, data, rideSource,
                        pending.vehicleUuid, "vehicle:pending_entity")) {
                    storeVehicle(player, data, entity);
                    data.clearDeployedVehicle(pending.vehicleUuid);
                    restoreSourceRideAfterFailedRetirement(player, data, rideSource);
                    CompanionDataService.save(player, data);
                    syncToClient(player);
                    FindMeDebugLogger.info("vehicle-handoff",
                            "phase=SOURCE_RETIREMENT_FAILED player={} sourceType={} source={} destination={}",
                            player.getUUID(), rideSource.type(), rideSource.uuid(), pending.vehicleUuid);
                    tell(player, "message.find_me.vehicle_switch_failed", ChatFormatting.YELLOW);
                    continue;
                }
                FindMeDebugLogger.info("vehicle-handoff",
                        "phase=HANDOFF_COMMITTED player={} sourceType={} source={} destination={} destinationType={} riding={}",
                        player.getUUID(), rideSource.type(), rideSource.uuid(), pending.vehicleUuid,
                        entityType(entity), VehicleCompatibilityService.isRiding(player, entity));
            }
            enforceSingleRideSlotForVehicle(player, data, pending.vehicleUuid, currentRide);
            data.setDeployedVehicle(pending.vehicleUuid);
            data.setLastKnownPosition(pending.vehicleUuid, SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
            CompanionDataService.save(player, data);
            syncToClient(player);
            tell(player, "message.find_me.vehicle_summoned", ChatFormatting.AQUA, entity.getDisplayName());
        }
    }

    private static boolean tryBoardManagedVehicle(ServerPlayer player, PlayerCompanionData data,
                                                  Entity target, Entity previousRide) {
        if (VehicleCompatibilityService.tryBoardVehicle(player, target, previousRide)) {
            return true;
        }
        return VehicleSeatService.hasSeat(data, target.getUUID())
                && VehicleSeatService.trySeat(player, target, previousRide, data);
    }

    public static boolean shouldCancelSwitchVehicleDamage(LivingEntity victim, DamageSource source) {
        if (victim == null || source == null || SWITCH_DAMAGE_PROTECTIONS.isEmpty()) {
            return false;
        }
        UUID victimUuid = victim.getUUID();
        for (VehicleSwitchDamageProtection protection : SWITCH_DAMAGE_PROTECTIONS) {
            if (!protection.mountUuid.equals(victimUuid)) {
                continue;
            }
            if (isProtectedVehicleDamage(victim, source, protection)) {
                victim.invulnerableTime = Math.max(victim.invulnerableTime, 20);
                victim.fallDistance = 0.0f;
                return true;
            }
        }
        return false;
    }

    public static boolean releaseVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
        if (ExternalVehicleBridge.available()) {
            boolean result = ExternalVehicleBridge.command(player, VehicleCommandAction.REMOVE, uuid, -1);
            if (ExternalVehicleBridge.available()) return result;
        }
        if (!isVehicleEntry(data, uuid)) {
            return false;
        }
        if (rejectBusy(player, data, uuid, "vehicle:release")) {
            return false;
        }
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_vehicle_release")) {
            tell(player, "message.find_me.backup_required_failed", ChatFormatting.RED);
            return false;
        }
        cancelPendingSummon(player.getUUID(), uuid);
        return removeUuid(player, data, uuid, entity);
    }

    public static boolean snapshotBeforeUnload(MinecraftServer server, Entity entity) {
        UUID uuid = entity.getUUID();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerCompanionData data = CompanionDataService.data(player);
            if (!data.containsVehicle(uuid)) {
                continue;
            }
            storeVehicle(player, data, entity);
            data.clearDeployedVehicle(uuid);
            CompanionDataService.save(player, data);
            syncToClient(player);
            return true;
        }
        return false;
    }

    public static boolean storeMountedEntity(ServerPlayer player, PlayerCompanionData data, Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!isVehicleEntry(data, entity.getUUID())) {
            return false;
        }
        return storeSelected(player, data, entity.getUUID(), entity);
    }

    public static boolean collectIfFindMeVehicle(ServerPlayer player, PlayerCompanionData data, Entity entity) {
        if (ExternalVehicleBridge.available()) {
            boolean result = ExternalVehicleBridge.command(player, VehicleCommandAction.STORE_CURRENT, null, -1);
            if (ExternalVehicleBridge.available()) return result;
        }
        if (entity == null || data == null || entity.isRemoved()) {
            return false;
        }
        if (!isVehicleEntry(data, entity.getUUID())) {
            return false;
        }
        UUID uuid = entity.getUUID();
        if (!storeVehicle(player, data, entity)) {
            return false;
        }
        data.clearDeployedVehicle(uuid);
        CompanionDataService.save(player, data);
        syncToClient(player);
        return true;
    }

    public static Optional<Entity> locateEntity(MinecraftServer server, PlayerCompanionData data, UUID uuid) {
        Optional<Entity> loaded = CompanionEntityLookup.findEntity(server, uuid);
        if (loaded.isPresent()) {
            return loaded;
        }
        Optional<SavedPosition> maybePosition = data.lastKnownPosition(uuid);
        if (maybePosition.isEmpty()) {
            return Optional.empty();
        }
        SavedPosition position = maybePosition.get();
        ServerLevel level = server.getLevel(position.dimension());
        if (level == null) {
            return Optional.empty();
        }
        level.getChunk(position.blockPos());
        return Optional.ofNullable(level.getEntity(uuid));
    }

    public static boolean recallIndex(ServerPlayer player, PlayerCompanionData data, int index) {
        if (ExternalVehicleBridge.available()) {
            boolean result = data.vehicleWheelUuidAt(index)
                    .map(uuid -> ExternalVehicleBridge.command(player, VehicleCommandAction.RECALL, uuid, -1))
                    .orElse(false);
            if (ExternalVehicleBridge.available()) return result;
        }
        Optional<UUID> uuid = data.vehicleWheelUuidAt(index);
        if (uuid.isEmpty()) {
            tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
            return false;
        }
        if (rejectBusy(player, data, uuid.get(), "vehicle:recall")) {
            return true;
        }
        cancelPendingSummon(player.getUUID(), uuid.get());
        Entity entity = locateEntity(player.getServer(), data, uuid.get()).orElse(null);
        return storeSelected(player, data, uuid.get(), entity);
    }

    private static boolean storeSelected(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
        cancelPendingSummon(player.getUUID(), uuid);
        Entity vehicle = entity == null ? locateEntity(player.getServer(), data, uuid).orElse(null) : entity;
        if (vehicle != null && !vehicle.isRemoved()) {
            if (!storeVehicle(player, data, vehicle)) {
                tell(player, "message.find_me.vehicle_store_failed", ChatFormatting.RED);
                return false;
            }
        }
        data.clearDeployedVehicle(uuid);
        CompanionDataService.save(player, data);
        tell(player, "message.find_me.vehicle_stored", ChatFormatting.GRAY, Component.literal(storedName(data, uuid)));
        return true;
    }

    public static boolean recallUuid(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (ExternalVehicleBridge.available()) {
            boolean result = ExternalVehicleBridge.command(player, VehicleCommandAction.RECALL, uuid, -1);
            if (ExternalVehicleBridge.available()) return result;
        }
        if (uuid == null || !data.containsVehicle(uuid)) {
            tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
            return false;
        }
        if (rejectBusy(player, data, uuid, "vehicle:recall")) return true;
        cancelPendingSummon(player.getUUID(), uuid);
        return storeSelected(player, data, uuid, locateEntity(player.getServer(), data, uuid).orElse(null));
    }

    private static void removeAt(ServerPlayer player, PlayerCompanionData data, int index) {
        Optional<UUID> uuid;
        if (index >= 0) {
            uuid = data.vehicleWheelUuidAt(index);
        } else {
            int warehouseIndex = -index - 1;
            List<UUID> warehouse = data.vehicleWarehouseOrder();
            uuid = warehouseIndex >= 0 && warehouseIndex < warehouse.size() ? Optional.of(warehouse.get(warehouseIndex)) : Optional.empty();
        }
        if (uuid.isEmpty()) {
            tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
            return;
        }
        if (rejectBusy(player, data, uuid.get(), "vehicle:remove")) {
            return;
        }
        cancelPendingSummon(player.getUUID(), uuid.get());
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_vehicle_release")) {
            tell(player, "message.find_me.backup_required_failed", ChatFormatting.RED);
            return;
        }
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid.get()).orElse(null);
        removeUuid(player, data, uuid.get(), entity);
    }

    private static void removeUuidCommand(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        if (uuid == null || !data.containsVehicle(uuid)) {
            tell(player, "message.find_me.invalid_vehicle_index", ChatFormatting.RED);
            return;
        }
        if (rejectBusy(player, data, uuid, "vehicle:remove")) return;
        cancelPendingSummon(player.getUUID(), uuid);
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_vehicle_release")) {
            tell(player, "message.find_me.backup_required_failed", ChatFormatting.RED);
            return;
        }
        removeUuid(player, data, uuid, CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null));
    }

    private static boolean removeUuid(ServerPlayer player, PlayerCompanionData data, UUID uuid, Entity entity) {
        CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.RELEASE);
        boolean hadSnapshot = data.storedEntity(uuid).isPresent();
        Entity release = entity;
        if (release == null) {
            release = restoreVehicle(player, data, uuid, findVehicleSpotForStored(player, data, uuid, player.blockPosition())).orElse(null);
        }
        if (release != null && !release.isRemoved()) {
            moveVehicleTo(release, player.serverLevel(), findVehicleSpotForEntity(player, release, player.blockPosition()), player.getYRot(), player.getXRot());
        }
        data.remove(uuid);
        CompanionDataService.save(player, data);
        tell(player, "message.find_me.vehicle_released", ChatFormatting.GRAY);
        FindMeDebugLogger.lifecycle("VEHICLE_RELEASE_RECORD_REMOVED", player, uuid, release,
                "BOUND", "RELEASED", "vehicle:release", hadSnapshot, release != null && !release.isRemoved());
        return true;
    }

    private static boolean rejectBusy(ServerPlayer player, PlayerCompanionData data, UUID uuid, String source) {
        String busyReason = CompanionLifecycleFacade.busyReason(player, data, uuid);
        if (busyReason == null) {
            return false;
        }
        FindMeDebugLogger.lifecycle("VEHICLE_ACTION_REJECTED_LOCKED", player, uuid, null,
                busyReason, "VEHICLE", source, data.storedEntity(uuid).isPresent(), false);
        tell(player, "message.find_me.busy", ChatFormatting.YELLOW,
                data.displayName(uuid).orElse(uuid.toString().substring(0, 8)));
        return true;
    }

    private static List<VehicleListPacket.Entry> entries(ServerPlayer player, PlayerCompanionData data, List<UUID> uuids) {
        ArrayList<VehicleListPacket.Entry> entries = new ArrayList<>();
        for (UUID uuid : uuids) {
            Entity entity = CompanionEntityLookup.findEntity(player.getServer(), uuid).orElse(null);
            Optional<CompoundTag> storedTag = rawStoredTag(data, uuid);
            String entityType = entity == null ? storedTag.map(VehicleManager::storedType).orElse("") : entityType(entity);
            String name = data.displayName(uuid).orElseGet(() -> entity == null ? storedTag.map(VehicleManager::storedName).orElse(uuid.toString().substring(0, 8)) : entity.getDisplayName().getString());
            boolean loaded = entity != null;
            boolean alive = entity == null ? storedTag.isPresent() : entity.isAlive();
            boolean ridden = player.getVehicle() != null && player.getVehicle().getUUID().equals(uuid);
            boolean deployed = ridden || data.isVehicleDeployed(uuid);
            CompoundTag previewTag = entity == null ? storedTag.map(CompoundTag::copy).orElse(null) : CompanionEntitySnapshots.previewEntityTag(entity, entityType);
            entries.add(new VehicleListPacket.Entry(uuid, entity == null ? -1 : entity.getId(), entityType, name, loaded, alive, deployed, ridden,
                    data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.SUMMON, entityType),
                    data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.RESCUE, entityType),
                    data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.STORAGE, entityType),
                    data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.SWITCH, entityType),
                    data.effectStyle(uuid, com.kuzhi.findme.common.CompanionEffectPurpose.SUMMON, entityType),
                    data.effectStyle(uuid, com.kuzhi.findme.common.CompanionEffectPurpose.RESCUE, entityType),
                    data.effectStyle(uuid, com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType), previewTag));
        }
        return entries;
    }

    private static boolean storeVehicle(ServerPlayer player, PlayerCompanionData data, Entity entity) {
        UUID uuid = entity.getUUID();
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.STORE, "vehicle:store")) {
            return false;
        }
        try {
            CompoundTag tag = new CompoundTag();
            if (!entity.save(tag)) {
                FindMeDebugLogger.lifecycle("VEHICLE_SNAPSHOT_FAILED", player, uuid, entity,
                        "ACTIVE", "ACTIVE", "vehicle:entity_save_failed", false, true);
                return false;
            }
            resetStoredVehicleTags(tag);
            tag.putBoolean(VEHICLE_MARKER, true);
            tag.putString("CompanionRescueName", entity.getDisplayName().getString());
            tag.putString("CompanionRescueType", entityType(entity));
            CompanionEntitySnapshots.writePreviewBounds(entity, tag);
            tag.putLong("CompanionRescueStoredAt", player.serverLevel().getGameTime());
            data.storeEntity(uuid, tag);
            data.setDisplayName(uuid, entity.getDisplayName().getString());
            data.setLastKnownPosition(uuid, SavedPosition.of(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
            if (!hasValidStoredVehicleSnapshot(data, uuid)) {
                FindMeDebugLogger.lifecycle("VEHICLE_REMOVE_BLOCKED", player, uuid, entity,
                        "ACTIVE", "ACTIVE", "vehicle:missing_valid_snapshot", false, true);
                return false;
            }
            CompanionDataService.save(player, data);
            FindMeDebugLogger.lifecycle("VEHICLE_SNAPSHOT_CREATED", player, uuid, entity,
                    "ACTIVE", "SNAPSHOT", "vehicle:store", true, true);
            if (player.getVehicle() == entity) {
                player.stopRiding();
            }
            for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
                passenger.stopRiding();
            }
            sendVehicleSealEffect(player, entity);
            entity.discard();
            FindMeDebugLogger.lifecycle("VEHICLE_ENTITY_REMOVED", player, uuid, entity,
                    "ACTIVE", "STORED", "vehicle:store", true, false);
            return true;
        } finally {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.STORE, "vehicle:store");
        }
    }

    private static boolean hasValidStoredVehicleSnapshot(PlayerCompanionData data, UUID uuid) {
        CompoundTag stored = rawStoredTag(data, uuid).orElse(null);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        return stored.contains("id") && (stored.getBoolean(VEHICLE_MARKER)
                || stored.getBoolean("FindMeVehicleMount"));
    }

    private static boolean scheduleStoredVehicleSummon(ServerPlayer player, PlayerCompanionData data, UUID uuid) {
        Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
        if (maybeTag.isEmpty()) {
            return false;
        }
        BlockPos pos = findVehicleSpotForStored(player, data, uuid, vehiclePlacementOrigin(player));
        Vec3 anchor = Vec3.atBottomCenterOf(pos);
        float radius = vehicleRadius(player, data, uuid, pos);
        float height = vehicleHeight(player, data, uuid, pos);
        String entityType = maybeTag.map(VehicleManager::storedType).orElse("");
        com.kuzhi.findme.common.CompanionEffectStyle style = data.effectStyle(uuid, com.kuzhi.findme.common.CompanionEffectPurpose.SUMMON, entityType);
        com.kuzhi.findme.common.CompanionAnimationStyle animation = data.animationStyle(uuid,
                com.kuzhi.findme.common.CompanionAnimationPurpose.SUMMON, entityType);
        if (animation == com.kuzhi.findme.common.CompanionAnimationStyle.GROUND_EMERGE) {
            sendVehicleSummonMagic(player, anchor, radius, height, RescueMagicPacket.Visual.GROUND_EMERGE);
        }
        if (style == com.kuzhi.findme.common.CompanionEffectStyle.MAGIC_CIRCLE
                || style == com.kuzhi.findme.common.CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE
                || style == com.kuzhi.findme.common.CompanionEffectStyle.VELOCITY_BURST) {
            RescueMagicPacket.Visual visual = style == com.kuzhi.findme.common.CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE
                    ? RescueMagicPacket.Visual.CUSTOM_TEXTURE
                    : style == com.kuzhi.findme.common.CompanionEffectStyle.VELOCITY_BURST
                    ? RescueMagicPacket.Visual.VELOCITY_BURST : RescueMagicPacket.Visual.DEFAULT;
            sendVehicleSummonMagic(player, anchor, radius, height, visual);
        }
        else if (style == com.kuzhi.findme.common.CompanionEffectStyle.ENDER) CompanionEnderEffectService.play(player, vehicleEffectBox(anchor, radius, height));
        int delay = CompanionArrivalMagicService.revealDelayTicks(VEHICLE_SUMMON_MAGIC_TICKS,
                RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON, animation);
        cancelPendingSummons(player.getUUID());
        PENDING_SUMMONS.add(new PendingVehicleSummon(player.getUUID(), uuid, pos, delay));
        return true;
    }

    private static Optional<Entity> restoreVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
        Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
        if (maybeTag.isEmpty()) {
            return Optional.empty();
        }
        if (!CompanionOperationLockService.tryBegin(player, uuid, CompanionOperationLockService.Operation.DEPLOY, "vehicle:restore")) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = maybeTag.get().copy();
            tag.putUUID("UUID", uuid);
            Entity restored = EntityType.loadEntityRecursive(tag, player.serverLevel(), entity -> {
                entity.moveTo((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                return entity;
            });
            if (restored == null) {
                return Optional.empty();
            }
            restored.setInvulnerable(false);
            if (!player.serverLevel().addFreshEntity(restored)) {
                return Optional.empty();
            }
            data.removeStoredEntity(uuid);
            data.setLastKnownPosition(uuid, SavedPosition.of(restored.level(), restored.getX(), restored.getY(), restored.getZ(), restored.getYRot(), restored.getXRot()));
            CompanionDataService.save(player, data);
            FindMeDebugLogger.lifecycle("VEHICLE_ENTITY_SPAWNED", player, uuid, restored,
                    "STORED", "ACTIVE", "vehicle:restore", false, true);
            return Optional.of(restored);
        } finally {
            CompanionOperationLockService.end(player, uuid, CompanionOperationLockService.Operation.DEPLOY, "vehicle:restore");
        }
    }

    private static Entity moveVehicleTo(Entity entity, ServerLevel level, BlockPos pos, float yRot, float xRot) {
        Entity moved = entity;
        double x = (double)pos.getX() + 0.5;
        double y = pos.getY();
        double z = (double)pos.getZ() + 0.5;
        if (moved.level() != level) {
            Entity changed = ExactEntityTeleporter.transfer(moved, level, x, y, z, yRot, xRot);
            if (changed != null) {
                moved = changed;
            }
        }
        moved.teleportTo(x, y, z);
        moved.moveTo(x, y, z, yRot, xRot);
        moved.setPos(x, y, z);
        moved.setYRot(yRot);
        moved.setXRot(xRot);
        moved.setDeltaMovement(Vec3.ZERO);
        moved.fallDistance = 0.0f;
        return moved;
    }

    public static void enforceSingleRideSlotForMount(ServerPlayer player, PlayerCompanionData data, UUID keepMountUuid, Entity previousRide) {
        collectPreviousRideForSharedSlot(player, data, keepMountUuid, previousRide);
        collectOtherVehiclesForSharedSlot(player, data, null, previousRide == null ? null : previousRide.getUUID());
    }

    private static void enforceSingleRideSlotForVehicle(ServerPlayer player, PlayerCompanionData data, UUID keepVehicleUuid, Entity previousRide) {
        collectPreviousRideForSharedSlot(player, data, keepVehicleUuid, previousRide);
        collectOtherVehiclesForSharedSlot(player, data, keepVehicleUuid, previousRide == null ? null : previousRide.getUUID());
        if (CompanionDeploymentService.collectOtherDeployed(player, data, com.kuzhi.findme.common.CompanionKind.MOUNT, null)) {
            CompanionSyncService.syncToClient(player, com.kuzhi.findme.common.CompanionKind.MOUNT);
        }
    }

    private static void collectPreviousRideForSharedSlot(ServerPlayer player, PlayerCompanionData data, UUID keepUuid, Entity previousRide) {
        UUID previousRideUuid = null;
        if (previousRide != null && !previousRide.isRemoved()) {
            previousRideUuid = previousRide.getUUID();
            if (!previousRideUuid.equals(keepUuid)) {
                if (isVehicleEntry(data, previousRideUuid)) {
                    if (storeVehicle(player, data, previousRide)) {
                        data.clearDeployedVehicle(previousRideUuid);
                    }
                } else if (!CompanionDeploymentService.retireMountAfterSharedRideSwitch(player, data, previousRide)
                        && player.getVehicle() == previousRide) {
                    player.stopRiding();
                }
            }
        }
    }

    private static void collectOtherVehiclesForSharedSlot(ServerPlayer player, PlayerCompanionData data, UUID keepVehicleUuid, UUID excludedUuid) {
        for (UUID otherVehicle : data.vehicleList()) {
            if (otherVehicle.equals(keepVehicleUuid) || otherVehicle.equals(excludedUuid)) {
                continue;
            }
            CompanionEntityLookup.findEntity(player.getServer(), otherVehicle).ifPresent(entity -> {
                if (storeVehicle(player, data, entity)) {
                    data.clearDeployedVehicle(otherVehicle);
                }
            });
        }
        data.deployedVehicle().ifPresent(previous -> {
            if (previous.equals(keepVehicleUuid) || previous.equals(excludedUuid)) {
                return;
            }
            locateEntity(player.getServer(), data, previous).ifPresent(entity -> {
                if (storeVehicle(player, data, entity)) {
                    data.clearDeployedVehicle(previous);
                }
            });
        });
    }

    private static boolean isBindableVehicle(Entity entity) {
        if (entity == null || entity instanceof Player || entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile) {
            return false;
        }
        return true;
    }

    public static Entity unwrapPart(Entity entity) {
        entity = unwrapKnownVehicleProxy(entity);
        if (entity instanceof PartEntity) {
            Entity parent = ((PartEntity<?>)entity).getParent();
            return parent == null ? entity : unwrapKnownVehicleProxy(parent);
        }
        return entity;
    }

    private static Entity unwrapKnownVehicleProxy(Entity entity) {
        if (entity == null || !isEntityType(entity, "automobility", "hitbox")) {
            return entity;
        }
        try {
            Method method = entity.getClass().getMethod("automobile");
            Object result = method.invoke(entity);
            if (result instanceof Entity vehicle && vehicle != entity && !vehicle.isRemoved()) {
                return vehicle;
            }
        }
        catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return entity;
    }

    private static boolean isEntityType(Entity entity, String namespace, String path) {
        ResourceLocation key = EntityType.getKey(entity.getType());
        return key != null && namespace.equals(key.getNamespace()) && path.equals(key.getPath());
    }

    private static void resetStoredVehicleTags(CompoundTag tag) {
        tag.putShort("Fire", (short)0);
        tag.putFloat("FallDistance", 0.0f);
        tag.putBoolean("Invulnerable", false);
        tag.remove("Motion");
        tag.remove("Passengers");
    }

    private static String storedName(PlayerCompanionData data, UUID uuid) {
        return data.displayName(uuid).orElseGet(() -> rawStoredTag(data, uuid).map(VehicleManager::storedName).orElse(uuid.toString().substring(0, 8)));
    }

    private static String storedName(CompoundTag tag) {
        String name = tag.getString("CompanionRescueName");
        if (!name.isBlank()) {
            return name;
        }
        String type = storedType(tag);
        int separator = type.indexOf(':');
        return separator >= 0 && separator + 1 < type.length() ? type.substring(separator + 1) : type;
    }

    private static String storedType(PlayerCompanionData data, UUID uuid) {
        return rawStoredTag(data, uuid).map(VehicleManager::storedType).orElse("");
    }

    private static String storedType(CompoundTag tag) {
        String type = tag.getString("CompanionRescueType");
        return type.isBlank() ? tag.getString("id") : type;
    }

    private static Optional<CompoundTag> rawStoredTag(PlayerCompanionData data, UUID uuid) {
        return data == null || uuid == null ? Optional.empty() : data.storedEntity(uuid);
    }

    private static String entityType(Entity entity) {
        ResourceLocation key = EntityType.getKey(entity.getType());
        return key == null ? "" : key.toString();
    }

    private static void sendVehicleSealEffect(ServerPlayer player, Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        AABB box = CompanionEntityVisualBoundsService.effectBounds(entity);
        Vec3 base = new Vec3(box.getCenter().x, box.minY, box.getCenter().z);
        float radius = vehicleRadius(entity);
        float height = vehicleHeight(entity);
        com.kuzhi.findme.common.CompanionEffectStyle style = CompanionDataService.data(player).effectStyle(entity.getUUID(), com.kuzhi.findme.common.CompanionEffectPurpose.STORAGE, entityType(entity));
        if (style == com.kuzhi.findme.common.CompanionEffectStyle.NONE) return;
        FindMeDebugLogger.info("effect-sizing", "side=server entity={} purpose=STORAGE style={} source=live bounds={} packetRadius={} packetHeight={}",
                FindMeDebugLogger.entity(entity), style, FindMeDebugLogger.box(box), radius, height);
        if (style == com.kuzhi.findme.common.CompanionEffectStyle.ENDER) { CompanionEnderEffectService.play(player, box); return; }
        ModNetwork.sendToPlayersNear(level, base, VEHICLE_EFFECT_PACKET_RADIUS, new VehicleSealEffectPacket(base.x, base.y, base.z, radius, height, VEHICLE_SEAL_TICKS, style == com.kuzhi.findme.common.CompanionEffectStyle.CUSTOM_MAGIC_CIRCLE));
        CompanionMagicAudioService.playStorageOpen(player, base, radius);
    }

    private static void sendVehicleSummonMagic(ServerPlayer player, Vec3 anchor, float radius, float height, RescueMagicPacket.Visual visual) {
        float safeRadius = Math.max(0.30f, radius);
        float safeHeight = Math.max(0.25f, height);
        RescueMagicPacket packet = new RescueMagicPacket(anchor.x, anchor.y, anchor.z, safeRadius, safeHeight, player.getYRot(), VEHICLE_SUMMON_MAGIC_TICKS, RescueMagicPacket.Style.GROUND_CIRCLE, RescueMagicPacket.Purpose.SUMMON, visual);
        FindMeDebugLogger.info("effect-sizing", "side=server entity=vehicle purpose=SUMMON visual={} source=stored-or-live packetRadius={} packetHeight={} anchor={}",
                visual, safeRadius, safeHeight, anchor);
        ModNetwork.sendToPlayersNear(player.serverLevel(), anchor, VEHICLE_EFFECT_PACKET_RADIUS, packet);
        CompanionMagicAudioService.playCircleOpen(player, anchor, safeRadius, false);
    }

    private static void sendVehicleSummonMagic(ServerPlayer player, Vec3 anchor, float radius, float height) {
        sendVehicleSummonMagic(player, anchor, radius, height, RescueMagicPacket.Visual.DEFAULT);
    }

    private static AABB vehicleEffectBox(Vec3 anchor, float radius, float height) {
        return new AABB(anchor.x - radius, anchor.y, anchor.z - radius, anchor.x + radius, anchor.y + Math.max(0.25f, height), anchor.z + radius);
    }

    private static float vehicleRadius(Entity entity) {
        if (entity == null) {
            return 0.45f;
        }
        return (float)Mth.clamp(CompanionEntityVisualBoundsService.summonCircleRadius(entity), 0.45f, 128.0f);
    }

    private static float vehicleHeight(Entity entity) {
        if (entity == null) {
            return 0.25f;
        }
        return (float)Mth.clamp(CompanionEntityVisualBoundsService.effectBounds(entity).getYsize(), 0.25, 128.0);
    }

    private static float vehicleRadius(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
        Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
        Entity preview = previewStoredVehicle(player, data, uuid, pos).orElse(null);
        if (preview != null) {
            return vehicleRadius(preview);
        }
        return rawStoredTag(data, uuid)
                .map(tag -> vehicleRadius(storedEffectWidth(tag), storedEffectDepth(tag)))
                .orElse(0.45f);
    }

    private static float vehicleHeight(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
        Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
        Entity preview = previewStoredVehicle(player, data, uuid, pos).orElse(null);
        if (preview != null) {
            return vehicleHeight(preview);
        }
        return rawStoredTag(data, uuid)
                .map(tag -> (float)Mth.clamp(storedEffectHeight(tag), 0.25f, 128.0f))
                .orElse(0.25f);
    }

    private static float vehicleRadius(double width, double depth) {
        double halfX = width * 0.5;
        double halfZ = depth * 0.5;
        return (float)Mth.clamp(Math.sqrt(halfX * halfX + halfZ * halfZ) * 1.28 + 0.16, 0.45, 128.0);
    }

    private static Optional<Entity> previewStoredVehicle(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos pos) {
        Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
        if (maybeTag.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = maybeTag.get().copy();
        tag.putUUID("UUID", uuid);
        Entity preview = EntityType.loadEntityRecursive(tag, player.serverLevel(), entity -> {
            entity.moveTo((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            return entity;
        });
        return Optional.ofNullable(preview);
    }

    private static float storedPreviewWidth(CompoundTag tag) {
        return tag.contains("CompanionPreviewWidth") ? tag.getFloat("CompanionPreviewWidth") : 1.0f;
    }

    private static float storedPreviewHeight(CompoundTag tag) {
        return tag.contains("CompanionPreviewHeight") ? tag.getFloat("CompanionPreviewHeight") : 1.0f;
    }

    private static float storedPreviewDepth(CompoundTag tag) {
        return tag.contains("CompanionPreviewDepth") ? tag.getFloat("CompanionPreviewDepth") : storedPreviewWidth(tag);
    }

    private static float storedEffectWidth(CompoundTag tag) {
        return tag.contains("CompanionEffectWidth") ? tag.getFloat("CompanionEffectWidth") : storedPreviewWidth(tag);
    }

    private static float storedEffectHeight(CompoundTag tag) {
        return tag.contains("CompanionEffectHeight") ? tag.getFloat("CompanionEffectHeight") : storedPreviewHeight(tag);
    }

    private static float storedEffectDepth(CompoundTag tag) {
        return tag.contains("CompanionEffectDepth") ? tag.getFloat("CompanionEffectDepth") : storedPreviewDepth(tag);
    }

    private static BlockPos findVehicleSpotForEntity(ServerPlayer player, Entity entity, BlockPos origin) {
        return findVehicleSpotForEntityNear(player, entity, origin);
    }

    private static BlockPos findVehicleSpotForEntityNear(ServerPlayer player, Entity entity, BlockPos origin) {
        if (entity == null) {
            return VehiclePlacementService.findVehicleSpotNear(player, origin, "");
        }
        AABB box = entity.getBoundingBox();
        double width = Math.max(1.0, box.getXsize());
        double height = Math.max(1.0, box.getYsize());
        double depth = Math.max(1.0, box.getZsize());
        return VehiclePlacementService.findVehicleSpotNear(player, origin, entityType(entity), width, height, depth);
    }

    private static BlockPos findVehicleSpotForStored(ServerPlayer player, PlayerCompanionData data, UUID uuid, BlockPos origin) {
        Optional<CompoundTag> maybeTag = rawStoredTag(data, uuid);
        if (maybeTag.isEmpty()) {
            return VehiclePlacementService.findVehicleSpotNear(player, origin, storedType(data, uuid));
        }
        CompoundTag tag = maybeTag.get();
        return VehiclePlacementService.findVehicleSpotNear(player, origin, storedType(tag), storedPreviewWidth(tag), storedPreviewHeight(tag), storedPreviewDepth(tag));
    }

    private static BlockPos vehiclePlacementOrigin(ServerPlayer player) {
        Entity ride = player.getVehicle();
        if (ride != null && !ride.isRemoved() && ride.level() == player.level()) {
            return BlockPos.containing(ride.getX(), ride.getY(), ride.getZ());
        }
        return player.blockPosition();
    }

    private static void protectOldMountFromVehicleSwitch(ServerPlayer player, UUID mountUuid, UUID vehicleUuid) {
        if (player == null || mountUuid == null || vehicleUuid == null) {
            return;
        }
        clearSwitchDamageProtection(player.getUUID(), mountUuid, vehicleUuid);
        SWITCH_DAMAGE_PROTECTIONS.add(new VehicleSwitchDamageProtection(player.getUUID(), mountUuid, vehicleUuid, SWITCH_DAMAGE_PROTECTION_TICKS));
    }

    private static void clearSwitchDamageProtection(UUID playerUuid, UUID mountUuid, UUID vehicleUuid) {
        SWITCH_DAMAGE_PROTECTIONS.removeIf(protection ->
                protection.playerUuid.equals(playerUuid)
                        && protection.mountUuid.equals(mountUuid)
                        && protection.vehicleUuid.equals(vehicleUuid));
    }

    private static void cancelPendingSummon(UUID playerUuid, UUID vehicleUuid) {
        if (playerUuid == null || vehicleUuid == null) {
            return;
        }
        PENDING_SUMMONS.removeIf(pending -> pending.playerUuid.equals(playerUuid) && pending.vehicleUuid.equals(vehicleUuid));
    }

    private static void cancelPendingSummons(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        PENDING_SUMMONS.removeIf(pending -> pending.playerUuid.equals(playerUuid));
    }

    public static boolean isPendingSummon(UUID vehicleUuid) {
        return vehicleUuid != null && PENDING_SUMMONS.stream().anyMatch(pending -> pending.vehicleUuid.equals(vehicleUuid));
    }

    private static void tickSwitchDamageProtections() {
        Iterator<VehicleSwitchDamageProtection> iterator = SWITCH_DAMAGE_PROTECTIONS.iterator();
        while (iterator.hasNext()) {
            VehicleSwitchDamageProtection protection = iterator.next();
            protection.ticksLeft--;
            if (protection.ticksLeft <= 0) {
                iterator.remove();
            }
        }
    }

    private static boolean isProtectedVehicleDamage(LivingEntity victim, DamageSource source, VehicleSwitchDamageProtection protection) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (isProtectedVehicleEntity(direct, protection.vehicleUuid) || isProtectedVehicleEntity(causing, protection.vehicleUuid)) {
            return true;
        }
        if (causing instanceof ServerPlayer player && player.getUUID().equals(protection.playerUuid)) {
            Entity vehicle = player.getVehicle();
            return isProtectedVehicleEntity(vehicle, protection.vehicleUuid);
        }
        if (direct instanceof ServerPlayer player && player.getUUID().equals(protection.playerUuid)) {
            Entity vehicle = player.getVehicle();
            return isProtectedVehicleEntity(vehicle, protection.vehicleUuid);
        }
        if (direct != null || causing != null || victim.getServer() == null) {
            return false;
        }
        Entity vehicle = CompanionEntityLookup.findEntity(victim.getServer(), protection.vehicleUuid).orElse(null);
        return vehicle != null && vehicle.level() == victim.level() && vehicle.distanceToSqr(victim) <= 16.0;
    }

    private static boolean isProtectedVehicleEntity(Entity entity, UUID vehicleUuid) {
        if (entity == null || vehicleUuid == null) {
            return false;
        }
        if (entity.getUUID().equals(vehicleUuid)) {
            return true;
        }
        Entity root = entity.getRootVehicle();
        return root != null && root.getUUID().equals(vehicleUuid);
    }

    private static void spawnBlinkParticles(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        AABB box = CompanionEntityVisualBoundsService.effectBounds(entity);
        double width = Math.max(0.15, Math.max(box.getXsize(), box.getZsize()));
        double height = Math.max(0.20, box.getYsize());
        int count = Mth.clamp((int)Math.round(8.0 + width * height * 10.0), 10, 160);
        level.sendParticles((ParticleOptions)ParticleTypes.PORTAL, box.getCenter().x, box.minY + height * 0.5, box.getCenter().z, count, width * 0.55, height * 0.45, width * 0.55, 0.1);
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting color, Object ... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }

    private static final class PendingVehicleSummon {
        private final UUID playerUuid;
        private final UUID vehicleUuid;
        private final BlockPos position;
        private final int delayTicks;
        private int age;

        private PendingVehicleSummon(UUID playerUuid, UUID vehicleUuid, BlockPos position, int delayTicks) {
            this.playerUuid = playerUuid;
            this.vehicleUuid = vehicleUuid;
            this.position = position;
            this.delayTicks = Math.max(1, delayTicks);
        }
    }

    private static final class VehicleSwitchDamageProtection {
        private final UUID playerUuid;
        private final UUID mountUuid;
        private final UUID vehicleUuid;
        private int ticksLeft;

        private VehicleSwitchDamageProtection(UUID playerUuid, UUID mountUuid, UUID vehicleUuid, int ticksLeft) {
            this.playerUuid = playerUuid;
            this.mountUuid = mountUuid;
            this.vehicleUuid = vehicleUuid;
            this.ticksLeft = ticksLeft;
        }
    }
}

