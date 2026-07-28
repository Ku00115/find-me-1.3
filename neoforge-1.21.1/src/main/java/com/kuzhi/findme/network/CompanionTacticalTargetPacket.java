package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientTacticalTargetOutlineState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionTacticalTargetPacket(int entityId, int durationTicks) implements CustomPacketPayload {
    public static final Type<CompanionTacticalTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_tactical_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTacticalTargetPacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionTacticalTargetPacket::encode, CompanionTacticalTargetPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void encode(CompanionTacticalTargetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId); buffer.writeVarInt(packet.durationTicks);
    }
    public static CompanionTacticalTargetPacket decode(FriendlyByteBuf buffer) {
        return new CompanionTacticalTargetPacket(buffer.readVarInt(), buffer.readVarInt());
    }
    public static void handle(CompanionTacticalTargetPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientTacticalTargetOutlineState.update(packet.entityId(), packet.durationTicks()));
        context.setPacketHandled(true);
    }
}
