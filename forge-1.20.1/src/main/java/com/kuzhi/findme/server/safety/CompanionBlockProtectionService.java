package com.kuzhi.findme.server.safety;

import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.FindMeWorldSavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;

/** Prevents owner-enabled FindMe companions from changing terrain while preserving combat damage. */
public final class CompanionBlockProtectionService {
    private static final ThreadLocal<TickContext> TICK_CONTEXT = ThreadLocal.withInitial(TickContext::new);

    private CompanionBlockProtectionService() {
    }

    public static void handleMobGriefing(EntityMobGriefingEvent event) {
        if (event != null && protectedSource(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    public static void handleLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (event != null && protectedSource(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void handleFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event != null && protectedSource(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void handleExplosion(ExplosionEvent.Detonate event) {
        if (event == null || !(event.getLevel() instanceof ServerLevel)) return;
        Entity source = event.getExplosion().getExploder();
        if (source == null) source = event.getExplosion().getDirectSourceEntity();
        if (protectedSource(source)) {
            event.getAffectedBlocks().clear();
        }
    }

    public static boolean protectedSource(Entity source) {
        if (source == null || source.level().isClientSide()
                || !(source.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        Set<UUID> visited = new HashSet<>();
        Entity current = source;
        for (int depth = 0; current != null && depth < 4 && visited.add(current.getUUID()); depth++) {
            UUID ownerUuid = FindMeWorldSavedData.get(server).companionOwner(current.getUUID()).orElse(null);
            if (ownerUuid != null) {
                return CompanionDataService.data(server, ownerUuid).uiSettings().boundCreatureBlockProtection();
            }
            current = causalOwner(current);
        }
        return false;
    }

    public static boolean beginProtectedTick(Entity entity) {
        if (!protectedSource(entity)) return false;
        TICK_CONTEXT.get().depth++;
        return true;
    }

    public static void endProtectedTick() {
        TickContext context = TICK_CONTEXT.get();
        if (context.depth > 0) context.depth--;
    }

    public static boolean isProtectedTick() {
        return TICK_CONTEXT.get().depth > 0;
    }

    private static Entity causalOwner(Entity entity) {
        if (entity instanceof Projectile projectile) return projectile.getOwner();
        if (entity instanceof PrimedTnt primedTnt) return primedTnt.getOwner();
        return null;
    }

    private static final class TickContext {
        private int depth;
    }
}
