package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientCompanionTeamState;
import com.kuzhi.findme.common.CompanionTeamTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CompanionTeamListPacket(long serverRevision, List<Entry> entries) {
public static void encode(CompanionTeamListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.serverRevision);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeEnum(entry.target);
            buffer.writeVarInt(entry.index);
            buffer.writeVarInt(entry.number);
            buffer.writeBoolean(entry.autoJoin);
            buffer.writeUtf(entry.name, 64);
            buffer.writeVarInt(entry.uuids.size());
            for (UUID uuid : entry.uuids) {
                buffer.writeUUID(uuid);
            }
        }
    }

    public static CompanionTeamListPacket decode(FriendlyByteBuf buffer) {
        long serverRevision = buffer.readLong();
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_TEAM_ENTRIES,
                "team entry");
        ArrayList<Entry> entries = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; ++i) {
            CompanionTeamTarget target = buffer.readEnum(CompanionTeamTarget.class);
            int index = buffer.readVarInt();
            int number = buffer.readVarInt();
            boolean autoJoin = buffer.readBoolean();
            String name = buffer.readUtf(64);
            int uuidCount = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_TEAM_MEMBERS,
                    "team member");
            ArrayList<UUID> uuids = new ArrayList<>(PacketDecodeLimits.initialCapacity(uuidCount));
            for (int u = 0; u < uuidCount; ++u) {
                uuids.add(buffer.readUUID());
            }
            entries.add(new Entry(target, index, number, autoJoin, name, uuids));
        }
        return new CompanionTeamListPacket(serverRevision, entries);
    }

    public static void handle(CompanionTeamListPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionTeamState.update(packet.serverRevision, packet.entries.stream()
                .map(entry -> new ClientCompanionTeamState.TeamEntry(entry.target, entry.index, entry.number, entry.autoJoin, entry.name, entry.uuids))
                .toList()));
        context.setPacketHandled(true);
    }

    public record Entry(CompanionTeamTarget target, int index, int number, boolean autoJoin, String name, List<UUID> uuids) {
        public Entry {
            number = Math.max(1, number);
            uuids = uuids == null ? List.of() : List.copyOf(uuids);
            name = name == null ? "" : name;
        }
    }
}
