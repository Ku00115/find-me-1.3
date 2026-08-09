package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractSkyStage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherContractSkyStageMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void findMe$hideUnrelatedEntity(Entity entity, double x, double y, double z, float yaw,
                                            float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                                            int packedLight, CallbackInfo callback) {
        if (!ClientContractSkyStage.shouldRenderEntity(entity)) {
            callback.cancel();
        }
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void findMe$hideEntityShadow(PoseStack poseStack, MultiBufferSource bufferSource, Entity entity,
                                                float weight, float partialTick, LevelReader level, float size,
                                                CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }
}
