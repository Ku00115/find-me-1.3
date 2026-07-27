package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientCompanionState;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.common.CompanionTacticalAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record CompanionListPacket(CompanionKind kind, long revision, int activeIndex,
                                  List<Entry> entries, List<Entry> allEntries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CompanionListPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionListPacket> STREAM_CODEC = NetworkCodecs.of(CompanionListPacket::encode, CompanionListPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void encode(CompanionListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum((Enum)packet.kind);
        buffer.writeLong(packet.revision);
        buffer.writeInt(packet.activeIndex);
        writeEntries(buffer, packet.entries);
        writeEntries(buffer, packet.allEntries);
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.uuid);
            buffer.writeInt(entry.entityId);
            buffer.writeUtf(entry.entityType, 128);
            buffer.writeUtf(entry.name, 128);
            buffer.writeBoolean(entry.loaded);
            buffer.writeBoolean(entry.alive);
            buffer.writeBoolean(entry.deployed);
            buffer.writeBoolean(entry.ridden);
            buffer.writeBoolean(entry.hasHome);
            buffer.writeBoolean(entry.homeResident);
            buffer.writeBoolean(entry.tacticalAction != null);
            if (entry.tacticalAction != null) buffer.writeEnum(entry.tacticalAction);
            buffer.writeFloat(entry.health);
            buffer.writeFloat(entry.maxHealth);
            buffer.writeFloat(entry.armor);
            buffer.writeEnum((Enum)entry.moveType);
            buffer.writeEnum(entry.summonAnimation); buffer.writeEnum(entry.rescueAnimation);
            buffer.writeEnum(entry.storageAnimation); buffer.writeEnum(entry.switchAnimation);
            buffer.writeEnum(entry.summonStyle); buffer.writeEnum(entry.rescueStyle); buffer.writeEnum(entry.storageStyle);
            buffer.writeNbt(entry.previewTag);
        }
    }

    public static CompanionListPacket decode(FriendlyByteBuf buffer) {
        CompanionKind kind = (CompanionKind)buffer.readEnum(CompanionKind.class);
        long revision = buffer.readLong();
        int activeIndex = buffer.readInt();
        ArrayList<Entry> entries = readEntries(buffer);
        ArrayList<Entry> allEntries = readEntries(buffer);
        return new CompanionListPacket(kind, revision, activeIndex, entries, allEntries);
    }

    private static ArrayList<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        ArrayList<Entry> entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; ++i) {
            UUID uuid = buffer.readUUID();
            int entityId = buffer.readInt();
            String entityType = buffer.readUtf(128);
            String name = buffer.readUtf(128);
            boolean loaded = buffer.readBoolean();
            boolean alive = buffer.readBoolean();
            boolean deployed = buffer.readBoolean();
            boolean ridden = buffer.readBoolean();
            boolean hasHome = buffer.readBoolean();
            boolean homeResident = buffer.readBoolean();
            CompanionTacticalAction tacticalAction = buffer.readBoolean()
                    ? buffer.readEnum(CompanionTacticalAction.class) : null;
            float health = buffer.readFloat();
            float maxHealth = buffer.readFloat();
            float armor = buffer.readFloat();
            CompanionMoveType moveType = buffer.readEnum(CompanionMoveType.class);
            entries.add(new Entry(uuid, entityId, entityType, name, loaded, alive, deployed, ridden, hasHome, homeResident, tacticalAction,
                    health, maxHealth, armor, moveType, buffer.readEnum(CompanionAnimationStyle.class),
                    buffer.readEnum(CompanionAnimationStyle.class), buffer.readEnum(CompanionAnimationStyle.class),
                    buffer.readEnum(CompanionAnimationStyle.class), buffer.readEnum(CompanionEffectStyle.class),
                    buffer.readEnum(CompanionEffectStyle.class), buffer.readEnum(CompanionEffectStyle.class), buffer.readNbt()));
        }
        return entries;
    }

    public static void handle(CompanionListPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionState.update(packet.kind, packet.revision,
                packet.activeIndex, packet.entries, packet.allEntries));
        context.setPacketHandled(true);
    }

    public record Entry(UUID uuid, int entityId, String entityType, String name, boolean loaded, boolean alive,
                        boolean deployed, boolean ridden, boolean hasHome, boolean homeResident,
                        CompanionTacticalAction tacticalAction, float health,
                        float maxHealth, float armor, CompanionMoveType moveType,
                        CompanionAnimationStyle summonAnimation, CompanionAnimationStyle rescueAnimation,
                        CompanionAnimationStyle storageAnimation, CompanionAnimationStyle switchAnimation,
                        CompanionEffectStyle summonStyle, CompanionEffectStyle rescueStyle,
                        CompanionEffectStyle storageStyle, CompoundTag previewTag) {
    }
}
