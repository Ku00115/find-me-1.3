package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.core.CompanionEntityLookup;
import com.kuzhi.findme.server.profile.CompanionEntityClassifier;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public final class CompanionThreatMemoryService {
    private static final int MEMORY_TICKS = 120;
    private static final Map<UUID, ThreatMemory> MEMORIES = new HashMap<>();

    private CompanionThreatMemoryService() {
    }

    public static void remember(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity attacker = sourceAttacker(event.getSource());
        if (attacker == null || attacker == player || attacker instanceof Player || CompanionEntityClassifier.isOwnedBy(player, attacker)) {
            return;
        }
        MEMORIES.put(player.getUUID(), new ThreatMemory(attacker.getUUID(), attacker.level().dimension(), player.serverLevel().getGameTime() + MEMORY_TICKS));
        CompanionTacticalOrderService.onOwnerHurt(player, attacker);
    }

    public static void handleIncomingDamage(LivingHurtEvent event) {
        if (VehicleManager.shouldCancelSwitchVehicleDamage(event.getEntity(), event.getSource())) {
            event.setAmount(0.0f);
            event.setCanceled(true);
            return;
        }
        remember(event);
    }

    public static Optional<LivingEntity> findRecentThreat(ServerPlayer player, double range) {
        ThreatMemory memory = MEMORIES.get(player.getUUID());
        if (memory == null) {
            return Optional.empty();
        }
        long now = player.serverLevel().getGameTime();
        if (now > memory.expiresAt() || !memory.dimension().equals(player.level().dimension())) {
            MEMORIES.remove(player.getUUID());
            return Optional.empty();
        }
        Entity entity = CompanionEntityLookup.findEntity(player.getServer(), memory.threatUuid()).orElse(null);
        if (!(entity instanceof LivingEntity living) || living.level() != player.level() || !living.isAlive() || living.distanceToSqr(player) > range * range) {
            return Optional.empty();
        }
        return Optional.of(living);
    }

    private static LivingEntity sourceAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) {
            return living;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private record ThreatMemory(UUID threatUuid, ResourceKey<Level> dimension, long expiresAt) {
    }
}
