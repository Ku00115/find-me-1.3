package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.home.CompanionHomeResidentService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class HomeResidentCollisionMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void findMe$ignoreResidentPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (self instanceof LivingEntity first && other instanceof LivingEntity second
                && CompanionHomeResidentService.isHomeResident(first)
                && CompanionHomeResidentService.isHomeResident(second)) {
            ci.cancel();
        }
    }
}
