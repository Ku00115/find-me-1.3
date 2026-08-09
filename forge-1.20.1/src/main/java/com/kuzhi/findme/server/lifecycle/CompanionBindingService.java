package com.kuzhi.findme.server.lifecycle;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.ModItems;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.profile.CompanionBindingProfileService;
import com.kuzhi.findme.common.NamePaperItem;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class CompanionBindingService {
    private CompanionBindingService() {
    }

    static BindingCheck checkManualBinding(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || player.level() != target.level()) {
            return BindingCheck.failure("message.find_me.contract_invalid_target", ChatFormatting.RED);
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        if (data.contains(target.getUUID())) {
            return BindingCheck.failure("message.find_me.contract_already_bound", ChatFormatting.YELLOW, target.getDisplayName());
        }
        Optional<CompanionKind> classified = CompanionRegistrationService.manualBindingKind(player, target);
        if (classified.isEmpty()) {
            return BindingCheck.failure("message.find_me.contract_not_allowed", ChatFormatting.RED, target.getDisplayName());
        }
        FindMeModule module = classified.get() == CompanionKind.MOUNT
                ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!FindMeModuleService.enabled(module)) {
            return BindingCheck.failure("message.find_me.module_disabled", ChatFormatting.YELLOW,
                    net.minecraft.network.chat.Component.translatable("module.find_me." + module.id()));
        }
        return BindingCheck.allowed(classified.get());
    }

    public static boolean handleNamePaperInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        ItemStack stack = event.getItemStack();
        LivingEntity target = livingTarget(event.getTarget());
        if (!stack.is(ModItems.NAME_PAPER.get()) || target == null) {
            return false;
        }
        Player player = event.getEntity();
        event.setCanceled(true);
        if (player.level().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return true;
        }
        CompanionContractService.StartResult result = CompanionContractService.start(serverPlayer, target, event.getHand());
        if (result.accepted()) {
            player.getCooldowns().addCooldown(stack.getItem(), 80);
        }
        if (result.consumePaperNow()) {
            NamePaperItem.consumeOne(stack, player);
        }
        event.setCancellationResult(result.accepted() ? InteractionResult.CONSUME : InteractionResult.FAIL);
        return true;
    }

    public static boolean forceBindAndStore(ServerPlayer player, LivingEntity target, CompanionKind kind) {
        FindMeModule module = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!FindMeModuleService.require(player, module)) {
            return false;
        }
        return bindAndStore(player, target, kind, true);
    }

    static boolean bindAndStore(ServerPlayer player, LivingEntity target, CompanionKind kind) {
        return bindAndStore(player, target, kind, false);
    }

    private static boolean bindAndStore(ServerPlayer player, LivingEntity target, CompanionKind kind,
                                        boolean force) {
        FindMeModule module = kind == CompanionKind.MOUNT ? FindMeModule.RIDING : FindMeModule.COMPANIONS;
        if (!FindMeModuleService.require(player, module)) {
            return false;
        }
        if (!force && !CompanionBindingProfileService.applyForBinding(player, target)) {
            return false;
        }
        if (!CompanionRegistrationService.registerContractedForStorage(player, target, kind)) {
            return false;
        }
        storeBoundTarget(player, kind, target);
        return true;
    }

    private static LivingEntity livingTarget(Entity target) {
        if (target instanceof LivingEntity living) {
            return living;
        }
        if (target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static void storeBoundTarget(ServerPlayer player, CompanionKind kind, LivingEntity target) {
        PlayerCompanionData data = CompanionDataService.data(player);
        CompanionLifecycleFacade.storeBoundTarget(player, data, kind, target, "binding:contract");
    }

    record BindingCheck(boolean allowed, CompanionKind kind, String messageKey, ChatFormatting color, Object[] args) {
        static BindingCheck allowed(CompanionKind kind) {
            return new BindingCheck(true, kind, "", ChatFormatting.WHITE, new Object[0]);
        }

        static BindingCheck failure(String messageKey, ChatFormatting color, Object... args) {
            return new BindingCheck(false, null, messageKey, color, args);
        }
    }
}

