package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.WaystoneDestinationScreen;
import com.kuzhi.findme.compat.waystones.WaystoneDestination;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record WaystoneDestinationListPacket(UUID mountUuid, List<WaystoneDestination> destinations) {
    public static void encode(WaystoneDestinationListPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.mountUuid);
        buffer.writeVarInt(packet.destinations.size());
        for (WaystoneDestination destination : packet.destinations) {
            buffer.writeUUID(destination.uuid());
            buffer.writeUtf(destination.name(), 256);
            buffer.writeResourceLocation(destination.dimension().location());
            buffer.writeBlockPos(destination.position());
        }
    }
    public static WaystoneDestinationListPacket decode(FriendlyByteBuf buffer) {
        UUID mount = buffer.readUUID();
        int size = PacketDecodeLimits.readCount(buffer, 1024, "waystone destination");
        ArrayList<WaystoneDestination> values = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; i++) {
            values.add(new WaystoneDestination(buffer.readUUID(), buffer.readUtf(256),
                    ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()), buffer.readBlockPos()));
        }
        return new WaystoneDestinationListPacket(mount, List.copyOf(values));
    }
    public static void handle(WaystoneDestinationListPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> WaystoneDestinationScreen.open(packet.mountUuid, packet.destinations));
        context.setPacketHandled(true);
    }
}
