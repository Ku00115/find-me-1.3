package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractSkyStage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
abstract class ParticleEngineContractSkyStageMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void findMe$hideParticles(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                      LightTexture lightTexture, Camera camera, float partialTick, Frustum frustum,
                                      CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }
}
