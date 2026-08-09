package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractSkyStage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererContractSkyStageMixin {
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void findMe$hideTerrain(RenderType renderType, PoseStack poseStack, double x, double y, double z,
                                    Matrix4f projectionMatrix, CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void findMe$hideWeather(LightTexture lightTexture, float partialTick, double x, double y, double z,
                                    CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderWorldBorder", at = @At("HEAD"), cancellable = true)
    private void findMe$hideWorldBorder(Camera camera, CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void findMe$hideBlockOutline(PoseStack poseStack, VertexConsumer consumer, Entity entity,
                                         double x, double y, double z, BlockPos pos, BlockState state,
                                         CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }

    @Inject(method = "renderDebug", at = @At("HEAD"), cancellable = true)
    private void findMe$hideDebugGeometry(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera,
                                          CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }
}
