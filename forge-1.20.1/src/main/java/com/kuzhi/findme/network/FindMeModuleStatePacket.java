package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record FindMeModuleStatePacket(long configuredMask, long effectiveMask, long availableMask,
                                      boolean canManage) {
public static void encode(FindMeModuleStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.configuredMask);
        buffer.writeLong(packet.effectiveMask);
        buffer.writeLong(packet.availableMask);
        buffer.writeBoolean(packet.canManage);
    }

    public static FindMeModuleStatePacket decode(FriendlyByteBuf buffer) {
        return new FindMeModuleStatePacket(buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readBoolean());
    }

    public static void handle(FindMeModuleStatePacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.kuzhi.findme.client.ClientFindMeModuleState.update(
                    packet.configuredMask, packet.effectiveMask, packet.availableMask, packet.canManage);
            com.kuzhi.findme.client.FindMeAuiManageScreen.updateModuleState();
        });
        context.setPacketHandled(true);
    }
}
