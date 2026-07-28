package com.kuzhi.findme.network;

import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record FindMeServerSettingsCommandPacket(int companionDeploymentLimit) {
    public static void encode(FindMeServerSettingsCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.companionDeploymentLimit);
    }

    public static FindMeServerSettingsCommandPacket decode(FriendlyByteBuf buffer) {
        return new FindMeServerSettingsCommandPacket(buffer.readVarInt());
    }

    public static void handle(FindMeServerSettingsCommandPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> FindMeModuleService.setCompanionDeploymentLimit(
                context.getSender(), packet.companionDeploymentLimit));
        context.setPacketHandled(true);
    }
}
