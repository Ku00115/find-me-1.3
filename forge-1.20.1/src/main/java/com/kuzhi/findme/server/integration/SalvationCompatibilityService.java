package com.kuzhi.findme.server.integration;

import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.server.core.FindMeDebugLogger;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** FindMe-owned adapter for Salvation's stable entity markers. */
public final class SalvationCompatibilityService {
    private static final String TAMED_KEY = "SalvationTamed";
    private static final String OWNER_KEY = "SalvationOwner";
    private static final String REDEEMED_KEY = "FindMeSalvationRedeemed";
    private static final String MOVEMENT_KEY = "FindMeSalvationMovement";
    private static final String GENERIC_CONTROL_KEY = "FindMeSalvationGenericControl";
    private static boolean registered;

    private SalvationCompatibilityService() {
    }

    public static void bootstrap() {
        if (registered) return;
        registered = true;
        FindMeApi.registerOwnershipProvider(SalvationCompatibilityService::ownerUuid);
        FindMeApi.registerMovementProfileProvider((type, entity) -> movement(entity));
    }

    public static void onSalvationSucceeded(ServerPlayer player, UUID entityUuid) {
        if (player == null || entityUuid == null || player.getServer() == null) return;
        player.getServer().execute(() -> {
            if (player.isRemoved()) return;
            if (player.serverLevel().getEntity(entityUuid) instanceof LivingEntity entity) {
                bindAndStore(player, entity);
            }
        });
    }

    static Optional<UUID> ownerUuid(LivingEntity entity) {
        if (entity == null) return Optional.empty();
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(TAMED_KEY) || !data.hasUUID(OWNER_KEY)) return Optional.empty();
        return Optional.of(data.getUUID(OWNER_KEY));
    }

    static Optional<CompanionMoveType> movement(LivingEntity entity) {
        if (entity == null || !entity.getPersistentData().getBoolean(REDEEMED_KEY)) {
            return Optional.empty();
        }
        try {
            return Optional.of(CompanionMoveType.valueOf(entity.getPersistentData().getString(MOVEMENT_KEY)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static void bindAndStore(ServerPlayer player, LivingEntity entity) {
        Optional<UUID> owner = ownerUuid(entity);
        if (!entity.isAlive() || entity.isRemoved() || entity.level() != player.level()
                || !entity.getPersistentData().getBoolean(REDEEMED_KEY)
                || owner.filter(player.getUUID()::equals).isEmpty()) {
            FindMeDebugLogger.info("integration", "salvation handoff rejected player={} entity={} reason=invalid_state",
                    player.getUUID(), entity.getUUID());
            return;
        }
        CompanionKind kind = entity.getPersistentData().getBoolean(GENERIC_CONTROL_KEY)
                ? CompanionKind.MOUNT : CompanionKind.COMPANION;
        boolean stored = FindMeApi.bindAndStore(player, entity, kind);
        FindMeDebugLogger.info("integration", "salvation handoff player={} entity={} kind={} stored={}",
                player.getUUID(), entity.getUUID(), kind, stored);
    }
}
