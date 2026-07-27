package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.HouseCommandAction;
import com.kuzhi.findme.server.home.HousePageService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record HouseCommandPacket(HouseCommandAction action, UUID houseId, UUID companionId, String value) {
public static void encode(HouseCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.houseId);
        buffer.writeUUID(packet.companionId == null ? new UUID(0L, 0L) : packet.companionId);
        buffer.writeBoolean(packet.companionId != null);
        buffer.writeUtf(packet.value == null ? "" : packet.value, 128);
    }

    public static HouseCommandPacket decode(FriendlyByteBuf buffer) {
        HouseCommandAction action = buffer.readEnum(HouseCommandAction.class);
        UUID houseId = buffer.readUUID();
        UUID companionId = buffer.readUUID();
        if (!buffer.readBoolean()) companionId = null;
        return new HouseCommandPacket(action, houseId, companionId, buffer.readUtf(128));
    }

    public static void handle(HouseCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                HousePageService.handle(context.getSender(), packet);
            }
        });
        context.setPacketHandled(true);
    }
}
