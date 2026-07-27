package com.kuzhi.findme.client;

import com.kuzhi.findme.network.ContractCameraPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public final class ClientContractCamera {
    private static CameraType previousCameraType;
    private static Entity previousCameraEntity;
    private static ArmorStand cameraEntity;
    private static int targetEntityId = -1;
    private static int ticksRemaining;
    private static int totalTicks;
    private static Vec3 playerAnchor = Vec3.ZERO;
    private static Vec3 targetAnchor = Vec3.ZERO;
    private static float targetRadius = 2.2f;
    private static double sideSign = 1.0;
    private static Vec3 smoothedCameraPosition;
    private static Vec3 smoothedLookAt;
    private static boolean renderLocalRider;
    private static boolean rideHomeCamera;
    private static float desiredHeadingYaw;
    private static float smoothedHeadingYaw;

    private ClientContractCamera() {
    }

    public static void start(ContractCameraPacket packet) {
        start(packet, false);
    }

    public static void startRideHome(ContractCameraPacket packet) {
        startRideHome(packet, packet.yaw());
    }

    public static void startRideHome(ContractCameraPacket packet, float headingYaw) {
        desiredHeadingYaw = Mth.wrapDegrees(headingYaw);
        smoothedHeadingYaw = desiredHeadingYaw;
        start(packet, true);
    }

    private static void start(ContractCameraPacket packet, boolean rideHome) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        boolean restarting = cameraEntity != null;
        if (!restarting) {
            previousCameraType = minecraft.options.getCameraType();
            previousCameraEntity = minecraft.getCameraEntity();
        } else {
            boolean changedLevel = cameraEntity.level() != minecraft.level;
            if (minecraft.getCameraEntity() == cameraEntity) {
                minecraft.setCameraEntity(minecraft.player);
            }
            if (changedLevel) {
                previousCameraEntity = minecraft.player;
            }
        }
        targetEntityId = packet.targetEntityId();
        ticksRemaining = Math.max(packet.durationTicks(), 20);
        totalTicks = ticksRemaining;
        playerAnchor = new Vec3(packet.playerX(), packet.playerY(), packet.playerZ());
        targetAnchor = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
        targetRadius = packet.targetRadius();
        renderLocalRider = rideHome;
        rideHomeCamera = rideHome;
        sideSign = chooseSideSign(minecraft);
        smoothedCameraPosition = null;
        smoothedLookAt = null;
        cameraEntity = new ArmorStand(minecraft.level, minecraft.player.getX(), minecraft.player.getY() + 2.0, minecraft.player.getZ());
        cameraEntity.setInvisible(true);
        cameraEntity.setNoGravity(true);
        cameraEntity.noPhysics = true;
        updateCamera(minecraft, 0.0f);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.gameRenderer.setRenderHand(false);
        minecraft.setCameraEntity(cameraEntity);
    }

    public static void finish(int durationTicks) {
        stop();
    }

    public static void stop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() == cameraEntity) {
            minecraft.setCameraEntity(validPreviousCamera(minecraft));
        }
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        minecraft.gameRenderer.setRenderHand(true);
        previousCameraType = null;
        previousCameraEntity = null;
        cameraEntity = null;
        targetEntityId = -1;
        ticksRemaining = 0;
        totalTicks = 0;
        playerAnchor = Vec3.ZERO;
        targetAnchor = Vec3.ZERO;
        targetRadius = 2.2f;
        sideSign = 1.0;
        smoothedCameraPosition = null;
        smoothedLookAt = null;
        renderLocalRider = false;
        rideHomeCamera = false;
        desiredHeadingYaw = 0.0f;
        smoothedHeadingYaw = 0.0f;
    }

    private static Entity validPreviousCamera(Minecraft minecraft) {
        if (previousCameraEntity == null || previousCameraEntity.isRemoved()
                || previousCameraEntity.level() != minecraft.level
                || previousCameraEntity instanceof net.minecraft.world.entity.player.Player
                && previousCameraEntity != minecraft.player) {
            return minecraft.player;
        }
        return previousCameraEntity;
    }

    public static void tick() {
        if (ticksRemaining <= 0) {
            if (cameraEntity != null) {
                stop();
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || cameraEntity == null
                || cameraEntity.level() != minecraft.level) {
            stop();
            return;
        }
        updateCamera(minecraft, 1.0f - (float)ticksRemaining / (float)Math.max(totalTicks, 1));
        if (minecraft.getCameraEntity() != cameraEntity) {
            minecraft.setCameraEntity(cameraEntity);
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        minecraft.gameRenderer.setRenderHand(false);
        ticksRemaining--;
    }

    public static boolean active() {
        return ticksRemaining > 0 && cameraEntity != null;
    }

    public static boolean renderLocalRider() {
        return renderLocalRider;
    }

    public static void setRideHomeHeading(float headingYaw) {
        desiredHeadingYaw = Mth.wrapDegrees(headingYaw);
    }

    public static void retargetRideHome(ContractCameraPacket packet, float headingYaw) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || cameraEntity == null
                || cameraEntity.level() != minecraft.level) {
            startRideHome(packet, headingYaw);
            return;
        }
        targetEntityId = packet.targetEntityId();
        targetAnchor = new Vec3(packet.targetX(), packet.targetY(), packet.targetZ());
        targetRadius = packet.targetRadius();
        desiredHeadingYaw = Mth.wrapDegrees(headingYaw);
        ticksRemaining = Math.max(ticksRemaining, Math.max(packet.durationTicks(), 20));
        totalTicks = Math.max(totalTicks, ticksRemaining);
    }

    private static void updateCamera(Minecraft minecraft, float progress) {
        ClientLevel level = minecraft.level;
        if (minecraft.player == null || level == null || cameraEntity == null) {
            return;
        }
        Entity target = level.getEntity(targetEntityId);
        Entity riddenMount = rideHomeCamera ? minecraft.player.getVehicle() : null;
        Entity visualSubject = riddenMount == null ? target : riddenMount;
        Vec3 playerBase = minecraft.player.position();
        Vec3 targetBase = target == null ? targetAnchor : target.position();
        double targetHeight = visualSubject == null ? targetRadius * 1.8 : visualSubject.getBbHeight();
        double targetWidth = visualSubject == null ? targetRadius * 1.8 : visualSubject.getBbWidth();
        Vec3 playerCenter = playerBase.add(0.0, Math.max(1.2, minecraft.player.getBbHeight() * 0.72), 0.0);
        Vec3 targetCenter = targetBase.add(0.0, Math.max(1.0, targetHeight * 0.48), 0.0);
        boolean ridingTarget = riddenMount != null;
        if (ridingTarget) {
            targetCenter = playerCenter;
        }
        Vec3 midpoint = ridingTarget ? targetCenter : playerCenter.add(targetCenter).scale(0.5);
        Vec3 axis = horizontal(targetCenter.subtract(playerCenter));
        if (ridingTarget && target != null && rideHomeCamera) {
            smoothedHeadingYaw = Mth.rotLerp(0.16f, smoothedHeadingYaw, desiredHeadingYaw);
            double radians = Math.toRadians(smoothedHeadingYaw);
            axis = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
        } else if (axis.lengthSqr() < 0.01) {
            axis = horizontal(minecraft.player.getLookAngle());
        }
        if (axis.lengthSqr() < 0.01) {
            axis = new Vec3(1.0, 0.0, 0.0);
        }
        axis = axis.normalize();
        Vec3 side = new Vec3(-axis.z, 0.0, axis.x);
        side = side.scale(sideSign);
        double separation = Math.sqrt(horizontal(targetCenter.subtract(playerCenter)).lengthSqr());
        double distance = ridingTarget
                ? Mth.clamp(targetWidth * 0.92 + 5.8, 7.0, 22.0)
                : Mth.clamp(separation * 0.58 + targetWidth * 1.05 + 4.4, 6.5, 19.0);
        double height = ridingTarget
                ? Mth.clamp(Math.max(minecraft.player.getBbHeight(), targetHeight) * 0.18 + 2.7, 3.0, 7.5)
                : Mth.clamp(Math.max(minecraft.player.getBbHeight(), targetHeight) * 0.30 + 2.25, 2.8, 8.2);
        Vec3 cameraAnchor = rideHomeCamera ? playerCenter : midpoint;
        Vec3 desiredCameraPos = cameraAnchor.add(side.scale(distance)).add(0.0, height, 0.0);
        Vec3 desiredLookAt = ridingTarget ? targetCenter : midpoint.add(0.0, Math.max(0.8, targetHeight * 0.12), 0.0);
        if (smoothedCameraPosition == null || smoothedLookAt == null) {
            smoothedCameraPosition = desiredCameraPos;
            smoothedLookAt = desiredLookAt;
        } else {
            double positionSmoothing = ridingTarget ? 0.12 : 0.24;
            double lookSmoothing = ridingTarget ? 0.10 : 0.24;
            smoothedCameraPosition = smoothedCameraPosition.lerp(desiredCameraPos, positionSmoothing);
            smoothedLookAt = smoothedLookAt.lerp(desiredLookAt, lookSmoothing);
        }
        float yaw = yawToward(smoothedCameraPosition, smoothedLookAt);
        float pitch = pitchToward(smoothedCameraPosition, smoothedLookAt);
        double oldX = cameraEntity.getX();
        double oldY = cameraEntity.getY();
        double oldZ = cameraEntity.getZ();
        float oldYaw = cameraEntity.getYRot();
        float oldPitch = cameraEntity.getXRot();
        cameraEntity.moveTo(smoothedCameraPosition.x, smoothedCameraPosition.y, smoothedCameraPosition.z, yaw, pitch);
        cameraEntity.xo = oldX;
        cameraEntity.yo = oldY;
        cameraEntity.zo = oldZ;
        cameraEntity.yRotO = oldYaw;
        cameraEntity.xRotO = oldPitch;
        cameraEntity.setYHeadRot(yaw);
        cameraEntity.yHeadRotO = oldYaw;
    }

    private static double chooseSideSign(Minecraft minecraft) {
        Vec3 playerCenter = playerAnchor.add(0.0, Math.max(1.2, minecraft.player.getBbHeight() * 0.72), 0.0);
        Vec3 targetCenter = targetAnchor.add(0.0, Math.max(1.0, targetRadius * 0.8), 0.0);
        Vec3 axis = horizontal(targetCenter.subtract(playerCenter));
        if (axis.lengthSqr() < 0.01) {
            axis = horizontal(minecraft.player.getLookAngle());
        }
        if (axis.lengthSqr() < 0.01) {
            axis = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 side = new Vec3(-axis.normalize().z, 0.0, axis.normalize().x);
        return horizontal(minecraft.player.getLookAngle()).dot(side) < 0.0 ? -1.0 : 1.0;
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0, vector.z);
    }

    private static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Mth.wrapDegrees((float)(Mth.atan2(dz, dx) * 57.2957763671875) - 90.0f);
    }

    private static float pitchToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return Mth.clamp((float)(-(Mth.atan2(dy, horizontal) * 57.2957763671875)), -35.0f, 42.0f);
    }
}
