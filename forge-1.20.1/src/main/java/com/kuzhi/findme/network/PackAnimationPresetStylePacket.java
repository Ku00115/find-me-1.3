package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.server.command.PackPresetCommandHandler;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record PackAnimationPresetStylePacket(List<String> entityTypes, CompanionEffectPurpose purpose, CompanionEffectStyle style) {
public static void encode(PackAnimationPresetStylePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityTypes.size());
        for (String entityType : packet.entityTypes) {
            buffer.writeUtf(entityType, 128);
        }
        buffer.writeEnum(packet.purpose);
        buffer.writeEnum(packet.style);
    }

    public static PackAnimationPresetStylePacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        ArrayList<String> entityTypes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entityTypes.add(buffer.readUtf(128));
        }
        return new PackAnimationPresetStylePacket(entityTypes, buffer.readEnum(CompanionEffectPurpose.class), buffer.readEnum(CompanionEffectStyle.class));
    }

    public static void handle(PackAnimationPresetStylePacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null && FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)) {
                PackPresetCommandHandler.setStyles(context.getSender(), packet.entityTypes, packet.purpose, packet.style);
            }
        });
        context.setPacketHandled(true);
    }
}
