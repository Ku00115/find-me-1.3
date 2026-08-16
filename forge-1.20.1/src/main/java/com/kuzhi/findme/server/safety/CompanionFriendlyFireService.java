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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
        Entity attackingEntity = attacker(event.getSource());
        UUID attackerOwner = ownerOf(level, attackingEntity);
        if (sameProtectedOwner(level.getServer(), victimOwner, attackerOwner)) {
            event.setCanceled(true);
            if (attackingEntity instanceof Mob mob) {
                clearCombatIntent(mob, event.getEntity());
            }
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
        if (isFriendlyTarget(target, newTarget)) {
            event.setNewTarget(null);
            if (target instanceof Mob mob) {
                clearCombatIntent(mob, newTarget);
            }
        }
    }

    /** Shared target blacklist used by Forge events and the vanilla AI entry points. */
    public static boolean isFriendlyTarget(Entity attacker, Entity target) {
        if (attacker == null || target == null || attacker == target
                || attacker.level().isClientSide()
                || !(attacker.level() instanceof ServerLevel level)) {
            return false;
        }
        return sameProtectedOwner(level.getServer(), ownerOf(level, attacker), ownerOf(level, target));
    }

    /** Global command/automation blacklist: bound creatures are never implicit attack targets. */
    public static boolean isBoundCompanion(Entity entity) {
        if (entity == null || entity.level().isClientSide()
                || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        return FindMeWorldSavedData.get(level.getServer()).companionOwner(entity.getUUID()).isPresent();
    }

    private static void clearCombatIntent(Mob mob, LivingEntity friendlyTarget) {
        if (mob.getTarget() == friendlyTarget) {
            mob.setTarget(null);
        }
        if (mob.getLastHurtByMob() == friendlyTarget) {
            mob.setLastHurtByMob(null);
        }
        if (mob.getLastHurtMob() == friendlyTarget) {
            mob.setLastHurtMob(null);
        }
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        mob.getNavigation().stop();
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
