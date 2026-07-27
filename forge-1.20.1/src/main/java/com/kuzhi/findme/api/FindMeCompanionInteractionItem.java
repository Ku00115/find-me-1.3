package com.kuzhi.findme.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Marker/action contract for addon items that must run before FindMe's mount interaction. */
public interface FindMeCompanionInteractionItem {
    InteractionResult interactWithCompanion(ItemStack stack, Player player, LivingEntity target, InteractionHand hand);
}
