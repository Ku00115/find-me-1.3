package com.kuzhi.findme.common;

import com.kuzhi.findme.server.vehicle.VehicleManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

public final class VehicleSeatCushionItem extends Item {
    public VehicleSeatCushionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        return VehicleManager.bindVehicleSeatAt(player, context.getClickedPos())
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.find_me.vehicle_seat_cushion.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
