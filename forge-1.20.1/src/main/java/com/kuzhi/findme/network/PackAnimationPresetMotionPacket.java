package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.command.PackPresetCommandHandler;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record PackAnimationPresetMotionPacket(List<String> entityTypes, CompanionAnimationPurpose purpose,
                                              CompanionAnimationStyle style) {
public static void encode(PackAnimationPresetMotionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityTypes.size());
        for (String entityType : packet.entityTypes) buffer.writeUtf(entityType, 128);
        buffer.writeEnum(packet.purpose);
        buffer.writeEnum(packet.style);
    }

    public static PackAnimationPresetMotionPacket decode(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_PRESET_ENTRIES,
                "preset motion entity");
        ArrayList<String> entityTypes = new ArrayList<>(PacketDecodeLimits.initialCapacity(size));
        for (int i = 0; i < size; i++) entityTypes.add(buffer.readUtf(128));
        return new PackAnimationPresetMotionPacket(entityTypes, buffer.readEnum(CompanionAnimationPurpose.class),
                buffer.readEnum(CompanionAnimationStyle.class));
    }

    public static void handle(PackAnimationPresetMotionPacket packet,
                              Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null
                    && FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)) {
                PackPresetCommandHandler.setAnimationStyles(context.getSender(), packet.entityTypes,
                        packet.purpose, packet.style);
            }
        });
        context.setPacketHandled(true);
    }
}
