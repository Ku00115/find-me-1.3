package com.kuzhi.findme.mixin.client;

import com.kuzhi.findme.client.ClientContractSkyStage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherContractSkyStageMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void findMe$hideBlockEntity(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
                                        MultiBufferSource bufferSource, CallbackInfo callback) {
        if (ClientContractSkyStage.active()) {
            callback.cancel();
        }
    }
}
