package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record WarehouseOperationResultPacket(boolean success, String message) {
public static void encode(WarehouseOperationResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message == null ? "" : packet.message, 256);
    }
    public static WarehouseOperationResultPacket decode(FriendlyByteBuf buffer) {
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
