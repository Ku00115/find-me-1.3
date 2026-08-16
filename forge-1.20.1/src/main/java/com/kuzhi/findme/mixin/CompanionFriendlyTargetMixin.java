package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.safety.CompanionFriendlyFireService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents modded goals from assigning a same-owner FindMe companion as a target. */
@Mixin(Mob.class)
abstract class CompanionFriendlyTargetMixin {
    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void findme$blacklistFriendlyTarget(LivingEntity target, CallbackInfo callback) {
        Mob self = (Mob)(Object)this;
        if (CompanionFriendlyFireService.isFriendlyTarget(self, target)) {
            callback.cancel();
        }
    }
}
