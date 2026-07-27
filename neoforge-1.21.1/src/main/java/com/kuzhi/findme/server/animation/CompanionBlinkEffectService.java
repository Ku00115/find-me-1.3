package com.kuzhi.findme.server.animation;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.kuzhi.findme.server.profile.CompanionEntityVisualBoundsService;

public final class CompanionBlinkEffectService {
    private CompanionBlinkEffectService() {
    }

    public static void spawn(LivingEntity living) {
        Level level = living.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB box = CompanionEntityVisualBoundsService.effectBounds(living);
        double width = Math.max(0.15, Math.max(box.getXsize(), box.getZsize()));
        double height = Math.max(0.20, box.getYsize());
        double size = Math.max(width, height * 0.55);
        int count = Mth.clamp((int)Math.round(8.0 + size * size * 16.0), 10, 480);
        serverLevel.sendParticles((ParticleOptions)ParticleTypes.PORTAL, box.getCenter().x, box.minY + height * 0.5, box.getCenter().z, count, width * 0.65, height * 0.45, width * 0.65, 0.16);
    }
}
