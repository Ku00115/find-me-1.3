package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.core.CompanionInteractionService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerInteractionMixin {
    @Unique
    private boolean findMe$nativeMountFallback;

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void findMe$planRegisteredMountInteraction(
            Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        findMe$nativeMountFallback = false;
        CompanionInteractionService.MountInteractionPlan plan = CompanionInteractionService.planMountInteraction(
                (Player) (Object) this, target, hand);
        if (plan == null) return;
        if (plan.nativeFirst()) {
            findMe$nativeMountFallback = true;
        } else {
            callback.setReturnValue(plan.immediateResult());
        }
    }

    @Inject(method = "interactOn", at = @At("RETURN"), cancellable = true)
    private void findMe$mountRegisteredCompanionAfterNativeInteraction(
            Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        if (!findMe$nativeMountFallback
                || !((Object) this instanceof ServerPlayer player)
                || !(target instanceof LivingEntity mount)) return;
        findMe$nativeMountFallback = false;
        InteractionResult nativeResult = callback.getReturnValue();
        InteractionResult result = CompanionInteractionService.mountAfterNativeInteraction(
                player, mount, nativeResult);
        if (result != nativeResult) {
            callback.setReturnValue(result);
        }
    }
}
