package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.FindMeAuiManageScreen;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFindMeManageScreenPacket() implements CustomPacketPayload {
    public static final Type<OpenFindMeManageScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "open_manage_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFindMeManageScreenPacket> STREAM_CODEC =
            NetworkCodecs.of(OpenFindMeManageScreenPacket::encode, OpenFindMeManageScreenPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(OpenFindMeManageScreenPacket packet, FriendlyByteBuf buffer) {
    }

    private static OpenFindMeManageScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenFindMeManageScreenPacket();
    }

    public static void handle(OpenFindMeManageScreenPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(FindMeAuiManageScreen::open);
        context.setPacketHandled(true);
    }
}
