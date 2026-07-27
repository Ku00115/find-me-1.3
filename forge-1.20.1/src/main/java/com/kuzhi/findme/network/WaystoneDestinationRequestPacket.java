package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.compat.waystones.WaystonesIntegration;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record WaystoneDestinationRequestPacket(UUID mountUuid) {
    public static void encode(WaystoneDestinationRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.mountUuid); }
    public static WaystoneDestinationRequestPacket decode(FriendlyByteBuf buffer) { return new WaystoneDestinationRequestPacket(buffer.readUUID()); }
    public static void handle(WaystoneDestinationRequestPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && WaystonesIntegration.available()) {
                ModNetwork.sendToPlayer(player, new WaystoneDestinationListPacket(packet.mountUuid,
                        WaystonesIntegration.destinations(player)));
            }
        });
        context.setPacketHandled(true);
    }
}
