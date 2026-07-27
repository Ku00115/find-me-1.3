package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.common.DoctorCommandAction;
import com.kuzhi.findme.server.safety.DoctorPageService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record DoctorCommandPacket(DoctorCommandAction action, UUID targetPlayer, int backupIndex, long expectedSavedAt,
                                  int detailPage, String backupName) {
    public DoctorCommandPacket(DoctorCommandAction action, UUID targetPlayer, int backupIndex, long expectedSavedAt,
                               int detailPage) {
        this(action, targetPlayer, backupIndex, expectedSavedAt, detailPage, "");
    }

    public DoctorCommandPacket {
        backupName = backupName == null ? "" : backupName;
    }
public static void encode(DoctorCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeBoolean(packet.targetPlayer != null);
        if (packet.targetPlayer != null) buffer.writeUUID(packet.targetPlayer);
        buffer.writeVarInt(Math.max(0, packet.backupIndex));
        buffer.writeLong(packet.expectedSavedAt);
        buffer.writeVarInt(Math.max(0, packet.detailPage));
        buffer.writeUtf(packet.backupName, 64);
    }

    public static DoctorCommandPacket decode(FriendlyByteBuf buffer) {
        DoctorCommandAction action = buffer.readEnum(DoctorCommandAction.class);
        UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        return new DoctorCommandPacket(action, target, buffer.readVarInt(), buffer.readLong(), buffer.readVarInt(),
                buffer.readUtf(64));
    }

    public static void handle(DoctorCommandPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) DoctorPageService.handle(context.getSender(), packet);
        });
        context.setPacketHandled(true);
    }
}
