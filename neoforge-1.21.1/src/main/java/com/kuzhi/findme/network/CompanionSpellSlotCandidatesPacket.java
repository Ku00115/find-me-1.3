package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.api.CompanionSpellRole;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionSpellSlotCandidatesPacket(UUID uuid, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<CompanionSpellSlotCandidatesPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_spell_slot_candidates"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionSpellSlotCandidatesPacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionSpellSlotCandidatesPacket::encode, CompanionSpellSlotCandidatesPacket::decode);

    public CompanionSpellSlotCandidatesPacket {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(CompanionSpellSlotCandidatesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.uuid);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeVarInt(entry.inventorySlot);
            buffer.writeUtf(entry.displayName, 128);
            buffer.writeVarInt(entry.spellLevel);
            buffer.writeVarInt(entry.count);
            buffer.writeResourceLocation(entry.iconResource);
            buffer.writeEnum(entry.role);
            buffer.writeNbt(entry.itemTag);
        }
    }

    private static CompanionSpellSlotCandidatesPacket decode(FriendlyByteBuf buffer) {
        UUID uuid = buffer.readUUID();
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) throw new DecoderException("Invalid FindMe spell candidate count: " + size);
        List<Entry> entries = new ArrayList<>(Math.min(size, 64));
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(buffer.readVarInt(), buffer.readUtf(128), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readResourceLocation(), buffer.readEnum(CompanionSpellRole.class),
                    buffer.readNbt()));
        }
        return new CompanionSpellSlotCandidatesPacket(uuid, entries);
    }

    public static void handle(CompanionSpellSlotCandidatesPacket packet,
                              Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> FindMeAuiManageScreen.openSpellScrollPicker(packet.uuid, packet.entries));
        context.setPacketHandled(true);
    }

    public record Entry(int inventorySlot, String displayName, int spellLevel, int count,
                        ResourceLocation iconResource, CompanionSpellRole role, CompoundTag itemTag) {
        public Entry {
            displayName = displayName == null ? "" : displayName;
            spellLevel = Math.max(0, spellLevel);
            count = Math.max(0, count);
            iconResource = iconResource == null
                    ? ResourceLocation.fromNamespaceAndPath("find_me", "textures/particle/contract_glyph.png")
                    : iconResource;
            role = role == null ? CompanionSpellRole.UTILITY : role;
            itemTag = itemTag == null ? new CompoundTag() : itemTag.copy();
            if (!itemTag.isEmpty()) {
                itemTag.putByte("Count", (byte)1);
                itemTag.putInt("count", 1);
            }
        }

        @Override
        public CompoundTag itemTag() {
            return itemTag.copy();
        }
    }
}
