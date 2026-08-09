package com.kuzhi.findme.network;

import com.kuzhi.findme.client.ClientFindMeHudLayout;
import com.kuzhi.findme.client.FindMeHudEditorScreen;
import com.kuzhi.findme.client.FindMeHudRenderer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;

public record OpenFindMeHudEditorPacket(boolean reset) {
    public static void encode(OpenFindMeHudEditorPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.reset());
    }

    public static OpenFindMeHudEditorPacket decode(FriendlyByteBuf buffer) {
        return new OpenFindMeHudEditorPacket(buffer.readBoolean());
    }

    public static void handle(OpenFindMeHudEditorPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (packet.reset()) {
                ClientFindMeHudLayout.reset();
                FindMeHudRenderer.refresh();
            } else {
                FindMeHudEditorScreen.open();
            }
        });
        context.setPacketHandled(true);
    }
}



