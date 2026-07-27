package com.kuzhi.findme.client;

import net.minecraft.world.entity.Entity;

public final class FindMeClientEffects {
    private FindMeClientEffects() {
    }

    public static void triggerVelocityBurst(Entity entity, float intensity) {
        triggerVelocityBurst(entity, intensity, false);
    }

    public static void triggerVelocityBurst(Entity entity, float intensity, boolean threeDimensional) {
        ClientRescueMagicRenderState.triggerVelocityBurst(entity, intensity, true, threeDimensional, false);
    }

    public static void triggerVelocityTrailRing(Entity entity, float intensity) {
        ClientRescueMagicRenderState.triggerVelocityBurst(entity, intensity, false, false, false);
    }

    public static void triggerFlightVelocityTrailRing(Entity entity, float intensity) {
        ClientRescueMagicRenderState.triggerVelocityBurst(entity, intensity, false, true, false);
    }

    public static void triggerFluidVelocityTrailRing(Entity entity, float intensity) {
        ClientRescueMagicRenderState.triggerVelocityBurst(entity, intensity, false, true, false);
    }
}
