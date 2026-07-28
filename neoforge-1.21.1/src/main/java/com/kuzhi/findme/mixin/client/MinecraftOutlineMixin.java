package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientSummonedOutlineState;
import com.kuzhi.findme.client.ClientTacticalTargetOutlineState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftOutlineMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void findMe$showSummonedOutline(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (ClientTacticalTargetOutlineState.shouldOutline(entity)
                || ClientSummonedOutlineState.shouldOutline(entity)) callback.setReturnValue(true);
    }
}
