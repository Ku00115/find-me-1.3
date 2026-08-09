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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PackAnimationPresetMotionPacket(List<String> entityTypes, CompanionAnimationPurpose purpose,
                                              CompanionAnimationStyle style) implements CustomPacketPayload {
    public static final Type<PackAnimationPresetMotionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "pack_animation_preset_motion"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PackAnimationPresetMotionPacket> STREAM_CODEC =
            NetworkCodecs.of(PackAnimationPresetMotionPacket::encode, PackAnimationPresetMotionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(PackAnimationPresetMotionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityTypes.size());
        for (String entityType : packet.entityTypes) buffer.writeUtf(entityType, 128);
        buffer.writeEnum(packet.purpose);
        buffer.writeEnum(packet.style);
    }

    private static PackAnimationPresetMotionPacket decode(FriendlyByteBuf buffer) {
        int size = PacketDecodeLimits.readCount(buffer, PacketDecodeLimits.MAX_PRESET_ENTRIES,
                "animation preset motion entity");
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
