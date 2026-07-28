package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientTacticalTargetOutlineState {
    public static final int COLOR = 0xF04444;
    private static int entityId = -1;
    private static int remainingTicks;

    private ClientTacticalTargetOutlineState() {
    }

    public static void update(int targetEntityId, int durationTicks) {
        entityId = targetEntityId;
        remainingTicks = Math.max(0, durationTicks);
        if (targetEntityId < 0 || remainingTicks == 0) clear();
    }

    public static void tick() {
        if (remainingTicks > 0 && --remainingTicks == 0) clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId >= 0 && (minecraft.level == null || minecraft.level.getEntity(entityId) == null)) clear();
    }

    public static boolean shouldOutline(Entity entity) {
        return entity != null && remainingTicks > 0 && entity.getId() == entityId
                && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getEntity(entityId) == entity;
    }

    public static void clear() {
        entityId = -1;
        remainingTicks = 0;
    }
}
