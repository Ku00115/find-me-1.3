package com.kuzhi.findme.server.animation;


import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class CompanionEnderEffectService {
    private CompanionEnderEffectService() {}
    public static void play(ServerPlayer owner, LivingEntity entity) {
        play(owner, CompanionEntityVisualBoundsService.effectBounds(entity));
    }

    public static void play(ServerLevel level, LivingEntity entity) {
        play(level, null, CompanionEntityVisualBoundsService.effectBounds(entity));
    }

    public static void play(ServerPlayer owner, AABB box) {
        play(owner.serverLevel(), owner, box);
    }

    private static void play(ServerLevel level, ServerPlayer owner, AABB box) {
        double sx=Math.max(0.10,box.getXsize()*0.48), sy=Math.max(0.12,box.getYsize()*0.42), sz=Math.max(0.10,box.getZsize()*0.48);
        int count=(int)Math.max(8,Math.min(640,(box.getXsize()*box.getYsize()+box.getZsize()*box.getYsize())*18));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,box.getCenter().x,box.minY+box.getYsize()*0.5,box.getCenter().z,count,sx,sy,sz,0.12);
        level.playSound(owner, box.getCenter().x, box.minY + box.getYsize() * 0.5, box.getCenter().z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.75f, 1.0f);
        if (owner != null) owner.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.75f, 1.0f);
    }
}
