package com.kuzhi.findme.server.core;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.lifecycle.CompanionBindingService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.server.vehicle.VehicleSeatService;
import com.kuzhi.findme.api.FindMeCompanionInteractionItem;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class CompanionInteractionService {
    private CompanionInteractionService() {
    }

    public static void handleEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (handleSkillBookInteract(event)) {
            return;
        }
        if (FindMeModuleService.enabled(FindMeModule.RIDING) && VehicleManager.handleBinderInteract(event)) {
            return;
        }
        CompanionBindingService.handleNamePaperInteract(event);
    }

    public static void handleMountInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isSecondaryUseActive()
                || !(event.getTarget() instanceof LivingEntity mount)) {
            return;
        }
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !CompanionDataService.data(player).contains(CompanionKind.MOUNT, mount.getUUID())) {
            return;
        }
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }

        boolean riding = player.getVehicle() == mount || player.startRiding(mount, true);
        FindMeMod.LOGGER.info("[FindMe ride/right-click] player={} target={} type={} result={}",
                player.getUUID(), mount.getUUID(), EntityType.getKey(mount.getType()), riding ? "MOUNTED" : "REJECTED");
        event.setCancellationResult(riding ? InteractionResult.CONSUME : InteractionResult.FAIL);
        event.setCanceled(true);
    }

    private static boolean handleSkillBookInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        ItemStack stack = event.getEntity().getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof FindMeCompanionInteractionItem item)
                || !(event.getTarget() instanceof LivingEntity target)) {
            return false;
        }
        InteractionResult result = item.interactWithCompanion(stack, event.getEntity(), target, event.getHand());
        event.setCancellationResult(result == InteractionResult.PASS ? InteractionResult.FAIL : result);
        event.setCanceled(true);
        return true;
    }
}
