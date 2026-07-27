package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.CompanionEffectPurpose;
import com.kuzhi.findme.common.CompanionEffectStyle;
import com.kuzhi.findme.server.ui.CompanionEffectStyleHandler;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompanionEffectStylePacket(UUID uuid, CompanionEffectPurpose purpose, CompanionEffectStyle style) implements CustomPacketPayload {
    public static final Type<CompanionEffectStylePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "companion_effect_style"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionEffectStylePacket> STREAM_CODEC = NetworkCodecs.of(CompanionEffectStylePacket::encode, CompanionEffectStylePacket::decode);
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void encode(CompanionEffectStylePacket p, FriendlyByteBuf b) { b.writeUUID(p.uuid); b.writeEnum(p.purpose); b.writeEnum(p.style); }
    public static CompanionEffectStylePacket decode(FriendlyByteBuf b) { return new CompanionEffectStylePacket(b.readUUID(), b.readEnum(CompanionEffectPurpose.class), b.readEnum(CompanionEffectStyle.class)); }
    public static void handle(CompanionEffectStylePacket p, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context c = supplier.get(); c.enqueueWork(() -> { if (c.getSender() != null
                && FindMeModuleService.require(c.getSender(), FindMeModule.MANAGEMENT)) CompanionEffectStyleHandler.set(c.getSender(), p.uuid, p.purpose, p.style); }); c.setPacketHandled(true);
    }
}
