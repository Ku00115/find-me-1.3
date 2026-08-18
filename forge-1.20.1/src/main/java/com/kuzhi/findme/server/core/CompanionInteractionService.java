package com.kuzhi.findme.server.core;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.lifecycle.CompanionBindingService;
import com.kuzhi.findme.server.lifecycle.CompanionMountCinematicFlowService;
import com.kuzhi.findme.server.vehicle.VehicleManager;
import com.kuzhi.findme.api.FindMeCompanionInteractionItem;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

    public static MountInteractionPlan planMountInteraction(Player interactingPlayer, Entity target,
                                                             InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || interactingPlayer.isSecondaryUseActive()
                || !(target instanceof LivingEntity mount)
                || !(interactingPlayer instanceof ServerPlayer player)
                || !CompanionDataService.runtimeIndex(player).contains(CompanionKind.MOUNT, mount.getUUID())) {
            return null;
        }
        if (!CompanionMountCinematicFlowService.isBoardingTransition(player, mount)) {
            return null;
        }
        if (!FindMeModuleService.require(player, FindMeModule.RIDING)) {
            return new MountInteractionPlan(InteractionResult.FAIL);
        }
        return new MountInteractionPlan(mount(player, mount));
    }

    private static InteractionResult mount(ServerPlayer player, LivingEntity mount) {
        if (player.getVehicle() == mount) {
            return InteractionResult.CONSUME;
        }
        boolean riding = player.startRiding(mount, true);
        FindMeMod.LOGGER.info("[FindMe ride/right-click] player={} target={} type={} result={}",
                player.getUUID(), mount.getUUID(), EntityType.getKey(mount.getType()), riding ? "MOUNTED" : "REJECTED");
        return riding ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    public record MountInteractionPlan(InteractionResult immediateResult) {
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
