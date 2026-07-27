package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionLifecycleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Shared summon-first boundary for features that need a selected creature deployed. */
public final class CompanionDeployRequestService {
    private CompanionDeployRequestService() {
    }

    public static Result request(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                 UUID uuid, Mode mode, String source) {
        LivingEntity ready = readyEntity(player, data, uuid, mode);
        if (ready != null && !CompanionLifecycleFacade.isBusy(player, data, uuid)) {
            return new Result(State.READY, ready);
        }
        if (CompanionLifecycleFacade.isBusy(player, data, uuid)) {
            return new Result(State.WAITING, null);
        }
        if (!uuid.equals(data.active(kind).orElse(null))) {
            data.setActiveUuid(kind, uuid);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, kind);
        }
        if (mode == Mode.AUTONOMOUS) {
            return deployDirect(player, data, kind, uuid, source);
        }
        boolean accepted = mode == Mode.AUTONOMOUS
                ? CompanionLifecycleFacade.summonActiveForTacticalOrder(player, data, kind, source)
                : CompanionLifecycleFacade.summonActive(player, data, kind, source);
        return new Result(accepted ? State.STARTED : State.REJECTED, null);
    }

    public static LivingEntity readyEntity(ServerPlayer player, PlayerCompanionData data, UUID uuid,
                                           Mode mode) {
        Entity found = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
        if (!(found instanceof LivingEntity living) || !living.isAlive() || living.level() != player.level()
                || CompanionHomeResidentService.isResident(uuid)) {
            return null;
        }
        if (mode == Mode.AUTONOMOUS && (player.distanceToSqr(living) > 48.0 * 48.0
                || data.kindOf(uuid).map(kind -> !data.isDeployed(kind, uuid)).orElse(true))) {
            return null;
        }
        if (mode == Mode.RIDDEN && player.getVehicle() != living) {
            return null;
        }
        return living;
    }

    private static Result deployDirect(ServerPlayer player, PlayerCompanionData data, CompanionKind kind,
                                       UUID uuid, String source) {
        Entity found = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElse(null);
        if (found instanceof LivingEntity resident && CompanionHomeResidentService.isResident(uuid)) {
            if (!CompanionStorageService.snapshotAndDiscardHomeResidentForSummon(player, data, resident)) {
                return new Result(State.WAITING, null);
            }
            found = null;
        }
        if (found instanceof LivingEntity occupied && occupied.getFirstPassenger() != null
                && occupied.getFirstPassenger() != player) {
            return new Result(State.REJECTED, null);
        }

        Optional<CompoundTag> stored = data.storedEntity(uuid);
        CompanionMoveType moveType = found instanceof LivingEntity living
                ? CompanionEntityClassifier.moveType(living, kind)
                : CompanionEntityClassifier.moveType(
                        stored.map(CompanionEntitySnapshots::storedEntityType).orElse(""), kind);
        BlockPos spawn = CompanionSpawnPlacementService.findSummonSpot(player, kind, moveType);
        LivingEntity deployed;
        if (found instanceof LivingEntity living) {
            deployed = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), spawn,
                    player.getYRot(), player.getXRot(), false);
        } else {
            Entity restored = CompanionLifecycleFacade.restoreStoredDirect(player, data, uuid, spawn,
                    player.getYRot(), player.getXRot(), source + ":direct_restore").orElse(null);
            if (!(restored instanceof LivingEntity living)) {
                return new Result(State.REJECTED, null);
            }
            deployed = living;
        }

        CompanionHomeResidentService.clearResident(deployed);
        CompanionDeploymentService.rememberDeployedWithinLimit(player, data, kind, uuid);
        data.setLifecycleState(uuid, CompanionLifecycleState.DEPLOYED);
        data.setLastKnownPosition(uuid, SavedPosition.of(deployed.level(), deployed.getX(), deployed.getY(),
                deployed.getZ(), deployed.getYRot(), deployed.getXRot()));
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        return new Result(State.READY, deployed);
    }

    public enum Mode {
        RIDDEN,
        AUTONOMOUS
    }

    public enum State {
        READY,
        WAITING,
        STARTED,
        REJECTED
    }

    public record Result(State state, LivingEntity entity) {
    }
}
