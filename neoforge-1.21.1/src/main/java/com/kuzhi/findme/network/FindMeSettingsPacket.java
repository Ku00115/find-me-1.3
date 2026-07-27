package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.FindMeSettingsAction;
import com.kuzhi.findme.common.FindMeUiSettings;
import com.kuzhi.findme.server.ui.FindMeSettingsService;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record FindMeSettingsPacket(FindMeSettingsAction action, FindMeUiSettings settings, boolean success, String message) implements CustomPacketPayload {
    public static final Type<FindMeSettingsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "ui_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FindMeSettingsPacket> STREAM_CODEC = NetworkCodecs.of(FindMeSettingsPacket::encode, FindMeSettingsPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FindMeSettingsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeNbt((packet.settings == null ? FindMeUiSettings.defaults() : packet.settings).save());
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message == null ? "" : packet.message, 256);
    }

    private static FindMeSettingsPacket decode(FriendlyByteBuf buffer) {
        FindMeSettingsAction action = buffer.readEnum(FindMeSettingsAction.class);
        CompoundTag tag = buffer.readNbt();
        return new FindMeSettingsPacket(action, FindMeUiSettings.load(tag), buffer.readBoolean(), buffer.readUtf(256));
    }

    public static void handle(FindMeSettingsPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        if (packet.action.updatesClient()) {
            handleClient(packet, contextSupplier);
        } else {
            handleServer(packet, contextSupplier);
        }
    }

    public static void handleServer(FindMeSettingsPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            FindMeSettingsService.handle(player, packet.action, packet.settings);
        });
        context.setPacketHandled(true);
    }

    public static void handleClient(FindMeSettingsPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.kuzhi.findme.client.ClientWheelPresentationState.update(packet.settings());
            com.kuzhi.findme.client.ClientCompanionTeamState.applyDefaultTeam(
                    com.kuzhi.findme.client.ClientWheelPresentationState.defaultTeamIndex());
            com.kuzhi.findme.client.FindMeAuiManageScreen.updateSettings(packet.settings(), packet.success(), packet.message());
        });
        context.setPacketHandled(true);
    }
}
