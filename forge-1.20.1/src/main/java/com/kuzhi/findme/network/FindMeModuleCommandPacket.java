package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record FindMeModuleCommandPacket(FindMeModule module, boolean enabled, boolean reset) {
    public FindMeModuleCommandPacket(FindMeModule module, boolean enabled) {
        this(module, enabled, false);
    }
public static void encode(FindMeModuleCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.reset);
        if (!packet.reset) buffer.writeEnum(packet.module);
        buffer.writeBoolean(packet.enabled);
    }

    public static FindMeModuleCommandPacket decode(FriendlyByteBuf buffer) {
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
