package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FindMeModuleCommandPacket(FindMeModule module, boolean enabled, boolean reset) implements CustomPacketPayload {
    public static final Type<FindMeModuleCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "module_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FindMeModuleCommandPacket> STREAM_CODEC =
            NetworkCodecs.of(FindMeModuleCommandPacket::encode, FindMeModuleCommandPacket::decode);

    public FindMeModuleCommandPacket(FindMeModule module, boolean enabled) {
        this(module, enabled, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FindMeModuleCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.reset);
        if (!packet.reset) buffer.writeEnum(packet.module);
        buffer.writeBoolean(packet.enabled);
    }

    private static FindMeModuleCommandPacket decode(FriendlyByteBuf buffer) {
        boolean reset = buffer.readBoolean();
        return new FindMeModuleCommandPacket(reset ? null : buffer.readEnum(FindMeModule.class), buffer.readBoolean(), reset);
    }

    public static void handle(FindMeModuleCommandPacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                if (packet.reset) {
                    FindMeModuleService.resetToDefaults(context.getSender());
                } else {
                    FindMeModuleService.setEnabled(context.getSender(), packet.module, packet.enabled);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
