package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientVehicleSealEffectState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record VehicleSealEffectPacket(double x, double y, double z, float radius, float height, int durationTicks, boolean customTexture) {
public static void encode(VehicleSealEffectPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.radius);
        buffer.writeFloat(packet.height);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeBoolean(packet.customTexture);
    }

    public static VehicleSealEffectPacket decode(FriendlyByteBuf buffer) {
        return new VehicleSealEffectPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(VehicleSealEffectPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientVehicleSealEffectState.start(packet));
        context.setPacketHandled(true);
    }
}
