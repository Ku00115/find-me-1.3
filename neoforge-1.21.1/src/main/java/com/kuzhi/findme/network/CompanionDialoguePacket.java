package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientCompanionDialogueState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record CompanionDialoguePacket(String call, String reply, String playerName, String creatureName, String seconds, int durationTicks, int replyDelayTicks, boolean urgent) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CompanionDialoguePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionDialoguePacket> STREAM_CODEC = NetworkCodecs.of(CompanionDialoguePacket::encode, CompanionDialoguePacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void encode(CompanionDialoguePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(safe(packet.call));
        buffer.writeUtf(safe(packet.reply));
        buffer.writeUtf(safe(packet.playerName));
        buffer.writeUtf(safe(packet.creatureName));
        buffer.writeUtf(safe(packet.seconds));
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeVarInt(packet.replyDelayTicks);
        buffer.writeBoolean(packet.urgent);
    }

    public static CompanionDialoguePacket decode(FriendlyByteBuf buffer) {
        return new CompanionDialoguePacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(CompanionDialoguePacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientCompanionDialogueState.start(packet));
        context.setPacketHandled(true);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
