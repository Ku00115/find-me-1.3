package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WarehouseOperationResultPacket(boolean success, String message) implements CustomPacketPayload {
    public static final Type<WarehouseOperationResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "warehouse_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseOperationResultPacket> STREAM_CODEC = NetworkCodecs.of(WarehouseOperationResultPacket::encode, WarehouseOperationResultPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void encode(WarehouseOperationResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message == null ? "" : packet.message, 256);
    }
    private static WarehouseOperationResultPacket decode(FriendlyByteBuf buffer) {
        return new WarehouseOperationResultPacket(buffer.readBoolean(), buffer.readUtf(256));
    }
    public static void handle(WarehouseOperationResultPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            FindMeAuiManageScreen.showResult(packet.success, packet.message);
        });
        context.setPacketHandled(true);
    }
}
