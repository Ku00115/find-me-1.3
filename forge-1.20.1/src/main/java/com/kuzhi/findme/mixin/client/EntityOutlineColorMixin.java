package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientSummonedOutlineState;
import com.kuzhi.findme.client.ClientTacticalTargetOutlineState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityOutlineColorMixin {
    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void findMe$summonedOutlineColor(CallbackInfoReturnable<Integer> callback) {
        Entity entity = (Entity)(Object)this;
        callback.setReturnValue(ClientTacticalTargetOutlineState.shouldOutline(entity)
                ? ClientTacticalTargetOutlineState.COLOR
                : ClientSummonedOutlineState.color(entity, callback.getReturnValue()));
    }
}
