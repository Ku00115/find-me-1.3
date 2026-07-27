package com.kuzhi.findme.client;

import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.RideHomeReadyPacket;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Waits for route geometry to be renderable before the destination cinematic moves. */
public final class ClientRideHomeReadyState {
    private static final int STABLE_TICKS = 8;
    private static UUID mountUuid;
    private static Vec3 routeStart = Vec3.ZERO;
    private static Vec3 destination = Vec3.ZERO;
    private static int stableTicks;
    private static int waitingTicks;
    private static boolean active;
    private static boolean sent;

    private ClientRideHomeReadyState() {
    }

    public static void awaitDestination(UUID expectedMountUuid, Vec3 expectedStart, Vec3 routeDestination) {
        if (active && expectedMountUuid != null && expectedMountUuid.equals(mountUuid)
                && routeStart.distanceToSqr(expectedStart) < 1.0E-4
                && destination.distanceToSqr(routeDestination) < 1.0E-4) {
            return;
        }
        mountUuid = expectedMountUuid;
        routeStart = expectedStart;
        destination = routeDestination;
        stableTicks = 0;
        waitingTicks = 0;
        active = true;
        sent = false;
    }

    public static void clear() {
        mountUuid = null;
        routeStart = Vec3.ZERO;
        destination = Vec3.ZERO;
        stableTicks = 0;
        waitingTicks = 0;
        active = false;
        sent = false;
    }

    public static void tick() {
        if (!active || sent) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stableTicks = 0;
            return;
        }
        waitingTicks++;
        boolean playerReady = minecraft.player.position().distanceToSqr(routeStart) <= 16.0;
        boolean chunksReady = routeChunksReceived(minecraft);
        boolean sectionReady = playerSectionCompiled(minecraft);
        boolean loadingScreen = minecraft.screen instanceof ReceivingLevelScreen;
        if (mountUuid == null || !playerReady || !chunksReady || !sectionReady || loadingScreen) {
            stableTicks = 0;
            if (waitingTicks == 20 || waitingTicks % 100 == 0) {
                FindMeMod.LOGGER.info("[FindMe ride-home] client load wait ticks={} player={} chunks={} section={} loadingScreen={} playerPos={} routeStart={} destination={}",
                        waitingTicks, playerReady, chunksReady, sectionReady, loadingScreen,
                        minecraft.player.blockPosition(), BlockPos.containing(routeStart),
                        BlockPos.containing(destination));
            }
            return;
        }
        if (++stableTicks < STABLE_TICKS) {
            return;
        }
        sent = true;
        ModNetwork.sendToServer(new RideHomeReadyPacket(mountUuid));
    }

    private static boolean routeChunksReceived(Minecraft minecraft) {
        Vec3 midpoint = routeStart.lerp(destination, 0.5);
        return minecraft.level.hasChunkAt(minecraft.player.blockPosition())
                && minecraft.level.hasChunkAt(BlockPos.containing(routeStart))
                && minecraft.level.hasChunkAt(BlockPos.containing(midpoint))
                && minecraft.level.hasChunkAt(BlockPos.containing(destination));
    }

    private static boolean playerSectionCompiled(Minecraft minecraft) {
        BlockPos playerPos = minecraft.player.blockPosition();
        return minecraft.level.isOutsideBuildHeight(playerPos.getY())
                || minecraft.levelRenderer.isSectionCompiled(playerPos)
                || minecraft.player.isSpectator()
                || !minecraft.player.isAlive();
    }
}
