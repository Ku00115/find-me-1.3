package com.kuzhi.findme.network;

import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record OpenFindMeManageScreenPacket() {
    public static void encode(OpenFindMeManageScreenPacket packet, FriendlyByteBuf buffer) {
    }

    public static OpenFindMeManageScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenFindMeManageScreenPacket();
    }

    public static void handle(OpenFindMeManageScreenPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(FindMeAuiManageScreen::open);
        context.setPacketHandled(true);
    }
}
