package com.kuzhi.findme.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Shared render policy for hiding the mounted entity below the first-person camera. */
public final class ClientRiddenMountRenderPolicy {
    private ClientRiddenMountRenderPolicy() {
    }

    public static boolean shouldHide(Minecraft minecraft, Entity entity) {
        if (minecraft == null
                || entity == null
                || ClientEntityPreviewRenderGuard.active()
                || minecraft.player == null
                || minecraft.options.getCameraType() != CameraType.FIRST_PERSON
                || !ClientWheelPresentationState.hideRiddenMountWhenLookingDown()
                || minecraft.player.getXRot() < 35.0F) {
            return false;
        }
        return minecraft.player.getVehicle() == entity;
    }
}
