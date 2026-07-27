package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientVehicleState;
import com.kuzhi.findme.common.CompanionMoveType;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.network.CompanionListPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record VehicleListPacket(long revision, int activeIndex, List<Entry> wheelEntries,
                                List<Entry> allEntries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleListPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "vehicle_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleListPacket> STREAM_CODEC = NetworkCodecs.of(VehicleListPacket::encode, VehicleListPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void encode(VehicleListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.revision);
        buffer.writeInt(packet.activeIndex);
        writeEntries(buffer, packet.wheelEntries);
        writeEntries(buffer, packet.allEntries);
    }

    public static VehicleListPacket decode(FriendlyByteBuf buffer) {
        long revision = buffer.readLong();
        int activeIndex = buffer.readInt();
        return new VehicleListPacket(revision, activeIndex, readEntries(buffer), readEntries(buffer));
    }

    public static void handle(VehicleListPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientVehicleState.update(packet.revision, packet.activeIndex,
                packet.wheelEntries, packet.allEntries));
        context.setPacketHandled(true);
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
            buffer.writeEnum(entry.summonAnimation);
            buffer.writeEnum(entry.rescueAnimation);
            buffer.writeEnum(entry.storageAnimation);
            buffer.writeEnum(entry.switchAnimation);
            buffer.writeEnum(entry.summonStyle);
            buffer.writeEnum(entry.rescueStyle);
            buffer.writeEnum(entry.storageStyle);
            buffer.writeNbt(entry.previewTag);
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        ArrayList<Entry> entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; ++i) {
            entries.add(new Entry(buffer.readUUID(), buffer.readInt(), buffer.readUtf(128), buffer.readUtf(128),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readEnum(CompanionAnimationStyle.class), buffer.readEnum(CompanionAnimationStyle.class),
                    buffer.readEnum(CompanionAnimationStyle.class), buffer.readEnum(CompanionAnimationStyle.class),
                    buffer.readEnum(CompanionEffectStyle.class), buffer.readEnum(CompanionEffectStyle.class),
                    buffer.readEnum(CompanionEffectStyle.class), buffer.readNbt()));
        }
        return entries;
    }

    public record Entry(UUID uuid, int entityId, String entityType, String name, boolean loaded, boolean alive,
                        boolean deployed, boolean ridden, CompanionAnimationStyle summonAnimation,
                        CompanionAnimationStyle rescueAnimation, CompanionAnimationStyle storageAnimation,
                        CompanionAnimationStyle switchAnimation, CompanionEffectStyle summonStyle,
                        CompanionEffectStyle rescueStyle, CompanionEffectStyle storageStyle, CompoundTag previewTag) {
        public CompanionListPacket.Entry asPreviewEntry() {
            boolean sable = "simulated:sable_sublevel".equals(this.entityType);
            return new CompanionListPacket.Entry(this.uuid, this.entityId, sable ? "" : this.entityType, this.name,
                    this.loaded, this.alive, this.deployed, this.ridden, false, false, null, 0.0f, 0.0f, 0.0f,
                    CompanionMoveType.WALK, summonAnimation, rescueAnimation, storageAnimation,
                    switchAnimation, summonStyle, rescueStyle, storageStyle, sable ? null : this.previewTag);
        }
    }
}
