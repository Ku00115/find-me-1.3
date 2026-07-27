package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.server.command.CompanionCommandHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record CompanionCommandPacket(CompanionKind kind, CompanionAction action, int targetEntityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CompanionCommandPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionCommandPacket> STREAM_CODEC = NetworkCodecs.of(CompanionCommandPacket::encode, CompanionCommandPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void encode(CompanionCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum((Enum)packet.kind);
        buffer.writeEnum((Enum)packet.action);
        buffer.writeInt(packet.targetEntityId);
    }

    public static CompanionCommandPacket decode(FriendlyByteBuf buffer) {
        return new CompanionCommandPacket((CompanionKind)buffer.readEnum(CompanionKind.class), (CompanionAction)buffer.readEnum(CompanionAction.class), buffer.readInt());
    }

    public static void handle(CompanionCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionCommandHandler.handle(context.getSender(), packet.kind, packet.action, packet.targetEntityId);
            }
        });
        context.setPacketHandled(true);
    }
}
