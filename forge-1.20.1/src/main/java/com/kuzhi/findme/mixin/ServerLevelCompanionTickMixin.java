package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.safety.CompanionBlockProtectionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelCompanionTickMixin {
    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void findme$tickProtectedCompanion(Entity entity) {
        boolean protectedTick = CompanionBlockProtectionService.beginProtectedTick(entity);
        try {
            entity.tick();
        } finally {
            if (protectedTick) CompanionBlockProtectionService.endProtectedTick();
        }
    }

    @Redirect(method = "tickPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void findme$tickProtectedPassenger(Entity entity) {
        boolean protectedTick = CompanionBlockProtectionService.beginProtectedTick(entity);
        try {
            entity.rideTick();
        } finally {
            if (protectedTick) CompanionBlockProtectionService.endProtectedTick();
        }
    }
}
