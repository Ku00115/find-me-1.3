package com.kuzhi.findme.network;

import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.ui.CompanionSpellSlotService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record CompanionSpellSlotPacket(UUID uuid, Action action, int companionSlot, int inventorySlot,
                                       UUID requestId) {
    public CompanionSpellSlotPacket(UUID uuid, Action action, int companionSlot) {
        this(uuid, action, companionSlot, -1, null);
    }

    public CompanionSpellSlotPacket(UUID uuid, Action action, int companionSlot, int inventorySlot) {
        this(uuid, action, companionSlot, inventorySlot, null);
    }

    public static void encode(CompanionSpellSlotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.uuid);
        buffer.writeEnum(packet.action);
        buffer.writeVarInt(packet.companionSlot);
        buffer.writeVarInt(packet.inventorySlot);
        buffer.writeBoolean(packet.requestId != null);
        if (packet.requestId != null) buffer.writeUUID(packet.requestId);
    }

    public static CompanionSpellSlotPacket decode(FriendlyByteBuf buffer) {
        UUID uuid = buffer.readUUID();
        Action action = buffer.readEnum(Action.class);
        int companionSlot = buffer.readVarInt();
        int inventorySlot = buffer.readVarInt();
        return new CompanionSpellSlotPacket(uuid, action, companionSlot, inventorySlot,
                buffer.readBoolean() ? buffer.readUUID() : null);
    }

    public static void handle(CompanionSpellSlotPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null
                    || !FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)) {
                return;
            }
            switch (packet.action) {
                case REQUEST_CANDIDATES -> CompanionSpellSlotService.sendCandidates(context.getSender(), packet.uuid,
                        packet.companionSlot, packet.requestId);
                case BIND_FROM_HAND -> CompanionSpellSlotService.bindFromHeldScroll(context.getSender(), packet.uuid,
                        packet.companionSlot);
                case BIND_INVENTORY_SLOT -> CompanionSpellSlotService.bindFromInventorySlot(context.getSender(),
                        packet.uuid, packet.companionSlot, packet.inventorySlot);
                case CLEAR -> CompanionSpellSlotService.clear(context.getSender(), packet.uuid, packet.companionSlot);
            }
        });
        context.setPacketHandled(true);
    }

    public enum Action {
        REQUEST_CANDIDATES,
        BIND_FROM_HAND,
        BIND_INVENTORY_SLOT,
        CLEAR
    }
}
