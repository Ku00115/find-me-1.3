package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.network.StorageEffectPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ClientStorageEffectState {
    private static final List<Effect> EFFECTS = new ArrayList<>();

    private ClientStorageEffectState() {
    }

    public static void start(StorageEffectPacket packet) {
        if (Config.enableDiagnosticLogging) {
            FindMeMod.LOGGER.info("[FindMe debug/effect-sizing] side=client kind=storage entityId={} visual={} radius={} height={} anchor={},{},{}",
                    packet.entityId(), packet.visual(), packet.radius(), packet.height(), packet.x(), packet.y(), packet.z());
        }
        if (packet.groundSink()) {
            ClientBurrowEffectState.startSinking(packet.entityId(), new Vec3(packet.x(), packet.y(), packet.z()), packet.radius(), packet.height(), Math.max(1, packet.durationTicks()));
            return;
        }
        EFFECTS.removeIf(effect -> effect.entityId == packet.entityId());
        EFFECTS.add(new Effect(packet.entityId(), new Vec3(packet.x(), packet.y(), packet.z()), Math.max(0.30f, packet.radius()), Math.max(0.25f, packet.height()), Math.max(1, packet.durationTicks()), packet.customTexture(), packet.groundSink()));
        if (EFFECTS.size() > 12) {
            EFFECTS.remove(0);
        }
    }

    static void tick() {
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            effect.age++;
            if (effect.age >= effect.durationTicks) {
                iterator.remove();
            }
        }
    }

    static List<Effect> effects() {
        return EFFECTS;
    }

    static void clear() {
        EFFECTS.clear();
    }

    static final class Effect {
        final int entityId;
        final Vec3 fallbackBase;
        final float radius;
        final float height;
        final int durationTicks;
        final boolean customTexture;
        final boolean groundSink;
        int age;

        Effect(int entityId, Vec3 fallbackBase, float radius, float height, int durationTicks, boolean customTexture, boolean groundSink) {
            this.entityId = entityId;
            this.fallbackBase = fallbackBase;
            this.radius = radius;
            this.height = height;
            this.durationTicks = durationTicks;
            this.customTexture = customTexture;
            this.groundSink = groundSink;
        }
    }
}
