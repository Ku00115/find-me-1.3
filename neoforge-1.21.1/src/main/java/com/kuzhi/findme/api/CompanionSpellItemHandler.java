package com.kuzhi.findme.api;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import com.kuzhi.findme.server.ui.CompanionSyncService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

final class CompanionSpellItemHandler implements IItemHandlerModifiable {
    private final ServerPlayer owner;
    private final UUID companionUuid;

    CompanionSpellItemHandler(ServerPlayer owner, UUID companionUuid) {
        this.owner = owner;
        this.companionUuid = companionUuid;
    }

    @Override
    public int getSlots() {
        return PlayerCompanionData.COMPANION_SPELL_SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!valid(slot)) return ItemStack.EMPTY;
        return CompanionDataService.data(owner).spellBinding(companionUuid, slot)
                .map(binding -> ItemStack.parseOptional(owner.registryAccess(), binding.itemTag()))
                .orElse(ItemStack.EMPTY);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!valid(slot)) return;
        PlayerCompanionData data = CompanionDataService.data(owner);
        if (stack == null || stack.isEmpty()) {
            data.clearSpellBinding(companionUuid, slot);
            commit(data);
            return;
        }
        FindMeApi.createCompanionSpellBinding(owner, companionUuid, stack).ifPresent(binding -> {
            data.setSpellBinding(companionUuid, slot, binding);
            commit(data);
        });
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!valid(slot) || stack == null || stack.isEmpty() || !getStackInSlot(slot).isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        Optional<CompanionSpellBinding> binding = FindMeApi.createCompanionSpellBinding(owner,
                companionUuid, stack);
        if (binding.isEmpty()) return stack;
        if (!simulate) {
            PlayerCompanionData data = CompanionDataService.data(owner);
            data.setSpellBinding(companionUuid, slot, binding.get());
            commit(data);
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!valid(slot) || amount <= 0) return ItemStack.EMPTY;
        ItemStack stored = getStackInSlot(slot);
        if (stored.isEmpty()) return ItemStack.EMPTY;
        stored.setCount(1);
        if (!simulate) {
            PlayerCompanionData data = CompanionDataService.data(owner);
            data.clearSpellBinding(companionUuid, slot);
            commit(data);
        }
        return stored;
    }

    @Override
    public int getSlotLimit(int slot) {
        return valid(slot) ? 1 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return valid(slot) && stack != null && !stack.isEmpty()
                && FindMeApi.createCompanionSpellBinding(owner, companionUuid, stack).isPresent();
    }

    private void commit(PlayerCompanionData data) {
        CompanionDataService.save(owner, data);
        data.kindOf(companionUuid).ifPresent(kind -> CompanionSyncService.syncToClient(owner, kind, data));
    }

    private boolean valid(int slot) {
        if (slot < 0 || slot >= getSlots()) return false;
        PlayerCompanionData data = CompanionDataService.data(owner);
        return data.kindOf(companionUuid).isPresent() && !data.deadList().contains(companionUuid)
                && !data.containsVehicle(companionUuid);
    }
}
