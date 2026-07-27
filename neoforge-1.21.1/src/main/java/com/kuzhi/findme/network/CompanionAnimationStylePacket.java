package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionAnimationPurpose;
import com.kuzhi.findme.common.CompanionAnimationStyle;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import com.kuzhi.findme.server.ui.CompanionAnimationStyleHandler;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionAnimationStylePacket(UUID uuid, CompanionAnimationPurpose purpose,
                                            CompanionAnimationStyle style) implements CustomPacketPayload {
    public static final Type<CompanionAnimationStylePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_animation_style"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionAnimationStylePacket> STREAM_CODEC =
            NetworkCodecs.of(CompanionAnimationStylePacket::encode, CompanionAnimationStylePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(CompanionAnimationStylePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.uuid);
        buffer.writeEnum(packet.purpose);
        buffer.writeEnum(packet.style);
    }

    private static CompanionAnimationStylePacket decode(FriendlyByteBuf buffer) {
        return new CompanionAnimationStylePacket(buffer.readUUID(), buffer.readEnum(CompanionAnimationPurpose.class),
                buffer.readEnum(CompanionAnimationStyle.class));
    }

    public static void handle(CompanionAnimationStylePacket packet,
                              Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null
                    && FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)) {
                CompanionAnimationStyleHandler.set(context.getSender(), packet.uuid, packet.purpose, packet.style);
            }
        });
        context.setPacketHandled(true);
    }
}
