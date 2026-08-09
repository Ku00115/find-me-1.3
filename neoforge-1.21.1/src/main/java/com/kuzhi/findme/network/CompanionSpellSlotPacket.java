package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.ui.CompanionSpellSlotService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionSpellSlotPacket(UUID uuid, Action action, int companionSlot, int inventorySlot)
        implements CustomPacketPayload {
    public static final Type<CompanionSpellSlotPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_spell_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionSpellSlotPacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionSpellSlotPacket::encode, CompanionSpellSlotPacket::decode);

    public CompanionSpellSlotPacket(UUID uuid, Action action, int companionSlot) {
        this(uuid, action, companionSlot, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(CompanionSpellSlotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.uuid);
        buffer.writeEnum(packet.action);
        buffer.writeVarInt(packet.companionSlot);
        buffer.writeVarInt(packet.inventorySlot);
    }

    private static CompanionSpellSlotPacket decode(FriendlyByteBuf buffer) {
        return new CompanionSpellSlotPacket(buffer.readUUID(), buffer.readEnum(Action.class),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(CompanionSpellSlotPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null
                    || !FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)) return;
            switch (packet.action) {
                case REQUEST_CANDIDATES -> CompanionSpellSlotService.sendCandidates(context.getSender(), packet.uuid);
                case BIND_FROM_HAND -> CompanionSpellSlotService.bindFromHeldScroll(context.getSender(), packet.uuid,
                        packet.companionSlot);
                case BIND_INVENTORY_SLOT -> CompanionSpellSlotService.bindFromInventorySlot(context.getSender(),
                        packet.uuid, packet.companionSlot, packet.inventorySlot);
                case CLEAR -> CompanionSpellSlotService.clear(context.getSender(), packet.uuid, packet.companionSlot);
            }
        });
        context.setPacketHandled(true);
    }

    public enum Action { REQUEST_CANDIDATES, BIND_FROM_HAND, BIND_INVENTORY_SLOT, CLEAR }
}
