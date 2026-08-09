package com.kuzhi.findme.api;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface FindMeCompanionSpellProvider {
    ResourceLocation id();

    Optional<CompanionSpellBinding> createBinding(ServerPlayer player, UUID companionUuid, ItemStack stack);

    default boolean tryCast(ServerPlayer owner, LivingEntity companion, CompanionSpellBinding binding,
                            CompanionSpellIntent intent, LivingEntity target) {
        return false;
    }

    default boolean isCasting(LivingEntity companion) {
        return false;
    }

    default CompanionMagicState magicState(ServerPlayer owner, UUID companionUuid,
                                           LivingEntity companion, CompoundTag storedEntity) {
        return CompanionMagicState.EMPTY;
    }
}
