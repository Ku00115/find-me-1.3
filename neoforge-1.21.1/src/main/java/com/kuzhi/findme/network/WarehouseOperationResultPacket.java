package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WarehouseOperationResultPacket(boolean success, String message, UUID targetUuid) implements CustomPacketPayload {
    public static final Type<WarehouseOperationResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "warehouse_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseOperationResultPacket> STREAM_CODEC = NetworkCodecs.of(WarehouseOperationResultPacket::encode, WarehouseOperationResultPacket::decode);

    public WarehouseOperationResultPacket(boolean success, String message) {
        this(success, message, null);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void encode(WarehouseOperationResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message == null ? "" : packet.message, 256);
        buffer.writeBoolean(packet.targetUuid != null);
        if (packet.targetUuid != null) buffer.writeUUID(packet.targetUuid);
    }
    private static WarehouseOperationResultPacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String message = buffer.readUtf(256);
        UUID targetUuid = buffer.readBoolean() ? buffer.readUUID() : null;
        return new WarehouseOperationResultPacket(success, message, targetUuid);
    }
    public static void handle(WarehouseOperationResultPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            FindMeAuiManageScreen.showResult(packet.success, packet.message, packet.targetUuid);
        });
        context.setPacketHandled(true);
    }
}
