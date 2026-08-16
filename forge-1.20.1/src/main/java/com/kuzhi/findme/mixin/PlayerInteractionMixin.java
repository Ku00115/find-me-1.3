package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.core.CompanionInteractionService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerInteractionMixin {
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void findMe$planRegisteredMountInteraction(
            Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        CompanionInteractionService.MountInteractionPlan plan = CompanionInteractionService.planMountInteraction(
                (Player) (Object) this, target, hand);
        if (plan != null) {
            callback.setReturnValue(plan.immediateResult());
        }
    }
}
