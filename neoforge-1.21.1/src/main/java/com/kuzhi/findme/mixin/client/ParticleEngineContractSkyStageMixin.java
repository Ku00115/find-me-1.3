package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractSkyStage;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineContractSkyStageMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At("HEAD"), cancellable = true)
    private void findMe$hideParticles(LightTexture lightTexture, Camera camera, float partialTick, Frustum frustum,
                                      Predicate<ParticleRenderType> renderTypePredicate, CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }
}
