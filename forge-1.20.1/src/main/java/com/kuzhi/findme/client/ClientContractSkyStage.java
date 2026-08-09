package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientContractSkyStage {
    private ClientContractSkyStage() {
    }

    public static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return ClientContractRenderState.active()
                && ClientContractRenderState.targetEntityId() >= 0
                && minecraft.level != null
                && minecraft.player != null;
    }

    public static boolean shouldRenderEntity(Entity entity) {
        if (!active()) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return entity == minecraft.player || entity.getId() == ClientContractRenderState.targetEntityId();
    }
}
