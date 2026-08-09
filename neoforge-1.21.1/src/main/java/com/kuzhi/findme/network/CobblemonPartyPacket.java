package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientCobblemonState;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionMoveType;
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

public record CobblemonPartyPacket(long serverRevision, int activeSlot, List<Entry> entries)
        implements CustomPacketPayload {
    public static final Type<CobblemonPartyPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "cobblemon_party"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CobblemonPartyPacket> STREAM_CODEC = NetworkCodecs.of(CobblemonPartyPacket::encode, CobblemonPartyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(CobblemonPartyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.serverRevision);
        buffer.writeInt(packet.activeSlot);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeVarInt(entry.slot);
            buffer.writeUUID(entry.uuid);
            buffer.writeInt(entry.entityId);
            buffer.writeUtf(entry.name, 128);
            buffer.writeBoolean(entry.deployed);
            buffer.writeBoolean(entry.ridden);
            buffer.writeBoolean(entry.fainted);
            buffer.writeBoolean(entry.rideable);
            buffer.writeEnum(entry.moveType);
            buffer.writeFloat(entry.health);
            buffer.writeFloat(entry.maxHealth);
            buffer.writeNbt(entry.previewTag);
        }
    }

    private static CobblemonPartyPacket decode(FriendlyByteBuf buffer) {
        long serverRevision = buffer.readLong();
        int activeSlot = buffer.readInt();
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_ROSTER_ENTRIES,
                "Cobblemon party entry");
        List<Entry> entries = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buffer.readVarInt(),
                    buffer.readUUID(),
                    buffer.readInt(),
                    buffer.readUtf(128),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readEnum(CompanionMoveType.class),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readNbt()
            ));
        }
        return new CobblemonPartyPacket(serverRevision, activeSlot, entries);
    }

    public static void handle(CobblemonPartyPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCobblemonState.update(packet.serverRevision, packet.activeSlot,
                packet.entries));
        context.setPacketHandled(true);
    }

    public record Entry(
            int slot,
            UUID uuid,
            int entityId,
            String name,
            boolean deployed,
            boolean ridden,
            boolean fainted,
            boolean rideable,
            CompanionMoveType moveType,
            float health,
            float maxHealth,
            CompoundTag previewTag
    ) {
        public CompanionListPacket.Entry asPreviewEntry() {
            return new CompanionListPacket.Entry(
                    uuid,
                    entityId,
                    "cobblemon:pokemon",
                    name,
                    deployed,
                    !fainted,
                    deployed,
                    ridden,
                    false,
                    false,
                    null,
                    health,
                    maxHealth,
                    0.0f,
                    moveType,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    com.kuzhi.findme.common.CompanionAnimationStyle.STANDARD,
                    CompanionEffectStyle.DEFAULT,
                    CompanionEffectStyle.DEFAULT,
                    CompanionEffectStyle.DEFAULT,
                    previewTag
            );
        }
    }
}
