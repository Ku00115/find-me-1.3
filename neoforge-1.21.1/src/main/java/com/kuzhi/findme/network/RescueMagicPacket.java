package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientRescueMagicRenderState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record RescueMagicPacket(int entityId, double x, double y, double z, float radius, float height, float yaw, int durationTicks, Style style, Purpose purpose, Visual visual) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RescueMagicPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "rescue_magic"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RescueMagicPacket> STREAM_CODEC = NetworkCodecs.of(RescueMagicPacket::encode, RescueMagicPacket::decode);

    public RescueMagicPacket(double x, double y, double z, float radius, float height, float yaw, int durationTicks, Style style, Purpose purpose, Visual visual) {
        this(-1, x, y, z, radius, height, yaw, durationTicks, style, purpose, visual);
    }

    public RescueMagicPacket(double x, double y, double z, float radius, float height, float yaw, int durationTicks, Style style, Purpose purpose, boolean customTexture) {
        this(-1, x, y, z, radius, height, yaw, durationTicks, style, purpose, customTexture ? Visual.CUSTOM_TEXTURE : Visual.DEFAULT);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void encode(RescueMagicPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.radius);
        buffer.writeFloat(packet.height);
        buffer.writeFloat(packet.yaw);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeEnum(packet.style);
        buffer.writeEnum(packet.purpose);
        buffer.writeEnum(packet.visual);
    }

    public static RescueMagicPacket decode(FriendlyByteBuf buffer) {
        return new RescueMagicPacket(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readEnum(Style.class), buffer.readEnum(Purpose.class), buffer.readEnum(Visual.class));
    }

    public static void handle(RescueMagicPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientRescueMagicRenderState.start(packet));
        context.setPacketHandled(true);
    }

    public enum Style {
        VERTICAL_PORTAL,
        GROUND_CIRCLE
    }

    public enum Purpose {
        SUMMON,
        RESCUE
    }

    public enum Visual {
        DEFAULT,
        VELOCITY_BURST,
        CUSTOM_TEXTURE,
        GROUND_EMERGE
    }

    public boolean customTexture() {
        return visual == Visual.CUSTOM_TEXTURE;
    }

    public boolean velocityBurst() {
        return visual == Visual.VELOCITY_BURST;
    }

    public boolean groundEmerge() {
        return visual == Visual.GROUND_EMERGE;
    }
}
