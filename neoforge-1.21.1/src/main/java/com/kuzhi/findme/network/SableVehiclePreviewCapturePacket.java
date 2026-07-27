package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.SableVehicleScreenshotPreviewCache;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record SableVehiclePreviewCapturePacket(UUID uuid, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SableVehiclePreviewCapturePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "sable_vehicle_preview_capture"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SableVehiclePreviewCapturePacket> STREAM_CODEC = NetworkCodecs.of(SableVehiclePreviewCapturePacket::encode, SableVehiclePreviewCapturePacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(SableVehiclePreviewCapturePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.uuid);
        buffer.writeDouble(packet.minX);
        buffer.writeDouble(packet.minY);
        buffer.writeDouble(packet.minZ);
        buffer.writeDouble(packet.maxX);
        buffer.writeDouble(packet.maxY);
        buffer.writeDouble(packet.maxZ);
    }

    public static SableVehiclePreviewCapturePacket decode(FriendlyByteBuf buffer) {
        return new SableVehiclePreviewCapturePacket(buffer.readUUID(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(SableVehiclePreviewCapturePacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> SableVehicleScreenshotPreviewCache.capture(packet));
        context.setPacketHandled(true);
    }
}
