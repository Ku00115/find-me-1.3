package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.FindMeAuiHouseScreen;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.HouseResidentMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record HousePagePacket(UUID houseId, UUID owner, String ownerName, String houseName, boolean readOnly,
                              int capacity, List<Resident> residents, List<Resident> available) {
public static void encode(HousePagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.houseId);
        buffer.writeUUID(packet.owner);
        buffer.writeUtf(packet.ownerName == null ? "" : packet.ownerName, 64);
        buffer.writeUtf(packet.houseName == null ? "" : packet.houseName, 64);
        buffer.writeBoolean(packet.readOnly);
        buffer.writeVarInt(Math.max(1, packet.capacity));
        writeResidents(buffer, packet.residents);
        writeResidents(buffer, packet.available);
    }

    private static void writeResidents(FriendlyByteBuf buffer, List<Resident> residents) {
        buffer.writeVarInt(residents == null ? 0 : residents.size());
        if (residents == null) return;
        for (Resident resident : residents) {
            buffer.writeUUID(resident.uuid());
            buffer.writeUtf(resident.name(), 128);
            buffer.writeUtf(resident.entityType(), 128);
            buffer.writeEnum(resident.kind());
            buffer.writeBoolean(resident.active());
            buffer.writeBoolean(resident.dead());
            buffer.writeBoolean(resident.otherHouse());
            buffer.writeEnum(resident.mode());
            buffer.writeNbt(resident.previewTag());
        }
    }

    public static HousePagePacket decode(FriendlyByteBuf buffer) {
        return new HousePagePacket(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(64),
                buffer.readBoolean(), Math.max(1, buffer.readVarInt()), readResidents(buffer), readResidents(buffer));
    }

    private static List<Resident> readResidents(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_PAGE_ENTRIES,
                "house resident");
        ArrayList<Resident> residents = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; i++) {
            residents.add(new Resident(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(128), buffer.readEnum(CompanionKind.class),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readEnum(HouseResidentMode.class), buffer.readNbt()));
        }
        return List.copyOf(residents);
    }

    public static void handle(HousePagePacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.kuzhi.findme.client.ClientHouseState.update(packet);
            FindMeAuiHouseScreen.open(packet);
        });
        context.setPacketHandled(true);
    }

    public record Resident(UUID uuid, String name, String entityType, CompanionKind kind,
                           boolean active, boolean dead, boolean otherHouse, HouseResidentMode mode,
                           CompoundTag previewTag) {
        public Resident {
            name = name == null ? "Unknown" : name;
            entityType = entityType == null ? "" : entityType;
            kind = kind == null ? CompanionKind.COMPANION : kind;
            mode = mode == null ? HouseResidentMode.WANDER : mode;
            previewTag = previewTag == null ? null : previewTag.copy();
        }
    }
}
