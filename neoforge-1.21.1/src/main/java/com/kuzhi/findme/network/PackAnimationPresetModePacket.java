package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientPackAnimationPresetScreenOpener;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PackAnimationPresetModePacket(boolean enabled, boolean openScreen) implements CustomPacketPayload {
    public static final Type<PackAnimationPresetModePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "pack_animation_preset_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PackAnimationPresetModePacket> STREAM_CODEC = NetworkCodecs.of(PackAnimationPresetModePacket::encode, PackAnimationPresetModePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(PackAnimationPresetModePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
        buffer.writeBoolean(packet.openScreen);
    }

    public static PackAnimationPresetModePacket decode(FriendlyByteBuf buffer) {
        return new PackAnimationPresetModePacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(PackAnimationPresetModePacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> ClientPackAnimationPresetScreenOpener.setMode(packet.enabled, packet.openScreen));
        context.setPacketHandled(true);
    }
}
