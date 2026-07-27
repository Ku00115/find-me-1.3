package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.common.VehicleCommandAction;
import com.kuzhi.findme.server.command.VehicleCommandHandler;
import java.util.function.Supplier;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record VehicleCommandPacket(VehicleCommandAction action, UUID targetUuid, int position) {
public static void encode(VehicleCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeBoolean(packet.targetUuid != null);
        if (packet.targetUuid != null) buffer.writeUUID(packet.targetUuid);
        buffer.writeInt(packet.position);
    }

    public static VehicleCommandPacket decode(FriendlyByteBuf buffer) {
        VehicleCommandAction action = buffer.readEnum(VehicleCommandAction.class);
        UUID targetUuid = buffer.readBoolean() ? buffer.readUUID() : null;
        return new VehicleCommandPacket(action, targetUuid, buffer.readInt());
    }

    public static void handle(VehicleCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                VehicleCommandHandler.handle(context.getSender(), packet.action, packet.targetUuid, packet.position);
            }
        });
        context.setPacketHandled(true);
    }

}
