package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.lifecycle.CompanionRideHomeJourneyService;
import com.kuzhi.findme.server.lifecycle.CompanionWaystoneJourneyService;
import com.kuzhi.findme.api.FindMeApi;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void findMe$ignoreRideHomeVehicleMovement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (CompanionRideHomeJourneyService.ownsMountedMovement(this.player)
                || CompanionWaystoneJourneyService.ownsMountedMovement(this.player)
                || FindMeApi.ownsExternalMountedMovement(this.player)) {
            ci.cancel();
        }
    }
}
