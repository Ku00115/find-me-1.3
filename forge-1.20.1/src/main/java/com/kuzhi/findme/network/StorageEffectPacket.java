package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientStorageEffectState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record StorageEffectPacket(int entityId, double x, double y, double z, float radius, float height, int durationTicks, Visual visual) {
    public StorageEffectPacket(int entityId, double x, double y, double z, float radius, float height, int durationTicks, boolean customTexture) {
        this(entityId, x, y, z, radius, height, durationTicks, customTexture ? Visual.CUSTOM_TEXTURE : Visual.DEFAULT);
    }
public static void encode(StorageEffectPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.radius);
        buffer.writeFloat(packet.height);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeEnum(packet.visual);
    }

    public static StorageEffectPacket decode(FriendlyByteBuf buffer) {
        return new StorageEffectPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readEnum(Visual.class));
    }

    public static void handle(StorageEffectPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientStorageEffectState.start(packet));
        context.setPacketHandled(true);
    }

    public enum Visual {
        DEFAULT,
        CUSTOM_TEXTURE,
        GROUND_SINK
    }

    public boolean customTexture() {
        return visual == Visual.CUSTOM_TEXTURE;
    }

    public boolean groundSink() {
        return visual == Visual.GROUND_SINK;
    }
}
