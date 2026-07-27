package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.PackEditorAction;
import com.kuzhi.findme.server.command.PackPresetCommandHandler;
import com.kuzhi.findme.server.profile.PackAnimationPresetService;
import com.kuzhi.findme.common.FindMeModule;
import com.kuzhi.findme.server.module.FindMeModuleService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PackEditorActionPacket(PackEditorAction action) implements CustomPacketPayload {
    public static final Type<PackEditorActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "pack_editor_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PackEditorActionPacket> STREAM_CODEC = NetworkCodecs.of(PackEditorActionPacket::encode, PackEditorActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(PackEditorActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
    }

    public static PackEditorActionPacket decode(FriendlyByteBuf buffer) {
        return new PackEditorActionPacket(buffer.readEnum(PackEditorAction.class));
    }

    public static void handle(PackEditorActionPacket packet, Supplier<FindMeNetworkContext.Context> supplier) {
        FindMeNetworkContext.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null || !FindMeModuleService.require(context.getSender(), FindMeModule.MANAGEMENT)
                    || !PackAnimationPresetService.isEditor(context.getSender())) return;
            switch (packet.action) {
                case CLOSE -> PackPresetCommandHandler.closeEditor(context.getSender());
                case RELOAD -> PackPresetCommandHandler.reload(context.getSender());
                case EXPORT -> PackPresetCommandHandler.exportPath(context.getSender());
            }
        });
        context.setPacketHandled(true);
    }
}
