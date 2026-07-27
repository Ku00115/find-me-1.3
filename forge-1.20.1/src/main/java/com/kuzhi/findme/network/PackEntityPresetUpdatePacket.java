package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.PackEntityPresetField;
import com.kuzhi.findme.server.command.PackPresetCommandHandler;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record PackEntityPresetUpdatePacket(List<String> entityTypes, PackEntityPresetField field, String value) {
    private static final int MAX_FIELD_VALUE_LENGTH = 32767;
public static void encode(PackEntityPresetUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityTypes.size());
        for (String entityType : packet.entityTypes) {
            buffer.writeUtf(entityType, 128);
        }
        buffer.writeEnum(packet.field);
        buffer.writeUtf(packet.value == null ? "" : packet.value, MAX_FIELD_VALUE_LENGTH);
    }

    public static PackEntityPresetUpdatePacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        ArrayList<String> entityTypes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entityTypes.add(buffer.readUtf(128));
        }
        return new PackEntityPresetUpdatePacket(entityTypes, buffer.readEnum(PackEntityPresetField.class), buffer.readUtf(MAX_FIELD_VALUE_LENGTH));
    }

    public static void handle(PackEntityPresetUpdatePacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null && FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)) {
                PackPresetCommandHandler.setField(context.getSender(), packet.entityTypes, packet.field, packet.value);
            }
        });
        context.setPacketHandled(true);
    }
}
