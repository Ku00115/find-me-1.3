package com.kuzhi.findme.network;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.minecraft.network.codec.StreamCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;

import com.kuzhi.findme.FindMeMod;

import com.kuzhi.findme.client.ClientContractCamera;
import com.kuzhi.findme.client.ClientContractRenderState;
import com.kuzhi.findme.client.ClientMountApproachPresentationState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.kuzhi.findme.network.FindMeNetworkContext;

public record ContractCameraPacket(boolean active, int targetEntityId, int durationTicks, float yaw, float pitch, double playerX, double playerY, double playerZ, double targetX, double targetY, double targetZ, float playerRadius, float targetRadius, String targetName, Mode mode, boolean cameraEnabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ContractCameraPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(FindMeMod.MODID, "contract_camera"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContractCameraPacket> STREAM_CODEC = NetworkCodecs.of(ContractCameraPacket::encode, ContractCameraPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public ContractCameraPacket(boolean active, int targetEntityId, int durationTicks) {
        this(active, targetEntityId, durationTicks, 0.0f, 0.0f, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.4f, 2.2f, "", active ? Mode.START : Mode.STOP, true);
    }

    public ContractCameraPacket(boolean active, int targetEntityId, int durationTicks, float yaw, float pitch, double playerX, double playerY, double playerZ, double targetX, double targetY, double targetZ, float playerRadius, float targetRadius, String targetName) {
        this(active, targetEntityId, durationTicks, yaw, pitch, playerX, playerY, playerZ, targetX, targetY, targetZ, playerRadius, targetRadius, targetName, active ? Mode.START : Mode.STOP, true);
    }

    public ContractCameraPacket(boolean active, int targetEntityId, int durationTicks, float yaw, float pitch, double playerX, double playerY, double playerZ, double targetX, double targetY, double targetZ, float playerRadius, float targetRadius, String targetName, Mode mode) {
        this(active, targetEntityId, durationTicks, yaw, pitch, playerX, playerY, playerZ, targetX, targetY, targetZ, playerRadius, targetRadius, targetName, mode, true);
    }

    public ContractCameraPacket(boolean active, int targetEntityId, int durationTicks, float yaw, float pitch, double playerX, double playerY, double playerZ, double targetX, double targetY, double targetZ, float playerRadius, float targetRadius, String targetName, Mode mode, boolean cameraEnabled) {
        this.active = active;
        this.targetEntityId = targetEntityId;
        this.durationTicks = durationTicks;
        this.yaw = yaw;
        this.pitch = pitch;
        this.playerX = playerX;
        this.playerY = playerY;
        this.playerZ = playerZ;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.playerRadius = playerRadius;
        this.targetRadius = targetRadius;
        this.targetName = targetName == null ? "" : targetName;
        this.mode = mode == null ? (active ? Mode.START : Mode.STOP) : mode;
        this.cameraEnabled = cameraEnabled;
    }

    public static void encode(ContractCameraPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeInt(packet.targetEntityId);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeFloat(packet.yaw);
        buffer.writeFloat(packet.pitch);
        buffer.writeDouble(packet.playerX);
        buffer.writeDouble(packet.playerY);
        buffer.writeDouble(packet.playerZ);
        buffer.writeDouble(packet.targetX);
        buffer.writeDouble(packet.targetY);
        buffer.writeDouble(packet.targetZ);
        buffer.writeFloat(packet.playerRadius);
        buffer.writeFloat(packet.targetRadius);
        buffer.writeUtf(packet.targetName, 128);
        buffer.writeEnum(packet.mode);
        buffer.writeBoolean(packet.cameraEnabled);
    }

    public static ContractCameraPacket decode(FriendlyByteBuf buffer) {
        return new ContractCameraPacket(buffer.readBoolean(), buffer.readInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readUtf(128), buffer.readEnum(Mode.class), buffer.readBoolean());
    }

    public static void handle(ContractCameraPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (packet.mode == Mode.MOUNT_APPROACH) {
                if (packet.active) {
                    ClientMountApproachPresentationState.hide(packet.targetEntityId(), packet.durationTicks());
                }
            } else if (packet.mode == Mode.START || packet.active) {
                if (packet.cameraEnabled) {
                    ClientContractCamera.start(packet);
                } else {
                    ClientContractCamera.stop();
                }
                ClientContractRenderState.start(packet);
            } else if (packet.mode == Mode.COMPLETE) {
                ClientContractCamera.finish(packet.durationTicks);
                ClientContractRenderState.finish(packet, ClientContractRenderState.Outcome.COMPLETE);
            } else if (packet.mode == Mode.CANCEL) {
                ClientContractCamera.finish(packet.durationTicks);
                ClientContractRenderState.finish(packet, ClientContractRenderState.Outcome.CANCEL);
            } else {
                ClientContractCamera.stop();
                ClientContractRenderState.stop();
            }
        });
        context.setPacketHandled(true);
    }

    public enum Mode {
        START,
        STOP,
        COMPLETE,
        CANCEL,
        MOUNT_APPROACH
    }
}
