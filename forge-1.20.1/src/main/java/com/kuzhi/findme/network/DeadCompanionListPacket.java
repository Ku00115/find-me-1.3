package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientCompanionState;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionMoveType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record DeadCompanionListPacket(List<Entry> entries) {
public static void encode(DeadCompanionListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeUUID(entry.uuid);
            buffer.writeEnum(entry.kind);
            buffer.writeUtf(entry.entityType, 128);
            buffer.writeUtf(entry.name, 128);
            buffer.writeFloat(entry.health);
            buffer.writeFloat(entry.maxHealth);
            buffer.writeFloat(entry.armor);
            buffer.writeEnum(entry.moveType);
            buffer.writeUUID(entry.recordId);
            buffer.writeVarInt(entry.previousTeamIndex);
            buffer.writeLong(entry.deathTime);
            buffer.writeLong(entry.worldDay);
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeUtf(entry.deathCause, 128);
            buffer.writeBoolean(entry.recoverable);
            buffer.writeUtf(entry.recoveryRequirements, 256);
            buffer.writeNbt(entry.previewTag);
        }
    }

    public static DeadCompanionListPacket decode(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_ROSTER_ENTRIES,
                "dead companion entry");
        ArrayList<Entry> entries = new ArrayList<Entry>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; ++i) {
            UUID uuid = buffer.readUUID();
            CompanionKind kind = buffer.readEnum(CompanionKind.class);
            String entityType = buffer.readUtf(128);
            String name = buffer.readUtf(128);
            float health = buffer.readFloat();
            float maxHealth = buffer.readFloat();
            float armor = buffer.readFloat();
            CompanionMoveType moveType = buffer.readEnum(CompanionMoveType.class);
            UUID recordId = buffer.readUUID();
            int previousTeamIndex = buffer.readVarInt();
            long deathTime = buffer.readLong();
            long worldDay = buffer.readLong();
            String dimension = buffer.readUtf(128);
            double x = buffer.readDouble();
            double y = buffer.readDouble();
            double z = buffer.readDouble();
            String deathCause = buffer.readUtf(128);
            boolean recoverable = buffer.readBoolean();
            String recoveryRequirements = buffer.readUtf(256);
            entries.add(new Entry(uuid, kind, entityType, name, health, maxHealth, armor, moveType, recordId, previousTeamIndex,
                    deathTime, worldDay, dimension, x, y, z, deathCause, recoverable, recoveryRequirements, buffer.readNbt()));
        }
        return new DeadCompanionListPacket(entries);
    }

    public static void handle(DeadCompanionListPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionState.updateDead(packet.entries));
        context.setPacketHandled(true);
    }

    public record Entry(UUID uuid, CompanionKind kind, String entityType, String name, float health, float maxHealth, float armor,
                        CompanionMoveType moveType, UUID recordId, int previousTeamIndex, long deathTime, long worldDay,
                        String dimension, double x, double y, double z, String deathCause, boolean recoverable,
                        String recoveryRequirements, CompoundTag previewTag) {
    }
}
