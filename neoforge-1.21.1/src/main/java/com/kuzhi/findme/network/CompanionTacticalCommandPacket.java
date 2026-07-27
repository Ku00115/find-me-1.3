package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.CompanionTacticalAction;
import com.kuzhi.findme.server.lifecycle.CompanionTacticalOrderService;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionTacticalCommandPacket(CompanionKind kind, java.util.UUID targetUuid,
                                              CompanionTacticalAction action, BlockPos targetPos,
                                              int targetEntityId) implements CustomPacketPayload {
    public static final Type<CompanionTacticalCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_tactical_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTacticalCommandPacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionTacticalCommandPacket::encode, CompanionTacticalCommandPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(CompanionTacticalCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind);
        buffer.writeUUID(packet.targetUuid);
        buffer.writeEnum(packet.action);
        buffer.writeBlockPos(packet.targetPos == null ? BlockPos.ZERO : packet.targetPos);
        buffer.writeInt(packet.targetEntityId);
    }

    private static CompanionTacticalCommandPacket decode(FriendlyByteBuf buffer) {
        return new CompanionTacticalCommandPacket(buffer.readEnum(CompanionKind.class), buffer.readUUID(),
                buffer.readEnum(CompanionTacticalAction.class), buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(CompanionTacticalCommandPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                CompanionTacticalOrderService.handle(context.getSender(), packet.kind, packet.targetUuid,
                        packet.action, packet.targetPos, packet.targetEntityId);
            }
        });
        context.setPacketHandled(true);
    }
}
