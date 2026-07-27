package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.data.CompanionDataService;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.data.CompanionEntitySnapshots;
import com.kuzhi.findme.server.data.DeadCompanionRecord;
import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import com.kuzhi.findme.server.lifecycle.CompanionShoulderService;
import com.kuzhi.findme.server.lifecycle.CompanionStorageService;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import com.kuzhi.findme.server.command.CompanionWheelTransactionService;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class CompanionSyncService {
    private static final Map<UUID, EnumMap<CompanionKind, Map<UUID, String>>> SENT_PREVIEW_SIGNATURES = new HashMap<>();

    private CompanionSyncService() {
    }

    public static void syncToClient(ServerPlayer player, CompanionKind kind) {
        syncToClient(player, kind, CompanionDataService.data(player));
    }

    public static void syncToClient(ServerPlayer player, CompanionKind kind, PlayerCompanionData data) {
        if (player == null || kind == null || data == null) {
            return;
        }
        ArrayList<CompanionListPacket.Entry> entries = new ArrayList<>();
        LinkedHashMap<UUID, CompanionListPacket.Entry> allByUuid = new LinkedHashMap<>();
        for (UUID uuid : data.list(kind)) {
            allByUuid.put(uuid, entryFor(player, data, kind, uuid));
        }
        for (UUID uuid : data.wheelOrder(kind)) {
            CompanionListPacket.Entry entry = allByUuid.get(uuid);
            if (entry != null) {
                entries.add(entry);
            }
        }
        ArrayList<CompanionListPacket.Entry> allEntries = new ArrayList<>(allByUuid.values());
        ModNetwork.sendToPlayer(player, new CompanionListPacket(kind, CompanionDataService.revision(player),
                data.activeWheelIndex(kind), entries, allEntries));
        CompanionWheelTransactionService.observeRoster(player, kind, allEntries);
    }

    private static CompanionListPacket.Entry entryFor(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
        Entity entity = CompanionEntityLookup.findLoadedEntity(player.getServer(), data, uuid).orElse(null);
        Optional<CompoundTag> shoulderTag = kind == CompanionKind.COMPANION ? CompanionShoulderService.shoulderEntityTag(player, uuid) : Optional.empty();
        Optional<CompoundTag> storedTag = shoulderTag.map(tag -> CompanionStorageService.storedShoulderTag(player, uuid, tag)).or(() -> data.storedEntity(uuid).map(tag -> CompanionStorageService.healedStoredEntity(player, data, uuid, tag)));
        String entityType = entity == null ? storedTag.map(CompanionEntitySnapshots::storedEntityType).orElse("") : EntityType.getKey(entity.getType()).toString();
        String name = data.displayName(uuid).orElseGet(() -> entity == null ? storedTag.map(tag -> CompanionEntitySnapshots.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8)) : entity.getDisplayName().getString());
        boolean loaded = entity != null;
        boolean alive = entity == null ? storedTag.isPresent() : entity.isAlive();
        boolean ridden = player.getVehicle() != null && player.getVehicle().getUUID().equals(uuid);
        boolean liveInWorld = entity instanceof LivingEntity living && living.isAlive() && !CompanionStorageService.isStoragePending(living);
        boolean homeResident = CompanionHomeResidentService.isResident(uuid);
        boolean deployed = kind == CompanionKind.MOUNT
                ? ridden || data.isDeployed(kind, uuid)
                : ridden || shoulderTag.isPresent() || data.isDeployed(kind, uuid) || liveInWorld && !homeResident;
        boolean hasHome = data.homePosition(uuid).isPresent();
        float health = 0.0f;
        float maxHealth = 0.0f;
        float armor = 0.0f;
        if (entity instanceof LivingEntity living) {
            health = living.getHealth();
            maxHealth = living.getMaxHealth();
            armor = living.getArmorValue();
        } else if (storedTag.isPresent()) {
            CompoundTag tag = storedTag.get();
            health = tag.getFloat("CompanionRescueHealth");
            maxHealth = tag.getFloat("CompanionRescueMaxHealth");
            armor = tag.getInt("CompanionRescueArmor");
        }
        CompanionMoveType entryMoveType = entity == null ? CompanionEntityClassifier.moveType(entityType, kind) : CompanionEntityClassifier.moveType(entity, kind);
        CompoundTag fullPreviewTag = entity == null ? storedTag.map(CompoundTag::copy).orElse(null) : CompanionEntitySnapshots.previewEntityTag(entity, entityType);
        CompoundTag previewTag = previewTagForPacket(player, kind, uuid, entityType, fullPreviewTag);
        return new CompanionListPacket.Entry(uuid, entity == null ? -1 : entity.getId(), entityType, name, loaded,
                alive, deployed, ridden, hasHome, homeResident,
                CompanionTacticalOrderService.currentAction(uuid), health, maxHealth, armor, entryMoveType,
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.SUMMON, entityType),
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.RESCUE, entityType),
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.STORAGE, entityType),
                data.animationStyle(uuid, com.kuzhi.findme.common.CompanionAnimationPurpose.SWITCH, entityType),
                data.effectStyle(uuid, CompanionEffectPurpose.SUMMON, entityType),
                data.effectStyle(uuid, CompanionEffectPurpose.RESCUE, entityType),
                data.effectStyle(uuid, CompanionEffectPurpose.STORAGE, entityType), previewTag);
    }

    public static void forgetPlayer(ServerPlayer player) {
        if (player != null) {
            SENT_PREVIEW_SIGNATURES.remove(player.getUUID());
        }
    }

    public static void syncDeadToClient(ServerPlayer player) {
        PlayerCompanionData data = CompanionDataService.data(player);
        ArrayList<DeadCompanionListPacket.Entry> entries = new ArrayList<>();
        for (UUID uuid : data.deadList()) {
            Optional<CompoundTag> storedTag = data.storedEntity(uuid);
            CompanionKind kind = data.kindOf(uuid).orElse(CompanionKind.COMPANION);
            String entityType = storedTag.map(CompanionEntitySnapshots::storedEntityType).orElse("");
            String name = data.displayName(uuid).orElseGet(() -> storedTag.map(tag -> CompanionEntitySnapshots.storedEntityName(tag, uuid)).orElse(uuid.toString().substring(0, 8)));
            DeadCompanionRecord record = data.deadRecord(uuid).orElse(new DeadCompanionRecord(UUID.randomUUID(), uuid, name, entityType, kind,
                    -1, 0L, 0L, "", 0.0, 0.0, 0.0, "鏈煡", false, ""));
            float health = storedTag.map(tag -> tag.getFloat("CompanionRescueHealth")).orElse(0.0f);
            float maxHealth = storedTag.map(tag -> tag.getFloat("CompanionRescueMaxHealth")).orElse(0.0f);
            float armor = storedTag.map(tag -> (float)tag.getInt("CompanionRescueArmor")).orElse(0.0f);
            CompanionMoveType moveType = CompanionEntityClassifier.moveType(entityType, kind);
            CompoundTag previewTag = storedTag.map(CompoundTag::copy).orElse(null);
            entries.add(new DeadCompanionListPacket.Entry(uuid, kind, entityType, name, health, maxHealth, armor, moveType,
                    record.recordId(), record.previousTeamIndex(), record.deathTime(), record.worldDay(), record.dimension(), record.x(), record.y(),
                    record.z(), record.deathCause(), record.recoverable(), record.recoveryRequirements(), previewTag));
        }
        ModNetwork.sendToPlayer(player, new DeadCompanionListPacket(entries));
    }

    private static CompoundTag previewTagForPacket(ServerPlayer player, CompanionKind kind, UUID uuid, String entityType, CompoundTag previewTag) {
        if (previewTag == null) {
            return null;
        }
        String signature = entityType + ":" + Integer.toHexString(previewTag.hashCode());
        Map<UUID, String> kindCache = SENT_PREVIEW_SIGNATURES
                .computeIfAbsent(player.getUUID(), ignored -> new EnumMap<>(CompanionKind.class))
                .computeIfAbsent(kind, ignored -> new HashMap<>());
        String previous = kindCache.put(uuid, signature);
        return signature.equals(previous) ? null : previewTag;
    }
}

