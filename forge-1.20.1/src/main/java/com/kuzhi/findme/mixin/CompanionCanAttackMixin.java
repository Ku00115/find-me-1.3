package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.safety.CompanionFriendlyFireService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes vanilla and modded target predicates reject same-owner FindMe companions. */
@Mixin(LivingEntity.class)
abstract class CompanionCanAttackMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void findme$blacklistFriendlyAttack(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (CompanionFriendlyFireService.isFriendlyTarget(self, target)) {
            callback.setReturnValue(false);
        }
    }
}
