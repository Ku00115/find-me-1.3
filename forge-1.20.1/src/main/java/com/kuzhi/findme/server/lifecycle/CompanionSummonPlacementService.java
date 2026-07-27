package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.server.lifecycle.CompanionCinematicLandingService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.safety.CompanionCombatRescueService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionSummonPlacementService {
    private CompanionSummonPlacementService() {
    }

    public static StoredRestore findStoredRestoreSpawn(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind kind, Optional<CompoundTag> storedTag, boolean fallingRescue, LivingEntity companionRescueTarget) {
        CompanionMoveType storedMoveType = storedTag.map(CompanionEntitySnapshots::storedEntityType)
                .map(type -> CompanionEntityClassifier.summonMoveType(player.getServer(), type, kind))
                .orElse(CompanionMoveType.WALK);
        CompanionMoveType storedMountMoveType = storedTag.map(CompanionEntitySnapshots::storedEntityType)
                .map(type -> CompanionEntityClassifier.moveType(player.getServer(), type, kind))
                .orElse(storedMoveType);
        boolean airborneGroundCompanion = CompanionSummonModeService.isAirborneGroundCompanionSummon(player, kind, storedMoveType);
        boolean airborneGroundMountSwitch = CompanionSummonModeService.isAirborneGroundMountSwitch(player, kind, storedMountMoveType);
        boolean airborneGroundVehicleRescue = CompanionSummonModeService.isAirborneGroundVehicleRescue(player, data, kind, storedMountMoveType);
        boolean companionRescue = companionRescueTarget != null;
        boolean restoreRescue = fallingRescue
                || CompanionSummonModeService.isAirborneGroundRescue(player, kind, storedMoveType)
                || airborneGroundVehicleRescue || airborneGroundCompanion || airborneGroundMountSwitch || companionRescue;
        RescueFlightMode rescueFlightMode = restoreRescue && storedMoveType == CompanionMoveType.FLY
                ? CompanionRescuePlanner.plan(player).flightMode() : null;
        String storedEntityType = storedTag.map(CompanionEntitySnapshots::storedEntityType).orElse("");
        boolean burrowSummon = !restoreRescue && data.animationStyle(uuid, CompanionAnimationPurpose.SUMMON,
                storedEntityType) == CompanionAnimationStyle.GROUND_EMERGE;
        BlockPos pos = storedTag.map(CompanionEntitySnapshots::storedEntityType)
                .map(type -> {
                    CompanionMoveType summonMoveType = CompanionEntityClassifier.summonMoveType(player.getServer(), type, kind);
                    if (companionRescue) {
                        return CompanionCombatRescueService.findSpawn(player, null, summonMoveType, companionRescueTarget);
                    }
                    if (kind == CompanionKind.MOUNT && burrowSummon) {
                        return CompanionSpawnPlacementService.findBurrowMountRideSpot(player);
                    }
                    if (kind == CompanionKind.MOUNT || airborneGroundCompanion) {
                        return CompanionSpawnPlacementService.findArrivalSpawn(player, summonMoveType, restoreRescue, storedEntityType, rescueFlightMode);
                    }
                    return burrowSummon ? CompanionSpawnPlacementService.findBurrowSummonSpot(player, kind, summonMoveType) : CompanionSpawnPlacementService.findSummonSpot(player, kind, summonMoveType);
                })
                .orElseGet(() -> {
                    if (companionRescue) {
                        return CompanionCombatRescueService.findSpawn(player, null, storedMoveType, companionRescueTarget);
                    }
                    if (airborneGroundCompanion || airborneGroundMountSwitch || airborneGroundVehicleRescue) {
                        return CompanionSpawnPlacementService.findArrivalSpawn(player, storedMountMoveType, true, storedEntityType, rescueFlightMode);
                    }
                    if (kind == CompanionKind.MOUNT && burrowSummon) {
                        return CompanionSpawnPlacementService.findBurrowMountRideSpot(player);
                    }
                    return burrowSummon ? CompanionSpawnPlacementService.findBurrowSummonSpot(player, kind, CompanionMoveType.WALK) : CompanionSpawnPlacementService.findSummonSpot(player, kind, CompanionMoveType.WALK);
                });
        return new StoredRestore(pos, restoreRescue);
    }

    public static BlockPos findLiveMoveTarget(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living, CompanionMoveType moveType, CompanionMoveType summonMoveType, MountCinematicMode mode, boolean airborneGroundCompanion, LivingEntity companionRescueTarget) {
        String entityType = net.minecraft.world.entity.EntityType.getKey(living.getType()).toString();
        if (kind == CompanionKind.MOUNT && !mode.isRescue()
                && data.animationStyle(living.getUUID(), CompanionAnimationPurpose.SUMMON,
                entityType) == CompanionAnimationStyle.GROUND_EMERGE) {
            return CompanionSpawnPlacementService.findBurrowMountRideSpot(player);
        }
        if (kind == CompanionKind.MOUNT) {
            RescueFlightMode rescueFlightMode = mode.isFlyingRescue()
                    ? CompanionRescuePlanner.plan(player).flightMode() : null;
            return CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, mode.isRescue(), entityType, rescueFlightMode);
        }
        if (companionRescueTarget != null) {
            return CompanionCombatRescueService.findSpawn(player, living, summonMoveType, companionRescueTarget);
        }
        if (airborneGroundCompanion) {
            RescueFlightMode rescueFlightMode = summonMoveType == CompanionMoveType.FLY
                    ? CompanionRescuePlanner.plan(player).flightMode() : null;
            return CompanionSpawnPlacementService.findArrivalSpawn(player, summonMoveType, true, "", rescueFlightMode);
        }
        if (data.animationStyle(living.getUUID(), CompanionAnimationPurpose.SUMMON,
                entityType) == CompanionAnimationStyle.GROUND_EMERGE) {
            return CompanionSpawnPlacementService.findBurrowSummonSpot(player, kind, summonMoveType);
        }
        return CompanionSpawnPlacementService.findSummonSpot(player, kind, summonMoveType);
    }

    public static Optional<BlockPos> adjustForOpenLiveSpace(ServerPlayer player, LivingEntity living, BlockPos target, MountCinematicMode mode) {
        if (mode.isRescue() || CompanionSpawnPlacementService.hasOpenEntitySpace(player.serverLevel(), living, (double)target.getX() + 0.5, target.getY(), (double)target.getZ() + 0.5)) {
            return Optional.of(target);
        }
        return CompanionSpawnPlacementService.findOpenEntitySpace(player.serverLevel(), living, target);
    }

    public static BlockPos findMountRescueRestoreSpawn(ServerPlayer player, CompanionMoveType moveType, String entityType) {
        return CompanionSpawnPlacementService.findArrivalSpawn(player, moveType, true, entityType);
    }

    public record StoredRestore(BlockPos pos, boolean preloadChunkArea) {
    }
}

