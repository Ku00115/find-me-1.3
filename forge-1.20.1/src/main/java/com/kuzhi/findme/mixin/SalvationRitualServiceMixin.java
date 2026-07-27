package com.kuzhi.findme.mixin;

import com.kuzhi.findme.server.integration.SalvationCompatibilityService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.kuzhi.findmesalvation.server.RitualService", remap = false)
public abstract class SalvationRitualServiceMixin {
    @Inject(method = "choose", at = @At("RETURN"), remap = false)
    private static void findMe$storeSalvationResult(ServerPlayer player, UUID targetUuid,
                                                     @Coerce Object choice, CallbackInfo ci) {
        if (choice instanceof Enum<?> value && "SALVATION".equals(value.name())) {
            SalvationCompatibilityService.onSalvationSucceeded(player, targetUuid);
        }
    }
}
