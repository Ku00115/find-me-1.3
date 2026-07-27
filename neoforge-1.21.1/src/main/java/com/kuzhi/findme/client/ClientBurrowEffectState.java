package com.kuzhi.findme.client;

import com.kuzhi.findme.network.RescueMagicPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ClientBurrowEffectState {
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final ParticleOptions WARM_DUST = new DustParticleOptions(new Vector3f(0.42f, 0.35f, 0.27f), 1.15f);
    private static final ParticleOptions ASH_DUST = new DustParticleOptions(new Vector3f(0.31f, 0.29f, 0.26f), 0.95f);

    private ClientBurrowEffectState() {
    }

    static void startEmerging(int entityId, Vec3 base, float radius, float height, int durationTicks, RescueMagicPacket.Purpose purpose) {
        start(new Effect(entityId, base, Math.max(0.25f, radius), Math.max(0.25f, height), Math.max(1, durationTicks), true, purpose));
    }

    static void startSinking(int entityId, Vec3 base, float radius, float height, int durationTicks) {
        start(new Effect(entityId, base, Math.max(0.25f, radius), Math.max(0.25f, height), Math.max(1, durationTicks), false, RescueMagicPacket.Purpose.SUMMON));
    }

    private static void start(Effect effect) {
        if (effect.entityId >= 0) {
            EFFECTS.removeIf(existing -> existing.entityId == effect.entityId);
        }
        EFFECTS.add(effect);
        if (EFFECTS.size() > 16) {
            EFFECTS.remove(0);
        }
    }

    static void tick() {
        Level level = Minecraft.getInstance().level;
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (level != null) {
                spawnDust(level, effect);
            }
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

    static double renderYOffset(int entityId, float partialTick) {
        if (entityId < 0) {
            return 0.0;
        }
        for (Effect effect : EFFECTS) {
            if (effect.entityId != entityId) {
                continue;
            }
            float progress = Mth.clamp((effect.age + partialTick) / (float)effect.durationTicks, 0.0f, 1.0f);
            double depth = effect.depth();
            if (effect.emerging) {
                float rise = smooth(Mth.clamp((progress - 0.08f) / 0.70f, 0.0f, 1.0f));
                return -depth * (1.0 - rise);
            }
            float sink = smooth(Mth.clamp((progress - 0.08f) / 0.70f, 0.0f, 1.0f));
            return -depth * sink;
        }
        return 0.0;
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static void spawnDust(Level level, Effect effect) {
        float progress = Mth.clamp(effect.age / (float)effect.durationTicks, 0.0f, 1.0f);
        float motion = smooth(Mth.clamp((progress - 0.08f) / 0.70f, 0.0f, 1.0f));
        float active = effect.emerging
                ? smooth(Mth.clamp((motion - 0.02f) / 0.70f, 0.0f, 1.0f)) * (1.0f - smooth(Mth.clamp((progress - 0.86f) / 0.14f, 0.0f, 1.0f)))
                : 1.0f - smooth(Mth.clamp((motion - 0.74f) / 0.22f, 0.0f, 1.0f));
        if (active <= 0.02f) {
            return;
        }
        RandomSource random = effect.random;
        float scale = Mth.clamp(effect.radius, 0.25f, 16.0f);
        int softCount = Mth.clamp(Math.round((1.2f + scale * 1.1f) * active) + (effect.age <= 1 ? 2 : 0), 1, effect.rescue() ? 24 : 16);
        if (!effect.rescue() && effect.age % 2 == 1) {
            softCount = Math.max(1, softCount - 2);
        }
        for (int i = 0; i < softCount; i++) {
            spawnSoftPuff(level, effect, random, active);
        }
        BlockState ground = groundState(level, effect.base);
        if (!ground.isAir() && effect.age % 2 == 0) {
            int gritCount = Mth.clamp(Math.round((scale * 0.7f + 0.8f) * active), 1, effect.rescue() ? 6 : 4);
            ParticleOptions grit = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < gritCount; i++) {
                spawnGroundGrit(level, effect, random, grit);
            }
        }
    }

    private static void spawnSoftPuff(Level level, Effect effect, RandomSource random, float active) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * effect.radius * (0.22 + 0.62 * active);
        double x = effect.base.x + Math.cos(angle) * distance + random.nextGaussian() * 0.035;
        double z = effect.base.z + Math.sin(angle) * distance + random.nextGaussian() * 0.035;
        double y = effect.base.y + 0.05 + random.nextDouble() * 0.13;
        double outward = 0.012 + random.nextDouble() * 0.018;
        double vx = Math.cos(angle) * outward + random.nextGaussian() * 0.006;
        double vz = Math.sin(angle) * outward + random.nextGaussian() * 0.006;
        double vy = effect.emerging ? 0.012 + random.nextDouble() * 0.018 : 0.004 + random.nextDouble() * 0.010;
        level.addParticle(random.nextInt(4) == 0 ? ASH_DUST : WARM_DUST, x, y, z, vx, vy, vz);
    }

    private static void spawnGroundGrit(Level level, Effect effect, RandomSource random, ParticleOptions grit) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * effect.radius * 0.72;
        double x = effect.base.x + Math.cos(angle) * distance;
        double z = effect.base.z + Math.sin(angle) * distance;
        double y = effect.base.y + 0.035 + random.nextDouble() * 0.055;
        double speed = 0.018 + random.nextDouble() * 0.026;
        level.addParticle(grit, x, y, z, Math.cos(angle) * speed, 0.025 + random.nextDouble() * 0.035, Math.sin(angle) * speed);
    }

    private static BlockState groundState(Level level, Vec3 base) {
        BlockPos pos = BlockPos.containing(base.x, base.y - 0.08, base.z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            state = level.getBlockState(pos.below());
        }
        return state;
    }

    static final class Effect {
        final int entityId;
        final Vec3 base;
        final float radius;
        final float height;
        final int durationTicks;
        final boolean emerging;
        final RescueMagicPacket.Purpose purpose;
        final RandomSource random = RandomSource.create();
        int age;

        Effect(int entityId, Vec3 base, float radius, float height, int durationTicks, boolean emerging, RescueMagicPacket.Purpose purpose) {
            this.entityId = entityId;
            this.base = base;
            this.radius = radius;
            this.height = height;
            this.durationTicks = durationTicks;
            this.emerging = emerging;
            this.purpose = purpose;
        }

        boolean rescue() {
            return purpose == RescueMagicPacket.Purpose.RESCUE;
        }

        double depth() {
            return Mth.clamp(height * 0.86f + radius * 0.25f, 0.30f, 4.75f);
        }
    }
}
