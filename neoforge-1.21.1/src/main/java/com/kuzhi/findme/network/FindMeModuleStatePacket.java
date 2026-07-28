package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FindMeModuleStatePacket(long configuredMask, long effectiveMask, long availableMask,
                                      boolean canManage, int companionDeploymentLimit) implements CustomPacketPayload {
    public static final Type<FindMeModuleStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "module_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FindMeModuleStatePacket> STREAM_CODEC =
            NetworkCodecs.of(FindMeModuleStatePacket::encode, FindMeModuleStatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FindMeModuleStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.configuredMask);
        buffer.writeLong(packet.effectiveMask);
        buffer.writeLong(packet.availableMask);
        buffer.writeBoolean(packet.canManage);
        buffer.writeVarInt(packet.companionDeploymentLimit);
    }

    private static FindMeModuleStatePacket decode(FriendlyByteBuf buffer) {
        return new FindMeModuleStatePacket(buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(FindMeModuleStatePacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.kuzhi.findme.client.ClientFindMeModuleState.update(
                    packet.configuredMask, packet.effectiveMask, packet.availableMask, packet.canManage,
                    packet.companionDeploymentLimit);
            com.kuzhi.findme.client.FindMeAuiManageScreen.updateModuleState();
        });
        context.setPacketHandled(true);
    }
}
