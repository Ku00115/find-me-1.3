package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.ui.CompanionMessageService;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.SavedPosition;
import com.kuzhi.findme.server.lifecycle.CompanionEscortService;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import com.kuzhi.findme.server.safety.CompanionSafetyService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import com.kuzhi.findme.server.lifecycle.CompanionEntityTransferService;
import com.kuzhi.findme.server.lifecycle.CompanionLifecycleFacade;
import com.kuzhi.findme.server.lifecycle.CompanionSpawnPlacementService;
import com.kuzhi.findme.server.lifecycle.CompanionTransientStateService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionListService {
    private CompanionListService() {
    }

    public static boolean selectWheelIndex(PlayerCompanionData data, CompanionKind kind, int wheelIndex) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(kind, wheelIndex);
        return maybeUuid.isPresent() && data.setActiveUuid(kind, maybeUuid.get());
    }

    public static int list(ServerPlayer player, CompanionKind kind) {
        PlayerCompanionData data = CompanionDataService.data(player);
        List<UUID> uuids = data.list(kind);
        if (uuids.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionMessageService.label(kind));
            return 0;
        }
        int active = data.activeIndex(kind);
        CompanionMessageService.tell(player, "message.find_me.registered_header", ChatFormatting.AQUA, CompanionMessageService.label(kind));
        for (int i = 0; i < uuids.size(); ++i) {
            UUID uuid = uuids.get(i);
            Entity entity = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
            String name = entity == null ? uuid.toString() : entity.getDisplayName().getString();
            String marker = i == active ? "* " : "  ";
            MutableComponent state = entity == null ? Component.translatable("message.find_me.state_unloaded") : Component.translatable(entity.isAlive() ? "message.find_me.state_loaded" : "message.find_me.state_dead");
            player.sendSystemMessage(Component.translatable("message.find_me.list_entry", marker, i, name, state).withStyle(i == active ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        return uuids.size();
    }

    public static int select(ServerPlayer player, CompanionKind kind, int index) {
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<UUID> uuid = data.wheelUuidAt(kind, index);
        if (uuid.isPresent() && rejectBusy(player, data, kind, uuid.get(), "list:select")) {
            return 0;
        }
        if (!data.setActiveIndex(kind, index)) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionMessageService.tell(player, "message.find_me.selected_index", ChatFormatting.GREEN, CompanionMessageService.label(kind), index);
        return 1;
    }

    public static int remove(ServerPlayer player, CompanionKind kind, int index) {
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<UUID> uuid = data.wheelUuidAt(kind, index);
        if (uuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        return releaseAndRemove(player, data, kind, index, uuid.get()) ? 1 : 0;
    }

    public static int assignWheelSlot(ServerPlayer player, CompanionKind kind, int index, int slot) {
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<UUID> uuid = data.wheelUuidAt(kind, index);
        if (uuid.isPresent() && rejectBusy(player, data, kind, uuid.get(), "wheel:assign_slot")) {
            return 0;
        }
        if (!data.setWheelSlot(kind, index, slot)) {
            CompanionMessageService.tell(player, "message.find_me.invalid_slot", ChatFormatting.RED, CompanionMessageService.label(kind));
            return 0;
        }
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionMessageService.tell(player, "message.find_me.assigned_slot", ChatFormatting.GREEN, CompanionMessageService.label(kind), index, slot + 1);
        return 1;
    }

    public static void moveToOther(ServerPlayer player, PlayerCompanionData data, CompanionKind fromKind, int index) {
        Optional<UUID> maybeUuid = data.wheelUuidAt(fromKind, index);
        if (maybeUuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(fromKind));
            return;
        }
        UUID uuid = maybeUuid.get();
        CompanionKind targetKind = otherKind(fromKind);
        CompanionCategoryTransferService.Result result =
                CompanionCategoryTransferService.transfer(player, data, uuid, targetKind);
        if (!result.success()) {
            player.displayClientMessage(Component.literal(result.message()).withStyle(ChatFormatting.RED), true);
        }
    }

    public static boolean releaseAndRemove(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, int index, UUID uuid) {
        if (rejectBusy(player, data, kind, uuid, "list:release_remove")) {
            return false;
        }
        if (!CompanionSafetyService.createForcedBackup(player, data, "before_release_remove")) {
            CompanionMessageService.tell(player, "message.find_me.backup_required_failed", ChatFormatting.RED, new Object[0]);
            return false;
        }
        CompanionTransientStateService.cancelTarget(player, data, uuid, CompanionTransientStateService.Reason.RELEASE);
        boolean hadSnapshot = data.storedEntity(uuid).isPresent();
        BlockPos releasePos = CompanionSpawnPlacementService.findSummonSpot(player, kind, CompanionMoveType.WALK);
        Entity entity = CompanionEntityLookup.locateEntity(player.getServer(), data, uuid).orElseGet(() -> CompanionLifecycleFacade.restoreStored(player, data, uuid, releasePos, player.getYRot(), player.getXRot(), "list:release_remove").orElse(null));
        if (entity == null) {
            data.remove(uuid);
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
            CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
            CompanionSyncService.syncDeadToClient(player);
            CompanionMessageService.tell(player, "message.find_me.released", ChatFormatting.GRAY, CompanionMessageService.label(kind), index);
            FindMeDebugLogger.lifecycle("RELEASE_RECORD_REMOVED", player, uuid, null,
                    "BOUND", "RELEASED", "list:release_remove_missing_entity", hadSnapshot, false);
            return true;
        }
        if (entity instanceof LivingEntity living && living.isAlive()) {
            CompanionEscortService.cancelIfEscorting(player, data, uuid);
            if (living.level() != player.level()) {
                living = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), releasePos, player.getYRot(), player.getXRot());
            }
            living = CompanionEntityTransferService.moveEntityTo(living, player.serverLevel(), releasePos, player.getYRot(), player.getXRot());
            if (player.getVehicle() == living) {
                player.stopRiding();
            }
            if (living.isPassenger()) {
                living.stopRiding();
            }
            living.invulnerableTime = Math.max(living.invulnerableTime, Config.DEFAULT_POST_TELEPORT_INVULNERABILITY_TICKS);
            living.fallDistance = 0.0f;
            data.setLastKnownPosition(uuid, SavedPosition.of(living.level(), living.getX(), living.getY(), living.getZ(), living.getYRot(), living.getXRot()));
        }
        data.remove(uuid);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, CompanionKind.MOUNT);
        CompanionSyncService.syncToClient(player, CompanionKind.COMPANION);
        CompanionMessageService.tell(player, "message.find_me.released", ChatFormatting.GRAY, CompanionMessageService.label(kind), index);
        FindMeDebugLogger.lifecycle("RELEASE_RECORD_REMOVED", player, uuid, entity,
                "BOUND", "RELEASED", "list:release_remove", hadSnapshot, entity != null && !entity.isRemoved());
        return true;
    }

    public static void rename(ServerPlayer player, CompanionKind kind, int index, String name) {
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<UUID> uuid = data.wheelUuidAt(kind, index);
        if (uuid.isEmpty()) {
            CompanionMessageService.tell(player, "message.find_me.invalid_index", ChatFormatting.RED, CompanionMessageService.label(kind));
            return;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        data.setDisplayName(uuid.get(), trimmed);
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind);
        CompanionMessageService.tell(player, "message.find_me.renamed", ChatFormatting.GREEN, new Object[0]);
    }

    private static CompanionKind otherKind(CompanionKind kind) {
        return kind == CompanionKind.MOUNT ? CompanionKind.COMPANION : CompanionKind.MOUNT;
    }

    public static boolean rejectBusy(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid, String source) {
        String busyReason = CompanionLifecycleFacade.busyReason(player, data, uuid);
        if (busyReason == null) {
            return false;
        }
        FindMeDebugLogger.lifecycle("LIST_ACTION_REJECTED_LOCKED", player, uuid, null,
                busyReason, "LIST", source, data.storedEntity(uuid).isPresent(), false);
        CompanionSummonLineService.showBusy(player, data, kind, uuid);
        CompanionSyncService.syncToClient(player, kind);
        return true;
    }
}

