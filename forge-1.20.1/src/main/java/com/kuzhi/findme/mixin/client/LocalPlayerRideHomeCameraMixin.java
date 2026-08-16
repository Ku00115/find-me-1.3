package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractCamera;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerRideHomeCameraMixin {
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void findMe$keepPlayerMovementDuringRideHomeCamera(CallbackInfoReturnable<Boolean> callback) {
        if (ClientContractCamera.keepsLocalPlayerControlled()) {
            callback.setReturnValue(true);
        }
    }
}
