package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.server.lifecycle.CompanionWaystoneJourneyService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record WaystoneJourneyRequestPacket(UUID mountUuid, UUID waystoneUuid) {
    public static void encode(WaystoneJourneyRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.mountUuid); buffer.writeUUID(packet.waystoneUuid); }
    public static WaystoneJourneyRequestPacket decode(FriendlyByteBuf buffer) { return new WaystoneJourneyRequestPacket(buffer.readUUID(), buffer.readUUID()); }
    public static void handle(WaystoneJourneyRequestPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> { if (context.getSender() != null) CompanionWaystoneJourneyService.start(context.getSender(), packet.mountUuid, packet.waystoneUuid); });
        context.setPacketHandled(true);
    }
}
