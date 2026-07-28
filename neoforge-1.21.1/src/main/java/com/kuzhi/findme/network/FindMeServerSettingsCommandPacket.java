package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FindMeServerSettingsCommandPacket(int companionDeploymentLimit) implements CustomPacketPayload {
    public static final Type<FindMeServerSettingsCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "server_settings_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FindMeServerSettingsCommandPacket> STREAM_CODEC =
            NetworkCodecs.of(FindMeServerSettingsCommandPacket::encode, FindMeServerSettingsCommandPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FindMeServerSettingsCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.companionDeploymentLimit);
    }

    private static FindMeServerSettingsCommandPacket decode(FriendlyByteBuf buffer) {
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
