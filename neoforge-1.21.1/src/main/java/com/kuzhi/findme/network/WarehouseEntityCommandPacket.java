package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.WarehouseEntityAction;
import com.kuzhi.findme.server.command.WarehouseCommandHandler;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WarehouseEntityCommandPacket(WarehouseEntityAction action, UUID uuid, String value) implements CustomPacketPayload {
    public static final Type<WarehouseEntityCommandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "warehouse_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseEntityCommandPacket> STREAM_CODEC = NetworkCodecs.of(WarehouseEntityCommandPacket::encode, WarehouseEntityCommandPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void encode(WarehouseEntityCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.uuid);
        buffer.writeUtf(packet.value == null ? "" : packet.value, 128);
    }
    private static WarehouseEntityCommandPacket decode(FriendlyByteBuf buffer) {
        return new WarehouseEntityCommandPacket(buffer.readEnum(WarehouseEntityAction.class), buffer.readUUID(), buffer.readUtf(128));
    }
    public static void handle(WarehouseEntityCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                WarehouseCommandHandler.handle(context.getSender(), packet.action, packet.uuid, packet.value);
            }
        });
        context.setPacketHandled(true);
    }
}
