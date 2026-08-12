package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

/** Applies per-player protection between an owner and their FindMe companions. */
public final class CompanionFriendlyFireService {
    private CompanionFriendlyFireService() {
    }

    public static void handleAttack(LivingAttackEvent event) {
        if (event == null || event.getEntity().level().isClientSide()
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        UUID victimOwner = ownerOf(level, event.getEntity());
        UUID attackerOwner = ownerOf(level, attacker(event.getSource()));
        if (sameProtectedOwner(level.getServer(), victimOwner, attackerOwner)) {
            event.setCanceled(true);
        }
    }

    public static void handleTargetChange(LivingChangeTargetEvent event) {
        if (event == null || event.getEntity().level().isClientSide()
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = event.getEntity();
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null) {
            return;
        }
        UUID mobOwner = ownerOf(level, target);
        UUID newTargetOwner = ownerOf(level, newTarget);
        if (sameProtectedOwner(level.getServer(), mobOwner, newTargetOwner)) {
            event.setNewTarget(null);
        }
    }

    private static Entity attacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        return source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
    }

    private static UUID ownerOf(ServerLevel level, Entity entity) {
        if (level == null || entity == null) {
            return null;
        }
        if (entity instanceof ServerPlayer player) {
            return player.getUUID();
        }
        return FindMeWorldSavedData.get(level.getServer()).companionOwner(entity.getUUID()).orElse(null);
    }

    private static boolean sameProtectedOwner(MinecraftServer server, UUID firstOwner, UUID secondOwner) {
        if (server == null || firstOwner == null || !firstOwner.equals(secondOwner)) {
            return false;
        }
        return CompanionDataService.data(server, firstOwner).uiSettings().friendlyFireProtection();
    }
}
