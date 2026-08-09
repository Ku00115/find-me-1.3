package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientFindMeHudLayout;
import com.kuzhi.findme.client.FindMeHudEditorScreen;
import com.kuzhi.findme.client.FindMeHudRenderer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFindMeHudEditorPacket(boolean reset) implements CustomPacketPayload {
    public static final Type<OpenFindMeHudEditorPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "open_hud_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFindMeHudEditorPacket> STREAM_CODEC =
            NetworkCodecs.of(OpenFindMeHudEditorPacket::encode, OpenFindMeHudEditorPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

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
