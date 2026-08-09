package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.server.data.PlayerCompanionData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class CompanionSpellItemReturnService {
    private CompanionSpellItemReturnService() {
    }

    public static void deliverPending(ServerPlayer player, PlayerCompanionData data) {
        if (player == null || data == null || !data.hasPendingSpellItemReturns()) return;
        for (var itemTag : data.drainPendingSpellItemReturns()) {
            ItemStack stack = ItemStack.of(itemTag);
            if (stack.isEmpty()) continue;
            stack.setCount(1);
            if (!player.addItem(stack)) player.drop(stack, false);
        }
    }
}
