package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SummonedOutlineMode;
import com.kuzhi.findme.network.CompanionListPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientSummonedOutlineState {
    private ClientSummonedOutlineState() {}
    public static boolean shouldOutline(Entity entity) {
        if (entity == null || mode() == SummonedOutlineMode.OFF) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(entity.getId()) != entity) return false;
        return deployedEntry(CompanionKind.MOUNT, entity) || deployedEntry(CompanionKind.COMPANION, entity);
    }
    public static int color(Entity entity, int vanillaColor) { return shouldOutline(entity) ? mode().color() : vanillaColor; }
    private static SummonedOutlineMode mode() { return ClientWheelPresentationState.summonedOutlineMode(); }
    private static boolean deployedEntry(CompanionKind kind, Entity entity) {
        for (CompanionListPacket.Entry entry : ClientCompanionState.allEntries(kind)) {
            if (entry.entityId() == entity.getId() && entry.uuid().equals(entity.getUUID())
                    && entry.alive() && (entry.deployed() || entry.ridden())) return true;
        }
        return false;
    }
}
