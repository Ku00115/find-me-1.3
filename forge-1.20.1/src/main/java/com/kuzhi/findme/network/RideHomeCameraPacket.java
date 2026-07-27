package com.kuzhi.findme.network;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.client.ClientContractCamera;
import com.kuzhi.findme.client.ClientRideHomeOrientationState;
import com.kuzhi.findme.client.ClientRideHomeReadyState;
import com.kuzhi.findme.client.ClientRideHomeTransitionState;
import net.minecraft.world.phys.Vec3;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RideHomeCameraPacket(boolean active, int targetEntityId, int durationTicks,
                                   double playerX, double playerY, double playerZ,
                                   double targetX, double targetY, double targetZ,
                                   float targetRadius, Mode mode,
                                    float headingYaw, float headingPitch) {
public static void encode(RideHomeCameraPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeInt(packet.targetEntityId);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeDouble(packet.playerX);
        buffer.writeDouble(packet.playerY);
        buffer.writeDouble(packet.playerZ);
        buffer.writeDouble(packet.targetX);
        buffer.writeDouble(packet.targetY);
        buffer.writeDouble(packet.targetZ);
        buffer.writeFloat(packet.targetRadius);
        buffer.writeEnum(packet.mode);
        buffer.writeFloat(packet.headingYaw);
        buffer.writeFloat(packet.headingPitch);
    }

    public static RideHomeCameraPacket decode(FriendlyByteBuf buffer) {
        return new RideHomeCameraPacket(buffer.readBoolean(), buffer.readInt(), buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readEnum(Mode.class),
                buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(RideHomeCameraPacket packet, Supplier<FindMeNetworkContext.Context> contextSupplier) {
        FindMeNetworkContext.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            switch (packet.mode) {
                case START -> {
                    ClientRideHomeOrientationState.clear();
                    ClientRideHomeReadyState.clear();
                    startCamera(packet);
                }
                case TRACK -> retargetCamera(packet);
                case DESTINATION -> {
                    if (ClientContractCamera.active()) {
                        retargetCamera(packet);
                    } else {
                        startCamera(packet);
                    }
                }
                case HEADING -> {
                    ClientContractCamera.setRideHomeHeading(packet.headingYaw);
                }
                case BLACKOUT -> ClientRideHomeTransitionState.startBlackout(packet.durationTicks);
                case REVEAL -> ClientRideHomeTransitionState.reveal(packet.durationTicks);
                case STOP -> {
                    ClientRideHomeOrientationState.clear();
                    ClientRideHomeReadyState.clear();
                    ClientRideHomeTransitionState.clear();
                    ClientContractCamera.stop();
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void startCamera(RideHomeCameraPacket packet) {
        ContractCameraPacket cameraPacket = new ContractCameraPacket(true, packet.targetEntityId, packet.durationTicks,
                0.0f, 0.0f, packet.playerX, packet.playerY, packet.playerZ,
                packet.targetX, packet.targetY, packet.targetZ, 1.55f, packet.targetRadius,
                "", ContractCameraPacket.Mode.START, true);
        ClientContractCamera.startRideHome(cameraPacket, packet.headingYaw);
    }

    private static void retargetCamera(RideHomeCameraPacket packet) {
        ContractCameraPacket cameraPacket = new ContractCameraPacket(true, packet.targetEntityId, packet.durationTicks,
                0.0f, 0.0f, packet.playerX, packet.playerY, packet.playerZ,
                packet.targetX, packet.targetY, packet.targetZ, 1.55f, packet.targetRadius,
                "", ContractCameraPacket.Mode.START, true);
        ClientContractCamera.retargetRideHome(cameraPacket, packet.headingYaw);
    }

    public enum Mode {
        START,
        TRACK,
        DESTINATION,
        HEADING,
        BLACKOUT,
        REVEAL,
        STOP
    }
}
