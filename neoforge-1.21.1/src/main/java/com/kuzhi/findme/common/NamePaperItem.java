package com.kuzhi.findme.common;

import com.kuzhi.findme.server.lifecycle.CompanionContractService;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public class NamePaperItem extends Item {
    public NamePaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        CompanionContractService.StartResult result = CompanionContractService.start(serverPlayer, target, hand);
        if (result.accepted()) {
            player.getCooldowns().addCooldown(this, 80);
        }
        if (result.consumePaperNow()) {
            consumeOne(stack, player);
        }
        return result.accepted() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    public static void consumeOne(ItemStack stack, Player player) {
        if (stack.isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        stack.shrink(1);
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.find_me.name_paper");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.find_me.name_paper.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
