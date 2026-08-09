package com.kuzhi.findme.network;

import com.kuzhi.findme.api.CompanionSpellRole;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.handler.codec.DecoderException;

public record CompanionSpellSlotCandidatesPacket(UUID uuid, int companionSlot, UUID requestId,
                                                  List<Entry> entries) {
    public CompanionSpellSlotCandidatesPacket {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static void encode(CompanionSpellSlotCandidatesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.uuid);
        buffer.writeVarInt(packet.companionSlot);
        buffer.writeUUID(packet.requestId);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeVarInt(entry.inventorySlot);
            buffer.writeUtf(entry.displayName, 128);
            buffer.writeVarInt(entry.spellLevel);
            buffer.writeVarInt(entry.count);
            buffer.writeResourceLocation(entry.iconResource);
            buffer.writeEnum(entry.role);
        }
    }

    public static CompanionSpellSlotCandidatesPacket decode(FriendlyByteBuf buffer) {
        UUID uuid = buffer.readUUID();
        int companionSlot = buffer.readVarInt();
        UUID requestId = buffer.readUUID();
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) throw new DecoderException("Invalid FindMe spell candidate count: " + size);
        List<Entry> entries = new ArrayList<>(Math.min(size, 64));
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readVarInt(), buffer.readUtf(128), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readResourceLocation(), buffer.readEnum(CompanionSpellRole.class)));
        }
        return new CompanionSpellSlotCandidatesPacket(uuid, companionSlot, requestId, entries);
    }

    public static void handle(CompanionSpellSlotCandidatesPacket packet,
                              Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> FindMeAuiManageScreen.openSpellScrollPicker(packet.uuid, packet.companionSlot,
                packet.requestId, packet.entries));
        context.setPacketHandled(true);
    }

    public record Entry(int inventorySlot, String displayName, int spellLevel, int count,
                        ResourceLocation iconResource, CompanionSpellRole role) {
        public Entry {
            displayName = displayName == null ? "" : displayName;
            spellLevel = Math.max(0, spellLevel);
            count = Math.max(0, count);
            iconResource = iconResource == null
                    ? ResourceLocation.fromNamespaceAndPath("find_me", "textures/particle/contract_glyph.png")
                    : iconResource;
            role = role == null ? CompanionSpellRole.UTILITY : role;
        }
    }
}
