package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.safety.CompanionBlockProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelCompanionBlockProtectionMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void findme$preventCompanionDestroy(BlockPos pos, boolean drop, Entity breaker, int recursionLeft,
                                                CallbackInfoReturnable<Boolean> callback) {
        if (CompanionBlockProtectionService.isProtectedTick()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void findme$preventCompanionBlockChange(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                                    CallbackInfoReturnable<Boolean> callback) {
        if (!CompanionBlockProtectionService.isProtectedTick()) return;
        Level level = (Level)(Object)this;
        if (!level.getBlockState(pos).equals(state)) {
            callback.setReturnValue(false);
        }
    }
}
