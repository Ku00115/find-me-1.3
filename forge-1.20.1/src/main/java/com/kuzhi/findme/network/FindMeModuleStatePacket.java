package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record FindMeModuleStatePacket(long configuredMask, long effectiveMask, long availableMask,
                                      boolean canManage, boolean canManageDefaults,
                                      int companionDeploymentLimit, int companionDeploymentMaximum,
                                      boolean creatureArrivalVoice) {
public static void encode(FindMeModuleStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.configuredMask);
        buffer.writeLong(packet.effectiveMask);
        buffer.writeLong(packet.availableMask);
        buffer.writeBoolean(packet.canManage);
        buffer.writeBoolean(packet.canManageDefaults);
        buffer.writeVarInt(packet.companionDeploymentLimit);
        buffer.writeVarInt(packet.companionDeploymentMaximum);
        buffer.writeBoolean(packet.creatureArrivalVoice);
    }

    public static FindMeModuleStatePacket decode(FriendlyByteBuf buffer) {
        return new FindMeModuleStatePacket(buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean());
    }

    public static void handle(FindMeModuleStatePacket packet,
                              Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            com.kuzhi.findme.client.ClientFindMeModuleState.update(
                    packet.configuredMask, packet.effectiveMask, packet.availableMask, packet.canManage,
                    packet.canManageDefaults, packet.companionDeploymentLimit,
                    packet.companionDeploymentMaximum, packet.creatureArrivalVoice);
            com.kuzhi.findme.client.FindMeAuiManageScreen.updateModuleState();
        });
        context.setPacketHandled(true);
    }
}
