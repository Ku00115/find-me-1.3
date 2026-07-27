package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CobblemonCommandAction;
import com.kuzhi.findme.compat.cobblemon.CobblemonCompat;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CobblemonCommandPacket(CobblemonCommandAction action, int slot, UUID targetUuid) implements CustomPacketPayload {
    public static final Type<CobblemonCommandPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "cobblemon_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CobblemonCommandPacket> STREAM_CODEC = NetworkCodecs.of(CobblemonCommandPacket::encode, CobblemonCommandPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(CobblemonCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeInt(packet.slot);
        buffer.writeUUID(packet.targetUuid);
    }

    private static CobblemonCommandPacket decode(FriendlyByteBuf buffer) {
        return new CobblemonCommandPacket(buffer.readEnum(CobblemonCommandAction.class), buffer.readInt(), buffer.readUUID());
    }

    public static void handle(CobblemonCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CobblemonCompat.handleCommand(context.getSender(), packet.action, packet.slot, packet.targetUuid);
            }
        });
        context.setPacketHandled(true);
    }
}
