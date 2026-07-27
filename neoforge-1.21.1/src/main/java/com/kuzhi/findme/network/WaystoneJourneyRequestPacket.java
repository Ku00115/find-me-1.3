package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.server.lifecycle.CompanionWaystoneJourneyService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WaystoneJourneyRequestPacket(UUID mountUuid, UUID waystoneUuid) implements CustomPacketPayload {
    public static final Type<WaystoneJourneyRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "waystone_journey_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WaystoneJourneyRequestPacket> STREAM_CODEC = NetworkCodecs.of(WaystoneJourneyRequestPacket::encode, WaystoneJourneyRequestPacket::decode);
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static void encode(WaystoneJourneyRequestPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.mountUuid); buffer.writeUUID(packet.waystoneUuid); }
    private static WaystoneJourneyRequestPacket decode(FriendlyByteBuf buffer) { return new WaystoneJourneyRequestPacket(buffer.readUUID(), buffer.readUUID()); }
    public static void handle(WaystoneJourneyRequestPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> { if (context.getSender() != null) CompanionWaystoneJourneyService.start(context.getSender(), packet.mountUuid, packet.waystoneUuid); });
        context.setPacketHandled(true);
    }
}
