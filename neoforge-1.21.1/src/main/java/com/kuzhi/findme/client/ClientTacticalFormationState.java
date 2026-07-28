package com.kuzhi.findme.client;

import com.kuzhi.findme.network.CompanionTacticalFormationPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class ClientTacticalFormationState {
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private ClientTacticalFormationState() { }
    public static void start(CompanionTacticalFormationPacket packet) {
        EFFECTS.removeIf(effect -> effect.operationUuid.equals(packet.operationUuid()));
        EFFECTS.add(new Effect(packet));
        while (EFFECTS.size() > 4) EFFECTS.remove(0);
    }
    public static void tick() {
        if (Minecraft.getInstance().level == null) { clear(); return; }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (++effect.age >= effect.durationTicks) iterator.remove();
        }
    }
    static List<Effect> effects() { return EFFECTS; }
    static void clear() { EFFECTS.clear(); }
    static final class Effect {
        final UUID operationUuid; final Vec3 center; final float centerRadius; final int durationTicks;
        final List<CompanionTacticalFormationPacket.Member> members; int age;
        Effect(CompanionTacticalFormationPacket packet) {
            operationUuid = packet.operationUuid(); center = new Vec3(packet.centerX(), packet.centerY(), packet.centerZ());
            centerRadius = packet.centerRadius(); durationTicks = Math.max(1, packet.durationTicks()); members = packet.members();
        }
    }
}
