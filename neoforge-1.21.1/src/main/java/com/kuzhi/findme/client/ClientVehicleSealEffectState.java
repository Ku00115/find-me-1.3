package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.FindMeMod;
import com.kuzhi.findme.network.VehicleSealEffectPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ClientVehicleSealEffectState {
    private static final List<Effect> EFFECTS = new ArrayList<>();

    private ClientVehicleSealEffectState() {
    }

    public static void start(VehicleSealEffectPacket packet) {
        if (Config.enableDiagnosticLogging) {
            FindMeMod.LOGGER.info("[FindMe debug/effect-sizing] side=client kind=vehicle_seal radius={} height={} anchor={},{},{}",
                    packet.radius(), packet.height(), packet.x(), packet.y(), packet.z());
        }
        EFFECTS.add(new Effect(new Vec3(packet.x(), packet.y(), packet.z()), Math.max(0.30f, packet.radius()), Math.max(0.25f, packet.height()), Math.max(1, packet.durationTicks()), packet.customTexture()));
        if (EFFECTS.size() > 10) {
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
        final Vec3 base;
        final float radius;
        final float height;
        final int durationTicks;
        final boolean customTexture;
        int age;

        Effect(Vec3 base, float radius, float height, int durationTicks, boolean customTexture) {
            this.base = base;
            this.radius = radius;
            this.height = height;
            this.durationTicks = durationTicks;
            this.customTexture = customTexture;
        }
    }
}
