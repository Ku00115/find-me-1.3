package com.kuzhi.findme.server.ui;

import com.kuzhi.findme.api.CompanionSpellBinding;
import com.kuzhi.findme.api.FindMeApi;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionSpellSlotCandidatesPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.server.data.CompanionDataService;
import com.kuzhi.findme.server.data.PlayerCompanionData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class CompanionSpellSlotService {
    private CompanionSpellSlotService() {
    }

    public static void sendCandidates(ServerPlayer player, UUID uuid) {
        if (player == null || uuid == null) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<CompanionKind> kind = validTarget(data, uuid);
        if (kind.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.invalid_target"), true);
            ModNetwork.sendToPlayer(player, new CompanionSpellSlotCandidatesPacket(uuid, List.of()));
            return;
        }
        List<CompanionSpellSlotCandidatesPacket.Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Optional<CompanionSpellBinding> binding = FindMeApi.createCompanionSpellBinding(player, uuid, stack);
            if (binding.isEmpty()) {
                continue;
            }
            entries.add(new CompanionSpellSlotCandidatesPacket.Entry(slot, binding.get().displayName(),
                    binding.get().spellLevel(), stack.getCount(), binding.get().iconResource(),
                    binding.get().role(), binding.get().itemTag()));
        }
        ModNetwork.sendToPlayer(player, new CompanionSpellSlotCandidatesPacket(uuid, entries));
    }

    public static void bindFromHeldScroll(ServerPlayer player, UUID uuid, int companionSlot) {
        if (player == null || uuid == null) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<CompanionKind> kind = validTarget(data, uuid);
        if (kind.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.invalid_target"), true);
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            Optional<CompanionSpellBinding> binding = FindMeApi.createCompanionSpellBinding(player, uuid, stack);
            if (binding.isEmpty()) {
                continue;
            }
            if (!validSlot(companionSlot)) return;
            data.clearSpellBinding(uuid, companionSlot).ifPresent(previous -> returnStoredItem(player, previous));
            data.setSpellBinding(uuid, companionSlot, binding.get());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            CompanionDataService.save(player, data);
            CompanionSyncService.syncToClient(player, kind.get(), data);
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.bound",
                    binding.get().displayName()), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.find_me.spell_slot.no_scroll"), true);
    }

    public static void bindFromInventorySlot(ServerPlayer player, UUID uuid, int companionSlot, int inventorySlot) {
        if (player == null || uuid == null) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<CompanionKind> kind = validTarget(data, uuid);
        if (kind.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.invalid_target"), true);
            return;
        }
        if (!validSlot(companionSlot) || inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) {
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.invalid_inventory_slot"), true);
            return;
        }
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        Optional<CompanionSpellBinding> binding = FindMeApi.createCompanionSpellBinding(player, uuid, stack);
        if (binding.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.selected_scroll_unavailable"), true);
            return;
        }
        Optional<CompanionSpellBinding> previous = data.spellBinding(uuid, companionSlot);
        data.setSpellBinding(uuid, companionSlot, binding.get());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        previous.ifPresent(value -> returnStoredItem(player, value));
        CompanionDataService.save(player, data);
        CompanionSyncService.syncToClient(player, kind.get(), data);
        player.displayClientMessage(Component.translatable("message.find_me.spell_slot.bound",
                binding.get().displayName()), true);
    }

    public static void clear(ServerPlayer player, UUID uuid, int companionSlot) {
        if (player == null || uuid == null) {
            return;
        }
        PlayerCompanionData data = CompanionDataService.data(player);
        Optional<CompanionKind> kind = data.kindOf(uuid);
        if (!validSlot(companionSlot)) return;
        Optional<CompanionSpellBinding> removed = data.clearSpellBinding(uuid, companionSlot);
        if (removed.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.find_me.spell_slot.empty"), true);
            return;
        }
        returnStoredItem(player, removed.get());
        CompanionDataService.save(player, data);
        kind.ifPresent(value -> CompanionSyncService.syncToClient(player, value, data));
        player.displayClientMessage(Component.translatable("message.find_me.spell_slot.cleared"), true);
    }

    private static Optional<CompanionKind> validTarget(PlayerCompanionData data, UUID uuid) {
        Optional<CompanionKind> kind = data.kindOf(uuid);
        if (kind.isEmpty() || data.deadList().contains(uuid) || data.containsVehicle(uuid)) {
            return Optional.empty();
        }
        return kind;
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < PlayerCompanionData.COMPANION_SPELL_SLOT_COUNT;
    }

    private static void returnStoredItem(ServerPlayer player, CompanionSpellBinding binding) {
        ItemStack stack = ItemStack.parseOptional(player.registryAccess(), binding.itemTag());
        if (stack.isEmpty()) {
            return;
        }
        stack.setCount(1);
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}
